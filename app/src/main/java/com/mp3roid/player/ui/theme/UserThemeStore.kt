package com.mp3roid.player.ui.theme

import android.content.Context
import java.io.File

/**
 * Die selbst gebauten Themes des Nutzers.
 *
 * Sie liegen als JSON in `filesDir/themes/` – im gleichen Format wie die
 * mitgelieferten Dateien in `assets/themes/`. Damit bleibt „ein Theme ist eine
 * JSON-Datei" auch für selbst gebaute Themes wahr (siehe [ThemeCatalog.toJson]).
 *
 * Der Ordner gehört der App, es ist kein zusätzliches Recht nötig.
 */
class UserThemeStore(private val context: Context) {

    private fun dir(): File = File(context.filesDir, "themes").apply { mkdirs() }

    /** Liest alle eigenen Themes, in der Reihenfolge, in der sie entstanden sind. */
    fun list(): List<MP3roidTheme> = dir()
        .listFiles()
        .orEmpty()
        .filter { it.isFile && it.name.endsWith(".json", ignoreCase = true) }
        .mapNotNull { file ->
            val text = try {
                file.readText()
            } catch (t: Throwable) {
                null
            }
            text?.let { ThemeCatalog.parse(it) }
        }
        .sortedBy { theme ->
            theme.id.removePrefix(ThemeCatalog.OWN_PREFIX).toIntOrNull() ?: Int.MAX_VALUE
        }

    /** Schreibt (oder überschreibt) ein Theme. */
    fun save(theme: MP3roidTheme) {
        runCatching {
            File(dir(), "${theme.id}.json").writeText(ThemeCatalog.toJson(theme))
        }
    }

    fun delete(id: String) {
        runCatching { File(dir(), "$id.json").delete() }
    }

    /**
     * Kennung für das nächste eigene Theme. Eine Lücke wird wiederverwendet,
     * damit die Dateinamen klein bleiben.
     */
    fun nextId(existing: List<MP3roidTheme>): String {
        val used = existing.mapTo(HashSet()) { it.id }
        var n = 1
        while (ThemeCatalog.OWN_PREFIX + n in used) n++
        return ThemeCatalog.OWN_PREFIX + n
    }

    /**
     * Name für das nächste eigene Theme: „Eigenes Theme 1", „2", … Der Name
     * wird nicht getippt – der Editor zeigt ihn nur an.
     */
    fun nextName(existing: List<MP3roidTheme>): String {
        val used = existing.mapTo(HashSet()) { it.name }
        var n = 1
        while ("Eigenes Theme $n" in used) n++
        return "Eigenes Theme $n"
    }
}
