package com.clienttools.sdk.inspector

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class InspectorFileStore(context: Context) {
    private val cacheDir = File(context.cacheDir, "inspector")
    private val TAG = "InspectorFileStore"

    init {
        cacheDir.mkdirs()
    }

    fun saveHtmlFile(tag: String, timestamp: String, htmlContent: String): FileInfo? = try {
        val tagDir = File(cacheDir, tag).also { it.mkdirs() }
        val file = File(tagDir, "${tag}_${timestamp}.html")
        file.writeText(htmlContent, Charsets.UTF_8)
        FileInfo(
            tag = tag,
            timestamp = timestamp,
            fileUrl = "file://${file.absolutePath}"
        )
    } catch (e: Exception) {
        Log.e(TAG, "Error saving HTML file", e)
        null
    }

    fun getAllFiles(): List<FileInfo> = try {
        val result = mutableListOf<FileInfo>()
        cacheDir.listFiles()?.forEach { tagDir ->
            if (!tagDir.isDirectory) return@forEach
            tagDir.listFiles()?.forEach { file ->
                if (!file.name.endsWith(".html")) return@forEach
                val timestamp = parseTimestamp(file.name) ?: return@forEach
                result.add(FileInfo(tagDir.name, timestamp, "file://${file.absolutePath}"))
            }
        }
        result.sortedByDescending { it.timestamp }
    } catch (e: Exception) {
        Log.e(TAG, "Error listing files", e)
        emptyList()
    }

    fun getFilePath(tag: String, timestamp: String): String? {
        val file = File(File(cacheDir, tag), "${tag}_${timestamp}.html")
        return if (file.exists()) "file://${file.absolutePath}" else null
    }

    fun deleteAll(): Boolean = try {
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
        true
    } catch (e: Exception) {
        Log.e(TAG, "Error deleting all", e)
        false
    }

    fun generateTimestamp(): String =
        SimpleDateFormat("MMdd-HHmm", Locale.US).format(Date())

    private fun parseTimestamp(filename: String): String? =
        Regex("""_(\d{4}-\d{4})\.html$""").find(filename)?.groupValues?.get(1)
}
