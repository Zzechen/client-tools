package com.clienttools.sdk.mock

import java.util.UUID
import kotlin.test.*

class MockRuleStoreTest {

    @BeforeTest
    fun setUp() {
        MockRuleStore.clear()
    }

    @Test
    fun `add stores entry and list returns it`() {
        val entry = MockRuleEntry(id = UUID.randomUUID().toString(), url = "/api/login", method = "POST")
        MockRuleStore.add(entry)
        val list = MockRuleStore.list()
        assertEquals(1, list.size)
        assertEquals(entry.id, list[0].id)
    }

    @Test
    fun `findMatch returns null when no rules`() {
        assertNull(MockRuleStore.findMatch("https://api.example.com/api/login", "POST"))
    }

    @Test
    fun `findMatch returns null when url does not match`() {
        MockRuleStore.add(MockRuleEntry(id = UUID.randomUUID().toString(), url = "/api/login", method = "POST"))
        assertNull(MockRuleStore.findMatch("https://api.example.com/api/logout", "POST"))
    }

    @Test
    fun `findMatch returns null when method does not match`() {
        MockRuleStore.add(MockRuleEntry(id = UUID.randomUUID().toString(), url = "/api/login", method = "POST"))
        assertNull(MockRuleStore.findMatch("https://api.example.com/api/login", "GET"))
    }

    @Test
    fun `findMatch is case-insensitive on method`() {
        MockRuleStore.add(MockRuleEntry(id = UUID.randomUUID().toString(), url = "/api/login", method = "POST"))
        assertNotNull(MockRuleStore.findMatch("https://api.example.com/api/login", "post"))
    }

    @Test
    fun `findMatch uses regex on url`() {
        MockRuleStore.add(MockRuleEntry(id = UUID.randomUUID().toString(), url = "/api/user/\\d+", method = "GET"))
        assertNotNull(MockRuleStore.findMatch("https://api.example.com/api/user/123", "GET"))
        assertNull(MockRuleStore.findMatch("https://api.example.com/api/user/abc", "GET"))
    }

    @Test
    fun `findMatch last-win when multiple rules match same url and method`() {
        val id1 = UUID.randomUUID().toString()
        val id2 = UUID.randomUUID().toString()
        MockRuleStore.add(MockRuleEntry(id = id1, url = "/api/login", method = "POST", status = 200, body = "first"))
        MockRuleStore.add(MockRuleEntry(id = id2, url = "/api/login", method = "POST", status = 401, body = "second"))
        val match = MockRuleStore.findMatch("https://api.example.com/api/login", "POST")
        assertEquals(id2, match?.id)
    }

    @Test
    fun `delete removes entry by id`() {
        val id = UUID.randomUUID().toString()
        MockRuleStore.add(MockRuleEntry(id = id, url = "/api/login", method = "POST"))
        assertTrue(MockRuleStore.delete(id))
        assertEquals(0, MockRuleStore.list().size)
    }

    @Test
    fun `delete returns false for nonexistent id`() {
        assertFalse(MockRuleStore.delete("nonexistent"))
    }

    @Test
    fun `clear removes all entries and returns count`() {
        MockRuleStore.add(MockRuleEntry(id = UUID.randomUUID().toString(), url = "/api/a", method = "GET"))
        MockRuleStore.add(MockRuleEntry(id = UUID.randomUUID().toString(), url = "/api/b", method = "POST"))
        val count = MockRuleStore.clear()
        assertEquals(2, count)
        assertEquals(0, MockRuleStore.list().size)
    }
}
