package com.clienttools.shared

import com.clienttools.shared.models.DesignDocument
import com.clienttools.shared.models.DocumentMetadata
import com.clienttools.shared.models.Node
import com.clienttools.shared.models.NodeType
import com.clienttools.shared.models.TextAttrs
import com.clienttools.shared.validation.DesignDocumentValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class DesignDocumentValidatorTest {

    private fun createValidDocument(): DesignDocument {
        val metadata = DocumentMetadata(
            name = "Test",
            createdAt = "2026-04-18T10:00:00Z",
            modifiedAt = "2026-04-18T10:00:00Z",
            screenWidthDp = 360f,
            screenHeightDp = 800f
        )
        return DesignDocument(
            metadata = metadata,
            anchorNodeId = "header",
            nodes = listOf(
                Node(
                    id = "header",
                    type = NodeType.CONTAINER,
                    screenX = 0f,
                    screenY = 0f,
                    widthDp = 360f,
                    heightDp = 100f
                )
            )
        )
    }

    @Test
    fun testValidDocumentPasses() {
        val doc = createValidDocument()
        assertTrue(DesignDocumentValidator.isValid(doc))
        assertEquals(0, DesignDocumentValidator.validate(doc).size)
    }

    @Test
    fun testMissingAnchorNodeFails() {
        val doc = createValidDocument().copy(anchorNodeId = "nonexistent")
        assertFalse(DesignDocumentValidator.isValid(doc))
        val errors = DesignDocumentValidator.validate(doc)
        assertEquals(1, errors.size)
        assertEquals("anchorNodeId", errors[0].field)
    }

    @Test
    fun testAnchorNodeWrongCoordinatesFails() {
        val doc = createValidDocument().copy(
            nodes = listOf(
                Node(
                    id = "header",
                    type = NodeType.CONTAINER,
                    screenX = 10f,
                    screenY = 20f,
                    widthDp = 360f,
                    heightDp = 100f
                )
            )
        )
        assertFalse(DesignDocumentValidator.isValid(doc))
        val errors = DesignDocumentValidator.validate(doc)
        assertTrue(errors.any { it.field == "anchorNodeId" })
    }

    @Test
    fun testDuplicateNodeIdsFails() {
        val doc = createValidDocument().copy(
            nodes = listOf(
                Node(id = "header", type = NodeType.CONTAINER, screenX = 0f, screenY = 0f, widthDp = 360f, heightDp = 100f),
                Node(id = "header", type = NodeType.TEXT, screenX = 10f, screenY = 10f, widthDp = 100f, heightDp = 24f)
            )
        )
        assertFalse(DesignDocumentValidator.isValid(doc))
        val errors = DesignDocumentValidator.validate(doc)
        assertTrue(errors.any { it.message.contains("Duplicate") })
    }

    @Test
    fun testNegativeWidthFails() {
        val doc = createValidDocument().copy(
            nodes = listOf(
                Node(id = "header", type = NodeType.CONTAINER, screenX = 0f, screenY = 0f, widthDp = -10f, heightDp = 100f)
            )
        )
        assertFalse(DesignDocumentValidator.isValid(doc))
        val errors = DesignDocumentValidator.validate(doc)
        assertTrue(errors.any { it.message.contains("Width") })
    }
}
