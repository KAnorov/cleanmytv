package ru.cleanmytv.app

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.Instant

class Debloat(private val context: Context, private val tv: TvClient) {

    private data class RawList(
        val id: String,
        val title: String,
        val header: List<String>,
        val entries: List<RawEntry>
    )

    private data class RawEntry(
        val pkg: String,
        val note: String,
        val suggested: Boolean,
        val section: String
    )

    private var cachedLists: List<RawList>? = null

    fun plan(probe: Probe): Plan {
        val sets = tv.packageSets()
        val home = tv.homeComponent()
        val homePkg = home?.substringBefore('/')
        val all = loadLists()
        val common = commonality(all)

        val scored = all.map { it to score(it, probe) }.sortedByDescending { it.second }
        val best = scored.firstOrNull { it.second > 0 }?.first
        val chosen: RawList
        val title: String
        val entries: List<DebloatEntry>

        if (best != null) {
            chosen = best
            title = best.title
            entries = best.entries.map { e ->
                DebloatEntry(
                    pkg = e.pkg,
                    note = e.note.ifBlank { common[e.pkg]?.second ?: "" },
                    suggested = e.suggested,
                    section = e.section,
                    state = stateOf(e.pkg, sets),
                    common = common[e.pkg]?.first ?: 0
                )
            }
        } else {
            chosen = RawList("custom", "Моего TV нет в списке: выберите пакеты вручную", emptyList(), emptyList())
            title = chosen.title
            entries = common.entries
                .filter { it.key in sets.installed }
                .sortedByDescending { it.value.first }
                .map { (pkg, value) ->
                    DebloatEntry(
                        pkg = pkg,
                        note = value.second,
                        suggested = false,
                        section = "Пакеты, которые отключают на других TV",
                        state = stateOf(pkg, sets),
                        common = value.first
                    )
                }
        }

        val record = loadRecord(probe)
        return Plan(
            listId = chosen.id,
            title = title,
            entries = entries,
            homePackage = homePkg,
            record = record
        )
    }

