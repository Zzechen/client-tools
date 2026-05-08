package com.clienttools.sdk.inspector

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DomQueryServiceTest {

    @Test
    fun parseNodes_validJson_returnsList() {
        val service = DomQueryService()
        val json = """[{"id":"btn","tagName":"button","x":10,"y":20,"width":100,"height":48,"text":"OK"}]"""
        val nodes = service.parseNodesJson(json, webViewLeftDp = 0f, webViewTopDp = 0f, webViewScrollXDp = 0f, webViewScrollYDp = 0f)
        assertEquals(1, nodes.size)
        assertEquals("btn", nodes[0].id)
        assertEquals("button", nodes[0].tagName)
        assertEquals(10, nodes[0].x)
        assertEquals(20, nodes[0].y)
        assertEquals(100, nodes[0].width)
        assertEquals(48, nodes[0].height)
        assertEquals("OK", nodes[0].text)
    }

    @Test
    fun parseNodes_withWebViewPosition_appliesCoordinateConversion() {
        val service = DomQueryService()
        val json = """[{"id":"","tagName":"div","x":50,"y":100,"width":200,"height":80,"text":""}]"""
        val nodes = service.parseNodesJson(json, webViewLeftDp = 10f, webViewTopDp = 20f, webViewScrollXDp = 5f, webViewScrollYDp = 15f)
        assertEquals(1, nodes.size)
        // screenX = webViewLeftDp(10) + webViewScrollXDp(5) + elementX(50) = 65
        assertEquals(65, nodes[0].x)
        // screenY = webViewTopDp(20) + webViewScrollYDp(15) + elementY(100) = 135
        assertEquals(135, nodes[0].y)
    }

    @Test
    fun parseNodes_invalidJson_returnsEmptyList() {
        val service = DomQueryService()
        val nodes = service.parseNodesJson("not-json", 0f, 0f, 0f, 0f)
        assertEquals(0, nodes.size)
    }

    @Test
    fun parseNodeById_validJson_returnsNode() {
        val service = DomQueryService()
        val json = """{"id":"title","tagName":"h1","x":0,"y":0,"width":300,"height":40,"text":"Hello"}"""
        val node = service.parseNodeJson(json, webViewLeftDp = 0f, webViewTopDp = 0f, webViewScrollXDp = 0f, webViewScrollYDp = 0f)
        assertNotNull(node)
        assertEquals("title", node!!.id)
        assertEquals("h1", node.tagName)
    }

    @Test
    fun parseNodeById_nullJson_returnsNull() {
        val service = DomQueryService()
        val node = service.parseNodeJson(null, 0f, 0f, 0f, 0f)
        assertNull(node)
    }
}
