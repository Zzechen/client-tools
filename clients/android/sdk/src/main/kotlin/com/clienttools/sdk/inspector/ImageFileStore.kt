package com.clienttools.sdk.inspector

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ImageFileStore(context: Context) {
    private val cacheDir = File(context.cacheDir, "inspector-images")
    private val TAG = "ImageFileStore"

    init {
        cacheDir.mkdirs()
    }

    fun saveImage(tag: String, timestamp: String, bytes: ByteArray, ext: String): ImageInfo? = try {
        val tagDir = File(cacheDir, tag).also { it.mkdirs() }
        val file = File(tagDir, "${tag}_${timestamp}.${ext}")
        file.writeBytes(bytes)
        ImageInfo(tag = tag, timestamp = timestamp, filePath = file.absolutePath, ext = ext)
    } catch (e: Exception) {
        Log.e(TAG, "Error saving image", e)
        null
    }

    fun getAllImages(): List<ImageInfo> = try {
        val result = mutableListOf<ImageInfo>()
        cacheDir.listFiles()?.forEach { tagDir ->
            if (!tagDir.isDirectory) return@forEach
            tagDir.listFiles()?.forEach { file ->
                val ext = file.extension.lowercase()
                if (ext != "png" && ext != "jpg" && ext != "jpeg") return@forEach
                val timestamp = parseTimestamp(file.name) ?: return@forEach
                result.add(ImageInfo(tagDir.name, timestamp, file.absolutePath, ext))
            }
        }
        result.sortedByDescending { it.timestamp }
    } catch (e: Exception) {
        Log.e(TAG, "Error listing images", e)
        emptyList()
    }

    fun getFilePath(tag: String, timestamp: String): String? {
        val tagDir = File(cacheDir, tag)
        return tagDir.listFiles()
            ?.firstOrNull { f ->
                val ext = f.extension.lowercase()
                (ext == "png" || ext == "jpg" || ext == "jpeg") && parseTimestamp(f.name) == timestamp
            }
            ?.absolutePath
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
        Regex("""_(\d{4}-\d{4})\.\w+$""").find(filename)?.groupValues?.get(1)
}
