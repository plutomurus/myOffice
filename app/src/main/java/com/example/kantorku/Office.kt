package com.example.kantorku

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * Ekstraksi TEKS dari DOCX dan PPTX (keduanya berkas ZIP berisi XML).
 * Hanya teks: tanpa tata letak, gambar, tabel terformat, header/footer, atau catatan pembicara.
 */
object Office {
    private const val MAX_ENTRY = 30L * 1024 * 1024
    private const val WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val DML_NS = "http://schemas.openxmlformats.org/drawingml/2006/main"
    private const val MC_NS = "http://schemas.openxmlformats.org/markup-compatibility/2006"

    private fun readEntries(c: Context, uri: Uri, accept: (String) -> Boolean): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        val input = c.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Tidak bisa membuka berkas")
        ZipInputStream(input.buffered()).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                if (e.isDirectory || !accept(e.name)) continue
                val bos = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0L
                while (true) {
                    val n = zin.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_ENTRY) throw IllegalStateException("Isi berkas terlalu besar")
                    bos.write(buf, 0, n)
                }
                out[e.name] = bos.toByteArray()
            }
        }
        return out
    }

    private fun extractText(xml: ByteArray, ns: String, word: Boolean): String {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        p.setInput(ByteArrayInputStream(xml), null)
        val sb = StringBuilder()
        var inTabs = false
        var skipAt = 0
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (skipAt > 0) {
                if (ev == XmlPullParser.END_TAG && p.depth == skipAt) skipAt = 0
                ev = p.next()
                continue
            }
            if (ev == XmlPullParser.START_TAG && p.namespace == MC_NS && p.name == "Fallback") {
                skipAt = p.depth
                ev = p.next()
                continue
            }
            if (ev == XmlPullParser.START_TAG && p.namespace == ns) {
                when (p.name) {
                    "t" -> sb.append(p.nextText())
                    "tabs" -> inTabs = true
                    "tab" -> if (word && !inTabs) sb.append('\t')
                    "br" -> sb.append('\n')
                }
            } else if (ev == XmlPullParser.END_TAG && p.namespace == ns) {
                when (p.name) {
                    "p" -> sb.append('\n')
                    "tabs" -> inTabs = false
                }
            }
            ev = p.next()
        }
        return sb.toString()
    }

    fun docxToText(c: Context, uri: Uri): String {
        val m = readEntries(c, uri) { it == "word/document.xml" }
        val xml = m["word/document.xml"] ?: throw IllegalStateException("Bukan berkas DOCX yang valid")
        return extractText(xml, WORD_NS, true).trimEnd()
    }

    fun pptxToText(c: Context, uri: Uri): String {
        val re = Regex("^ppt/slides/slide(\\d+)\\.xml$")
        val m = readEntries(c, uri) { re.matches(it) }
        if (m.isEmpty()) throw IllegalStateException("Bukan berkas PPTX yang valid")
        fun num(name: String): Int = re.find(name)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
        val sb = StringBuilder()
        for (name in m.keys.sortedBy { num(it) }) {
            sb.append("=== Slide ${num(name)} ===\n")
            sb.append(extractText(m.getValue(name), DML_NS, false).trim())
            sb.append("\n\n")
        }
        return sb.toString().trimEnd()
    }
}
