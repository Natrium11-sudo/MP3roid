package com.mp3roid.player.ui.theme

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Verwaltet alle Themes.
 *
 * Mitgelieferte Themes stehen in [MP3roidThemes]. Zusätzlich wird der Ordner
 * `assets/themes/` durchsucht: jede `*.json`-Datei darin ist ein Theme und
 * taucht automatisch in den Einstellungen auf – ohne eine Zeile Code.
 *
 * Selbst gebaute Themes liegen im **gleichen Format** in `filesDir/themes/`
 * (siehe [UserThemeStore]) und lassen sich im Editor der App bearbeiten.
 * [toJson] ist das Gegenstück zu [parse].
 *
 * Beispiel:
 * ```json
 * {
 *   "id": "mein-theme", "name": "Mein Theme", "dark": true,
 *   "background": "#07070B", "surface": "#111119", "surfaceElevated": "#1A1A26",
 *   "accent": "#FFB74D", "accentSecondary": "#8B5CF6",
 *   "textPrimary": "#F2F2F7", "textSecondary": "#8E8EA8",
 *   "gradient": ["#FFB74D", "#8B5CF6"],
 *   "coverCornerRadius": 24, "cardCornerRadius": 18, "controlSize": 72
 * }
 * ```
 */
object ThemeCatalog {

    /** Kennung, an der selbst gebaute Themes zu erkennen sind. */
    const val OWN_PREFIX = "own-"

    /** Auf dieses Theme fällt die App zurück, wenn eine Kennung verschwunden ist. */
    const val FALLBACK_ID = "dark-void"

    private var bundledCache: List<MP3roidTheme>? = null

    /**
     * Die mitgelieferten Themes: vier in Kotlin ([MP3roidThemes]) plus die
     * JSON-Dateien aus `assets/themes/`. Ändert sich zur Laufzeit nie.
     */
    fun bundledThemes(context: Context): List<MP3roidTheme> {
        bundledCache?.let { return it }
        val loaded = MP3roidThemes.builtIn + loadFromAssets(context)
        bundledCache = loaded
        return loaded
    }

    /** Die selbst gebauten Themes des Nutzers aus `filesDir/themes/`. */
    fun ownThemes(context: Context): List<MP3roidTheme> = UserThemeStore(context).list()

    /** Alles, was in den Einstellungen als Kachel erscheint. */
    fun all(context: Context): List<MP3roidTheme> =
        bundledThemes(context) + ownThemes(context)

    /** Gehört diese Kennung zu einem selbst gebauten Theme? */
    fun isOwn(id: String): Boolean = id.startsWith(OWN_PREFIX)

    fun byId(context: Context, id: String?): MP3roidTheme =
        all(context).firstOrNull { it.id == id }
            ?: bundledThemes(context).firstOrNull { it.id == FALLBACK_ID }
            ?: MP3roidThemes.darkVoid

    private fun loadFromAssets(context: Context): List<MP3roidTheme> {
        val entries = try {
            context.assets.list("themes")?.toList().orEmpty()
        } catch (t: Throwable) {
            emptyList()
        }
        return entries
            .filter { it.endsWith(".json", ignoreCase = true) }
            .sorted()
            .mapNotNull { name ->
                try {
                    context.assets.open("themes/$name").bufferedReader().use { it.readText() }
                } catch (t: Throwable) {
                    null
                }?.let { parse(it) }
            }
    }

    /** Parst eine Theme-JSON. Fehlende Felder bekommen sinnvolle Vorgaben. */
    fun parse(json: String): MP3roidTheme? = try {
        val o = JSONObject(json)
        val id = o.optString("id").trim()
        if (id.isEmpty()) {
            null
        } else {
            val accent = colorOf(o, "accent", "#FFB74D")
            val accent2 = colorOf(o, "accentSecondary", "#8B5CF6")
            val gradient = o.optJSONArray("gradient")
                ?.let { arr -> (0 until arr.length()).map { i -> arr.optString(i) } }
                ?.filter { it.isNotBlank() }
                ?.map { parseColor(it) }
                .orEmpty()
                .ifEmpty { listOf(accent, accent2) }
            MP3roidTheme(
                id = id,
                name = o.optString("name", id),
                description = o.optString("description", ""),
                colors = MP3roidColors(
                    background = colorOf(o, "background", "#07070B"),
                    surface = colorOf(o, "surface", "#111119"),
                    surfaceElevated = colorOf(o, "surfaceElevated", "#1A1A26"),
                    accent = accent,
                    accentSecondary = accent2,
                    textPrimary = colorOf(o, "textPrimary", "#F2F2F7"),
                    textSecondary = colorOf(o, "textSecondary", "#8E8EA8"),
                    gradient = gradient,
                    isDark = o.optBoolean("dark", true),
                    coverCorner = o.optInt("coverCornerRadius", 22).dp,
                    cardCorner = o.optInt("cardCornerRadius", 18).dp,
                    controlSize = o.optInt("controlSize", 72).dp,
                ),
            )
        }
    } catch (t: Throwable) {
        null
    }

    /**
     * Gegenstück zu [parse]: schreibt ein Theme als JSON – dasselbe Format wie
     * die mitgelieferten Dateien.
     */
    fun toJson(theme: MP3roidTheme): String {
        val c = theme.colors
        return JSONObject()
            .put("id", theme.id)
            .put("name", theme.name)
            .put("description", theme.description)
            .put("dark", c.isDark)
            .put("background", hexOf(c.background))
            .put("surface", hexOf(c.surface))
            .put("surfaceElevated", hexOf(c.surfaceElevated))
            .put("accent", hexOf(c.accent))
            .put("accentSecondary", hexOf(c.accentSecondary))
            .put("textPrimary", hexOf(c.textPrimary))
            .put("textSecondary", hexOf(c.textSecondary))
            .put("gradient", JSONArray(c.gradient.map { hexOf(it) }))
            .put("coverCornerRadius", c.coverCorner.value.toInt())
            .put("cardCornerRadius", c.cardCorner.value.toInt())
            .put("controlSize", c.controlSize.value.toInt())
            .toString(2)
    }

    /** Farbe als `#RRGGBB` – dieselbe Schreibweise, die [parse] erwartet. */
    fun hexOf(color: Color): String =
        String.format(Locale.US, "#%06X", 0xFFFFFF and color.toArgb())

    private fun colorOf(o: JSONObject, key: String, fallback: String): Color =
        parseColor(o.optString(key, fallback))

    private fun parseColor(value: String): Color = try {
        Color(AndroidColor.parseColor(value))
    } catch (t: Throwable) {
        Color(0xFF808080)
    }
}
