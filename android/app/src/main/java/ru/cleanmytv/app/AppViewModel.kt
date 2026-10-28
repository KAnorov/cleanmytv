package ru.cleanmytv.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { Connect, Clean, Launcher }

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("cleanmytv", Context.MODE_PRIVATE)
    private var tv: TvClient? = null
    private var debloat: Debloat? = null

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

    fun setHost(value: String) {
        host = value
    }

    fun connect() {
        if (connecting) return
        val address = host.trim()
        if (address.isBlank()) {
            error = "Введите IP-адрес TV, например 192.168.1.50."
            return
        }
        connecting = true
        error = null
        message = null
        probe = null
        plan = null
        launcher = null
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
        if (probe == null) return
        screen = target
        when (target) {
            Screen.Clean -> if (plan == null) loadPlan()
            Screen.Launcher -> if (launcher == null) refreshLauncher()
            Screen.Connect -> Unit
        }
    }

    fun toggle(pkg: String) {
        selected = if (pkg in selected) selected - pkg else selected + pkg
    }

    fun selectAllSuggested() {
        val p = plan ?: return
        selected = p.entries.filter { it.suggested && it.state != "absent" }.map { it.pkg }.toSet()
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

    fun clearMessages() {
        error = null
        message = null
    }

    fun disconnect() {
        val client = tv
        tv = null
        debloat = null
        probe = null
        plan = null
        launcher = null
        selected = emptySet()
        screen = Screen.Connect
        message = null
        error = null
        viewModelScope.launch(Dispatchers.IO) { runCatching { client?.close() } }
    }

    private suspend fun reloadPlan() {
        val p = probe ?: return
        val d = debloat ?: return
        val next = withContext(Dispatchers.IO) { d.plan(p) }
        plan = next
        selected = next.suggestedInstalled.map { it.pkg }.toSet()
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
