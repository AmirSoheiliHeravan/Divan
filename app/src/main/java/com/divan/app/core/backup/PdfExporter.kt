package com.divan.app.core.backup

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** خروجی PDF گزارش‌ها (الهام از پولکی) — A4 فارسی راست‌به‌چپ، چندصفحه‌ای.
 *  فایل در cache/exports نوشته می‌شود (FileProvider) و در API 29+ یک نسخه هم در Downloads ذخیره می‌شود. */
object PdfExporter {

    data class Line(val label: String, val value: String)

    suspend fun export(
        context: Context,
        title: String,
        subtitle: String,
        summary: List<Pair<String, String>>,
        sections: List<Pair<String, List<Line>>>,
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val W = 595; val H = 842; val M = 42f
            val doc = PdfDocument()
            var pageNo = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            var c: Canvas = page.canvas
            var y = 56f

            val ink = Color.rgb(20, 32, 27)
            val muted = Color.rgb(110, 124, 118)
            val green = Color.rgb(46, 125, 91)
            val gold = Color.rgb(217, 178, 95)

            val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 19f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT }
            val subP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(220, 233, 226); textSize = 10.5f; textAlign = Paint.Align.RIGHT }
            val headP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 12.5f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT }
            val rowP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 11f; textAlign = Paint.Align.RIGHT }
            val valP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 11f; textAlign = Paint.Align.LEFT }
            val keyP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 11f; textAlign = Paint.Align.RIGHT }
            val footP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 8.5f; textAlign = Paint.Align.RIGHT }

            fun newPage() {
                doc.finishPage(page)
                pageNo += 1
                page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
                c = page.canvas
                y = 56f
            }

            // هدر سبز با عنوان
            c.drawRect(0f, 0f, W.toFloat(), 92f, Paint().apply { color = green })
            y = 44f
            c.drawText(title, W - M, y, titleP)
            y = 66f
            c.drawText(subtitle, W - M, y, subP)
            y = 82f
            c.drawText("Divan · دیوان", W - M, y, subP)
            y = 124f

            // خلاصه بازه
            summary.forEach { (k, v) ->
                if (y > H - 120f) newPage()
                c.drawText(v, M, y, valP)
                c.drawText(k, W - M, y, keyP)
                y += 21f
            }
            y += 8f

            // بخش‌ها با هدر طلایی
            sections.forEach { (sec, rows) ->
                if (rows.isEmpty()) return@forEach
                if (y > H - 140f) newPage()
                c.drawRect(M, y, (W - M).toFloat(), y + 24f, Paint().apply { color = gold })
                c.drawText(sec, (W - M) - 9f, y + 17f, headP)
                y += 36f
                rows.forEach { r ->
                    if (y > H - 64f) newPage()
                    c.drawText(r.label, W - M, y, rowP)
                    c.drawText(r.value, M, y, valP)
                    y += 18f
                }
                y += 12f
            }

            // پانویس
            if (y > H - 56f) newPage()
            val now = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date())
            c.drawText("تولیدشده توسط اپلیکیشن دیوان (Divan) · $now", W - M, (H - 28).toFloat(), footP)

            doc.finishPage(page)

            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val out = File(dir, "Divan-report-${System.currentTimeMillis()}.pdf")
            out.outputStream().use { doc.writeTo(it) }

            // نسخه Downloads در API 29+ (بهترین تلاش — اشتراک‌گذاری از cache همیشه کار می‌کند)
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                runCatching {
                    val cv = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "Divan-report.pdf")
                        put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    }
                    val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                    uri?.let { context.contentResolver.openOutputStream(it)?.use { os -> File(out.absolutePath).inputStream().use { ins -> ins.copyTo(os) } } }
                }
            }

            doc.close()
            out
        }.getOrNull()
    }
}
