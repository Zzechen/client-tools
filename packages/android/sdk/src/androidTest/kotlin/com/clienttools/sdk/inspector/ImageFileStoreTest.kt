package com.clienttools.sdk.inspector

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageFileStoreTest {

    private lateinit var store: ImageFileStore

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        store = ImageFileStore(context)
        store.deleteAll()
    }

    @Test
    fun saveImage_returnsCorrectMetadata() {
        val bytes = ByteArray(100) { it.toByte() }
        val result = store.saveImage("login", "0419-1430", bytes, "png")
        assert(result != null)
        assert(result!!.tag == "login")
        assert(result.timestamp == "0419-1430")
        assert(result.ext == "png")
        assert(java.io.File(result.filePath).exists())
    }

    @Test
    fun getAllImages_returnsAllSaved() {
        val bytes = ByteArray(10)
        store.saveImage("login", "0419-1430", bytes, "png")
        store.saveImage("home",  "0419-1440", bytes, "jpg")
        val images = store.getAllImages()
        assert(images.size == 2)
    }

    @Test
    fun getFilePath_returnsPathForExisting() {
        val bytes = ByteArray(10)
        store.saveImage("login", "0419-1430", bytes, "png")
        val path = store.getFilePath("login", "0419-1430")
        assert(path != null)
        assert(java.io.File(path!!).exists())
    }

    @Test
    fun getFilePath_returnsNullForMissing() {
        val path = store.getFilePath("notexist", "0000-0000")
        assert(path == null)
    }

    @Test
    fun deleteAll_clearsAllFiles() {
        val bytes = ByteArray(10)
        store.saveImage("login", "0419-1430", bytes, "png")
        store.deleteAll()
        assert(store.getAllImages().isEmpty())
    }

    @Test
    fun generateTimestamp_hasCorrectFormat() {
        val ts = store.generateTimestamp()
        assert(ts.matches(Regex("""\d{4}-\d{4}""")))
    }
}
