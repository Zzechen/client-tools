package com.clienttools.sdk.webview

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WebViewManager.init(context)
        WebViewFileStore.deleteAll()
    }

    @Test
    fun testPushHtml() {
        val result = WebViewManager.pushHtml("login", "<html>Test</html>")

        assert((result["code"] as Int) == 0)
        val data = result["data"] as Map<*, *>
        assert(data["tag"] == "login")
        assert((data["fileSize"] as Long) > 0)
    }

    @Test
    fun testPushHtmlWithTimestamp() {
        val result = WebViewManager.pushHtml("login", "<html>Test</html>", "0418-1430")

        assert((result["code"] as Int) == 0)
        val data = result["data"] as Map<*, *>
        assert(data["timestamp"] == "0418-1430")
    }

    @Test
    fun testGetFiles() {
        WebViewManager.pushHtml("login", "<html>Test1</html>")
        WebViewManager.pushHtml("login", "<html>Test2</html>")

        val result = WebViewManager.getFiles()

        assert((result["code"] as Int) == 0)
        val data = result["data"] as Map<*, *>
        val files = data["files"] as List<*>
        assert(files.size == 2)
    }

    @Test
    fun testGetState() {
        val state = WebViewManager.getState()

        assert(state.isVisible == false)
        assert(state.opacity == 1.0f)
        assert(state.offsetX == 0)
        assert(state.offsetY == 0)
    }
}
