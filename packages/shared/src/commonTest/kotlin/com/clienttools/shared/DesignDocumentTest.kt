package com.clienttools.shared

import com.clienttools.shared.models.DocumentMetadata
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class DesignDocumentTest {

    @Test
    fun testDocumentMetadataSerializationDeserialization() {
        val metadata = DocumentMetadata(
            name = "Login Screen v1.0",
            description = "User login page",
            designerName = "Alice",
            createdAt = "2026-04-18T10:00:00Z",
            modifiedAt = "2026-04-18T14:30:00Z",
            screenWidthDp = 360f,
            screenHeightDp = 800f,
            tags = listOf("authentication", "mobile")
        )

        val json = Json.encodeToString(DocumentMetadata.serializer(), metadata)
        val decoded = Json.decodeFromString(DocumentMetadata.serializer(), json)

        assertEquals(metadata, decoded)
    }
}
