package com.clienttools.sdk.http

import kotlin.test.*

class CustomRouteTest {

    @Test
    fun `HttpMethod GET value is GET`() {
        assertEquals("GET", HttpMethod.GET.value)
    }

    @Test
    fun `HttpMethod POST value is POST`() {
        assertEquals("POST", HttpMethod.POST.value)
    }

    @Test
    fun `CustomResult ok has code 0 and data`() {
        val r = CustomResult.ok("hello")
        assertEquals(0, r.code)
        assertEquals("ok", r.message)
        assertEquals("hello", r.data)
    }

    @Test
    fun `CustomResult ok with no arg has empty data`() {
        val r = CustomResult.ok()
        assertEquals("", r.data)
    }

    @Test
    fun `CustomResult error has code -1 and null data`() {
        val r = CustomResult.error("something failed")
        assertEquals(-1, r.code)
        assertEquals("something failed", r.message)
        assertNull(r.data)
    }

    @Test
    fun `CustomResult error allows custom code`() {
        val r = CustomResult.error("forbidden", code = 403)
        assertEquals(403, r.code)
    }

    @Test
    fun `buildCustomResultJson ok produces valid json`() {
        val r = CustomResult.ok("world")
        val json = buildCustomResultJson(r)
        assertEquals("""{"code":0,"message":"ok","data":"world"}""", json)
    }

    @Test
    fun `buildCustomResultJson error produces null data`() {
        val r = CustomResult.error("oops")
        val json = buildCustomResultJson(r)
        assertEquals("""{"code":-1,"message":"oops","data":null}""", json)
    }

    @Test
    fun `buildCustomResultJson escapes quotes in message`() {
        val r = CustomResult.error("say \"hello\"")
        val json = buildCustomResultJson(r)
        assertEquals("""{"code":-1,"message":"say \"hello\"","data":null}""", json)
    }

    @Test
    fun `buildCustomResultJson escapes quotes in data`() {
        val r = CustomResult.ok("""{"key":"val"}""")
        val json = buildCustomResultJson(r)
        assertEquals("""{"code":0,"message":"ok","data":"{\"key\":\"val\"}"}""", json)
    }
}
