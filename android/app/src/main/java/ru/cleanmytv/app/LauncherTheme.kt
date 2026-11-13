package ru.cleanmytv.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID

/** Приложение на телевизоре, которое лаунчер может показать плиткой. */
data class TvApp(val pkg: String, val label: String, val tv: Boolean)

/**
 * Настройки вида главного экрана. Лаунчер читает их из config.json в своей папке
 * на TV, поэтому всё меняется с телефона и без пересборки приложения.
 */
data class LauncherView(
    val present: Boolean,
    val columns: Int,
    val rowsBelowDock: Int,
    val dockRow: Boolean,
    val dockTitle: Boolean,
    val fillDock: Boolean,
    val appLibrary: Boolean,
    val reorderButton: Boolean,
    val showStats: Boolean,
    val animations: Boolean,
    val advancedEffects: Boolean,
    val reducedMotion: Boolean,
    val greetingName: String,
    val hidden: List<String>,
    val dock: List<String>,
    val apps: List<TvApp>,
    val wallpaper: Boolean
) {
    val tvApps: List<TvApp> get() = apps.filter { it.tv }
    val phoneApps: List<TvApp> get() = apps.filter { !it.tv }
    val hiddenSet: Set<String> get() = hidden.toSet()

    /** Порядок плиток как у лаунчера: сначала док, затем по алфавиту. */
    fun ordered(): List<TvApp> = tvApps.sortedWith(
        compareBy<TvApp> { app ->
            val index = dock.indexOf(app.pkg)
            if (index >= 0) index else Int.MAX_VALUE
        }.thenBy { it.label.lowercase() }
    )

    fun withHidden(pkg: String, hide: Boolean): LauncherView {
        val next = if (hide) (hidden + pkg).distinct() else hidden - pkg
        return copy(hidden = next)
    }

    fun withDock(pkg: String): LauncherView = copy(dock = (listOf(pkg) + dock).distinct())
}

class LauncherTheme(private val tv: TvClient, private val context: Context) {

    private var base: JSONObject = JSONObject()

    fun read(): LauncherView {
        val raw = tv.readBase64File(CONFIG)
        val cfg = if (raw == null || raw.isEmpty()) JSONObject() else parse(raw)
        base = cfg
        return LauncherView(
            present = raw != null,
            columns = cfg.optInt("columns", 4).coerceIn(4, 8),
            rowsBelowDock = cfg.optInt("rows_below_dock", 0).coerceIn(0, 3),
            dockRow = cfg.optBoolean("dock_row", true),
            dockTitle = cfg.optBoolean("dock_title", true),
            fillDock = cfg.optBoolean("fill_dock", true),
            appLibrary = cfg.optBoolean("app_library", false),
            reorderButton = cfg.optBoolean("reorder_button", false),
            showStats = cfg.optBoolean("show_stats", false),
            animations = cfg.optBoolean("animations", true),
            advancedEffects = cfg.optBoolean("advanced_effects", false),
            reducedMotion = cfg.optBoolean("reduced_motion", false),
            greetingName = cfg.optString("greeting_name", ""),
            hidden = cfg.optJSONArray("hidden_apps").asStringList(),
            dock = cfg.optJSONArray("dock_apps").asStringList(),
            apps = apps(),
            wallpaper = cfg.optBoolean("wallpaper", true) && tv.fileExists(WALLPAPER)
        )
    }

    fun save(view: LauncherView): ApplyResult {
        ensureDir()
        val cfg = JSONObject(base.toString())
        cfg.put("columns", view.columns)
        cfg.put("rows_below_dock", view.rowsBelowDock)
        cfg.put("dock_row", view.dockRow)
        cfg.put("dock_title", view.dockTitle)
        cfg.put("fill_dock", view.fillDock)
        cfg.put("app_library", view.appLibrary)
        cfg.put("reorder_button", view.reorderButton)
        cfg.put("show_stats", view.showStats)
        cfg.put("animations", view.animations)
        cfg.put("advanced_effects", view.advancedEffects)
        cfg.put("reduced_motion", view.reducedMotion)
        cfg.put("wallpaper", view.wallpaper)
        if (view.greetingName.isBlank()) {
            cfg.remove("greeting_name")
        } else {
            cfg.put("greeting_name", view.greetingName.trim())
        }
        cfg.put("hidden_apps", JSONArray(view.hidden))
        if (view.dock.isEmpty()) cfg.remove("dock_apps") else cfg.put("dock_apps", JSONArray(view.dock))
        writeConfig(cfg)
        bumpRevision()
        restartHome()
        base = cfg
        return ApplyResult(true, "Вид отправлен на TV. Главный экран перезапущен.")
    }

    fun setWallpaper(uri: Uri): ApplyResult {
        val bytes = prepareWallpaper(uri)
        ensureDir()
        tv.writeBase64File(WALLPAPER, bytes)
        setFlag("wallpaper", true)
        bumpRevision()
        restartHome()
        return ApplyResult(true, "Обои обновлены. Home перезапущен.")
    }

