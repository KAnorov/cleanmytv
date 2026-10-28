package ru.cleanmytv.app

import android.content.Context
import java.io.File

object LauncherOps {

    private val DISABLE_STOCK = setOf(
        "com.mitv.tvhome.atv",
        "com.mitv.tvhome",
        "com.google.android.tvlauncher",
        "com.google.android.apps.tv.launcherx"
    )

    private val STOCK_ORDER = listOf(
        "com.mitv.tvhome.atv",
        "com.mitv.tvhome",
        "com.google.android.tvlauncher",
        "com.google.android.apps.tv.launcherx",
        "com.tcl.tvlauncher",
        "com.tcl.tv",
        "com.mediatek.tvlauncher",
        "com.philips.tvlauncher",
        "com.sony.dtv.launcher",
        "com.google.android.leanbacklauncher"
    )

    fun status(tv: TvClient): LauncherStatus {
        val sets = tv.packageSets()
        val installed = CLEAN_LAUNCHER in sets.installed
        val home = tv.homeComponent()
        val isHome = home?.startsWith("$CLEAN_LAUNCHER/") == true
        val enabledStock = DISABLE_STOCK.filter { it in sets.enabled }.sorted()
        val stock = STOCK_ORDER.firstOrNull { it in sets.installed }
        return LauncherStatus(
            installed = installed,
            versionName = if (installed) tv.versionOf(CLEAN_LAUNCHER) else null,
            isHome = isHome,
            homeComponent = home,
            stockLauncher = stock,
            enabledStock = enabledStock
        )
    }

    /**
     * Makes the clean launcher the one Android starts after a reboot: Home points at it and the
     * stock launchers are switched off for the user, so they cannot take Home back.
     */
    fun makeDefault(tv: TvClient): ApplyResult {
        val before = tv.packageSets()
        if (CLEAN_LAUNCHER !in before.installed) {
            throw IllegalStateException("Сначала установите лаунчер.")
        }

        runCatching { tv.startLauncher() }
        tv.setHome(CLEAN_HOME)

        val warnings = mutableListOf<String>()
        for (pkg in DISABLE_STOCK) {
            if (pkg !in before.enabled) continue
            try {
                tv.disablePackage(pkg)
            } catch (e: Throwable) {
                warnings += "$pkg: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        val after = tv.packageSets()
        val stillEnabled = DISABLE_STOCK.filter { it in after.enabled }.sorted()
        for (pkg in stillEnabled) {
            warnings += "Не удалось отключить $pkg: он может снова открыться после перезагрузки."
        }

        val home = tv.homeComponent()
        if (home?.startsWith("$CLEAN_LAUNCHER/") != true) {
            runCatching { useStock(tv) }
            throw IllegalStateException(
                "Home не переключился на новый лаунчер (сейчас ${home ?: "не задан"}). " +
                    "Штатный лаунчер возвращён."
            )
        }

        val pinned = stillEnabled.isEmpty()
        return ApplyResult(
            ok = pinned,
            summary = if (pinned) {
                "Новый лаунчер закреплён как Home; штатные лаунчеры отключены."
            } else {
                "Home назначен, но часть штатных лаунчеров не отключилась."
            },
            warnings = warnings
        )
    }

    fun install(context: Context, tv: TvClient): ApplyResult {
        val apk = File(context.cacheDir, "launcher.apk")
        context.assets.open("launcher.apk").use { input ->
            apk.outputStream().use { output -> input.copyTo(output) }
        }

        try {
            tv.installApk(apk)
        } catch (e: Throwable) {
            val message = e.message.orEmpty()
            if (!message.contains("INCOMPATIBLE", ignoreCase = true) &&
                !message.contains("signature", ignoreCase = true)
            ) {
                throw e
            }
            // The copy on the TV was signed with another key. Put the stock Home back first,
            // then replace the launcher; if the new install fails, the TV still has a working Home.
            val stock = runCatching { useStock(tv) }
            if (stock.isFailure) throw e
            tv.uninstall(CLEAN_LAUNCHER)
            tv.installApk(apk)
        }

        return try {
            val pinned = makeDefault(tv)
            pinned.copy(summary = "Лаунчер установлен. " + pinned.summary)
        } catch (e: Throwable) {
            runCatching { useStock(tv) }
            throw e
        }
    }

    fun useStock(tv: TvClient): ApplyResult {
        val sets = tv.packageSets()
        val stock = STOCK_ORDER.firstOrNull { it in sets.installed }
            ?: throw IllegalStateException("На TV не найден известный штатный лаунчер.")
        tv.enablePackage(stock)
        val component = tv.resolveHomeFor(stock)
            ?: throw IllegalStateException("Не удалось определить компонент Home для $stock.")
        tv.setHome(component)
        val home = tv.homeComponent()
        if (home != component) {
            throw IllegalStateException("Home не переключился на $component (сейчас ${home ?: "не задан"}).")
        }
        return ApplyResult(true, "Home возвращён: $stock.")
    }
}
