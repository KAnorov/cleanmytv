package ru.cleanmytv.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Ищет телевизоры в локальной сети: перебирает адреса /24 вокруг адреса телефона
 * и проверяет, принимает ли устройство TCP-соединение на порту отладки 5555.
 * Так же ищет и вторая версия Clean Your TV: TV, у которого открыт только
 * порт беспроводной отладки, найдётся лишь после сопряжения — его сюда не относим.
 *
 * У найденного адреса сразу спрашиваем имя: Android TV с Google Cast отвечает
 * на порту 8008 (eureka_info) названием устройства из настроек и моделью.
 * Это единственный способ узнать имя до подключения по ADB — сам ADB отдаёт
 * данные только после разрешения на TV.
 */
object Discovery {

    const val ADB_PORT = 5555
    private const val CAST_PORT = 8008
    private const val CONNECT_TIMEOUT_MS = 300
    private const val NAME_TIMEOUT_MS = 800
    private const val MAX_JSON_CHARS = 16 * 1024
    private const val WORKERS = 32

    data class Found(
        val host: String,
        val name: String = "",
        val model: String = "",
        val manufacturer: String = ""
    ) {
        /** Крупная строка в списке: имя из Cast, иначе модель, иначе адрес. */
        val title: String
            get() = when {
                name.isNotBlank() -> name
                model.isNotBlank() -> model
                else -> host
            }

        /** Вторая строка: адрес и уточнение модели, если имя уже показано. */
        val details: String
            get() {
                if (title == host) return ""
                val hardware = listOf(manufacturer, model)
                    .filter { it.isNotBlank() && !it.equals(title, ignoreCase = true) }
                    .joinToString(" ")
                    .trim()
                return if (hardware.isEmpty()) host else "$host · $hardware"
            }
    }

    private data class CastInfo(val name: String, val model: String, val manufacturer: String)

    /** Адрес телефона в Wi-Fi сети, например 192.168.1.23. */
    fun localAddress(): String? {
        val candidates = mutableListOf<Inet4Address>()
        try {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        candidates += addr
                    }
                }
            }
        } catch (_: Throwable) {
            return null
        }
        val preferred = candidates.firstOrNull { it.isSiteLocalAddress } ?: candidates.firstOrNull()
        return preferred?.hostAddress
    }

    /**
     * @param onProgress сколько адресов уже проверено и что найдено к этому моменту.
     * @param shouldStop позволяет прервать поиск, когда пользователь закрыл экран.
     */
    fun scan(
        onProgress: (checked: Int, found: List<Found>) -> Unit,
        shouldStop: () -> Boolean
    ): List<Found> {
        val local = localAddress() ?: return emptyList()
        val prefix = local.substringBeforeLast('.', "")
        if (prefix.isEmpty()) return emptyList()
        val mine = local.substringAfterLast('.').toIntOrNull() ?: return emptyList()

        val hosts = (1..254).filter { it != mine }.map { "$prefix.$it" }
        val found = Collections.synchronizedList(mutableListOf<Found>())
        val checked = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(WORKERS)

        for (ip in hosts) {
            pool.execute {
                if (!shouldStop()) {
                    if (accepts(ip)) found += describe(ip)
                    val done = checked.incrementAndGet()
                    if (done % 16 == 0 || done == hosts.size) {
                        onProgress(done, snapshot(found))
                    }
                }
            }
        }
        pool.shutdown()
        pool.awaitTermination(30, TimeUnit.SECONDS)
        return snapshot(found)
    }

    private fun snapshot(found: List<Found>): List<Found> = synchronized(found) {
        found.sortedBy { it.host.substringAfterLast('.').toIntOrNull() ?: 0 }
    }

    private fun accepts(ip: String): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(ip, ADB_PORT), CONNECT_TIMEOUT_MS)
            true
        }
    } catch (_: Throwable) {
        false
    }

    private fun describe(ip: String): Found {
        val info = castInfo(ip) ?: return Found(ip)
        return Found(ip, info.name, info.model, info.manufacturer)
    }

    /** Имя и модель из Google Cast; null — устройство не отвечает или это не Cast. */
    private fun castInfo(ip: String): CastInfo? {
        for (path in EUREKA_PATHS) {
            val body = httpGet("http://$ip:$CAST_PORT$path") ?: continue
            try {
                val json = JSONObject(body)
                val info = json.optJSONObject("device_info")
                val name = json.optString("name").trim()
                val model = info?.optString("model_name")?.trim().orEmpty()
                val manufacturer = info?.optString("manufacturer")?.trim().orEmpty()
                if (name.isNotEmpty() || model.isNotEmpty()) return CastInfo(name, model, manufacturer)
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun httpGet(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = NAME_TIMEOUT_MS
                readTimeout = NAME_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "CleanMyTV")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(MAX_JSON_CHARS)
                var total = 0
                while (total < buffer.size) {
                    val read = reader.read(buffer, total, buffer.size - total)
                    if (read <= 0) break
                    total += read
                }
                String(buffer, 0, total)
            }
        } catch (_: Throwable) {
            null
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Throwable) {
            }
        }
    }

    private val EUREKA_PATHS = listOf("/setup/eureka_info?options=detail", "/setup/eureka_info")
}
