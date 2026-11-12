package ru.cleanmytv.app

import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Ищет телевизоры в локальной сети: перебирает адреса /24 вокруг адреса телефона
 * и проверяет, принимает ли устройство TCP-соединение на порту отладки 5555.
 * Так же ищет и вторая версия Clean Your TV: TV, у которого открыт только
 * порт беспроводной отладки, найдётся лишь после сопряжения — его сюда не относим.
 */
object Discovery {

    const val ADB_PORT = 5555
    private const val CONNECT_TIMEOUT_MS = 300
    private const val WORKERS = 32

    data class Found(val host: String)

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
                    if (accepts(ip)) found += Found(ip)
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
}
