package com.example.gazecaa

import android.content.Context
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Backup completo (profili, impostazioni, sessioni, foto, voci) in un unico file .zip. */
object Backup {
    private val folders = listOf("files", "shared_prefs")

    fun create(ctx: Context): File {
        val dataDir = File(ctx.applicationInfo.dataDir)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ITALY).format(Date())
        val out = File(File(ctx.cacheDir, "export").apply { mkdirs() }, "backup_sguardo_caa_$stamp.zip")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            for (sub in folders) {
                val root = File(dataDir, sub)
                if (!root.exists()) continue
                root.walkTopDown().filter { it.isFile }.forEach { f ->
                    zip.putNextEntry(ZipEntry(sub + "/" + f.relativeTo(root).path))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return out
    }

    /** Ripristina un backup; restituisce il numero di file ripristinati (0 = file non valido). */
    fun restore(ctx: Context, uri: Uri): Int {
        val dataDir = File(ctx.applicationInfo.dataDir).canonicalFile
        var count = 0
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    val name = e.name
                    if (!e.isDirectory && folders.any { name.startsWith("$it/") }) {
                        val target = File(dataDir, name).canonicalFile
                        // protezione "zip slip": solo dentro la cartella dell'app
                        if (target.path.startsWith(dataDir.path + File.separator)) {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                            count++
                        }
                    }
                    zip.closeEntry()
                    e = zip.nextEntry
                }
            }
        }
        return count
    }
}
