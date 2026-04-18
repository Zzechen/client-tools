package com.clienttools.sdk.inspector

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InspectorApiHandlerTest {

    private lateinit var store: InspectorFileStore
    private lateinit var handler: InspectorApiHandler

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        store = InspectorFileStore(context)
        store.deleteAll()
        handler = InspectorApiHandler(store, getTopViewModel = { null })
    }

    @Test
    fun pushHtml_savesFileAndReturns200() {
        val body = """{"tag":"login","html":"<html>Test</html>","timestamp":"0418-1430"}"""
        val response = handler.handlePushHtml(body)
        assert(response.status.requestStatus == 200)
        val files = store.getAllFiles()
        assert(files.any { it.tag == "login" && it.timestamp == "0418-1430" })
    }

    @Test
    fun pushHtml_missingTag_returns400() {
        val body = """{"html":"<html>Test</html>"}"""
        val response = handler.handlePushHtml(body)
        assert(response.status.requestStatus == 400)
    }

    @Test
    fun getFiles_returnsAllFiles() {
        store.saveHtmlFile("login", "0418-1430", "<html>A</html>")
        store.saveHtmlFile("home", "0418-1500", "<html>B</html>")
        val response = handler.handleGetFiles(currentFile = null)
        assert(response.status.requestStatus == 200)
    }

    @Test
    fun show_fileNotFound_returns404() {
        val body = """{"tag":"notexist","timestamp":"0000-0000"}"""
        val response = handler.handleShow(body)
        assert(response.status.requestStatus == 404)
    }

    @Test
    fun hide_returnsSuccess() {
        val response = handler.handleHide()
        assert(response.status.requestStatus == 200)
    }

    @Test
    fun adjust_returnsUpdatedValues() {
        // viewModel is null → should still return 200 with current state
        val body = """{"offsetX":10,"offsetY":-5,"opacity":0.7}"""
        val response = handler.handleAdjust(body, currentOffsetX = 0, currentOffsetY = 0, currentOpacity = 0.5f)
        assert(response.status.requestStatus == 200)
    }
}
