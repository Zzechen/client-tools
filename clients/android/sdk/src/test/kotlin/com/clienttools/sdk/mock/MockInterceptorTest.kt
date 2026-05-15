package com.clienttools.sdk.mock

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.UUID
import kotlin.test.*

class MockInterceptorTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient

    @BeforeTest
    fun setUp() {
        MockRuleStore.clear()
        mockWebServer = MockWebServer()
        mockWebServer.start()
        client = OkHttpClient.Builder()
            .addInterceptor(MockInterceptor())
            .build()
    }

    @AfterTest
    fun tearDown() {
        mockWebServer.shutdown()
        MockRuleStore.clear()
    }

    @Test
    fun `unmatched request passes through to real server`() {
        mockWebServer.enqueue(MockResponse().setBody("real response").setResponseCode(200))
        val url = mockWebServer.url("/api/data").toString()
        val resp = client.newCall(Request.Builder().url(url).build()).execute()
        assertEquals(200, resp.code)
        assertEquals("real response", resp.body?.string())
        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun `matched request returns mock response without hitting server`() {
        MockRuleStore.add(MockRuleEntry(
            id = UUID.randomUUID().toString(),
            url = "/api/login",
            method = "POST",
            status = 200,
            headers = mapOf("Content-Type" to "application/json"),
            body = """{"token":"abc123"}"""
        ))
        val url = mockWebServer.url("/api/login").toString()
        val resp = client.newCall(
            Request.Builder().url(url).post("".toRequestBody()).build()
        ).execute()
        assertEquals(200, resp.code)
        assertEquals("""{"token":"abc123"}""", resp.body?.string())
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun `mock returns correct status code`() {
        MockRuleStore.add(MockRuleEntry(
            id = UUID.randomUUID().toString(),
            url = "/api/login",
            method = "POST",
            status = 401,
            body = """{"error":"unauthorized"}"""
        ))
        val url = mockWebServer.url("/api/login").toString()
        val resp = client.newCall(
            Request.Builder().url(url).post("".toRequestBody()).build()
        ).execute()
        assertEquals(401, resp.code)
    }

    @Test
    fun `timeout error throws SocketTimeoutException`() {
        MockRuleStore.add(MockRuleEntry(
            id = UUID.randomUUID().toString(),
            url = "/api/login",
            method = "POST",
            error = "timeout"
        ))
        val url = mockWebServer.url("/api/login").toString()
        assertFailsWith<SocketTimeoutException> {
            client.newCall(Request.Builder().url(url).post("".toRequestBody()).build()).execute()
        }
    }

    @Test
    fun `connection_refused error throws ConnectException`() {
        MockRuleStore.add(MockRuleEntry(
            id = UUID.randomUUID().toString(),
            url = "/api/login",
            method = "POST",
            error = "connection_refused"
        ))
        val url = mockWebServer.url("/api/login").toString()
        assertFailsWith<ConnectException> {
            client.newCall(Request.Builder().url(url).post("".toRequestBody()).build()).execute()
        }
    }
}
