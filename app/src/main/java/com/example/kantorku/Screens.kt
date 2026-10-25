package com.example.kantorku

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberOpener(state: AppState, mimes: Array<String>): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) state.openUri(uri)
    }
    return { launcher.launch(mimes) }
}

@Composable
fun HomeScreen(state: AppState) {
    val open = rememberOpener(state, arrayOf("*/*"))
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("KantorKu", style = MaterialTheme.typography.headlineMedium)
        Text("Buka PDF, DOCX, PPTX, TXT, MD, atau CSV. DOCX dan PPTX hanya ditampilkan sebagai teks (tanpa tata letak, gambar, atau format). XLSX dan format lama (DOC, PPT, XLS) belum didukung.")
        Spacer(Modifier.height(12.dp))
        Button(onClick = open) { Text("Buka berkas") }
        Spacer(Modifier.height(16.dp))
        Text("Terbaru", style = MaterialTheme.typography.titleMedium)
        if (state.recents.isEmpty()) Text("Belum ada berkas.")
        LazyColumn {
            items(state.recents.toList()) { r ->
                Text(
                    r.name,
                    modifier = Modifier.fillMaxWidth().clickable { state.openUri(Uri.parse(r.uri)) }.padding(vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
fun PdfScreen(state: AppState) {
    val open = rememberOpener(state, arrayOf("application/pdf"))
    Column(Modifier.fillMaxSize()) {
        Button(onClick = open, modifier = Modifier.padding(8.dp)) { Text("Buka PDF") }
        val doc = state.pdf
        if (doc == null) {
            Text("Belum ada PDF dibuka.", modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn(
                Modifier.fillMaxSize().background(Color(0xFFE0E0E0)),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(doc.pageCount) { i ->
                    val bmp by produceState<Bitmap?>(null, doc, i) {
                        value = withContext(Dispatchers.IO) { runCatching { doc.render(i, 1080) }.getOrNull() }
                    }
                    val b = bmp
                    if (b != null) {
                        Image(
                            bitmap = b.asImageBitmap(),
                            contentDescription = "Halaman ${i + 1}",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().height(400.dp).background(Color.White))
                    }
                }
            }
        }
    }
}

@Composable
fun EditorScreen(state: AppState) {
    val ctx = LocalContext.current
    val open = rememberOpener(state, arrayOf("*/*"))
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri != null) {
            runCatching { writeText(ctx, uri, state.text) }
                .onSuccess { toast(ctx, "Tersimpan") }
                .onFailure { toast(ctx, "Gagal menyimpan: ${it.message}") }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = open) { Text("Buka") }
            Button(onClick = { save.launch("dokumen.txt") }) { Text("Simpan") }
        }
        val words = state.text.split(Regex("\\s+")).count { it.isNotEmpty() }
        Text("$words kata", modifier = Modifier.padding(horizontal = 12.dp))
        if (state.note.isNotEmpty()) {
            Text(state.note, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
        TextField(
            value = state.text,
            onValueChange = { state.text = it },
            modifier = Modifier.fillMaxSize(),
            placeholder = { Text("Mulai mengetik...") }
        )
    }
}

@Composable
private fun GridCell(text: String, w: Dp, bg: Color, bold: Boolean = false, onClick: (() -> Unit)? = null) {
    Box(
        Modifier
            .width(w)
            .height(40.dp)
            .background(bg)
            .border(0.5.dp, Color.LightGray)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, maxLines = 1, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun SheetScreen(state: AppState) {
    val ctx = LocalContext.current
    var sel by remember { mutableStateOf("A1") }
    val open = rememberOpener(state, arrayOf("*/*"))
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) {
            runCatching { writeText(ctx, uri, toCsv(state.cells)) }
                .onSuccess { toast(ctx, "Tersimpan") }
                .onFailure { toast(ctx, "Gagal menyimpan: ${it.message}") }
        }
    }
    val ev = SheetEval { state.cells[it] ?: "" }
    val hs = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = open) { Text("Impor CSV") }
            Button(onClick = { export.launch("lembar.csv") }) { Text("Ekspor CSV") }
        }
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(sel, modifier = Modifier.width(48.dp), fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = state.cells[sel] ?: "",
                onValueChange = { v -> if (v.isEmpty()) state.cells.remove(sel) else state.cells[sel] = v },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Isi sel, atau =SUM(A1:A5)") }
            )
        }
        Row(Modifier.horizontalScroll(hs)) {
            GridCell("", 40.dp, Color(0xFFEEEEEE))
            for (c in 0 until COLS) GridCell("${'A' + c}", 88.dp, Color(0xFFEEEEEE), bold = true)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(ROWS) { r ->
                Row(Modifier.horizontalScroll(hs)) {
                    GridCell("${r + 1}", 40.dp, Color(0xFFEEEEEE), bold = true)
                    for (c in 0 until COLS) {
                        val ref = cellRef(r, c)
                        GridCell(
                            text = ev.display(ref),
                            w = 88.dp,
                            bg = if (ref == sel) Color(0xFFD6E4FF) else Color.White,
                            onClick = { sel = ref }
                        )
                    }
                }
            }
        }
    }
}
