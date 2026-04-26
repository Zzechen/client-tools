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
        val nodes = service.parseNodesJson(json, webViewLeft = 0, webViewTop = 0, webViewScrollX = 0, webViewScrollY = 0, offsetXPx = 0, offsetYPx = 0)
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
    fun parseNodes_withOffset_appliesCoordinateConversion() {
        val service = DomQueryService()
        val json = """[{"id":"","tagName":"div","x":50,"y":100,"width":200,"height":80,"text":""}]"""
        val nodes = service.parseNodesJson(json, webViewLeft = 10, webViewTop = 20, webViewScrollX = 5, webViewScrollY = 15, offsetXPx = 3, offsetYPx = 7)
        assertEquals(1, nodes.size)
        // screenX = webViewLeft(10) + webViewScrollX(5) + elementX(50) + offsetXPx(3) = 68
        assertEquals(68, nodes[0].x)
        // screenY = webViewTop(20) + webViewScrollY(15) + elementY(100) + offsetYPx(7) = 142
        assertEquals(142, nodes[0].y)
    }

    @Test
    fun parseNodes_invalidJson_returnsEmptyList() {
        val service = DomQueryService()
        val nodes = service.parseNodesJson("not-json", 0, 0, 0, 0, 0, 0)
        assertEquals(0, nodes.size)
    }

    @Test
    fun parseNodeById_validJson_returnsNode() {
        val service = DomQueryService()
        val json = """{"id":"title","tagName":"h1","x":0,"y":0,"width":300,"height":40,"text":"Hello"}"""
        val node = service.parseNodeJson(json, webViewLeft = 0, webViewTop = 0, webViewScrollX = 0, webViewScrollY = 0, offsetXPx = 0, offsetYPx = 0)
        assertNotNull(node)
        assertEquals("title", node!!.id)
        assertEquals("h1", node.tagName)
    }

    @Test
    fun parseNodeById_nullJson_returnsNull() {
        val service = DomQueryService()
        val node = service.parseNodeJson(null, 0, 0, 0, 0, 0, 0)
        assertNull(node)
    }
}
