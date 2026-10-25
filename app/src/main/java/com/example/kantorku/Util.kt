package com.example.kantorku

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast

const val ROWS = 50
const val COLS = 10

fun toast(c: Context, m: String) = Toast.makeText(c, m, Toast.LENGTH_LONG).show()

fun readText(c: Context, uri: Uri): String =
    c.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""

fun writeText(c: Context, uri: Uri, text: String) {
    c.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) }
}

fun cellRef(row: Int, col: Int): String = "${'A' + col}${row + 1}"

private fun parseCsvLine(line: String): List<String> {
    val out = ArrayList<String>()
    val sb = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
            c == '"' -> quoted = !quoted
            c == ',' && !quoted -> { out.add(sb.toString()); sb.clear() }
            else -> sb.append(c)
        }
        i++
    }
    out.add(sb.toString())
    return out
}

// Catatan: sel CSV yang berisi baris baru (multi-baris) belum didukung.
fun parseCsv(text: String): Map<String, String> {
    val result = HashMap<String, String>()
    val lines = text.removePrefix("\uFEFF").split("\n")
    for ((r, raw) in lines.withIndex()) {
        if (r >= ROWS) break
        val fields = parseCsvLine(raw.trimEnd('\r'))
        for ((c, v) in fields.withIndex()) {
            if (c >= COLS) break
            if (v.isNotEmpty()) result[cellRef(r, c)] = v
        }
    }
    return result
}

private fun csvQuote(s: String): String =
    if (s.contains(',') || s.contains('"') || s.contains('\n')) "\"" + s.replace("\"", "\"\"") + "\"" else s

fun toCsv(cells: Map<String, String>): String {
    var last = -1
    for (r in 0 until ROWS) for (c in 0 until COLS) if (!cells[cellRef(r, c)].isNullOrEmpty()) last = r
    val sb = StringBuilder()
    for (r in 0..last) {
        sb.append((0 until COLS).joinToString(",") { csvQuote(cells[cellRef(r, it)] ?: "") })
        sb.append('\n')
    }
    return sb.toString()
}

fun displayName(c: Context, uri: Uri): String =
    runCatching {
        c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "berkas"

fun fileSize(c: Context, uri: Uri): Long =
    runCatching {
        c.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else -1L
        }
    }.getOrNull() ?: -1L

fun formatSize(b: Long): String = when {
    b < 0 -> "?"
    b < 1024 -> "$b B"
    b < 1024L * 1024 -> String.format(java.util.Locale.US, "%.1f KB", b / 1024.0)
    else -> String.format(java.util.Locale.US, "%.2f MB", b / 1048576.0)
}
