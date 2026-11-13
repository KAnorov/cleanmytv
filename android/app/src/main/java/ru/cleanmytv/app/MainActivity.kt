package ru.cleanmytv.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

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
        primary = Color(0xFF116B52),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFCBEFE2),
        onPrimaryContainer = Color(0xFF04281D),
        secondary = Color(0xFF4B635B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDDEAE4),
        onSecondaryContainer = Color(0xFF12241E),
        background = Color(0xFFF5F8F6),
        onBackground = Color(0xFF161D1A),
        surface = Color.White,
        onSurface = Color(0xFF161D1A),
        surfaceVariant = Color(0xFFE4ECE8),
        onSurfaceVariant = Color(0xFF46524C),
        outline = Color(0xFF7B8A83),
        outlineVariant = Color(0xFFD3DEDA),
        error = Color(0xFFB3261E),
        onError = Color.White,
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B)
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(22.dp)
    )
    MaterialTheme(colorScheme = colors, typography = Typography(), shapes = shapes, content = content)
}

private data class Tab(val screen: Screen, val label: String, val icon: ImageVector, val needsTv: Boolean)

private val TABS = listOf(
    Tab(Screen.Connect, "Подключение", Icons.Default.Search, false),
    Tab(Screen.Clean, "Очистка", Icons.Default.Delete, true),
    Tab(Screen.Appearance, "Вид", Icons.Default.Star, true),
    Tab(Screen.Disabled, "Отключено", Icons.Default.CheckCircle, true),
    Tab(Screen.More, "Ещё", Icons.Default.MoreVert, false)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CleanMyTvApp(vm: AppViewModel) {
    val nested = vm.screen == Screen.Launcher || vm.screen == Screen.TvSettings || vm.screen == Screen.Report
    BackHandler(enabled = nested) { vm.open(Screen.More) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                title = {
                    Column {
                        Text(
                            "CleanMyTV",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            vm.probe?.let { "${it.manufacturer} ${it.model}".trim().ifBlank { it.host } }
                                ?: "Телефон подключается к TV по Wi-Fi",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    if (vm.probe != null) {
                        TextButton(onClick = vm::disconnect) { Text("Отключить") }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val selectedScreen = if (nested) Screen.More else vm.screen
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedScreen == tab.screen,
                        onClick = { vm.open(tab.screen) },
                        enabled = !tab.needsTv || vm.probe != null,
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = {
                            Text(
                                tab.label,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (vm.busy || vm.connecting) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            StatusBanners(vm)
            Box(Modifier.weight(1f)) {
                when (vm.screen) {
                    Screen.Connect -> ConnectScreen(vm)
                    Screen.Clean -> CleanScreen(vm)
                    Screen.Appearance -> AppearanceScreen(vm)
                    Screen.Disabled -> DisabledScreen(vm)
                    Screen.More -> MoreScreen(vm)
                    Screen.Launcher -> LauncherScreen(vm)
                    Screen.TvSettings -> TvSettingsScreen(vm)
                    Screen.Report -> ReportScreen(vm)
                }
            }
        }
    }
}

// ---- общие элементы ----

@Composable
private fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun PanelTitle(text: String, hint: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun StatusBanners(vm: AppViewModel) {
    val error = vm.error
    val message = vm.message
    if (error == null && message == null) return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (error != null) {
            Banner(error, isError = true, onDismiss = vm::clearMessages)
        }
        if (message != null) {
            Banner(message, isError = false, onDismiss = vm::clearMessages)
        }
    }
}

@Composable
private fun Banner(text: String, isError: Boolean, onDismiss: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text,
                modifier = Modifier.weight(1f).padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Скрыть", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    hint: String? = null,
    enabled: Boolean = true
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    hint: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                "$value",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0)
        )
        if (hint != null) Caption(hint)
    }
}

@Composable
private fun EmptyState(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// ---- подключение ----

@Composable
private fun ConnectScreen(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenTitle(
            "Подключение к TV",
            "Только ваш телевизор и Wi-Fi. Аккаунты и облако не нужны."
        )

        Panel {
            PanelTitle("На телевизоре")
            Step(1, "Настройки → Для разработчиков → Отладка по USB — включить.")
            Step(2, "Если есть «Отладка по сети» или «Отладка ADB» — включить и её.")
            Step(3, "Телефон и TV должны быть в одной Wi-Fi сети.")
            Step(4, "IP-адрес TV виден в Настройках → Сеть.")
        }

        Panel {
            PanelTitle("Адрес телевизора")
            OutlinedTextField(
                value = vm.host,
                onValueChange = vm::updateHost,
                label = { Text("IP-адрес") },
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
                Text(if (vm.connecting) "Подключаемся…" else "Подключить")
            }
            if (vm.connecting) {
                Caption(
                    "Если на TV появился запрос — нажмите «Разрешить» и включите " +
                        "«Всегда разрешать с этого компьютера»."
                )
            }
        }

        Panel {
            PanelTitle(
                "Поиск в сети",
                "Приложение проверит адреса в вашей Wi-Fi сети и найдёт телевизоры с открытым портом 5555."
            )
            OutlinedButton(
                onClick = vm::startScan,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (vm.scanning) "Остановить поиск" else "Найти телевизоры")
            }
            if (vm.scanning) {
                Caption("Проверено адресов: ${vm.scanChecked} из 253")
            }
            vm.found.forEach { device ->
                FoundRow(device.host, vm.connecting) { vm.connectTo(device.host) }
            }
        }

        vm.probe?.let { p ->
            Panel {
                PanelTitle("${p.manufacturer} ${p.model}".trim(), "Подключено по адресу ${p.host}")
                LabeledValue("Android", "${p.release} (API ${p.sdk})")
                LabeledValue("Устройство", p.device)
                LabeledValue("Сборка", p.build.ifBlank { "не определена" })
                Button(onClick = { vm.open(Screen.Clean) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Далее: очистка")
                }
            }
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .size(22.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(108.dp)
        )
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FoundRow(host: String, connecting: Boolean, onConnect: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                host,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onConnect, enabled = !connecting) { Text("Подключить") }
        }
    }
}

// ---- очистка ----

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
                ScreenTitle("Очистка", "Список для этой модели не загрузился.")
                OutlinedButton(onClick = vm::loadPlan) { Text("Повторить") }
            }
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

    val recommended = plan.suggestedBySection
    val others = plan.entries.filter { !it.suggested && it.state != "absent" }
    var showOthers by remember(plan.listId) { mutableStateOf(recommended.isEmpty()) }
    var showScript by remember(plan.listId) { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                ScreenTitle(
                    "Очистка",
                    "Выключите лишнее и оставьте TV быстрым. Ничего не удаляется — всё можно вернуть."
                )
            }

            if (plan.fallback) {
                item {
                    Panel {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Точного списка для этой модели пока нет",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Показано только то, что отключают во всех известных списках " +
                                        "этого производителя.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                Panel {
                    val total = plan.entries.count { it.suggested && it.state == "enabled" }
                    Text(
                        "Выбрано ${vm.selected.size} из $total",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Caption(
                        when {
                            total == 0 -> "Все рекомендованные пакеты уже отключены."
                            vm.selected.isEmpty() -> "Отметьте пакеты ниже или возьмите все рекомендованные."
                            else -> "Отключение обратимо: пакеты вернутся кнопкой «Восстановить»."
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::selectAllSuggested, modifier = Modifier.weight(1f)) {
                            Text("Все рекомендованные")
                        }
                        OutlinedButton(onClick = vm::clearSelection, modifier = Modifier.weight(1f)) {
                            Text("Снять")
                        }
                    }
                    Text(
                        "Список: ${plan.title}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            recommended.forEach { (section, entries) ->
                item(key = "head-$section") {
                    SectionHeader(
                        title = section,
                        count = entries.size,
                        onSelect = { vm.select(entries.map { it.pkg }) }
                    )
                }
                items(entries, key = { it.pkg }) { entry ->
                    EntryCard(entry, entry.pkg in vm.selected) { vm.toggle(entry.pkg) }
                }
            }

            if (others.isNotEmpty()) {
                item(key = "others-head") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showOthers = !showOthers }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (showOthers) "Что не трогаем" else "Что не трогаем (${others.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Пакеты, которые в списке отмечены как нужные телевизору. " +
                                    "Можно отключить вручную, но лучше не надо.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            if (showOthers) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (showOthers) {
                    items(others, key = { it.pkg }) { entry ->
                        EntryCard(entry, entry.pkg in vm.selected) { vm.toggle(entry.pkg) }
                    }
                }
            }

            if (plan.record != null) {
                item(key = "script") {
                    Panel {
                        Row(
                            Modifier.fillMaxWidth().clickable { showScript = !showScript },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Скрипт отката",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Caption("Точка восстановления: ${plan.record.created}")
                            }
                            Icon(
                                if (showScript) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (showScript) {
                            Text(
                                "Файл можно запустить на любом компьютере с adb, даже если приложения " +
                                    "под рукой уже нет.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val name = vm.prepareExport("sh") ?: return@OutlinedButton
                                        scriptLauncher.launch(name)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text(".sh для Linux") }
                                OutlinedButton(
                                    onClick = {
                                        val name = vm.prepareExport("ps1") ?: return@OutlinedButton
                                        scriptLauncher.launch(name)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text(".ps1 для Windows") }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)) }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { confirmApply = true },
                        enabled = vm.selected.isNotEmpty() && !vm.busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (vm.selected.isEmpty()) "Применить" else "Отключить · ${vm.selected.size}")
                    }
                    OutlinedButton(
                        onClick = { confirmRestore = true },
                        enabled = plan.record != null && !vm.busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Восстановить")
                    }
                }
                Caption("Пакеты отключаются только для пользователя TV, файлы остаются на месте.")
            }
        }
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
            text = {
                Text(
                    "Все пакеты из записи, которые были включены, снова включатся. " +
                        "Ничего не удаляется."
                )
            },
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
private fun SectionHeader(title: String, count: Int, onSelect: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onSelect) { Text("Выбрать") }
    }
}

