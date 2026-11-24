package com.mp3roid.player.ui

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.view.WindowManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Was MP3roid vom Hintergrundbild des Systems weiß.
 */
sealed interface WallpaperState {

    /** Ausgeschaltet: MP3roid malt den Verlauf des Themes. */
    data object Off : WallpaperState

    data object Loading : WallpaperState

    data class Ready(val image: ImageBitmap) : WallpaperState

    /** Kein Bild bekommen – [reason] sagt dem Nutzer warum. */
    data class Unavailable(val reason: String) : WallpaperState
}

/**
 * Mehr Bildpunkte lädt MP3roid nicht: vier Millionen sind 16 MB. Das verkraftet
 * ein Gerät mit 2 GB RAM auch neben der Wiedergabe.
 */
private const val MAX_PIXELS = 4_000_000f

/**
 * Im Weich-Modus lädt MP3roid nur die halbe Auflösung. Ein halbes Bild reicht,
 * weil die Unschärfe die Feinheiten ohnehin schluckt – und es ist die Hälfte
 * der Arbeit für den Weichzeichner.
 */
private const val SOFT_DIVISOR = 2

/**
 * Kasten-Radius des Weichzeichners, in halber Auflösung gerechnet: das sind am
 * Bildschirm etwa fünf Bildpunkte Unschärfe – ruhig genug für Schrift darauf,
 * aber ohne die matschigen Flecken einer winzigen Hochrechnung.
 */
private const val SOFT_RADIUS = 2

/**
 * Die längere Kante des echten Displays.
 *
 * `displayMetrics` zieht die Systemleisten heraus und liefert auf Einsteigergeräten nur
 * 720x1473, während die Fensterfläche mit ihren Rändern 720x1600 umfasst –
 * so groß ist der Bildkasten, in dem das Bild liegt. Geladen werden muss genau
 * diese Fläche, sonst wäre das Bild kleiner als sein Platz und der Beschnitt
 * müsste es vergrößern, wodurch es weicher würde als auf dem Sperrbildschirm.
 */
private fun displaySide(context: Context): Int {
    val metrics = context.resources.displayMetrics
    val fromMetrics = max(metrics.widthPixels, metrics.heightPixels)
    return try {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val size = Point()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealSize(size)
        max(fromMetrics, max(size.x, size.y))
    } catch (e: Throwable) {
        fromMetrics
    }
}

/**
 * Liest das Hintergrundbild des Systems.
 *
 * Das Bild wird so groß geladen, wie der Bildschirm es braucht: es wird nie
 * unnötig verkleinert – sonst wäre es weicher als auf dem Sperrbildschirm, wo
 * das System dasselbe Bild in voller Größe zeigt – und nie vergrößert, denn
 * dabei entstünde kein Bildpunkt dazu. Verkleinert wird außerdem in
 * Halbierungsschritten, damit dabei gemittelt und nicht abgetastet wird.
 *
 * Bis Android 12 verlangt der [WallpaperManager] dafür `READ_EXTERNAL_STORAGE`.
 * Auf Einsteigergeräten hat MP3roid dieses Recht ohnehin für die Musik. Ab Android 13
 * gibt das System nur noch das mitgelieferte Bild heraus, ab Android 14 wirft es
 * grundsätzlich [SecurityException] – deshalb steht hier überall `runCatching`
 * statt eines Absturzes.
 */
suspend fun loadSystemWallpaper(context: Context, soft: Boolean): WallpaperState =
    withContext(Dispatchers.IO) {
        try {
            val drawable = WallpaperManager.getInstance(context).drawable
                ?: return@withContext WallpaperState.Unavailable(
                    "Auf dem Gerät ist kein Hintergrundbild hinterlegt – MP3roid " +
                        "zeichnet weiter den Verlauf des Themes.",
                )
            val screenSide = displaySide(context)
            val bitmap = drawable.renderFor(screenSide, soft)
                ?: return@withContext WallpaperState.Unavailable(
                    "Dieses Hintergrundbild ist eine Animation – MP3roid kann es " +
                        "nicht übernehmen und zeichnet weiter den Verlauf des Themes.",
                )
            WallpaperState.Ready(bitmap.asImageBitmap())
        } catch (e: SecurityException) {
            WallpaperState.Unavailable(
                "Das System gibt das Hintergrundbild nicht heraus – MP3roid " +
                    "zeichnet weiter den Verlauf des Themes.",
            )
        } catch (e: OutOfMemoryError) {
            // Ein sehr großes Bild kann den Speicher sprengen, bevor es
            // verkleinert ist – auf einem Gerät mit 2 GB RAM gut möglich.
            WallpaperState.Unavailable(
                "Das Bild ist zu groß für dieses Gerät – MP3roid zeichnet " +
                    "weiter den Verlauf des Themes.",
            )
        } catch (e: Exception) {
            WallpaperState.Unavailable(
                "Das Hintergrundbild ließ sich nicht lesen – MP3roid zeichnet " +
                    "weiter den Verlauf des Themes.",
            )
        }
    }

/**
 * Zeichnet das Bild in der Größe, die der Bildschirm braucht.
 *
 * [screenSide] ist die längere Kante der Fensterfläche. Das Bild muss sie im
 * Ausschnitt abdecken (MP3roid schneidet wie Android selbst am linken Rand ab,
 * siehe `MP3roidRoot`), deshalb ist `screenSide / kurze Kante` die untere
 * Grenze der Verkleinerung: darunter müsste die Grafik Bildpunkte erfinden.
 * Kleiner wird nur, wenn der Speicher sonst nicht reicht.
 */
