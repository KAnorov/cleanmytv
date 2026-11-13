package ru.cleanmytv.app

/**
 * Текст отчёта для страницы релизов: по нему видно, что за TV и что на нём
 * отключено, но без адресов, серийников и прочих личных данных.
 */
object Report {

    fun build(
        probe: Probe,
        plan: Plan?,
        disabled: List<String>,
        leftovers: List<String>,
        version: String
    ): String {
        val lines = mutableListOf<String>()
        lines += "# CleanMyTV: отчёт о телевизоре"
        lines += ""
        lines += "Приложение: CleanMyTV $version (Android)"
        lines += "Производитель: ${probe.manufacturer.ifBlank { "не определён" }}"
        lines += "Модель: ${probe.model.ifBlank { "не определена" }}"
        lines += "Устройство: ${probe.device.ifBlank { "не определено" }}"
        lines += "Android: ${probe.release} (API ${probe.sdk})"
        lines += "Сборка: ${probe.build.ifBlank { "не определена" }}"
        lines += ""
        lines += "Список очистки: ${plan?.listId ?: "не загружен"}"
        lines += "Название списка: ${plan?.title ?: "—"}"
        lines += "Пакетов в списке: ${plan?.entries?.size ?: 0}"
        lines += "Рекомендовано к отключению: ${plan?.entries?.count { it.suggested } ?: 0}"
        lines += "Отключено этим приложением: ${disabled.size}"
        if (leftovers.isNotEmpty()) {
            lines += "Вернулось после обновления прошивки: ${leftovers.size}"
        }
        lines += ""
        if (disabled.isEmpty()) {
            lines += "Отключённых пакетов нет."
        } else {
            lines += "## Отключённые пакеты"
            disabled.forEach { lines += "- $it" }
        }
        lines += ""
        if (leftovers.isNotEmpty()) {
            lines += "## Вернулись после обновления прошивки"
            leftovers.forEach { lines += "- $it" }
            lines += ""
        }
        lines += "## Что делать с отчётом"
        lines += "Откройте https://github.com/KAnorov/cleanmytv/issues и создайте задачу:"
        lines += "«Список для <модель TV>». Вставьте этот текст целиком."
        lines += "Если модели нет в списке, по отчёту её можно добавить."
        return lines.joinToString("\n")
    }
}
