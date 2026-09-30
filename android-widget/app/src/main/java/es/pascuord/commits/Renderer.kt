package es.pascuord.commits

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Dibuja el gráfico de cuadraditos en un bitmap del tamaño del widget. */
object Renderer {
    private val LIGHT = intArrayOf(0xFFE7ECE8.toInt(), 0xFFACEEBB.toInt(), 0xFF4AC26B.toInt(), 0xFF2DA44E.toInt(), 0xFF116329.toInt())
    private val DARK = intArrayOf(0xFF1E2621.toInt(), 0xFF0E4429.toInt(), 0xFF006D32.toInt(), 0xFF26A641.toInt(), 0xFF39D353.toInt())

    fun currentStreak(days: List<Day>): Int {
        val today = LocalDate.now().toString()
        val past = days.filter { it.date <= today }
        var i = past.size - 1
        if (i >= 0 && past[i].count == 0) i-- // hoy aún puede no tener commits
        var n = 0
        while (i >= 0 && past[i].count > 0) { n++; i-- }
        return n
    }

    fun render(
        data: Contributions?,
        user: String,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        dark: Boolean
    ): Bitmap {
        val w = widthPx.coerceIn(100, 1400)
        val h = heightPx.coerceIn(60, 1000)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val dp = density

        val bg = if (dark) 0xFF0F1411.toInt() else Color.WHITE
        val fg = if (dark) 0xFFE6EDE7.toInt() else 0xFF17201A.toInt()
        val muted = if (dark) 0xFF8E9D92.toInt() else 0xFF5D6B61.toInt()
        val levels = if (dark) DARK else LIGHT

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = bg
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 18 * dp, 18 * dp, paint)

        val pad = 12 * dp
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12 * dp }

        if (data == null || data.days.isEmpty()) {
            text.color = muted
            text.textAlign = Paint.Align.CENTER
            val msg = if (user.isBlank()) "Toca para configurar Mis Commits" else "Cargando @$user…"
            c.drawText(msg, w / 2f, h / 2f + 4 * dp, text)
            return bmp
        }

        // Cabecera: usuario, total y racha (se oculta si el widget es muy bajo)
        val showHeader = h > 80 * dp
        var top = pad
        if (showHeader) {
            val base = pad + 11 * dp
            text.color = fg
            text.typeface = Typeface.DEFAULT_BOLD
            c.drawText("@$user", pad, base, text)
            text.typeface = Typeface.DEFAULT
            text.color = muted
            text.textAlign = Paint.Align.RIGHT
            val streak = currentStreak(data.days)
            val right = "${"%,d".format(data.total).replace(',', '.')} en un año · racha $streak"
            c.drawText(right, w - pad, base, text)
            top = base + 8 * dp
        }

        val availW = w - pad * 2
        val availH = h - top - pad
        val gapRatio = 0.22f

        val first = LocalDate.parse(data.days.first().date)
        val lead = first.dayOfWeek.value % 7 // domingo = 0, como en GitHub
        val slots = lead + data.days.size
        val totalCols = ceil(slots / 7f).toInt()

        // El alto manda en el tamaño de cada cuadrado; se muestran las semanas más recientes que quepan
        val cell = max(availH / (7 + 6 * gapRatio), 4 * dp)
        val gap = cell * gapRatio
        val cols = min(totalCols, floor((availW + gap) / (cell + gap)).toInt()).coerceAtLeast(1)
        val startCol = totalCols - cols

        val gridW = cols * cell + (cols - 1) * gap
        val gridH = 7 * cell + 6 * gap
        val x0 = pad + (availW - gridW) / 2f
        val y0 = top + (availH - gridH) / 2f
        val r = cell * 0.22f

        val today = LocalDate.now().toString()
        data.days.forEachIndexed { i, d ->
            if (d.date > today) return@forEachIndexed
            val slot = lead + i
            val col = slot / 7 - startCol
            if (col < 0) return@forEachIndexed
            val row = slot % 7
            val x = x0 + col * (cell + gap)
            val y = y0 + row * (cell + gap)
            paint.color = levels[d.level.coerceIn(0, 4)]
            c.drawRoundRect(RectF(x, y, x + cell, y + cell), r, r, paint)
        }
        return bmp
    }
}
