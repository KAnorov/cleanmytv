package ru.cleanmytv.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Скачивание APK из релиза и передача его системному установщику. */
object UpdateInstaller {

    private const val FILE_NAME = "CleanMyTV-update.apk"

    /** Разрешено ли приложению открывать установщик APK. */
    fun canInstall(context: Application): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Экран «Установка неизвестных приложений» для этого приложения. */
    fun openSettings(context: Application) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /**
     * Качает APK в кеш приложения. GitHub отдаёт файл редиректом, поэтому
     * HttpURLConnection сам переходит по нему. Прогресс — в процентах.
     */
    fun download(
        context: Application,
        url: String,
        expectedSize: Long,
        onProgress: (Int) -> Unit
    ): File {
        val target = File(context.cacheDir, FILE_NAME)
        if (target.exists()) target.delete()
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "CleanMyTV-Android")
                setRequestProperty("Accept", "application/octet-stream")
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("GitHub ответил кодом $code")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var last = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            val percent = ((copied * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != last) {
                                last = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
            if (target.length() < 1024) throw IllegalStateException("Скачался пустой файл")
            return target
        } catch (e: Throwable) {
            target.delete()
            throw e
        } finally {
            connection?.disconnect()
        }
    }

    /** Открывает системный установщик: он покажет «Обновить приложение?». */
    fun install(context: Application, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