private fun Drawable.renderFor(screenSide: Int, soft: Boolean): Bitmap? {
    val sourceWidth = intrinsicWidth
    val sourceHeight = intrinsicHeight
    if (sourceWidth <= 0 || sourceHeight <= 0) return null

    var factor = min(1f, screenSide / min(sourceWidth, sourceHeight).toFloat())
    val pixels = sourceWidth.toFloat() * sourceHeight
    if (pixels * factor * factor > MAX_PIXELS) factor = sqrt(MAX_PIXELS / pixels)
    if (soft) factor /= SOFT_DIVISOR

    val targetWidth = (sourceWidth * factor).roundToInt().coerceAtLeast(1)
    val targetHeight = (sourceHeight * factor).roundToInt().coerceAtLeast(1)
    val bitmap = scaleTo(targetWidth, targetHeight)
    return if (soft) bitmap.blurred(SOFT_RADIUS) else bitmap
}

/**
 * Verkleinert in Halbierungsschritten auf die Zielgröße.
 *
 * Jede Halbierung mittelt vier Nachbarbildpunkte zu einem. Ein einziger großer
 * Schritt würde dagegen nur einzelne Bildpunkte abtasten – so entstehen die
 * Treppchen und Flimmerflecken, die ein Bild „gestaucht“ aussehen lassen.
 */
private fun Drawable.scaleTo(targetWidth: Int, targetHeight: Int): Bitmap {
    val sourceWidth = intrinsicWidth
    val sourceHeight = intrinsicHeight

    // Erste Stufe: im Kopf halbieren, bis es knapp über dem Ziel liegt.
    var stageWidth = sourceWidth
    var stageHeight = sourceHeight
    while (stageWidth / 2 >= targetWidth && stageHeight / 2 >= targetHeight) {
        stageWidth /= 2
        stageHeight /= 2
    }

    // Diese Stufe in einem Zug zeichnen: der Faktor ist 1/2ⁿ, die Grafik mittelt
    // dabei also über die Nachbarn.
    var current = drawInto(stageWidth, stageHeight)
    while (current.width / 2 >= targetWidth && current.height / 2 >= targetHeight) {
        val next = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
        current.recycle()
        current = next
    }
    if (current.width != targetWidth || current.height != targetHeight) {
        val next = Bitmap.createScaledBitmap(current, targetWidth, targetHeight, true)
        current.recycle()
        current = next
    }
    return current
}

/** Zeichnet das Bild in eine neue Bitmap dieser Größe. */
private fun Drawable.drawInto(width: Int, height: Int): Bitmap {
    val sourceWidth = intrinsicWidth
    val sourceHeight = intrinsicHeight
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val restore = canvas.save()
    canvas.scale(width / sourceWidth.toFloat(), height / sourceHeight.toFloat())
    setBounds(0, 0, sourceWidth, sourceHeight)
    draw(canvas)
    canvas.restoreToCount(restore)
    return bitmap
}

/**
 * Zeichnet das Bild weich: drei Durchgänge eines Kastenfilters je Richtung, was
 * einer glatten Unschärfe sehr nahe kommt. Das Bild ist dabei klein, deshalb
 * kostet das kaum Zeit – anders als ein echter Weichzeichner über ein großes
 * Bild, den sich ein Gerät mit 2 GB RAM nicht leisten sollte.
 */
private fun Bitmap.blurred(radius: Int): Bitmap {
    if (radius < 1) return this
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    val temp = IntArray(pixels.size)
    repeat(3) {
        boxBlurRows(pixels, temp, width, height, radius)
        boxBlurColumns(temp, pixels, width, height, radius)
    }
    setPixels(pixels, 0, width, 0, 0, width, height)
    return this
}

private fun boxBlurRows(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
    val window = 2 * radius + 1
    for (y in 0 until height) {
        val row = y * width
        var alpha = 0
        var red = 0
        var green = 0
        var blue = 0
        for (i in -radius..radius) {
            val color = src[row + i.coerceIn(0, width - 1)]
            alpha += color ushr 24 and 0xFF
            red += color shr 16 and 0xFF
            green += color shr 8 and 0xFF
            blue += color and 0xFF
        }
        for (x in 0 until width) {
            dst[row + x] = ((alpha / window) shl 24) or
                ((red / window) shl 16) or
                ((green / window) shl 8) or
                (blue / window)
            val out = src[row + (x - radius).coerceAtLeast(0)]
            val into = src[row + (x + radius + 1).coerceAtMost(width - 1)]
            alpha += (into ushr 24 and 0xFF) - (out ushr 24 and 0xFF)
            red += (into shr 16 and 0xFF) - (out shr 16 and 0xFF)
            green += (into shr 8 and 0xFF) - (out shr 8 and 0xFF)
            blue += (into and 0xFF) - (out and 0xFF)
        }
    }
}

private fun boxBlurColumns(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
    val window = 2 * radius + 1
    for (x in 0 until width) {
        var alpha = 0
        var red = 0
        var green = 0
        var blue = 0
        for (i in -radius..radius) {
            val color = src[i.coerceIn(0, height - 1) * width + x]
            alpha += color ushr 24 and 0xFF
            red += color shr 16 and 0xFF
            green += color shr 8 and 0xFF
            blue += color and 0xFF
        }
        for (y in 0 until height) {
            dst[y * width + x] = ((alpha / window) shl 24) or
                ((red / window) shl 16) or
                ((green / window) shl 8) or
                (blue / window)
            val out = src[(y - radius).coerceAtLeast(0) * width + x]
            val into = src[(y + radius + 1).coerceAtMost(height - 1) * width + x]
            alpha += (into ushr 24 and 0xFF) - (out ushr 24 and 0xFF)
            red += (into shr 16 and 0xFF) - (out shr 16 and 0xFF)
            green += (into shr 8 and 0xFF) - (out shr 8 and 0xFF)
            blue += (into and 0xFF) - (out and 0xFF)
        }
    }
}
