package ru.cleanmytv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CleanMyTvTheme {
                CleanMyTvApp(vm)
            }
        }
    }
}

@Composable
private fun CleanMyTvTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = Color(0xFF2F6B3A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD9E9DA),
        onPrimaryContainer = Color(0xFF13351B),
        secondary = Color(0xFF6B5B3A),
        background = Color(0xFFF7F4EC),
        surface = Color(0xFFFDFBF5),
        error = Color(0xFFB3261E)
    )
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CleanMyTvApp(vm: AppViewModel) {
    BackHandler(enabled = vm.screen != Screen.Connect) { vm.open(Screen.Connect) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CleanMyTV") },
                actions = {
                    if (vm.probe != null) {
                        TextButton(onClick = vm::disconnect) { Text("Отключить") }
                        Spacer(Modifier.width(4.dp))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (vm.probe != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = vm.screen == Screen.Connect,
                        onClick = { vm.open(Screen.Connect) },
                        label = { Text("Подключение") }
                    )
                    FilterChip(
                        selected = vm.screen == Screen.Clean,
                        onClick = { vm.open(Screen.Clean) },
                        label = { Text("Очистка") }
                    )
                    FilterChip(
                        selected = vm.screen == Screen.Launcher,
                        onClick = { vm.open(Screen.Launcher) },
                        label = { Text("Лаунчер") }
                    )
                }
                HorizontalDivider()
            }
            if (vm.busy || vm.connecting) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            when (vm.screen) {
                Screen.Connect -> ConnectScreen(vm)
                Screen.Clean -> CleanScreen(vm)
                Screen.Launcher -> LauncherScreen(vm)
            }
        }
    }
}

@Composable
private fun ConnectScreen(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Подключение", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Приложение подключается к TV по Wi-Fi через порт отладки 5555. " +
                "Никаких аккаунтов и облака — только ваш телевизор.",
            style = MaterialTheme.typography.bodyMedium
        )

        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("На телевизоре", fontWeight = FontWeight.Bold)
                Text("1. Настройки → Для разработчиков → Отладка по USB — включить.")
                Text("2. Если есть «Отладка по сети» или «Отладка ADB» — включить и её.")
                Text("3. Телефон и TV должны быть в одной Wi-Fi сети.")
                Text("4. IP-адрес TV виден в Настройках → Сеть.")
            }
        }

        OutlinedTextField(
            value = vm.host,
            onValueChange = vm::updateHost,
            label = { Text("IP-адрес TV") },
            placeholder = { Text("192.168.1.50") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = vm::connect,
            enabled = !vm.connecting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (vm.connecting) "Подключение…" else "Подключить")
        }

        if (vm.connecting) {
            Text(
                "Если на TV появился запрос — нажмите «Разрешить» и включите " +
                    "«Всегда разрешать с этого компьютера».",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        vm.probe?.let { p ->
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${p.manufacturer} ${p.model}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Android ${p.release} (API ${p.sdk})", style = MaterialTheme.typography.bodyMedium)
                    Text("Устройство: ${p.device}", style = MaterialTheme.typography.bodySmall)
                    Text("Адрес: ${p.host}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(
                onClick = { vm.open(Screen.Clean) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Далее: очистка")
            }
        }

        MessageCards(vm)
    }
}

@Composable
private fun CleanScreen(vm: AppViewModel) {
    val plan = vm.plan
    if (plan == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var confirmApply by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Очистка", style = MaterialTheme.typography.headlineMedium)
        Text(
            plan.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Выбрано: ${vm.selected.size}", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = vm::selectAllSuggested) { Text("Все рекомендованные") }
            TextButton(onClick = vm::clearSelection) { Text("Снять") }
        }

        if (plan.entries.none { it.suggested }) {
            Text(
                "Для этой модели готового списка нет: отметьте пакеты вручную.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(plan.entries, key = { it.pkg }) { entry ->
                EntryRow(entry, entry.pkg in vm.selected, vm::toggle)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { confirmApply = true },
                enabled = vm.selected.isNotEmpty() && !vm.busy,
                modifier = Modifier.weight(1f)
            ) {
                Text("Применить")
            }
            OutlinedButton(
                onClick = { confirmRestore = true },
                enabled = plan.record != null && !vm.busy,
                modifier = Modifier.weight(1f)
            ) {
                Text("Восстановить")
            }
        }

        plan.record?.let {
            Text(
                "Точка восстановления: ${it.created}",
                style = MaterialTheme.typography.labelSmall
            )
        }

        MessageCards(vm)
    }

    if (confirmApply) {
        AlertDialog(
            onDismissRequest = { confirmApply = false },
            title = { Text("Отключить ${vm.selected.size} пакетов?") },
            text = {
                Text(
                    "Пакеты будут отключены только для пользователя TV, а не удалены. " +
                        "Перед изменением сохраняется точка восстановления."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmApply = false
                    vm.applySelected()
                }) { Text("Отключить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmApply = false }) { Text("Отмена") }
            }
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Восстановить состояние?") },
            text = { Text("Все пакеты из записи, которые были включены, снова включатся. Ничего не удаляется.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    vm.restore()
                }) { Text("Восстановить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun EntryRow(entry: DebloatEntry, checked: Boolean, onToggle: (String) -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().padding(10.dp)) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle(entry.pkg) },
                enabled = entry.state == "enabled"
            )
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.pkg,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
                if (entry.note.isNotBlank()) {
                    Text(
                        entry.note,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when (entry.state) {
                            "enabled" -> "включён"
                            "disabled" -> "уже отключён"
                            else -> "нет на TV"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (entry.state) {
                            "enabled" -> MaterialTheme.colorScheme.primary
                            "disabled" -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.outline
                        }
                    )
                    if (entry.suggested) {
                        Text("рекомендовано", style = MaterialTheme.typography.labelSmall)
                    }
                    if (entry.common > 1) {
                        Text("в ${entry.common} списках", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun LauncherScreen(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Лаунчер", style = MaterialTheme.typography.headlineMedium)

        val st = vm.launcher
        if (st == null) {
            if (!vm.busy) {
                Text("Статус лаунчера не загружен.")
                OutlinedButton(onClick = vm::refreshLauncher) { Text("Обновить") }
            } else {
                CircularProgressIndicator()
            }
        } else {
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (st.installed) "Лаунчер установлен" else "Лаунчер не установлен",
                        fontWeight = FontWeight.Bold
                    )
                    Text("Версия: ${st.versionName ?: "—"}")
                    Text("Home: ${if (st.isHome) "этот лаунчер" else st.homeComponent ?: "не определён"}")
                    Text("Штатный лаунчер: ${st.stockLauncher ?: "не найден"}")
                }
            }

            Button(
                onClick = vm::installLauncher,
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (st.installed) "Переустановить лаунчер" else "Установить лаунчер")
            }

            OutlinedButton(
                onClick = vm::useStockLauncher,
                enabled = !vm.busy && st.stockLauncher != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Вернуть штатный лаунчер")
            }

            Text(
                "Встроенный лаунчер 2.4 устанавливается на TV, запускается и получает Home. " +
                    "После установки нажмите Home на пульте.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        MessageCards(vm)
    }
}

@Composable
private fun MessageCards(vm: AppViewModel) {
    vm.error?.let { text ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.padding(12.dp)) {
                Text("Ошибка", fontWeight = FontWeight.Bold)
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    vm.message?.let { text ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(12.dp)) {
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
