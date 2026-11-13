package ru.cleanmytv.app

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

enum class Screen { Connect, Clean, Appearance, Disabled, More, Launcher, TvSettings, Report }

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("cleanmytv", Context.MODE_PRIVATE)
    private var tv: TvClient? = null
    private var debloat: Debloat? = null
    private val scanCancelled = AtomicBoolean(false)

    var host by mutableStateOf(prefs.getString("host", "") ?: "")
    var screen by mutableStateOf(Screen.Connect)
    var connecting by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var probe by mutableStateOf<Probe?>(null)
        private set

    var plan by mutableStateOf<Plan?>(null)
        private set
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set
    var launcher by mutableStateOf<LauncherStatus?>(null)
        private set

    var scanning by mutableStateOf(false)
        private set
    var scanChecked by mutableStateOf(0)
        private set
    var found by mutableStateOf<List<Discovery.Found>>(emptyList())
        private set

    var disabledState by mutableStateOf<DisabledState?>(null)
        private set
    var tvSettings by mutableStateOf<TvSettingsState?>(null)
        private set
    var launcherView by mutableStateOf<LauncherView?>(null)
        private set
    var reportText by mutableStateOf<String?>(null)
        private set
    private var theme: LauncherTheme? = null

    var update by mutableStateOf<UpdateInfo?>(null)
        private set
    var checkingUpdate by mutableStateOf(false)
        private set
    var installingUpdate by mutableStateOf(false)
        private set
    var updateProgress by mutableStateOf<Int?>(null)
        private set

    var exportText by mutableStateOf<String?>(null)
        private set
    var exportName by mutableStateOf("")
        private set

    fun updateHost(value: String) {
        host = value
    }

    fun connect() {
        val address = host.trim()
        if (address.isBlank()) {
            error = "Введите IP-адрес TV, например 192.168.1.50."
            return
        }
        connectTo(address)
    }

    fun connectTo(address: String) {
        if (connecting) return
        host = address
        connecting = true
        error = null
        message = null
        probe = null
        plan = null
        launcher = null
        disabledState = null
        tvSettings = null
        launcherView = null
        reportText = null
        theme = null
        selected = emptySet()
        viewModelScope.launch {
            try {
                val client = withContext(Dispatchers.IO) {
                    TvClient(getApplication()).also { it.connect(address) }
                }
                tv?.close()
                tv = client
                probe = client.probe
                prefs.edit().putString("host", address).apply()
                stopScan()
                screen = Screen.Clean
                loadPlan()
            } catch (e: Throwable) {
                error = friendly(e)
                withContext(Dispatchers.IO) { runCatching { tv?.close() } }
                tv = null
            } finally {
                connecting = false
            }
        }
    }

    fun open(target: Screen) {
        if (probe == null && target != Screen.Connect && target != Screen.More) return
        screen = target
        when (target) {
            Screen.Clean -> if (plan == null) loadPlan()
            Screen.Launcher -> if (launcher == null) refreshLauncher()
            Screen.Disabled -> loadDisabled()
            Screen.Appearance -> if (launcherView == null) loadLauncherView()
            Screen.TvSettings -> if (tvSettings == null) loadTvSettings()
            Screen.Report -> loadReport()
            Screen.More, Screen.Connect -> Unit
        }
    }

    fun toggle(pkg: String) {
        selected = if (pkg in selected) selected - pkg else selected + pkg
    }

    fun selectAllSuggested() {
        val p = plan ?: return
        selected = p.entries.filter { it.suggested && it.state == "enabled" }.map { it.pkg }.toSet()
    }

    fun select(pkgs: Collection<String>) {
        selected = selected + pkgs.filter { pkg -> plan?.entries?.any { it.pkg == pkg && it.state == "enabled" } == true }
    }

    fun deselect(pkgs: Collection<String>) {
        selected = selected - pkgs.toSet()
    }

    fun clearSelection() {
        selected = emptySet()
    }

    fun loadPlan() {
        val p = probe ?: return
        val client = tv ?: return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                val d = Debloat(getApplication(), client)
                val next = withContext(Dispatchers.IO) { d.plan(p) }
                debloat = d
                plan = next
                selected = next.suggestedInstalled.map { it.pkg }.toSet()
            } catch (e: Throwable) {
                error = friendly(e)
            } finally {
                busy = false
            }
        }
    }

    fun applySelected() {
        val p = plan ?: return
        val d = debloat ?: return
        if (selected.isEmpty()) {
            error = "Ничего не выбрано. Поставьте галочки на пакетах."
            return
        }
        busy = true
        error = null
        message = null
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { d.apply(p, selected) }
                message = render(result)
                reloadPlan()
            } catch (e: Throwable) {
                error = friendly(e)
            } finally {
                busy = false
            }
        }
    }

    fun restore() {
        val record = plan?.record
        val d = debloat
        if (record == null || d == null) {
            error = "Точки восстановления пока нет."
            return
        }
        busy = true
        error = null
        message = null
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { d.restore(record) }
                message = render(result)
                reloadPlan()
            } catch (e: Throwable) {
                error = friendly(e)
            } finally {
                busy = false
            }
        }
    }

    // ---- поиск телевизоров в сети ----

    fun startScan() {
        if (scanning) {
            stopScan()
            return
        }
        if (Discovery.localAddress() == null) {
            error = "Телефон не видит Wi-Fi сеть. Подключите его к той же сети, что и TV."
            return
        }
        scanCancelled.set(false)
        scanning = true
        scanChecked = 0
        found = emptyList()
        error = null
        message = null
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    Discovery.scan(
                        onProgress = { checked, list ->
                            scanChecked = checked
                            found = list
                        },
                        shouldStop = { scanCancelled.get() }
                    )
                }
                if (!scanCancelled.get()) {
                    found = result
                    if (result.isEmpty()) {
                        message = "Телевизоров с открытым портом 5555 не нашлось. Проверьте, что на TV " +
                            "включены «Отладка по USB» и «Отладка по сети», и что он в той же Wi-Fi сети."
                    }
                }
            } catch (e: Throwable) {
                error = friendly(e)
            } finally {
                scanning = false
            }
        }
    }

    private fun stopScan() {
        scanCancelled.set(true)
        scanning = false
    }

    // ---- что отключено и хвосты после обновления ----

    fun loadDisabled() {
        val d = debloat ?: return
        val record = plan?.record
        busy = true
        error = null
        viewModelScope.launch {
            try {
                disabledState = withContext(Dispatchers.IO) {
                    if (record == null) DisabledState(emptyList(), emptyList()) else d.disabledState(record)
                }
            } catch (e: Throwable) {
                error = friendly(e)
            } finally {
                busy = false
            }
        }
    }

    fun restoreOne(pkg: String) {
        val d = debloat ?: return
        busy = true
        error = null
        message = null
        viewModelScope.launch {
            try {
                message = render(withContext(Dispatchers.IO) { d.restoreOne(pkg) })
                reloadPlan()
            } catch (e: Throwable) {
                error = friendly(e)
            } finally {
                busy = false
            }
        }
    }

    fun reapplyLeftovers() {
        val d = debloat ?: return
        val leftovers = disabledState?.leftovers.orEmpty()
        busy = true
        error = null
        message = null
        viewModelScope.launch {
            try {
                message = render(withContext(Dispatchers.IO) { d.reapply(leftovers) })
                reloadPlan()
            } catch (e: Throwable) {
                error = friendly(e)
            } finally {
                busy = false
            }
        }
    }

    // ---- настройки самого телевизора ----

    fun loadTvSettings() {
        val client = tv ?: return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                tvSettings = withContext(Dispatchers.IO) { TvSettings(client).read() }
            } catch (e: Throwable) {
                error = friendly(e)
            } finally {
                busy = false
            }
        }
    }

    fun setDnsBlocking(enable: Boolean) {
        val client = tv ?: return
        busy = true
        error = null
        message = null
        viewModelScope.launch {
            try {
                val settings = TvSettings(client)
                message = withContext(Dispatchers.IO) {
                    if (enable) settings.enableDnsBlocking() else settings.disableDnsBlocking()
                }
                tvSettings = withContext(Dispatchers.IO) { settings.read() }
            } catch (e: Throwable) {
                error = friendly(e)
            } finally {
                busy = false
            }
        }
    }

    fun setAnimationsOff(off: Boolean) {
        val client = tv ?: return
        busy = true
        error = null
        message = null
        viewModelScope.launch {
            try {
                val settings = TvSettings(client)
                message = withContext(Dispatchers.IO) {
                    if (off) settings.disableAnimations() else settings.resetAnimations()
                }
                tvSettings = withContext(Dispatchers.IO) { settings.read() }
            } catch (e: Throwable) {
                error = friendly(e)
            } finally {
                busy = false
            }
        }
    }

    // ---- вид главного экрана ----

    private fun themeFor(client: TvClient): LauncherTheme =
        theme ?: LauncherTheme(client, getApplication()).also { theme = it }

    fun loadLauncherView() {
        val client = tv ?: return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                launcherView = withContext(Dispatchers.IO) { themeFor(client).read() }
            } catch (e: Throwable) {
                error = friendly(e)
            } finally {
                busy = false
            }
        }
    }

    fun saveLauncherView(view: LauncherView) {
        val client = tv ?: return
        busy = true
        error = null
        message = null
        viewModelScope.launch {
            try {
                val settings = themeFor(client)
                message = render(withContext(Dispatchers.IO) { settings.save(view) })
                launcherView = withContext(Dispatchers.IO) { settings.read() }
            } catch (e: Throwable) {
                error = friendly(e)
            } finally {
                busy = false
            }
        }
    }

    fun applyWallpaper(uri: Uri) {
        val client = tv ?: return
        busy = true
        error = null
        message = null
        viewModelScope.launch {
            try {
                val settings = themeFor(client)
                message = render(withContext(Dispatchers.IO) { settings.setWallpaper(uri) })
                launcherView = withContext(Dispatchers.IO) { settings.read() }
            } catch (e: Throwable) {
                error = friendly(e)
            } finally {
                busy = false
            }
        }
    }

    fun removeWallpaper() {
        val client = tv ?: return
        busy = true
        error = null
        message = null
        viewModelScope.launch {
            try {
                val settings = themeFor(client)
                message = render(withContext(Dispatchers.IO) { settings.clearWallpaper() })
                launcherView = withContext(Dispatchers.IO) { settings.read() }
            } catch (e: Throwable) {
                error = friendly(e)
            } finally {
                busy = false
            }
        }
    }

    fun resetTheme() {
        val client = tv ?: return
        busy = true
        error = null
        message = null
        viewModelScope.launch {
            try {
                val settings = themeFor(client)
                message = render(withContext(Dispatchers.IO) { settings.reset() })
                launcherView = withContext(Dispatchers.IO) { settings.read() }
            } catch (e: Throwable) {
                error = friendly(e)
            } finally {
                busy = false
            }
        }
    }

    // ---- отчёт для добавления модели в список ----

    fun loadReport() {
        val p = probe ?: return
        val record = plan?.record
        val d = debloat
        busy = true
        error = null
        viewModelScope.launch {
            try {
                val state = if (d != null && record != null) {
                    withContext(Dispatchers.IO) { d.disabledState(record) }
                } else {
                    null
                }
                if (state != null) disabledState = state
                val changed = record?.packages
                    ?.filterValues { it == "enabled" }
                    ?.keys
                    ?.sorted()
                    .orEmpty()
                reportText = Report.build(
                    probe = p,
                    plan = plan,
                    disabled = state?.disabled ?: changed,
                    leftovers = state?.leftovers.orEmpty(),
                    version = BuildConfig.VERSION_NAME
                )
            } catch (e: Throwable) {
                error = friendly(e)
            } finally {
                busy = false
            }
        }
    }

    fun prepareReportExport(): String? {
        val text = reportText ?: return null
        exportText = text
        val model = (probe?.model ?: "tv").replace(Regex("[^A-Za-z0-9.-]"), "_")
        exportName = "cleanmytv-report-$model.md"
        return exportName
    }

    // ---- лаунчер ----

    fun refreshLauncher() {
        val client = tv ?: return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                launcher = withContext(Dispatchers.IO) { LauncherOps.status(client) }
            } catch (e: Throwable) {
                error = friendly(e)
            } finally {
                busy = false
            }
        }
    }

    fun installLauncher() {
        val client = tv ?: return
        busy = true
        error = null
        message = null
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { LauncherOps.install(getApplication(), client) }
                message = render(result)
                launcher = withContext(Dispatchers.IO) { LauncherOps.status(client) }
                reloadPlan()
            } catch (e: Throwable) {
                error = friendly(e)
            } finally {
                busy = false
            }
        }
    }

    fun makeDefaultLauncher() {
        val client = tv ?: return
        busy = true
        error = null
        message = null
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { LauncherOps.makeDefault(client) }
                message = render(result)
                launcher = withContext(Dispatchers.IO) { LauncherOps.status(client) }
                reloadPlan()
            } catch (e: Throwable) {
                error = friendly(e)
            } finally {
                busy = false
            }
        }
    }

    fun useStockLauncher() {
        val client = tv ?: return
        busy = true
        error = null
        message = null
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { LauncherOps.useStock(client) }
                message = render(result)
                launcher = withContext(Dispatchers.IO) { LauncherOps.status(client) }
                reloadPlan()
            } catch (e: Throwable) {
                error = friendly(e)
            } finally {
                busy = false
            }
        }
    }

    // ---- обновления и экспорт ----

    fun checkUpdates() {
        if (checkingUpdate) return
        checkingUpdate = true
        error = null
        viewModelScope.launch {
            try {
                update = withContext(Dispatchers.IO) { Updates.check(BuildConfig.VERSION_NAME) }
            } catch (e: Throwable) {
                error = friendly(e)
            } finally {
                checkingUpdate = false
            }
        }
    }

    /** Скачивает APK последнего релиза и открывает системный установщик. */
    fun installUpdate() {
        val info = update ?: return
        val url = info.apkUrl
        if (url.isNullOrBlank()) {
            error = "В последнем релизе нет APK — откройте страницу релиза."
            return
        }
        if (installingUpdate) return
        val app = getApplication<Application>()
        if (!UpdateInstaller.canInstall(app)) {
            message = "Разрешите CleanMyTV установку приложений на открывшемся экране, " +
                "затем нажмите «Обновить» снова."
            UpdateInstaller.openSettings(app)
            return
        }
        installingUpdate = true
        updateProgress = 0
        error = null
        message = null
        viewModelScope.launch {
            try {
                val apk = withContext(Dispatchers.IO) {
                    UpdateInstaller.download(app, url, info.apkSize) { percent ->
                        updateProgress = percent
                    }
                }
                updateProgress = null
                UpdateInstaller.install(app, apk)
            } catch (e: Throwable) {
                updateProgress = null
                error = friendly(e)
            } finally {
                installingUpdate = false
            }
        }
    }

    /** Готовит текст скрипта и возвращает имя файла; сохранением файла занимается экран. */
    fun prepareExport(extension: String): String? {
        val p = probe ?: return null
        val record = plan?.record
        if (record == null) {
            error = "Точки восстановления пока нет — сохранять нечего."
            return null
        }
        exportText = if (extension.equals("ps1", ignoreCase = true)) {
            RestoreScript.powershell(p, record)
        } else {
            RestoreScript.bash(p, record)
        }
        exportName = RestoreScript.fileName(p, extension)
        return exportName
    }

    fun writeExport(context: Context, uri: Uri) {
        val text = exportText ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("Не удалось открыть файл для записи")
                    stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                message = "Файл сохранён: $exportName"
            } catch (e: Throwable) {
                error = friendly(e)
            }
        }
    }

    fun clearMessages() {
        error = null
        message = null
    }

    fun disconnect() {
        val client = tv
        tv = null
        debloat = null
        theme = null
        probe = null
        plan = null
        launcher = null
        disabledState = null
        tvSettings = null
        launcherView = null
        reportText = null
        selected = emptySet()
        screen = Screen.Connect
        message = null
        error = null
        stopScan()
        viewModelScope.launch(Dispatchers.IO) { runCatching { client?.close() } }
    }

    private suspend fun reloadPlan() {
        val p = probe ?: return
        val d = debloat ?: return
        val next = withContext(Dispatchers.IO) { d.plan(p) }
        plan = next
        selected = next.suggestedInstalled.map { it.pkg }.toSet()
        val record = next.record
        disabledState = withContext(Dispatchers.IO) {
            if (record == null) DisabledState(emptyList(), emptyList()) else d.disabledState(record)
        }
    }

    private fun render(result: ApplyResult): String {
        if (result.warnings.isEmpty()) return result.summary
        return result.summary + "\n\n" + result.warnings.joinToString("\n")
    }

    private fun friendly(e: Throwable): String {
        val text = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
        val lower = text.lowercase()
        if (lower.contains("auth") || lower.contains("allow") || lower.contains("timeout") || lower.contains("timed out")) {
            return "$text\n\nПосмотрите на TV: подтвердите «Разрешить отладку по USB» и включите «Всегда разрешать с этого компьютера»."
        }
        return "$text\n\nПроверьте, что телефон и TV в одной Wi-Fi сети и на TV включена «Отладка по USB»."
    }

    override fun onCleared() {
        runCatching { tv?.close() }
        super.onCleared()
    }
}
