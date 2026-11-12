package ru.cleanmytv.app

/**
 * Собирает скрипт восстановления, который можно запустить с любого компьютера,
 * где есть adb — даже если этого приложения уже нет под рукой.
 */
object RestoreScript {

    fun bash(probe: Probe, record: RecoveryRecord): String {
        val serial = "${probe.host}:${Discovery.ADB_PORT}"
        val lines = mutableListOf<String>()
        lines += "#!/usr/bin/env bash"
        lines += "# Восстановление TV ${probe.manufacturer} ${probe.model}".trim()
        lines += "# Запись CleanMyTV от ${record.created}"
        lines += "set -u"
        lines += ""
        lines += "adb connect $serial || true"
        lines += ""
        lines += "# включить приложения, которые отключало приложение"
        lines += enableCommands(serial, record)
        lines += homeCommands(serial, record)
        lines += ""
        lines += "echo \"Готово. Перезагрузите TV, чтобы вернулся прежний экран.\""
        return lines.joinToString("\n")
    }

    fun powershell(probe: Probe, record: RecoveryRecord): String {
        val serial = "${probe.host}:${Discovery.ADB_PORT}"
        val lines = mutableListOf<String>()
        lines += "# Восстановление TV ${probe.manufacturer} ${probe.model}".trim()
        lines += "# Запись CleanMyTV от ${record.created}"
        lines += "# Запуск: powershell -ExecutionPolicy Bypass -File этот-файл.ps1"
        lines += ""
        lines += "adb connect $serial"
        lines += ""
        lines += "# включить приложения, которые отключало приложение"
        lines += enableCommands(serial, record)
        lines += homeCommands(serial, record)
        lines += ""
        lines += "Write-Output \"Готово. Перезагрузите TV, чтобы вернулся прежний экран.\""
        return lines.joinToString("\r\n")
    }

    fun fileName(probe: Probe, extension: String): String {
        val host = probe.host.replace(Regex("[^A-Za-z0-9.-]"), "_")
        return "cleanmytv-restore-$host.$extension"
    }

    private fun enableCommands(serial: String, record: RecoveryRecord): List<String> =
        record.packages
            .filterValues { it == "enabled" }
            .keys
            .sorted()
            .map { "adb -s $serial shell pm enable $it" }

    private fun homeCommands(serial: String, record: RecoveryRecord): List<String> {
        val home = record.home ?: return emptyList()
        if (!home.matches(TvClient.COMPONENT_RE)) return emptyList()
        return listOf("", "# вернуть прежний экран Home", "adb -s $serial shell cmd package set-home-activity --user 0 $home")
    }
}
