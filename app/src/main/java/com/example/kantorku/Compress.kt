package com.example.kantorku

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class Picked(val uri: Uri, val name: String, val size: Long)

/** Nama, level Deflater, keterangan. */
val LEVELS = listOf(
    Triple("Besar", 9, "kompresi maksimal: hasil paling kecil, paling lambat"),
    Triple("Sedang", 6, "seimbang antara ukuran dan kecepatan"),
    Triple("Kecil", 1, "kompresi ringan: paling cepat, hasil lebih besar")
)

private class CountingOutputStream(private val target: OutputStream) : OutputStream() {
    var count = 0L
    override fun write(b: Int) { target.write(b); count++ }
    override fun write(b: ByteArray, off: Int, len: Int) { target.write(b, off, len); count += len }
    override fun flush() = target.flush()
    override fun close() = target.close()
}

private fun uniqueName(raw: String, used: MutableSet<String>): String {
    val base = raw.replace('/', '_').replace('\\', '_').ifEmpty { "berkas" }
    var name = base
    var k = 1
    while (!used.add(name)) {
        val dot = base.lastIndexOf('.')
        name = if (dot > 0) base.substring(0, dot) + " ($k)" + base.substring(dot) else "$base ($k)"
        k++
    }
    return name
}

/** Mengembalikan pasangan (total byte masukan, byte ZIP hasil). */
fun compressToZip(c: Context, inputs: List<Pair<Uri, String>>, level: Int, dest: Uri): Pair<Long, Long> {
    val os = c.contentResolver.openOutputStream(dest, "wt")
        ?: throw IllegalStateException("Tidak bisa menulis berkas tujuan")
    val counter = CountingOutputStream(os.buffered())
    var inTotal = 0L
    ZipOutputStream(counter).use { z ->
        z.setLevel(level)
        val used = HashSet<String>()
        for ((uri, rawName) in inputs) {
            val name = uniqueName(rawName, used)
            z.putNextEntry(ZipEntry(name))
            val ins = c.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Tidak bisa membaca $name")
            ins.use {
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = it.read(buf)
                    if (n < 0) break
                    z.write(buf, 0, n)
                    inTotal += n
                }
            }
            z.closeEntry()
        }
    }
    return inTotal to counter.count
}

@Composable
fun CompressScreen(state: AppState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        for (u in uris) state.toCompress.add(Picked(u, displayName(ctx, u), fileSize(ctx, u)))
        state.compressResult = ""
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { dest: Uri? ->
        if (dest != null) {
            val items = state.toCompress.toList().map { it.uri to it.name }
            val level = LEVELS[state.level].second
            state.busy = true
            state.compressResult = ""
            scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { compressToZip(ctx, items, level, dest) } }
                state.busy = false
                r.onSuccess { (inB, outB) ->
                    state.compressResult = if (inB > 0 && outB < inB) {
                        val saved = 100.0 * (inB - outB) / inB
                        "Selesai: ${formatSize(inB)} menjadi ${formatSize(outB)} (hemat " +
                            String.format(java.util.Locale.US, "%.1f", saved) + "%)"
                    } else {
                        "Selesai, tetapi ukuran tidak berkurang (${formatSize(inB)} menjadi ${formatSize(outB)}). " +
                            "Berkas yang sudah terkompresi (JPG, MP4, ZIP, DOCX, dll.) memang sulit dikecilkan lagi."
                    }
                }.onFailure {
                    state.compressResult = "Gagal: ${it.message}"
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Kompres berkas ke ZIP", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Button(onClick = { pick.launch(arrayOf("*/*")) }) { Text("Pilih berkas") }
            Button(onClick = { state.toCompress.clear(); state.compressResult = "" }) { Text("Kosongkan") }
        }
        Text("Tingkat kompresi", style = MaterialTheme.typography.titleSmall)
        LEVELS.forEachIndexed { i, lv ->
            Row(
                Modifier.fillMaxWidth().clickable { state.level = i },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = state.level == i, onClick = { state.level = i })
                Text("${lv.first}: ${lv.third}")
            }
        }
        Spacer(Modifier.height(8.dp))
        val total = state.toCompress.sumOf { if (it.size > 0) it.size else 0L }
        Text("${state.toCompress.size} berkas dipilih, total ${formatSize(total)}")
        LazyColumn(Modifier.weight(1f)) {
            items(state.toCompress.toList()) { f ->
                Text("${f.name}  (${formatSize(f.size)})", modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        if (state.busy) Text("Memproses...")
        if (state.compressResult.isNotEmpty()) Text(state.compressResult)
        Button(
            onClick = { save.launch("arsip.zip") },
            enabled = state.toCompress.isNotEmpty() && !state.busy,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text("Kompres dan simpan") }
    }
}
