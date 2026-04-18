package com.clienttools.shared

import com.clienttools.shared.models.DesignDocument
import com.clienttools.shared.models.DocumentMetadata
import com.clienttools.shared.models.Node
import com.clienttools.shared.models.NodeType
import com.clienttools.shared.models.TextAttrs
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

    @Test
    fun testDesignDocumentSerializationDeserialization() {
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

        val nodes = listOf(
            Node(
                id = "header",
                type = NodeType.CONTAINER,
                screenX = 0f,
                screenY = 0f,
                widthDp = 360f,
                heightDp = 100f,
                attrs = null,
                customAttrs = mapOf("backgroundColor" to "#FF6200EE")
            ),
            Node(
                id = "title",
                type = NodeType.TEXT,
                screenX = 16f,
                screenY = 20f,
                widthDp = 100f,
                heightDp = 24f,
                attrs = TextAttrs(
                    fontSize = 24f,
                    color = "#FFFFFF",
                    fontWeight = "bold"
                ),
                customAttrs = emptyMap()
            )
        )

        val document = DesignDocument(
            version = "1.0",
            metadata = metadata,
            anchorNodeId = "header",
            nodes = nodes
        )

        val json = Json.encodeToString(DesignDocument.serializer(), document)
        val decoded = Json.decodeFromString(DesignDocument.serializer(), json)

        assertEquals(document, decoded)
        assertEquals("header", decoded.anchorNodeId)
        assertEquals(2, decoded.nodes.size)
    }

    @Test
    fun testNodeCustomAttrsDeserialization() {
        val node = Node(
            id = "button",
            type = NodeType.CONTAINER,
            screenX = 10f,
            screenY = 20f,
            widthDp = 100f,
            heightDp = 50f,
            attrs = null,
            customAttrs = mapOf(
                "backgroundColor" to "#FF6200EE",
                "borderRadius" to "8"
            )
        )

        val json = Json.encodeToString(Node.serializer(), node)
        val decoded = Json.decodeFromString(Node.serializer(), json)

        assertEquals(node, decoded)
        assertEquals(mapOf("backgroundColor" to "#FF6200EE", "borderRadius" to "8"), decoded.customAttrs)
    }
}
