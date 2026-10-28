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
        val stock = STOCK_ORDER.firstOrNull { it in sets.installed }
        return LauncherStatus(
            installed = installed,
            versionName = if (installed) tv.versionOf(CLEAN_LAUNCHER) else null,
            isHome = isHome,
            homeComponent = home,
            stockLauncher = stock
        )
    }

    fun install(context: Context, tv: TvClient): ApplyResult {
        val apk = File(context.cacheDir, "launcher.apk")
        context.assets.open("launcher.apk").use { input ->
            apk.outputStream().use { output -> input.copyTo(output) }
        }

        tv.installApk(apk)
        tv.startLauncher()
        tv.setHome(CLEAN_HOME)

        val home = tv.homeComponent()
        if (home?.startsWith("$CLEAN_LAUNCHER/") != true) {
            runCatching { useStock(tv) }
            throw IllegalStateException(
                "Лаунчер установлен, но Home переключился на ${home ?: "неизвестный экран"}. " +
                    "Штатный лаунчер возвращён."
            )
        }

        val warnings = mutableListOf<String>()
        val sets = tv.packageSets()
        val candidates = DISABLE_STOCK.filter { it in sets.enabled }
        for (pkg in candidates) {
            try {
                tv.disablePackage(pkg)
            } catch (e: Throwable) {
                warnings += "$pkg: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        return ApplyResult(
            ok = true,
            summary = "Лаунчер установлен и назначен Home. Нажмите Home на пульте.",
            warnings = warnings
        )
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
