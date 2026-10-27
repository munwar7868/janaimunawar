package com.munawar.ai

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.View
import android.widget.CheckBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Files & photos: browse any folder you grant (Android's folder picker), then share, zip or delete files;
 * and a photo cleaner that deletes a whole album after Android's own confirmation dialog.
 */
class FilesScreen(private val a: MainActivity) : Screen {
    private class Entry(val docId: String, val name: String, val mime: String, val size: Long) {
        val dir: Boolean get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
    }

    private val pathLabel = a.label("No folder chosen yet.", 13f, Theme.MUTED)
    private val list = a.vbox()
    private val photoBox = a.vbox()
    private var tree: Uri? = null
    private val stack = ArrayList<String>()
    private val selected = HashSet<String>()
    private var entries: List<Entry> = emptyList()
    override val view: View = build()

    private fun build(): View {
        val root = a.vbox().apply { setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(28)) }
        root.addView(a.label("Files & photos", 22f, Theme.CYAN, true))
        root.addView(a.label("Pick a folder once (Android asks you to allow it). Then browse, share, zip or delete files inside it. Deleting asks you to confirm.", 12f, Theme.MUTED))

        val card = a.card()
        card.addView(a.label("📂 File manager", 16f, Theme.CYAN, true))
        card.addView(a.button("Choose folder", Theme.PURPLE) { pickFolder() }, a.lp(MATCH, WRAP, m = 3))
        card.addView(pathLabel)
        val bar = a.hbox()
        bar.addView(a.button("⬆ Up", Theme.CARD) { up() }, a.lp(0, WRAP, 1f, 2))
        bar.addView(a.button("📤 Share", Theme.CYAN) { share() }, a.lp(0, WRAP, 1f, 2))
        bar.addView(a.button("🗜 Zip", Theme.PURPLE) { zip() }, a.lp(0, WRAP, 1f, 2))
        bar.addView(a.button("🗑 Delete", Theme.RED) { delete() }, a.lp(0, WRAP, 1f, 2))
        card.addView(bar)
        card.addView(list)
        root.addView(card, a.lp(MATCH, WRAP, m = 5))

        val pc = a.card()
        pc.addView(a.label("🖼 Photo cleaner", 16f, Theme.CYAN, true))
        pc.addView(a.label("Lists your photo albums. Delete a whole album (e.g. Screenshots, WhatsApp Images). Android shows its own “Allow delete?” dialog before anything is removed. Up to 500 photos per round.", 12f, Theme.MUTED))
        pc.addView(a.button("Scan albums", Theme.PURPLE) { scanAlbums() }, a.lp(MATCH, WRAP, m = 3))
        pc.addView(photoBox)
        root.addView(pc, a.lp(MATCH, WRAP, m = 5))

        val tools = a.card()
        tools.addView(a.label("✨ More", 16f, Theme.CYAN, true))
        tools.addView(a.button("🔤 Extract text from an image (OCR)", Theme.CARD) { a.openTool("ocr") }, a.lp(MATCH, WRAP, m = 3))
        tools.addView(a.button("📄 Summarize a file", Theme.CARD) { a.openTool("summarize") }, a.lp(MATCH, WRAP, m = 3))
        tools.addView(a.button("📷 Take a photo", Theme.CARD) { a.scope.launch { val r = Commands.run(a.host, "take a photo", false); if (r != null) a.toast(r.message) } }, a.lp(MATCH, WRAP, m = 3))
        root.addView(tools, a.lp(MATCH, WRAP, m = 5))
        return a.scroll(root)
    }

    override fun onShow() {
        if (tree == null && a.prefs.filesTree.isNotBlank()) {
            tree = try { Uri.parse(a.prefs.filesTree) } catch (e: Exception) { null }
            if (tree != null) { stack.clear(); load() }
        }
    }

    // ---------------- file manager ----------------
    private fun pickFolder() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        a.startForResult(i) { code, data ->
            val u = data?.data
            if (code == Activity.RESULT_OK && u != null) {
                try { a.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (e: Exception) {}
                tree = u; a.prefs.filesTree = u.toString(); stack.clear(); selected.clear(); load()
            }
        }
    }

    private fun parentId(): String? { val t = tree ?: return null; return stack.lastOrNull() ?: DocumentsContract.getTreeDocumentId(t) }
    private fun docUri(e: Entry): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, e.docId)

    private fun load() {
        val t = tree ?: return
        val pid = parentId() ?: return
        val out = ArrayList<Entry>()
        try {
            a.contentResolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(t, pid),
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE),
                null, null, null
            )?.use { c -> while (c.moveToNext()) out.add(Entry(c.getString(0), c.getString(1) ?: "?", c.getString(2) ?: "", c.getLong(3))) }
        } catch (e: Exception) {
            a.toast("Can't read that folder. Choose it again."); tree = null; a.prefs.filesTree = ""; return
        }
        entries = out.sortedWith(compareByDescending<Entry> { it.dir }.thenBy { it.name.lowercase() })
        selected.clear()
        pathLabel.text = "📁 " + (pid.substringAfter(":").ifEmpty { "(top)" }) + "  •  ${entries.size} items"
        list.removeAllViews()
        if (entries.isEmpty()) list.addView(a.label("This folder is empty.", 13f, Theme.MUTED))
        for (e in entries.take(300)) {
            if (e.dir) {
                list.addView(a.button("📁 ${e.name}", Theme.CARD) { stack.add(e.docId); load() }, a.lp(MATCH, WRAP, m = 2))
            } else {
                val cb = CheckBox(a).apply {
                    text = "${e.name}  (${size(e.size)})"; setTextColor(Theme.TEXT); textSize = 14f * Theme.fontScale
                    setOnCheckedChangeListener { _, on -> if (on) selected.add(e.docId) else selected.remove(e.docId) }
                }
                list.addView(cb)
            }
        }
    }

    private fun size(b: Long): String = if (b >= 1048576) "%.1f MB".format(b / 1048576.0) else "${b / 1024} KB"

    private fun up() { if (stack.isNotEmpty()) { stack.removeAt(stack.size - 1); load() } else a.toast("Already at the top folder.") }

    private fun picked(): List<Entry> = entries.filter { !it.dir && selected.contains(it.docId) }

    private fun share() {
        val p = picked()
        if (p.isEmpty()) { a.toast("Tick one or more files first."); return }
        val uris = ArrayList<Uri>(p.map { docUri(it) })
        val i = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        i.type = "*/*"; i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { a.startActivity(Intent.createChooser(i, "Share files")) } catch (e: Exception) { a.toast("Couldn't open the share sheet.") }
    }

    private fun zip() {
        val p = picked()
        val t = tree; val pid = parentId()
        if (p.isEmpty() || t == null || pid == null) { a.toast("Tick one or more files first."); return }
        a.toast("Zipping…")
        a.scope.launch {
            val msg = withContext(Dispatchers.IO) {
                try {
                    val parent = DocumentsContract.buildDocumentUriUsingTree(t, pid)
                    val dest = DocumentsContract.createDocument(a.contentResolver, parent, "application/zip", "munawar_${System.currentTimeMillis()}.zip")
                        ?: return@withContext "Couldn't create the zip here."
                    a.contentResolver.openOutputStream(dest)?.use { os ->
                        ZipOutputStream(os.buffered()).use { zos ->
                            for (e in p) {
                                zos.putNextEntry(ZipEntry(e.name))
                                a.contentResolver.openInputStream(docUri(e))?.use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                    }
                    "✔ Zip created with ${p.size} file(s)."
                } catch (ex: Exception) { "Zip failed: ${ex.message}" }
            }
            a.toast(msg); load()
        }
    }

    private fun delete() {
        val p = picked()
        if (p.isEmpty()) { a.toast("Tick one or more files first."); return }
        AlertDialog.Builder(a).setTitle("Delete ${p.size} file(s)?").setMessage(p.joinToString("\n") { it.name }.take(400))
            .setPositiveButton("Delete") { _, _ ->
                var n = 0
                for (e in p) try { if (DocumentsContract.deleteDocument(a.contentResolver, docUri(e))) n++ } catch (x: Exception) {}
                a.toast("Deleted $n of ${p.size}"); load()
            }.setNegativeButton("Cancel", null).show()
    }

    // ---------------- photo cleaner ----------------
    private fun scanAlbums() {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        if (!Perms.has(a, perm)) {
            a.requestPerm(perm) { g -> if (g) scanAlbums() else a.toast("Photo permission denied.") }
            return
        }
        val albums = LinkedHashMap<String, ArrayList<Long>>()
        try {
            a.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME), null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")
                ?.use { c -> while (c.moveToNext()) albums.getOrPut(c.getString(1) ?: "Unknown") { ArrayList() }.add(c.getLong(0)) }
        } catch (e: Exception) { a.toast("Couldn't read photos."); return }
        photoBox.removeAllViews()
        if (albums.isEmpty()) photoBox.addView(a.label("No photos found.", 13f, Theme.MUTED))
        for ((name, ids) in albums.entries.sortedByDescending { it.value.size }) {
            val row = a.hbox()
            row.addView(a.label("📷 $name  (${ids.size})", 14f), a.lp(0, WRAP, 1f))
            row.addView(a.button("Delete", Theme.RED) { confirmDelete(name, ids) }, a.lp(WRAP, WRAP, m = 2))
            photoBox.addView(row)
        }
    }

    private fun confirmDelete(name: String, ids: List<Long>) {
        val batch = ids.take(500)
        AlertDialog.Builder(a).setTitle("Delete photos in “$name”?")
            .setMessage("${batch.size} photo(s) will be moved to trash / deleted after Android's confirmation.")
            .setPositiveButton("Continue") { _, _ ->
                val uris = batch.map { ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it) }
                if (Build.VERSION.SDK_INT >= 30) {
                    try {
                        val pi = MediaStore.createDeleteRequest(a.contentResolver, uris)
                        a.startSenderForResult(pi.intentSender) { code, _ ->
                            a.toast(if (code == Activity.RESULT_OK) "✔ Deleted ${batch.size} photo(s)" else "Cancelled")
                            scanAlbums()
                        }
                    } catch (e: Exception) { a.toast("Couldn't start the delete request.") }
                } else {
                    var n = 0
                    for (u in uris) try { n += a.contentResolver.delete(u, null, null) } catch (e: Exception) {}
                    a.toast("Deleted $n photo(s). On Android 10 some photos must be removed from Gallery.")
                    scanAlbums()
                }
            }.setNegativeButton("Cancel", null).show()
    }
}
