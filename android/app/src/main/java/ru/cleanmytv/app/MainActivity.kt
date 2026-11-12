package ru.cleanmytv.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalContext
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
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
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
                    FilterChip(
                        selected = vm.screen == Screen.Disabled,
                        onClick = { vm.open(Screen.Disabled) },
                        label = { Text("Отключено") }
                    )
                    FilterChip(
                        selected = vm.screen == Screen.TvSettings,
                        onClick = { vm.open(Screen.TvSettings) },
                        label = { Text("Настройки TV") }
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
                Screen.Disabled -> DisabledScreen(vm)
                Screen.TvSettings -> TvSettingsScreen(vm)
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

        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Поиск в сети", fontWeight = FontWeight.Bold)
                Text(
                    "Приложение проверит адреса в вашей Wi-Fi сети и найдёт телевизоры " +
                        "с открытым портом отладки 5555. Это занимает до минуты.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = vm::startScan,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (vm.scanning) "Остановить поиск" else "Найти телевизоры")
                }
                if (vm.scanning) {
                    Text(
                        "Проверено адресов: ${vm.scanChecked} из 253",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (vm.found.isNotEmpty()) {
                    Text(
                        "Найдено: ${vm.found.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    vm.found.forEach { device ->
                        OutlinedButton(
                            onClick = { vm.connectTo(device.host) },
                            enabled = !vm.connecting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Подключить ${device.host}")
                        }
                    }
                }
            }
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

        UpdateCard(vm)
        MessageCards(vm)
    }
}