    fun clearWallpaper(): ApplyResult {
        ensureDir()
        tv.removeFile(WALLPAPER)
        setFlag("wallpaper", false)
        bumpRevision()
        restartHome()
        return ApplyResult(true, "Обои убраны, фон снова однотонный.")
    }

    fun reset(): ApplyResult {
        ensureDir()
        tv.shell("rm -f $CONFIG $WALLPAPER $DIR/wallpapers/* 2>/dev/null")
        bumpRevision()
        restartHome()
        base = JSONObject()
        return ApplyResult(true, "Вид сброшен к встроенному.")
    }

    private fun setFlag(key: String, value: Boolean) {
        val cfg = JSONObject(base.toString())
        cfg.put(key, value)
        writeConfig(cfg)
        base = cfg
    }

    private fun writeConfig(cfg: JSONObject) {
        val bytes = (cfg.toString(2) + "\n").toByteArray(Charsets.UTF_8)
        if (bytes.size > 64 * 1024) {
            throw IllegalStateException("Настройки получились больше 64 КБ — лаунчер такие файлы игнорирует.")
        }
        tv.writeBase64File(CONFIG, bytes)
    }

    private fun bumpRevision() {
        val id = UUID.randomUUID().toString().replace("-", "")
        tv.shell("printf '%s' '$id' > $REVISION")
    }

    /**
     * Перезапускает Home, чтобы лаунчер перечитал настройки. force-stop снимает
     * службы доступности, поэтому включённые службы возвращаются на место.
     */
    private fun restartHome() {
        val cmd = "a=\$(settings get secure enabled_accessibility_services); " +
            "am force-stop $CLEAN_LAUNCHER; " +
            "am start -n $CLEAN_HOME >/dev/null; " +
            "case \"\$a\" in *$CLEAN_LAUNCHER*) sleep 1; " +
            "settings put secure enabled_accessibility_services ''; " +
            "settings put secure enabled_accessibility_services \"\$a\"; " +
            "settings put secure accessibility_enabled 1 ;; esac"
        tv.shell(cmd)
    }

    /** Папку настроек на некоторых TV может создать только сам лаунчер — тогда его открывают. */
    private fun ensureDir() {
        if (runCatching { tv.versionOf(CLEAN_LAUNCHER) }.getOrNull() == null) {
            throw IllegalStateException(
                "Лаунчер CleanYourTV не установлен. Откройте «Ещё» → «Лаунчер», установите его — " +
                    "после этого появятся настройки вида."
            )
        }
        var out = tv.shell(WRITABLE)
        for (attempt in 0 until 3) {
            if (out.contains("writable")) return
            tv.startLauncher()
            Thread.sleep(1500)
            out = tv.shell(WRITABLE)
        }
        throw IllegalStateException(
            "TV не разрешает записать папку настроек лаунчера ($DIR). " +
                "Нажмите Home на пульте, чтобы лаунчер открылся, и попробуйте снова."
        )
    }

    private fun apps(): List<TvApp> {
        val tvPkgs = query("android.intent.category.LEANBACK_LAUNCHER")
        val phonePkgs = query("android.intent.category.LAUNCHER")
        val skip = setOf(CLEAN_LAUNCHER, "com.android.tv.settings", "com.android.settings")
        val out = mutableListOf<TvApp>()
        for (pkg in tvPkgs) if (pkg !in skip) out += TvApp(pkg, labelFor(pkg), true)
        for (pkg in phonePkgs) if (pkg !in skip && pkg !in tvPkgs) out += TvApp(pkg, labelFor(pkg), false)
        return out.sortedWith(compareByDescending<TvApp> { it.tv }.thenBy { it.label.lowercase() })
    }

    private fun query(category: String): Set<String> {
        val out = tv.shell(
            "cmd package query-activities --brief -a android.intent.action.MAIN -c $category"
        )
        val set = mutableSetOf<String>()
        for (line in out.lineSequence()) {
            val pkg = line.trim().substringBefore('/')
            if (pkg.matches(TvClient.PACKAGE_RE)) set += pkg
        }
        return set
    }

