package ru.cleanmytv.app

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import dadb.InstallResult
import java.io.Closeable
import java.io.File

class TvClient(private val context: Context) : Closeable {

    private var dadb: Dadb? = null
    var probe: Probe? = null
        private set

    val isConnected: Boolean get() = dadb != null

    fun connect(rawHost: String): Probe {
        val host = rawHost.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        require(host.matches(Regex("^[A-Za-z0-9.-]+$"))) { "Введите IP-адрес TV, например 192.168.1.50" }

        val dir = File(context.filesDir, "adb").apply { mkdirs() }
        val privateKey = File(dir, "adbkey")
        val publicKey = File(dir, "adbkey.pub")
        if (!privateKey.exists() || !publicKey.exists()) {
            AdbKeyPair.generate(privateKey, publicKey)
        }
        val key = AdbKeyPair.read(privateKey, publicKey)

        close()
        val connection = Dadb.create(
            host,
            5555,
            key,
            connectTimeout = 15_000,
            socketTimeout = 180_000
        )
        dadb = connection
        val p = readProbe(host)
        probe = p
        return p
    }

    private fun readProbe(host: String): Probe {
        val out = shell(
            "getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.product.device; " +
                "getprop ro.build.version.release; getprop ro.build.version.sdk; " +
                "getprop ro.build.display.id; getprop ro.adb.secure"
        )
        val lines = out.lines().map { it.trim() }
        fun at(i: Int): String = lines.getOrNull(i) ?: ""
        return Probe(
            host = host,
            manufacturer = at(0),
            model = at(1),
            device = at(2),
            release = at(3),
            sdk = at(4).toIntOrNull() ?: 0,
            build = at(5),
            adbSecure = at(6)
        )
    }

    fun shell(command: String): String {
        val d = dadb ?: throw IllegalStateException("Нет подключения к TV")
        return d.shell(command).allOutput
    }

    fun packageSets(): PackageSets {
        val installed = shell("pm list packages")
            .lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        val disabled = shell("pm list packages -d")
            .lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        return PackageSets(installed, disabled)
    }

    fun homeComponent(): String? {
        val out = shell(
            "cmd package resolve-activity --brief -a android.intent.action.MAIN " +
                "-c android.intent.category.HOME | tail -n 1"
        ).trim()
        val line = out.lines().lastOrNull { it.contains("/") && !it.contains("ResolverActivity") }?.trim()
        return line?.takeIf { it.matches(COMPONENT_RE) }
    }

    fun homeClaims(): List<String> {
        val out = shell(
            "cmd package query-activities --brief -a android.intent.action.MAIN " +
                "-c android.intent.category.HOME"
        )
        return out.lines()
            .map { it.trim() }
            .filter { it.matches(COMPONENT_RE) }
            .distinct()
    }

    fun resolveHomeFor(pkg: String): String? {
        validatePackage(pkg)
        val out = shell(
            "cmd package resolve-activity --brief -a android.intent.action.MAIN " +
                "-c android.intent.category.HOME $pkg | tail -n 1"
        ).trim()
        return out.lines().lastOrNull { it.matches(COMPONENT_RE) }?.trim()
    }

    fun disablePackage(pkg: String): String {
        validatePackage(pkg)
        return shell("pm disable-user --user 0 $pkg").trim()
    }

    fun enablePackage(pkg: String): String {
        validatePackage(pkg)
        return shell("pm enable $pkg").trim()
    }

    fun startLauncher() {
        shell("am start -W -n $CLEAN_HOME")
    }

    fun setHome(component: String) {
        require(component.matches(COMPONENT_RE)) { "Некорректный компонент Home: $component" }
        shell("cmd package set-home-activity --user 0 $component")
    }

    fun installApk(file: File): String {
        val d = dadb ?: throw IllegalStateException("Нет подключения к TV")
        return when (val result = d.install(file, "-r")) {
            is InstallResult.Success -> "ok"
            is InstallResult.Failure -> throw IllegalStateException(result.reason ?: "Установка не удалась")
        }
    }

    fun versionOf(pkg: String): String? {
        validatePackage(pkg)
        val out = shell("dumpsys package $pkg | grep -m1 versionName")
        return out.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("versionName=") }
            ?.substringAfter("=")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    override fun close() {
        try {
            dadb?.close()
        } catch (_: Throwable) {
        }
        dadb = null
    }

    private fun validatePackage(pkg: String) {
        require(pkg.matches(PACKAGE_RE)) { "Некорректное имя пакета: $pkg" }
    }

    companion object {
        val PACKAGE_RE = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")
        val COMPONENT_RE = Regex("^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$")

        val KNOWN_STOCK = listOf(
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher",
            "com.mitv.tvhome.atv",
            "com.mitv.tvhome",
            "com.tcl.tv",
            "com.tcl.tvlauncher",
            "com.tcl.launcher",
            "com.mediatek.tvlauncher",
            "com.philips.tvlauncher",
            "com.sony.dtv.launcher"
        )
    }
}