@Composable
private fun EntryCard(entry: DebloatEntry, checked: Boolean, onToggle: () -> Unit) {
    val selectable = entry.state == "enabled"
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (checked) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            1.dp,
            if (checked) Color.Transparent else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth().clickable(enabled = selectable, onClick = onToggle)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 6.dp, bottom = 10.dp, end = 12.dp)) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
                enabled = selectable
            )
            Column(Modifier.weight(1f).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    LauncherTheme.labelFor(entry.pkg),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (checked) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    entry.pkg,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.note.isNotBlank()) {
                    Text(
                        entry.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Text(
                            "рекомендовано",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (entry.common > 1) {
                        Text(
                            "в ${entry.common} списках",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ---- вид главного экрана ----

@Composable
private fun AppearanceScreen(vm: AppViewModel) {
    val loaded = vm.launcherView
    if (loaded == null) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScreenTitle("Вид главного экрана", "Настройки читаются из лаунчера на TV.")
            if (vm.busy) {
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                OutlinedButton(onClick = vm::loadLauncherView) { Text("Загрузить настройки") }
            }
        }
        return
    }

    var draft by remember(loaded) { mutableStateOf(loaded) }
    var confirmReset by remember { mutableStateOf(false) }
    val dirty = draft != loaded

    val context = LocalContext.current
    val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.applyWallpaper(uri)
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScreenTitle(
                "Вид главного экрана",
                if (loaded.present) {
                    "Настройки лаунчера на TV. Изменения применяются кнопкой внизу."
                } else {
                    "Своего config.json на TV ещё нет — покажем встроенный вид и создадим файл при сохранении."
                }
            )

            Panel {
                PanelTitle(
                    "Макет",
                    "Приложений в ряду и рядов под доком. В доке столько же мест, сколько приложений в ряду."
                )
                SliderRow(
                    title = "Приложений в ряду",
                    value = draft.columns,
                    range = 4..8,
                    onChange = { draft = draft.copy(columns = it) },
                    hint = "Начиная с 5 плитки уменьшаются, чтобы поместиться."
                )
                HorizontalDivider()
                SliderRow(
                    title = "Рядов сетки под доком",
                    value = draft.rowsBelowDock,
                    range = 0..3,
                    onChange = { draft = draft.copy(rowsBelowDock = it) },
                    hint = "0 прижимает док к нижнему краю экрана."
                )
            }

            Panel {
                PanelTitle("Док и плитки")
                SwitchRow(
                    title = "Показывать док",
                    checked = draft.dockRow,
                    onChange = { draft = draft.copy(dockRow = it) }
                )
                SwitchRow(
                    title = "Подпись дока",
                    checked = draft.dockTitle,
                    onChange = { draft = draft.copy(dockTitle = it) }
                )
                SwitchRow(
                    title = "Заполнять док",
                    checked = draft.fillDock,
                    onChange = { draft = draft.copy(fillDock = it) }
                )
                SwitchRow(
                    title = "Кнопка сортировки",
                    checked = draft.reorderButton,
                    onChange = { draft = draft.copy(reorderButton = it) }
                )
                SwitchRow(
                    title = "Библиотека приложений",
                    checked = draft.appLibrary,
                    onChange = { draft = draft.copy(appLibrary = it) },
                    hint = "Отдельный экран со всеми приложениями, включая не-TV."
                )
            }

            Panel {
                PanelTitle("Приветствие")
                OutlinedTextField(
                    value = draft.greetingName,
                    onValueChange = { draft = draft.copy(greetingName = it) },
                    label = { Text("Имя после приветствия") },
                    placeholder = { Text("Мама") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Caption("«Мама» покажет ДОБРОЕ УТРО, МАМА. Пустое поле — только приветствие.")
            }

            Panel {
                PanelTitle("Обои")
                SwitchRow(
                    title = "Обои на главном экране",
                    checked = draft.wallpaper,
                    onChange = { on -> draft = draft.copy(wallpaper = on) },
                    hint = "Картинка обрезается под 16:9 (1920×1080) и отправляется на TV."
                )
                OutlinedButton(
                    onClick = { pickWallpaper.launch("image/*") },
                    enabled = !vm.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Выбрать картинку")
                }
                if (loaded.wallpaper) {
                    TextButton(onClick = vm::removeWallpaper, enabled = !vm.busy) {
                        Text("Убрать обои")
                    }
                }
                Caption(
                    "Обои применяются сразу. Несохранённые изменения остальных настроек " +
                        "после этого сбросятся."
                )
            }

            Panel {
                PanelTitle(
                    "Скрытые плитки",
                    "Снимите галочку, чтобы вернуть приложение на главный экран."
                )
                val hidden = draft.hiddenSet
                draft.tvApps.forEach { app ->
                    val visible = app.pkg !in hidden
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                draft = draft.withHidden(app.pkg, visible)
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = visible,
                            onCheckedChange = { draft = draft.withHidden(app.pkg, it) }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                app.pkg,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (draft.tvApps.isEmpty()) {
                    EmptyState("Лаунчер не нашёл приложений с телевизионным интерфейсом.")
                }
                if (draft.phoneApps.isNotEmpty()) {
                    HorizontalDivider()
                    Caption(
                        "Приложений с телефонным интерфейсом: ${draft.phoneApps.size}. " +
                            "В сетку они не попадают — лаунчер показывает их в библиотеке."
                    )
                }
            }

            Panel {
                PanelTitle(
                    "Движение и эффекты",
                    "Для слабых телевизоров полезно уменьшить анимации."
                )
                SwitchRow(
                    title = "Анимации",
                    checked = draft.animations,
                    onChange = { draft = draft.copy(animations = it) }
                )
                SwitchRow(
                    title = "Дополнительные эффекты",
                    checked = draft.advancedEffects,
                    onChange = { draft = draft.copy(advancedEffects = it) }
                )
                SwitchRow(
                    title = "Меньше движения",
                    checked = draft.reducedMotion,
                    onChange = { draft = draft.copy(reducedMotion = it) },
                    hint = "Убирает параллакс и длинные переходы."
                )
                SwitchRow(
                    title = "Статистика",
                    checked = draft.showStats,
                    onChange = { draft = draft.copy(showStats = it) },
                    hint = "Показывает на главном экране время работы и память."
                )
            }

            Panel {
                PanelTitle("Сброс")
                Caption("Вернуть встроенный вид: удалить config.json, обои и перезапустить Home.")
                OutlinedButton(
                    onClick = { confirmReset = true },
                    enabled = !vm.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Сбросить вид к встроенному")
                }
            }

            Spacer(Modifier.height(4.dp))
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { vm.saveLauncherView(draft) },
                    enabled = dirty && !vm.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (dirty) "Сохранить на TV" else "Всё сохранено")
                }
                if (dirty) {
                    Caption("После сохранения лаунчер перезапустится и перечитает настройки.")
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Сбросить вид?") },
            text = {
                Text(
                    "С TV удалятся config.json и обои, главный экран вернётся к встроенному виду " +
                        "и перезапустится. Отключённые пакеты это не затрагивает."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    vm.resetTheme()
                }) { Text("Сбросить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Отмена") }
            }
        )
    }
}

// ---- что отключено ----

@Composable
private fun DisabledScreen(vm: AppViewModel) {
    val state = vm.disabledState
    var pendingRestore by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                ScreenTitle(
                    "Что отключено",
                    "Только то, что сделало это приложение. Любой пакет можно вернуть по одному."
                )
            }

            if (state == null) {
                item {
                    if (vm.busy) {
                        Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        OutlinedButton(onClick = vm::loadDisabled) { Text("Загрузить список") }
                    }
                }
            } else {
                if (state.leftovers.isNotEmpty()) {
                    item {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Вернулось после обновления прошивки: ${state.leftovers.size}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Такие пакеты TV включает заново при обновлении. Можно отключить их " +
                                        "снова одной кнопкой.",
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
                }

                item {
                    Text(
                        "Отключено сейчас: ${state.disabled.size}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (state.disabled.isEmpty()) {
                    item {
                        Panel {
                            EmptyState("Пока ничего не отключено. Список появится после первой очистки.")
                        }
                    }
                } else {
                    items(state.disabled, key = { it }) { pkg ->
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        LauncherTheme.labelFor(pkg),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        pkg,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = { pendingRestore = pkg },
                                    enabled = !vm.busy
                                ) { Text("Вернуть") }
                            }
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = vm::loadDisabled,
                        enabled = !vm.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Обновить")
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)) }
        }
    }

    pendingRestore?.let { pkg ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Включить обратно?") },
            text = {
                Text(
                    "$pkg снова появится на TV. Ничего не удаляется, отключить его можно будет " +
                        "в любой момент."
                )
            },
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

// ---- ещё ----

@Composable
private fun MoreScreen(vm: AppViewModel) {
    val connected = vm.probe != null
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenTitle("Ещё", "Лаунчер, настройки телевизора и отчёт для добавления модели.")

        Panel {
            PanelTitle("Телевизор")
            NavRow(
                icon = Icons.Default.Home,
                title = "Лаунчер",
                subtitle = vm.launcher?.let {
                    if (it.installed) "Установлен, версия ${it.versionName ?: it.bundledVersion}" else "Не установлен"
                } ?: "Установка и назначение главным экраном",
                enabled = connected
            ) { vm.open(Screen.Launcher) }
            HorizontalDivider()
            NavRow(
                icon = Icons.Default.Settings,
                title = "Настройки TV",
                subtitle = vm.tvSettings?.let {
                    "Блокировка рекламы: ${if (it.dnsBlocking) "включена" else "выключена"}"
                } ?: "Private DNS и анимации системы",
                enabled = connected
            ) { vm.open(Screen.TvSettings) }
            HorizontalDivider()
            NavRow(
                icon = Icons.AutoMirrored.Filled.List,
                title = "Отчёт о телевизоре",
                subtitle = "Текст для новой задачи: «список для такой-то модели»",
                enabled = connected
            ) { vm.open(Screen.Report) }
            if (!connected) {
                Caption("Сначала подключитесь к TV на вкладке «Подключение».")
            }
        }

        UpdatesPanel(vm)
        AboutPanel()
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tint)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UpdatesPanel(vm: AppViewModel) {
    val context = LocalContext.current
    Panel {
        PanelTitle("Обновления", "Релизы приложения на GitHub.")
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
                color = if (u.newer) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (u.notes.isNotBlank()) {
                Text(
                    u.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (u.newer) {
                if (u.apkUrl.isNullOrBlank()) {
                    Caption("В релизе пока нет APK — скачайте его со страницы релиза.")
                } else {
                    Button(
                        onClick = vm::installUpdate,
                        enabled = !vm.installingUpdate,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (vm.installingUpdate) "Скачиваем…" else "Обновить до ${u.version}")
                    }
                    val progress = vm.updateProgress
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Caption("Скачано $progress%")
                    } else {
                        Caption(
                            "APK скачается с GitHub и откроется системный установщик. " +
                                "При первом обновлении разрешите установку из этого приложения."
                        )
                    }
                }
            }
            if (u.url.startsWith("https://")) {
                TextButton(onClick = { openUrl(context, u.url) }) {
                    Text("Открыть страницу релиза")
                }
            }
        }
    }
}

