package com.example.kantorku

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class Recent(val uri: String, val name: String)

private const val MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
private const val MIME_PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"

class AppState(context: Context) {
    private val ctx = context.applicationContext
    private val prefs = ctx.getSharedPreferences("kantorku", Context.MODE_PRIVATE)

    var tab by mutableIntStateOf(0)
    var text by mutableStateOf("")
    var note by mutableStateOf("")
    var pdf by mutableStateOf<PdfDoc?>(null)
    val cells = mutableStateMapOf<String, String>()
    val recents = mutableStateListOf<Recent>()

    // Kompresi
    val toCompress = mutableStateListOf<Picked>()
    var level by mutableIntStateOf(1)
    var busy by mutableStateOf(false)
    var compressResult by mutableStateOf("")

    init {
        (prefs.getString("recents", "") ?: "").split("\n").forEach { line ->
            val p = line.split("\t", limit = 2)
            if (p.size == 2) recents.add(Recent(uri = p[1], name = p[0]))
        }
    }

    fun openUri(uri: Uri) {
        val name = displayName(ctx, uri)
        val lower = name.lowercase()
        val mime = runCatching { ctx.contentResolver.getType(uri) }.getOrNull() ?: ""
        try {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            when {
                lower.endsWith(".pdf") || mime == "application/pdf" -> {
                    val d = PdfDoc(ctx, uri)
                    pdf?.close()
                    pdf = d
                    tab = 1
                }
                lower.endsWith(".docx") || mime == MIME_DOCX -> {
                    text = Office.docxToText(ctx, uri)
                    note = "Teks diekstrak dari $name. Tata letak, gambar, dan format tidak ditampilkan. Menyimpan akan membuat berkas TXT, bukan DOCX."
                    tab = 2
                }
                lower.endsWith(".pptx") || mime == MIME_PPTX -> {
                    text = Office.pptxToText(ctx, uri)
                    note = "Teks diekstrak dari $name (urutan mengikuti nomor berkas slide). Gambar, bagan, dan catatan pembicara tidak ditampilkan. Menyimpan akan membuat berkas TXT, bukan PPTX."
                    tab = 2
                }
                lower.endsWith(".csv") -> {
                    cells.clear()
                    cells.putAll(parseCsv(readText(ctx, uri)))
                    tab = 3
                }
                lower.endsWith(".txt") || lower.endsWith(".md") -> {
                    text = readText(ctx, uri)
                    note = ""
                    tab = 2
                }
                else -> {
                    toast(ctx, "Format belum didukung: $name (saat ini PDF, DOCX, PPTX, TXT, MD, CSV)")
                    return
                }
            }
            addRecent(Recent(uri.toString(), name))
        } catch (e: Exception) {
            toast(ctx, "Gagal membuka $name: ${e.message}")
        }
    }

    private fun addRecent(r: Recent) {
        recents.removeAll { it.uri == r.uri }
        recents.add(0, r)
        while (recents.size > 10) recents.removeAt(recents.size - 1)
        prefs.edit().putString(
            "recents",
            recents.joinToString("\n") { it.name.replace('\t', ' ').replace('\n', ' ') + "\t" + it.uri }
        ).apply()
    }

    fun close() {
        pdf?.close()
    }
}