    fun apply(plan: Plan, selected: Set<String>): ApplyResult {
        val probe = tv.probe ?: throw IllegalStateException("Нет подключения к TV")
        val sets = tv.packageSets()
        val warnings = mutableListOf<String>()
        val targets = selected
            .filter { it in sets.enabled }
            .toMutableList()

        val home = tv.homeComponent()
        val homePkg = home?.substringBefore('/')
        if (homePkg != null && homePkg != CLEAN_LAUNCHER && homePkg in targets) {
            targets.remove(homePkg)
            warnings += "Пропущен $homePkg: он владеет Home, а новый лаунчер ещё не установлен."
        }
        if ("com.google.android.tungsten.setupwraith" in targets) {
            targets.remove("com.google.android.tungsten.setupwraith")
            warnings += "Пропущен com.google.android.tungsten.setupwraith: он может отвечать за сохранённую Wi-Fi сеть."
        }

        if (targets.isEmpty()) {
            return ApplyResult(false, "Нечего отключать.", warnings)
        }

        saveRecord(probe, home, targets)

        var done = 0
        for (pkg in targets) {
            try {
                val out = tv.disablePackage(pkg)
                if (out.contains("disabled", ignoreCase = true) || out.isBlank()) done++ else warnings += "$pkg: ${out.take(140)}"
            } catch (e: Throwable) {
                warnings += "$pkg: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        return ApplyResult(
            ok = done > 0,
            summary = "Отключено $done из ${targets.size} пакетов.",
            warnings = warnings
        )
    }

    fun restore(record: RecoveryRecord): ApplyResult {
        val warnings = mutableListOf<String>()
        var enabled = 0
        for ((pkg, state) in record.packages) {
            if (state != "enabled") continue
            try {
                tv.enablePackage(pkg)
                enabled++
            } catch (e: Throwable) {
                warnings += "$pkg: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        var homeNote = ""
        val home = record.home
        if (home != null && home.matches(TvClient.COMPONENT_RE)) {
            try {
                tv.setHome(home)
                homeNote = " Home: $home."
            } catch (e: Throwable) {
                warnings += "Home: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        return ApplyResult(
            ok = true,
            summary = "Включено обратно: $enabled пакетов.$homeNote",
            warnings = warnings
        )
    }

    private fun stateOf(pkg: String, sets: PackageSets): String = when {
        pkg !in sets.installed -> "absent"
        pkg in sets.disabled -> "disabled"
        else -> "enabled"
    }

    private fun commonality(lists: List<RawList>): Map<String, Pair<Int, String>> {
        val out = mutableMapOf<String, Pair<Int, String>>()
        for (list in lists) {
            for (entry in list.entries) {
                if (!entry.suggested) continue
                val old = out[entry.pkg]
                val note = if (old == null || old.second.isBlank()) entry.note else old.second
                out[entry.pkg] = ((old?.first ?: 0) + 1) to note
            }
        }
        return out
    }

    private fun loadLists(): List<RawList> {
        cachedLists?.let { return it }
        val ids = context.assets.list("upstream/examples")?.sorted() ?: emptyList()
        val out = mutableListOf<RawList>()
        for (id in ids) {
            val path = "upstream/examples/$id/debloat.txt"
            val text = try {
                context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (_: Throwable) {
                continue
            }
            out += parse(id, text)
        }
        cachedLists = out
        return out
    }

    private fun parse(id: String, text: String): RawList {
        val header = mutableListOf<String>()
        val entries = mutableListOf<RawEntry>()
        var section = ""
        var seenPackage = false

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#")) {
                val body = line.removePrefix("#").trim()
                if (body.isEmpty()) continue
                val pkg = body.substringBefore(' ').substringBefore('\t').trim()
                if (pkg.matches(TvClient.PACKAGE_RE)) {
                    val note = body.substringAfter(pkg).trim().removePrefix("#").trim()
                    entries += RawEntry(pkg, note, false, section)
                    seenPackage = true
                } else {
                    section = body
                    if (!seenPackage) header += body
                }
                continue
            }
            val pkg = line.substringBefore('#').trim()
            if (!pkg.matches(TvClient.PACKAGE_RE)) continue
            val note = line.substringAfter('#', "").trim()
            entries += RawEntry(pkg, note, true, section)
            seenPackage = true
        }

        val title = header.firstOrNull()?.takeIf { it.isNotBlank() } ?: id
        return RawList(id, title, header, entries)
    }

    private fun score(list: RawList, probe: Probe): Int {
        val hay = (list.id + " " + list.title + " " + list.header.joinToString(" ")).lowercase()
        val model = probe.model.lowercase().trim()
        val device = probe.device.lowercase().trim()
        val manufacturer = probe.manufacturer.lowercase().trim()
        var score = 0

        if (model.isNotEmpty() && hay.contains(model)) score += 8
        if (device.isNotEmpty() && hay.contains(device)) score += 5
        if (manufacturer.isNotEmpty() && hay.contains(manufacturer)) score += 2
        if (probe.sdk == 28 && hay.contains("android 9")) score += 2

        // A known family: MiTV-MSSP0 is an Android 9 Xiaomi set without its own list.
        if (manufacturer.contains("xiaomi") && model.startsWith("mitv-mssp")) {
            if (list.id.contains("mitv-mssp3-android9") && probe.sdk <= 28) score += 10
            else if (list.id.contains("mitv-mssp3")) score += 4
        }
        if (list.id.contains("android9") && probe.sdk == 28) score += 1
        return score
    }

    private fun recordFile(probe: Probe): File {
        val safe = probe.host.replace(Regex("[^A-Za-z0-9.-]"), "_")
        return File(context.filesDir, "recovery-$safe.json")
    }

    private fun loadRecord(probe: Probe): RecoveryRecord? {
        val file = recordFile(probe)
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            val packagesJson = json.optJSONObject("packages") ?: JSONObject()
            val packages = mutableMapOf<String, String>()
            for (key in packagesJson.keys()) packages[key] = packagesJson.optString(key)
            RecoveryRecord(
                host = json.optString("host"),
                device = json.optString("device"),
                created = json.optString("created"),
                home = json.optString("home").takeIf { it.isNotBlank() && it != "null" },
                packages = packages
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun saveRecord(probe: Probe, home: String?, targets: List<String>) {
        val file = recordFile(probe)
        val old = loadRecord(probe)
        if (old != null && old.device.isNotBlank() && old.device != probe.device) {
            throw IllegalStateException(
                "Запись восстановления на этом телефоне сделана для другого TV (${old.device}). " +
                    "Удалите $file вручную, если это действительно новый TV."
            )
        }
        val sets = tv.packageSets()
        val packages = old?.packages?.toMutableMap() ?: mutableMapOf()
        for (pkg in targets) {
            if (!packages.containsKey(pkg)) packages[pkg] = stateOf(pkg, sets)
        }
        val json = JSONObject()
        json.put("host", probe.host)
        json.put("device", probe.device)
        json.put("created", old?.created ?: Instant.now().toString())
        json.put("home", old?.home ?: home ?: "")
        json.put("packages", JSONObject(packages as Map<*, *>))
        file.writeText(json.toString(2))
    }
}