    /** Картинка из галереи: обрезаем под 16:9 и жмём, чтобы передача по ADB была быстрой. */
    private fun prepareWallpaper(uri: Uri): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException("Не удалось прочитать картинку.")
        }
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        val target = maxOf(WALL_W, WALL_H)
        while (longest / (sample * 2) >= target) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val source = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: throw IllegalStateException("Не удалось прочитать картинку.")

        val ratio = maxOf(WALL_W / source.width.toFloat(), WALL_H / source.height.toFloat())
        val width = (source.width * ratio).toInt().coerceAtLeast(WALL_W)
        val height = (source.height * ratio).toInt().coerceAtLeast(WALL_H)
        val scaled = Bitmap.createScaledBitmap(source, width, height, true)
        if (scaled !== source) source.recycle()
        val cropped = Bitmap.createBitmap(scaled, (width - WALL_W) / 2, (height - WALL_H) / 2, WALL_W, WALL_H)
        if (cropped !== scaled) scaled.recycle()

        val out = ByteArrayOutputStream()
        cropped.compress(Bitmap.CompressFormat.JPEG, 86, out)
        cropped.recycle()
        return out.toByteArray()
    }

    private fun parse(raw: ByteArray): JSONObject = try {
        JSONObject(String(raw, Charsets.UTF_8).trim().removePrefix("\uFEFF"))
    } catch (_: Throwable) {
        JSONObject()
    }

    companion object {
        private const val WALL_W = 1920
        private const val WALL_H = 1080

        private const val DIR = "/sdcard/Android/data/$CLEAN_LAUNCHER/files"
        private const val CONFIG = "$DIR/config.json"
        private const val WALLPAPER = "$DIR/wallpaper.jpg"
        private const val REVISION = "$DIR/theme_revision"
        private const val WRITABLE =
            "mkdir -p $DIR/wallpapers 2>/dev/null; touch $DIR/.cytv 2>/dev/null && rm -f $DIR/.cytv && echo writable"

        private val KNOWN = mapOf(
            "com.netflix.ninja" to "Netflix",
            "com.google.android.youtube.tv" to "YouTube",
            "com.google.android.youtube.tvkids" to "YouTube Kids",
            "com.google.android.youtube.tvmusic" to "YouTube Music",
            "com.amazon.amazonvideo.livingroom" to "Prime Video",
            "com.disney.disneyplus" to "Disney+",
            "com.spotify.tv.android" to "Spotify",
            "com.plexapp.android" to "Plex",
            "org.jellyfin.androidtv" to "Jellyfin",
            "org.xbmc.kodi" to "Kodi",
            "com.apple.atve.androidtv.appletv" to "Apple TV",
            "com.wbd.stream" to "Max",
            "com.hbo.hbonow" to "HBO Max",
            "com.hulu.livingroomplus" to "Hulu",
            "tv.twitch.android.app" to "Twitch",
            "com.google.android.videos" to "Google TV",
            "com.google.android.apps.youtube.unplugged" to "YouTube TV",
            "com.android.vending" to "Play Store",
            "com.android.tv.settings" to "Настройки",
            "com.google.android.tvlauncher" to "Штатный лаунчер",
            "com.google.android.apps.tv.launcherx" to "Google TV Home",
            "com.mediatek.wwtv.tvcenter" to "Live TV",
            "com.sony.dtv.tvlin" to "Live TV",
            "com.stremio.one" to "Stremio",
            "com.peacocktv.peacockandroid" to "Peacock",
            "com.cbs.ott" to "Paramount+",
            "com.crunchyroll.crunchyroid" to "Crunchyroll",
            "com.bbc.iplayer.tv" to "BBC iPlayer",
            "tv.pluto.android" to "Pluto TV",
            "com.tubitv" to "Tubi",
            "com.android.chrome" to "Chrome",
            "com.amazon.music.tv" to "Amazon Music",
            "com.apple.android.music" to "Apple Music",
            "com.teamsmart.videomanager.tv" to "SmartTube",
            "com.liskovsoft.smarttubetv.beta" to "SmartTube",
            "org.videolan.vlc" to "VLC",
            "com.mxtech.videoplayer.ad" to "MX Player",
            "ru.kinopoisk" to "Кинопоиск",
            "ru.ivi.client" to "Иви",
            "ru.wink" to "Wink",
            "ru.more.play" to "More.tv",
            "com.google.android.play.games" to "Play Игры"
        )

        private val GENERIC = setOf(
            "com", "org", "net", "tv", "android", "app", "apps", "androidtv", "atv",
            "in", "co", "www", "livingroom", "ott", "mobile"
        )

        /** Имя для плитки: известные приложения по-человечески, остальные — из имени пакета. */
        fun labelFor(pkg: String): String {
            KNOWN[pkg]?.let { return it }
            val parts = pkg.split('.').filter { it.lowercase() !in GENERIC }
            val word = parts.getOrNull(if (parts.size > 1) 1 else 0) ?: pkg.substringAfterLast('.')
            return word
                .replace('_', ' ')
                .replace('-', ' ')
                .replace(Regex("([a-z])([A-Z])"), "$1 $2")
                .split(' ')
                .filter { it.isNotBlank() }
                .joinToString(" ") { piece -> piece.replaceFirstChar { it.uppercase() } }
        }
    }
}

private fun JSONArray?.asStringList(): List<String> {
    if (this == null) return emptyList()
    val out = mutableListOf<String>()
    for (i in 0 until length()) {
        val value = optString(i)
        if (value.isNotBlank()) out += value
    }
    return out
}
