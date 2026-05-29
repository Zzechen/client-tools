package com.clienttools.sdk.webview

import kotlin.test.*

class WebViewRedirectStoreTest {

    @BeforeTest
    fun setUp() {
        WebViewRedirectStore.clear()
    }

    @Test
    fun `resolveRedirect returns original url when no rules`() {
        assertEquals("https://example.com/page", WebViewRedirectStore.resolveRedirect("https://example.com/page"))
    }

    @Test
    fun `resolveRedirect returns targetUrl when pattern matches`() {
        WebViewRedirectStore.add(WebViewRedirectEntry(
            id = "1", urlPattern = "https://example\\.com/page", targetUrl = "http://192.168.1.1:3000/page"
        ))
        assertEquals("http://192.168.1.1:3000/page", WebViewRedirectStore.resolveRedirect("https://example.com/page"))
    }

    @Test
    fun `resolveRedirect first rule wins when multiple match`() {
        WebViewRedirectStore.add(WebViewRedirectEntry(id = "1", urlPattern = "example\\.com", targetUrl = "http://first"))
        WebViewRedirectStore.add(WebViewRedirectEntry(id = "2", urlPattern = "example\\.com", targetUrl = "http://second"))
        assertEquals("http://first", WebViewRedirectStore.resolveRedirect("https://example.com/page"))
    }

    @Test
    fun `resolveRedirect appends original query params to targetUrl`() {
        WebViewRedirectStore.add(WebViewRedirectEntry(
            id = "1", urlPattern = "example\\.com/page", targetUrl = "http://192.168.1.1:3000/page"
        ))
        val result = WebViewRedirectStore.resolveRedirect("https://example.com/page?foo=bar&baz=qux")
        assertTrue(result.startsWith("http://192.168.1.1:3000/page"))
        assertTrue(result.contains("foo=bar"))
        assertTrue(result.contains("baz=qux"))
    }

    @Test
    fun `resolveRedirect original query wins on conflict`() {
        WebViewRedirectStore.add(WebViewRedirectEntry(
            id = "1", urlPattern = "example\\.com", targetUrl = "http://192.168.1.1:3000?foo=TARGET"
        ))
        val result = WebViewRedirectStore.resolveRedirect("https://example.com?foo=ORIGINAL")
        assertTrue(result.contains("foo=ORIGINAL"))
        assertFalse(result.contains("foo=TARGET"))
    }

    @Test
    fun `resolveRedirect returns original url when no pattern matches`() {
        WebViewRedirectStore.add(WebViewRedirectEntry(id = "1", urlPattern = "other\\.com", targetUrl = "http://x"))
        assertEquals("https://example.com", WebViewRedirectStore.resolveRedirect("https://example.com"))
    }

    @Test
    fun `resolveRedirect uses regex prefix matching`() {
        WebViewRedirectStore.add(WebViewRedirectEntry(
            id = "1", urlPattern = "example\\.com/section/.*", targetUrl = "http://local:3000"
        ))
        assertEquals("http://local:3000", WebViewRedirectStore.resolveRedirect("https://example.com/section/page1"))
        assertEquals("https://example.com/other", WebViewRedirectStore.resolveRedirect("https://example.com/other"))
    }

    @Test
    fun `delete removes rule`() {
        WebViewRedirectStore.add(WebViewRedirectEntry(id = "1", urlPattern = "x\\.com", targetUrl = "http://y"))
        WebViewRedirectStore.delete("1")
        assertEquals("https://x.com", WebViewRedirectStore.resolveRedirect("https://x.com"))
    }

    @Test
    fun `clear removes all rules and returns count`() {
        WebViewRedirectStore.add(WebViewRedirectEntry(id = "1", urlPattern = "a", targetUrl = "b"))
        WebViewRedirectStore.add(WebViewRedirectEntry(id = "2", urlPattern = "c", targetUrl = "d"))
        val count = WebViewRedirectStore.clear()
        assertEquals(2, count)
        assertEquals(0, WebViewRedirectStore.list().size)
    }
}