@Composable
private fun AboutPanel() {
    val context = LocalContext.current
    Panel {
        PanelTitle("О приложении", "CleanMyTV ${BuildConfig.VERSION_NAME}")
        Text(
            "Приложение приводит Android TV в порядок: обратимо отключает лишние пакеты, " +
                "ставит чистый лаунчер и меняет пару системных настроек. Ничего не удаляется.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Сделано по мотивам открытого проекта Clean Your TV (лицензия MIT). " +
                "Лаунчер и списки пакетов — оттуда.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { openUrl(context, "https://github.com/KAnorov/cleanmytv") },
                modifier = Modifier.weight(1f)
            ) { Text("Исходный код") }
            OutlinedButton(
                onClick = { openUrl(context, "https://github.com/KAnorov/cleanmytv/issues") },
                modifier = Modifier.weight(1f)
            ) { Text("Проблемы") }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

// ---- лаунчер ----

@Composable
private fun LauncherScreen(vm: AppViewModel) {
    val st = vm.launcher
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenTitle(
            "Лаунчер",
            "Чистый главный экран с плитками приложений. Ставится с телефона."
        )

        if (st == null) {
            if (vm.busy) {
                Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                OutlinedButton(onClick = vm::refreshLauncher) { Text("Загрузить статус") }
            }
            return@Column
        }

        Panel {
            PanelTitle(
                if (st.installed) "Лаунчер установлен" else "Лаунчер не установлен",
                "Версия: ${st.versionName ?: "—"} · в сборке приложения: ${st.bundledVersion}"
            )
            LabeledValue("Home", if (st.isHome) "этот лаунчер" else st.homeComponent ?: "не определён")
            LabeledValue("Штатный", st.stockLauncher ?: "не найден")
            LabeledValue(
                "После перезагрузки",
                if (st.pinned) "останется главным" else "может вернуться штатный"
            )
            if (st.enabledStock.isNotEmpty()) {
                Text(
                    "Включены штатные: ${st.enabledStock.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

        Panel {
            Caption(
                "«Сделать по умолчанию» назначает новый лаунчер Home и отключает штатные, " +
                    "чтобы после перезагрузки не возвращался старый экран. После установки " +
                    "нажмите Home на пульте. Вид главного экрана настраивается на вкладке «Вид»."
            )
        }
    }
}

// ---- настройки телевизора ----

@Composable
private fun TvSettingsScreen(vm: AppViewModel) {
    val settings = vm.tvSettings
    var pending by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenTitle(
            "Настройки TV",
            "Две настройки, которые убирают рекламу и ускоряют меню. Обе обратимы."
        )

        if (settings == null) {
            if (vm.busy) {
                Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                OutlinedButton(onClick = vm::loadTvSettings) { Text("Загрузить настройки") }
            }
        } else {
            Panel {
                PanelTitle(
                    "Блокировка рекламы",
                    "Private DNS ${TvSettings.DNS_HOST}: реклама не загружается на уровне DNS."
                )
                SwitchRow(
                    title = "Включена",
                    checked = settings.dnsBlocking,
                    enabled = !vm.busy,
                    onChange = { on -> pending = if (on) "dns-on" else "dns-off" },
                    hint = if (settings.dnsBlocking) {
                        "Сервер: ${settings.dnsSpecifier.ifBlank { TvSettings.DNS_HOST }}"
                    } else {
                        "TV использует DNS провайдера."
                    }
                )
                Caption("Режим: ${settings.dnsMode.ifBlank { "—" }}")
            }

            Panel {
                PanelTitle(
                    "Анимации системы",
                    when {
                        settings.animationsOff -> "Выключены: меню переключается без задержек."
                        settings.animationsDefault -> "Стандартная скорость (1.0)."
                        else -> "Нестандартная скорость: ${settings.windowScale} / " +
                            "${settings.transitionScale} / ${settings.animatorScale}."
                    }
                )
                SwitchRow(
                    title = "Выключить анимации",
                    checked = settings.animationsOff,
                    enabled = !vm.busy,
                    onChange = { off -> pending = if (off) "anim-off" else "anim-on" },
                    hint = "Окно ${settings.windowScale} · переходы ${settings.transitionScale} · " +
                        "аниматор ${settings.animatorScale}"
                )
            }

            OutlinedButton(
                onClick = vm::loadTvSettings,
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Обновить")
            }

            Caption(
                "После смены Private DNS перезагрузите TV: некоторые прошивки держат старый DNS " +
                    "до перезапуска. Если что-то перестало открываться — выключите блокировку, " +
                    "это мгновенно возвращает всё как было."
            )
        }
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
                        "anim-off" -> "Изменятся три системных множителя: окно, переходы и аниматор " +
                            "станут 0.0. Меню начнёт открываться мгновенно."
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
                    Text(if (action.endsWith("off")) "Выключить" else "Включить")
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("Отмена") }
            }
        )
    }
}

// ---- отчёт ----

@Composable
private fun ReportScreen(vm: AppViewModel) {
    val text = vm.reportText
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember(text) { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) vm.writeExport(context.applicationContext, uri)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenTitle(
            "Отчёт о телевизоре",
            "Текст без адресов и серийников. Его вставляют в новую задачу, чтобы добавить " +
                "вашу модель в список очистки."
        )

        if (text == null) {
            if (vm.busy) {
                Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                OutlinedButton(onClick = vm::loadReport) { Text("Собрать отчёт") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(text))
                        copied = true
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Скопировать") }
                OutlinedButton(
                    onClick = {
                        val name = vm.prepareReportExport() ?: return@OutlinedButton
                        saveLauncher.launch(name)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Сохранить файл") }
            }
            if (copied) {
                Caption("Отчёт скопирован — вставьте его в новую задачу на GitHub.")
            }

            Panel {
                SelectionContainer {
                    Text(
                        text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            OutlinedButton(
                onClick = { openUrl(context, "https://github.com/KAnorov/cleanmytv/issues/new") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Создать задачу на GitHub")
            }
            TextButton(onClick = vm::loadReport, enabled = !vm.busy) {
                Text("Собрать заново")
            }
        }
    }
}
