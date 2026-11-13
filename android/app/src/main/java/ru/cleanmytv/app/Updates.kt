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

    private const val API =
        "https://api.github.com/repos/KAnorov/cleanmytv/releases?per_page=30"

    fun check(current: String): UpdateInfo {
        val connection = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "CleanMyTV-Android")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("GitHub ответил кодом $code")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val releases = JSONArray(body)
            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                if (release.optBoolean("draft")) continue
                val tag = release.optString("tag_name")
                if (!tag.startsWith("android-")) continue
                val version = tag.removePrefix("android-v").removePrefix("android-")
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
        } finally {
            connection.disconnect()
        }
    }

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