@Composable
private fun UpdateCard(vm: AppViewModel) {
    val context = LocalContext.current
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Обновления", fontWeight = FontWeight.Bold)
            OutlinedButton(
                onClick = vm::checkUpdates,
                enabled = !vm.checkingUpdate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (vm.checkingUpdate) "Проверка…" else "Проверить обновления")
            }
            vm.update?.let { u ->
                val current = BuildConfig.VERSION_NAME
                Text(
                    when {
                        u.newer -> "Доступна версия ${u.version} — у вас $current."
                        u.version == current -> "У вас последняя версия ($current)."
                        else -> "У вас сборка $current, последний релиз — ${u.version}."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (u.newer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
                if (u.notes.isNotBlank()) {
                    Text(
                        u.notes,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (u.url.startsWith("https://")) {
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.url)))
                        }
                    }) {
                        Text("Открыть страницу релиза")
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanScreen(vm: AppViewModel) {
    val plan = vm.plan
    if (plan == null) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (vm.busy || vm.connecting) {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Text("Не удалось загрузить список.", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = vm::loadPlan) { Text("Повторить") }
            }
            MessageCards(vm)
        }
        return
    }

    var confirmApply by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scriptLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) vm.writeExport(context.applicationContext, uri)
    }

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        val name = vm.prepareExport("sh") ?: return@TextButton
                        scriptLauncher.launch(name)
                    }
                ) { Text("Скрипт отката .sh") }
                TextButton(
                    onClick = {
                        val name = vm.prepareExport("ps1") ?: return@TextButton
                        scriptLauncher.launch(name)
                    }
                ) { Text("Скрипт отката .ps1") }
            }
            Text(
                "Скрипт сохраняется отдельным файлом: его можно запустить на любом компьютере " +
                    "с adb, даже если приложения под рукой уже нет.",
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
private fun DisabledScreen(vm: AppViewModel) {
    val state = vm.disabledState
    var pendingRestore by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Что отключено", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Здесь только то, что отключило это приложение. Любой пакет можно вернуть по одному.",
            style = MaterialTheme.typography.bodySmall
        )

        if (state == null) {
            if (vm.busy) {
                Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                OutlinedButton(onClick = vm::loadDisabled) { Text("Загрузить список") }
            }
        } else {
            if (state.leftovers.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Вернулось после обновления прошивки: ${state.leftovers.size}",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Такие пакеты TV включает заново при обновлении. Можно отключить их снова одной кнопкой.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = vm::reapplyLeftovers,
                            enabled = !vm.busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Отключить снова")
                        }
                        state.leftovers.forEach { pkg ->
                            Text(pkg, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                }
            }

            Text(
                "Отключено сейчас: ${state.disabled.size}",
                style = MaterialTheme.typography.titleSmall
            )

            if (state.disabled.isEmpty()) {
                Text(
                    "Пока ничего не отключено. Список появится после первой очистки.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.disabled, key = { it }) { pkg ->
                        Card {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    pkg,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { pendingRestore = pkg },
                                    enabled = !vm.busy
                                ) { Text("Вернуть") }
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = vm::loadDisabled,
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Обновить")
            }
        }

        MessageCards(vm)
    }

    pendingRestore?.let { pkg ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Включить обратно?") },
            text = { Text("$pkg снова появится на TV. Ничего не удаляется, отключить его можно будет в любой момент.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    vm.restoreOne(pkg)
                }) { Text("Включить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun TvSettingsScreen(vm: AppViewModel) {
    val settings = vm.tvSettings
    var pending by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Настройки TV", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Две настройки, которые забирают рекламу и ускоряют меню. Обе меняются через ADB и обратимы.",
            style = MaterialTheme.typography.bodySmall
        )

        if (settings == null) {
            if (vm.busy) {
                CircularProgressIndicator()
            } else {
                OutlinedButton(onClick = vm::loadTvSettings) { Text("Загрузить настройки") }
            }
        } else {
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Private DNS", fontWeight = FontWeight.Bold)
                    Text(
                        if (settings.dnsBlocking) {
                            "Включён: ${settings.dnsSpecifier}. Реклама в приложениях режется на уровне DNS."
                        } else {
                            "Выключен. TV использует DNS от провайдера."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Режим: ${settings.dnsMode}, сервер: ${settings.dnsSpecifier.ifBlank { "—" }}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Button(
                        onClick = { pending = if (settings.dnsBlocking) "dns-off" else "dns-on" },
                        enabled = !vm.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (settings.dnsBlocking) "Выключить блокировку" else "Включить блокировку рекламы")
                    }
                }
            }

            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Анимации", fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            settings.animationsOff -> "Выключены: меню переключается без задержек."
                            settings.animationsDefault -> "Стандартная скорость (1.0)."
                            else -> "Нестандартная скорость: ${settings.windowScale} / ${settings.transitionScale} / ${settings.animatorScale}."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Окно ${settings.windowScale} · переходы ${settings.transitionScale} · аниматор ${settings.animatorScale}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Button(
                        onClick = { pending = if (settings.animationsOff) "anim-on" else "anim-off" },
                        enabled = !vm.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (settings.animationsOff) "Вернуть анимации" else "Выключить анимации")
                    }
                }
            }

            OutlinedButton(
                onClick = vm::loadTvSettings,
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Обновить")
            }

            Text(
                "После смены Private DNS перезагрузите TV: некоторые прошивки держат старый DNS до перезапуска. " +
                    "Если что-то перестало открываться — выключите блокировку, это мгновенно возвращает всё как было.",
                style = MaterialTheme.typography.labelSmall
            )
        }

        MessageCards(vm)
    }

    pending?.let { action ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = {
                Text(
                    when (action) {
                        "dns-on" -> "Включить Private DNS?"
                        "dns-off" -> "Выключить Private DNS?"
                        "anim-off" -> "Выключить анимации?"
                        else -> "Вернуть анимации?"
                    }
                )
            },
            text = {
                Text(
                    when (action) {
                        "dns-on" -> "Все DNS-запросы TV пойдут через ${TvSettings.DNS_HOST}. " +
                            "Реклама в приложениях и на экране ожидания перестанет загружаться. " +
                            "Настройка обратима."
                        "dns-off" -> "TV вернётся к DNS провайдера. Настройка обратима."
                        "anim-off" -> "Изменятся три системных множителя: окно, переходы и аниматор станут 0.0. " +
                            "Меню начнёт открываться мгновенно."
                        else -> "Множители вернутся к стандартным 1.0."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    when (action) {
                        "dns-on" -> vm.setDnsBlocking(true)
                        "dns-off" -> vm.setDnsBlocking(false)
                        "anim-off" -> vm.setAnimationsOff(true)
                        else -> vm.setAnimationsOff(false)
                    }
                }) {
                    Text(
                        if (action.endsWith("off")) "Выключить" else "Включить"
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("Отмена") }
            }
        )
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
                    Text(
                        "Закреплён после перезагрузки: ${if (st.pinned) "да" else "нет"}",
                        color = if (st.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Text("Штатный лаунчер: ${st.stockLauncher ?: "не найден"}")
                    if (st.enabledStock.isNotEmpty()) {
                        Text("Включены штатные: ${st.enabledStock.joinToString()}")
                    }
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
                onClick = vm::makeDefaultLauncher,
                enabled = !vm.busy && st.installed,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сделать по умолчанию")
            }

            OutlinedButton(
                onClick = vm::useStockLauncher,
                enabled = !vm.busy && st.stockLauncher != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Вернуть штатный лаунчер")
            }

            Text(
                "«Сделать по умолчанию» назначает новый лаунчер Home и отключает штатные, " +
                    "чтобы после перезагрузки не возвращался старый экран. После установки нажмите Home на пульте.",
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
