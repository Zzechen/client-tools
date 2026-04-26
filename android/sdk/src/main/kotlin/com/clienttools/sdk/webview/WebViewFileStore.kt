package com.clienttools.sdk.webview

import android.content.Context
import android.util.Log
import com.clienttools.sdk.model.WebViewFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object WebViewFileStore {
    private lateinit var cacheDir: File
    private val TAG = "WebViewFileStore"
    private var currentFile: Pair<String, String>? = null  // (tag, timestamp)

    fun init(context: Context) {
        cacheDir = File(context.cacheDir, "webview")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    fun saveHtmlFile(tag: String, timestamp: String, htmlContent: String): WebViewFile? = try {
        // Create tag directory
        val tagDir = File(cacheDir, tag)
        if (!tagDir.exists()) {
            tagDir.mkdirs()
        }

        // Generate filename and save
        val filename = "${tag}_${timestamp}.html"
        val file = File(tagDir, filename)
        file.writeText(htmlContent, Charsets.UTF_8)

        // Mark previous file as not current
        val existingFiles = listFilesByTag(tag)

        WebViewFile(
            tag = tag,
            timestamp = timestamp,
            filePath = file.absolutePath,
            fileSize = file.length(),
            isCurrent = true
        ).also {
            currentFile = Pair(tag, timestamp)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error saving HTML file", e)
        null
    }

    fun getAllFiles(): List<WebViewFile> = try {
        val files = mutableListOf<WebViewFile>()
        cacheDir.listFiles()?.forEach { tagDir ->
            if (tagDir.isDirectory) {
                tagDir.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".html")) {
                        val timestamp = parseTimestamp(file.name)
                        if (timestamp.isNotEmpty()) {
                            files.add(WebViewFile(
                                tag = tagDir.name,
                                timestamp = timestamp,
                                filePath = file.absolutePath,
                                fileSize = file.length(),
                                isCurrent = currentFile == Pair(tagDir.name, timestamp)
                            ))
                        }
                    }
                }
            }
        }
        files.sortByDescending { it.timestamp }
        files
    } catch (e: Exception) {
        Log.e(TAG, "Error getting all files", e)
        emptyList()
    }

    fun listFilesByTag(tag: String): List<WebViewFile> {
        return getAllFiles().filter { it.tag == tag }
    }

    fun getCurrentFile(): Pair<String, String>? = currentFile

    fun setCurrentFile(tag: String, timestamp: String) {
        currentFile = Pair(tag, timestamp)
    }

    fun deleteFile(filePath: String): Boolean = try {
        val file = File(filePath)
        file.delete().also { success ->
            if (success && currentFile != null) {
                val isCurrentFile = filePath.endsWith("_${currentFile!!.second}.html")
                if (isCurrentFile) {
                    currentFile = null
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error deleting file", e)
        false
    }

    fun deleteAll(): Boolean = try {
        cacheDir.deleteRecursively()
        currentFile = null
        cacheDir.mkdirs()
        true
    } catch (e: Exception) {
        Log.e(TAG, "Error deleting all files", e)
        false
    }

    private fun parseTimestamp(filename: String): String {
        // Extract timestamp from "tag_0418-1430.html"
        val regex = """_(\d{4}-\d{4})\.html$""".toRegex()
        return regex.find(filename)?.groupValues?.get(1) ?: ""
    }
}
