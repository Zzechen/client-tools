package com.clienttools.sdk.inspector

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InspectorFileStoreTest {

    private lateinit var store: InspectorFileStore

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        store = InspectorFileStore(context)
        store.deleteAll()
    }

    @Test
    fun saveHtmlFile_returnsCorrectMetadata() {
        val result = store.saveHtmlFile("login", "0418-1430", "<html>Test</html>")
        assert(result != null)
        assert(result!!.tag == "login")
        assert(result.timestamp == "0418-1430")
        assert(result.fileUrl.startsWith("file://"))
        assert(result.fileUrl.endsWith("login_0418-1430.html"))
    }

    @Test
    fun getAllFiles_returnsAllSaved() {
        store.saveHtmlFile("login", "0418-1430", "<html>A</html>")
        store.saveHtmlFile("login", "0418-1440", "<html>B</html>")
        store.saveHtmlFile("home", "0418-1500", "<html>C</html>")
        val files = store.getAllFiles()
        assert(files.size == 3)
    }

    @Test
    fun getFilePath_returnsFileUrl() {
        store.saveHtmlFile("login", "0418-1430", "<html>Test</html>")
        val url = store.getFilePath("login", "0418-1430")
        assert(url != null)
        assert(url!!.startsWith("file://"))
    }

    @Test
    fun getFilePath_returnsNullForMissing() {
        val url = store.getFilePath("notexist", "0000-0000")
        assert(url == null)
    }

    @Test
    fun deleteAll_clearsAllFiles() {
        store.saveHtmlFile("login", "0418-1430", "<html>A</html>")
        store.deleteAll()
        assert(store.getAllFiles().isEmpty())
    }

    @Test
    fun generateTimestamp_hasCorrectFormat() {
        val ts = store.generateTimestamp()
        assert(ts.matches(Regex("""\d{4}-\d{4}""")))
    }
}
