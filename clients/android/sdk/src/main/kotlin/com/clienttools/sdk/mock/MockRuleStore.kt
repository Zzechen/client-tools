package com.clienttools.sdk.mock

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

data class MockRuleEntry(
    val id: String,
    val url: String,
    val method: String,
    val delayMs: Long = 0L,
    val error: String = "",
    val status: Int = 200,
    val headers: Map<String, String> = emptyMap(),
    val body: String = ""
)

object MockRuleStore {
    private val rules = ConcurrentHashMap<String, MockRuleEntry>()
    private val insertOrder = CopyOnWriteArrayList<String>()

    fun add(entry: MockRuleEntry): MockRuleEntry {
        rules[entry.id] = entry
        insertOrder.add(entry.id)
        return entry
    }

    fun delete(id: String): Boolean {
        val removed = rules.remove(id) != null
        if (removed) insertOrder.remove(id)
        return removed
    }

    fun list(): List<MockRuleEntry> = insertOrder.mapNotNull { rules[it] }

    fun clear(): Int {
        val count = rules.size
        rules.clear()
        insertOrder.clear()
        return count
    }

    fun findMatch(url: String, method: String): MockRuleEntry? {
        val upperMethod = method.uppercase()
        return insertOrder.reversed()
            .mapNotNull { rules[it] }
            .firstOrNull { entry ->
                entry.method == upperMethod && Regex(entry.url).containsMatchIn(url)
            }
    }
}
