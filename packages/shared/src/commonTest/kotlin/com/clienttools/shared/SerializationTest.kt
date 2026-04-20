package com.clienttools.shared

import com.clienttools.shared.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun testTextAttrsSerialize() {
        val attrs: NodeAttrs = TextAttrs(
            fontSize = 16f,
            color = "#FF333333",
            fontWeight = "700"
        )
        val encoded = json.encodeToString(attrs)
        assert(encoded.contains("\"type\":\"text\""))
        assert(encoded.contains("\"fontSize\":16.0"))
        assert(encoded.contains("\"color\":\"#FF333333\""))
    }

    @Test
    fun testTextAttrsDeserialize() {
        val jsonStr = """{"type":"text","fontSize":16.0,"color":"#FF333333","fontWeight":"700"}"""
        val attrs: NodeAttrs = json.decodeFromString(jsonStr)
        assertIs<TextAttrs>(attrs)
        assertEquals(16f, attrs.fontSize)
        assertEquals("#FF333333", attrs.color)
    }

    @Test
    fun testImageAttrsSerialize() {
        val attrs: NodeAttrs = ImageAttrs()
        val encoded = json.encodeToString(attrs)
        assert(encoded.contains("\"type\":\"image\""))
        assert(encoded.contains("\"scaleType\":\"fitCenter\""))
    }

    @Test
    fun testListAttrsDeserialize() {
        val jsonStr = """{"type":"list","itemSpacing":8.0,"orientation":"VERTICAL"}"""
        val attrs: NodeAttrs = json.decodeFromString(jsonStr)
        assertIs<ListAttrs>(attrs)
        assertEquals(8f, attrs.itemSpacing)
    }

    @Test
    fun testContainerAttrsDeserialize() {
        val jsonStr = """{"type":"container","paddingTop":12.0,"paddingBottom":12.0,"paddingLeft":16.0,"paddingRight":16.0}"""
        val attrs: NodeAttrs = json.decodeFromString(jsonStr)
        assertIs<ContainerAttrs>(attrs)
        assertEquals(12f, attrs.paddingTop)
    }

    @Test
    fun testNodeSerialize() {
        val node = Node(
            id = "text_1",
            type = NodeType.TEXT,
            screenX = 16f,
            screenY = 48f,
            widthDp = 200f,
            heightDp = 24f,
            attrs = TextAttrs(fontSize = 16f, color = "#FF333333", fontWeight = "700")
        )
        val encoded = json.encodeToString(node)
        assert(encoded.contains("\"id\":\"text_1\""))
        assert(encoded.contains("\"type\":\"TEXT\""))
        assert(encoded.contains("\"screenX\":16.0"))
    }

    @Test
    fun testApiResponseWithNodeList() {
        val device = DeviceInfo(
            screenWidthDp = 375,
            screenHeightDp = 812,
            density = 3f,
            orientation = "portrait"
        )
        val response = ApiResponse(
            code = 0,
            message = "success",
            sdkVersion = 1,
            device = device,
            data = listOf(
                Node("text_1", NodeType.TEXT, 16f, 48f, 200f, 24f, null)
            )
        )
        val encoded = json.encodeToString(response)
        val decoded: ApiResponse<List<Node>> = json.decodeFromString(encoded)
        assertEquals(0, decoded.code)
        assertEquals(1, decoded.data?.size)
        assertEquals("text_1", decoded.data?.first()?.id)
    }

    @Test
    fun testModifyViewRequestSerialize() {
        val request = ModifyViewRequest(
            id = "login_text_1",
            props = ViewProps(marginTopDiffDp = 4f, paddingLeftDiffDp = 8f)
        )
        val encoded = json.encodeToString(request)
        val decoded: ModifyViewRequest = json.decodeFromString(encoded)
        assertEquals("login_text_1", decoded.id)
        assertEquals(4f, decoded.props.marginTopDiffDp)
        assertEquals(null, decoded.props.marginBottomDiffDp)
    }

    @Test
    fun testViewPropsWrapContent() {
        val jsonStr = """{"widthDp":"wrap_content","heightDp":"42.0"}"""
        val props: ViewProps = json.decodeFromString(jsonStr)
        assertEquals("wrap_content", props.widthDp)
        assertEquals("42.0", props.heightDp)
    }

    @Test
    fun testPageChangedEventSerialize() {
        val event = PageChangedEvent(
            activityName = "com.example.LoginActivity",
            timestamp = "0417-1423"
        )
        val encoded = json.encodeToString(event)
        val decoded: PageChangedEvent = json.decodeFromString(encoded)
        assertEquals("page_changed", decoded.event)
        assertEquals("com.example.LoginActivity", decoded.activityName)
    }
}
