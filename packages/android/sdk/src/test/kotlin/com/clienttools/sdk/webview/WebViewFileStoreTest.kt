package com.clienttools.sdk.webview

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class WebViewFileStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WebViewFileStore.init(context)
        WebViewFileStore.deleteAll()
    }

    @Test
    fun testSaveHtmlFile() {
        val tag = "login"
        val timestamp = "0418-1430"
        val htmlContent = "<html><body>Test</body></html>"

        val result = WebViewFileStore.saveHtmlFile(tag, timestamp, htmlContent)

        assert(result != null)
        assert(result?.fileSize == htmlContent.length.toLong())
        assert(result?.tag == tag)
        assert(result?.timestamp == timestamp)
        assert(result?.isCurrent == true)  // First file should be marked as current
        assert(result?.filePath?.endsWith("login_0418-1430.html") == true)
    }

    @Test
    fun testGetAllFiles() {
        WebViewFileStore.saveHtmlFile("login", "0418-1430", "<html>Login1</html>")
        WebViewFileStore.saveHtmlFile("login", "0418-1440", "<html>Login2</html>")
        WebViewFileStore.saveHtmlFile("home", "0418-1500", "<html>Home</html>")

        val allFiles = WebViewFileStore.getAllFiles()

        assert(allFiles.size == 3)
        assert(allFiles.count { it.tag == "login" } == 2)
        assert(allFiles.count { it.isCurrent && it.tag == "login" } == 1)
        assert(allFiles[0].timestamp == "0418-1440")  // Latest for login
    }

    @Test
    fun testListFilesByTag() {
        WebViewFileStore.saveHtmlFile("login", "0417-1400", "<html>Old</html>")
        WebViewFileStore.saveHtmlFile("login", "0418-1430", "<html>New</html>")

        val files = WebViewFileStore.listFilesByTag("login")

        assert(files.size == 2)
        assert(files[0].timestamp == "0418-1430")
    }

    @Test
    fun testDeleteFile() {
        val file1 = WebViewFileStore.saveHtmlFile("login", "0418-1430", "<html>Test</html>")!!
        WebViewFileStore.saveHtmlFile("login", "0418-1440", "<html>Test2</html>")

        val deleted = WebViewFileStore.deleteFile(file1.filePath)

        assert(deleted == true)
        assert(WebViewFileStore.getAllFiles().size == 1)
    }

    @Test
    fun testDeleteAll() {
        WebViewFileStore.saveHtmlFile("login", "0418-1430", "<html>Test</html>")
        WebViewFileStore.saveHtmlFile("home", "0418-1500", "<html>Test</html>")

        WebViewFileStore.deleteAll()

        assert(WebViewFileStore.getAllFiles().isEmpty())
    }

    @Test
    fun testGetCurrentFile() {
        WebViewFileStore.saveHtmlFile("login", "0418-1430", "<html>Test</html>")

        val current = WebViewFileStore.getCurrentFile()

        assert(current == Pair("login", "0418-1430"))
    }
}
