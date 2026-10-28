package ru.cleanmytv.app

const val CLEAN_LAUNCHER = "com.gathin.tvhome"
const val CLEAN_HOME = "$CLEAN_LAUNCHER/.MainActivity"

data class Probe(
    val host: String,
    val manufacturer: String,
    val model: String,
    val device: String,
    val release: String,
    val sdk: Int,
    val build: String,
    val adbSecure: String
)

data class PackageSets(
    val installed: Set<String>,
    val disabled: Set<String>
) {
    val enabled: Set<String> get() = installed - disabled
}

data class DebloatEntry(
    val pkg: String,
    val note: String,
    val suggested: Boolean,
    val section: String,
    val state: String,
    val common: Int = 0
)

data class DebloatList(
    val id: String,
    val title: String,
    val header: List<String>,
    val entries: List<DebloatEntry>,
    val score: Int
)

data class RecoveryRecord(
    val host: String,
    val device: String,
    val created: String,
    val home: String?,
    val packages: Map<String, String>
)

data class Plan(
    val listId: String,
    val title: String,
    val entries: List<DebloatEntry>,
    val homePackage: String?,
    val record: RecoveryRecord?
) {
    val suggestedInstalled: List<DebloatEntry>
        get() = entries.filter { it.suggested && it.state == "enabled" }
}

data class LauncherStatus(
    val installed: Boolean,
    val versionName: String?,
    val isHome: Boolean,
    val homeComponent: String?,
    val stockLauncher: String?,
    val bundledVersion: String = "2.4"
)

data class ApplyResult(
    val ok: Boolean,
    val summary: String,
    val warnings: List<String> = emptyList()
)
