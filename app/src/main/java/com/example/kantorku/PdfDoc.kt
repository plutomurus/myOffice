package com.example.kantorku

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor

class PdfDoc(ctx: Context, uri: Uri) {
    private val pfd: ParcelFileDescriptor =
        ctx.contentResolver.openFileDescriptor(uri, "r") ?: throw IllegalStateException("Tidak bisa membuka berkas")
    private val renderer: PdfRenderer = try {
        PdfRenderer(pfd)
    } catch (e: Exception) {
        pfd.close()
        throw e
    }

    val pageCount: Int = renderer.pageCount

    fun render(index: Int, width: Int): Bitmap = synchronized(this) {
        val page = renderer.openPage(index)
        try {
            val h = (page.height.toFloat() * width / page.width).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        } finally {
            page.close()
        }
    }

    fun close() = synchronized(this) {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}
