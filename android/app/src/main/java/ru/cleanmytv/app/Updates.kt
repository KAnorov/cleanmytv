package ru.cleanmytv.app

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val tag: String,
    val name: String,
    val url: String,
    val notes: String,
    val version: String,
    val newer: Boolean,
    val apkUrl: String?,
    val apkSize: Long
)

/** Проверка обновлений APK по релизам репозитория на GitHub. */
object Updates {

    private const val REPO = "https://github.com/KAnorov/cleanmytv"
    private const val API = "https://api.github.com/repos/KAnorov/cleanmytv/releases?per_page=30"
    private const val FEED = "$REPO/releases.atom"

    /**
     * Сначала спрашиваем API: там есть размер и текст релиза. Если GitHub ограничил
     * частоту запросов (обычная история с общих IP), читаем ленту релизов — она без лимита,
     * а ссылку на APK собираем по имени файла.
     */
    fun check(current: String): UpdateInfo {
        try {
            return checkApi(current)
        } catch (apiError: Throwable) {
            try {
                return checkFeed(current)
            } catch (_: Throwable) {
                throw apiError
            }
        }
    }

    private fun checkApi(current: String): UpdateInfo {
        val body = httpGet(API, "application/vnd.github+json")
        val releases = JSONArray(body)
        for (i in 0 until releases.length()) {
            val release = releases.getJSONObject(i)
            if (release.optBoolean("draft")) continue
            val tag = release.optString("tag_name")
            if (!tag.startsWith("android-")) continue
            val version = versionOf(tag)
            var apkUrl: String? = null
            var apkSize = 0L
            val assets = release.optJSONArray("assets")
            if (assets != null) {
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    if (!asset.optString("name").endsWith(".apk", ignoreCase = true)) continue
                    apkUrl = asset.optString("browser_download_url")
                        .takeIf { it.startsWith("https://") }
                    apkSize = asset.optLong("size")
                    break
                }
            }
            return UpdateInfo(
                tag = tag,
                name = release.optString("name").ifBlank { tag },
                url = release.optString("html_url"),
                notes = release.optString("body").trim().take(700),
                version = version,
                newer = isNewer(version, current),
                apkUrl = apkUrl,
                apkSize = apkSize
            )
        }
        throw IllegalStateException("В репозитории пока нет релизов с тегом android-*")
    }

    private fun checkFeed(current: String): UpdateInfo {
        val feed = httpGet(FEED, "application/atom+xml")
        val entries = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
            .findAll(feed)
            .map { it.groupValues[1] }
            .toList()
        val entry = entries.firstOrNull { tagOf(it)?.startsWith("android-") == true }
            ?: throw IllegalStateException("В ленте релизов нет сборок для Android")
        val tag = tagOf(entry)
            ?: throw IllegalStateException("Не удалось разобрать тег релиза")
        val version = versionOf(tag)
        val html = Regex("<content type=\"html\">(.*?)</content>", RegexOption.DOT_MATCHES_ALL)
            .find(entry)?.groupValues?.get(1).orEmpty()
        val name = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
            .find(entry)?.groupValues?.get(1)?.let(::plainText).orEmpty()
        val asset = "$REPO/releases/download/$tag/CleanMyTV-android-$version.apk"
        return UpdateInfo(
            tag = tag,
            name = name.ifBlank { tag },
            url = "$REPO/releases/tag/$tag",
            notes = plainText(html).trim().take(700),
            version = version,
            newer = isNewer(version, current),
            apkUrl = asset,
            apkSize = 0
        )
    }

    private fun httpGet(url: String, accept: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "CleanMyTV-Android")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("GitHub ответил кодом $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun tagOf(entry: String): String? =
        Regex("<id>[^<]*/([^/<]+)</id>")
            .find(entry)?.groupValues?.get(1)?.trim()

    private fun versionOf(tag: String): String =
        tag.removePrefix("android-v").removePrefix("android-")

    private fun plainText(html: String): String = html
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p>|</li>|</div>|</h\\d>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace(Regex("\n{3,}"), "\n\n")

    private fun isNewer(candidate: String, current: String): Boolean {
        val left = candidate.split('.').mapNotNull { it.toIntOrNull() }
        val right = current.split('.').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(i) { 0 }
            val b = right.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
