package com.clienttools.sdk.inspector

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageApiHandlerTest {

    private lateinit var imageStore: ImageFileStore
    private lateinit var htmlStore: InspectorFileStore
    private lateinit var handler: InspectorApiHandler

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        imageStore = ImageFileStore(context)
        imageStore.deleteAll()
        htmlStore = InspectorFileStore(context)
        htmlStore.deleteAll()
        handler = InspectorApiHandler(htmlStore, imageStore, getTopViewModel = { null })
    }

    @Test
    fun pushImage_savesFileAndReturns200() {
        val fakeBytes = ByteArray(16) { it.toByte() }
        val base64 = Base64.encodeToString(fakeBytes, Base64.NO_WRAP)
        val body = """{"tag":"login","timestamp":"0419-1430","image":"$base64","ext":"png"}"""
        val response = handler.handlePushImage(body)
        assert(response.status.requestStatus == 200)
        val images = imageStore.getAllImages()
        assert(images.any { it.tag == "login" && it.timestamp == "0419-1430" })
    }

    @Test
    fun pushImage_missingTag_returns400() {
        val body = """{"image":"abc"}"""
        val response = handler.handlePushImage(body)
        assert(response.status.requestStatus == 400)
    }

    @Test
    fun pushImage_missingImage_returns400() {
        val body = """{"tag":"login"}"""
        val response = handler.handlePushImage(body)
        assert(response.status.requestStatus == 400)
    }

    @Test
    fun showImage_fileNotFound_returns404() {
        val body = """{"tag":"notexist","timestamp":"0000-0000"}"""
        val response = handler.handleShowImage(body)
        assert(response.status.requestStatus == 404)
    }

    @Test
    fun getImages_returnsAllImages() {
        val bytes = ByteArray(10)
        imageStore.saveImage("login", "0419-1430", bytes, "png")
        imageStore.saveImage("home",  "0419-1440", bytes, "jpg")
        val response = handler.handleGetImages(currentImage = null)
        assert(response.status.requestStatus == 200)
    }
}
