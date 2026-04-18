package com.clienttools.shared

import com.clienttools.shared.models.DesignDocument
import com.clienttools.shared.validation.DesignDocumentValidator
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class DesignDocumentIntegrationTest {

    @Test
    fun testLoadAndValidateExampleDocument() {
        // 这个测试验证示例 JSON 可以被正确反序列化和验证
        val exampleJson = """
        {
          "version": "1.0",
          "metadata": {
            "name": "Login Screen v1.0",
            "description": "User login page",
            "designerName": "Alice",
            "createdAt": "2026-04-18T10:00:00Z",
            "modifiedAt": "2026-04-18T14:30:00Z",
            "screenWidthDp": 360,
            "screenHeightDp": 800,
            "tags": ["authentication", "mobile"]
          },
          "anchorNodeId": "header",
          "nodes": [
            {
              "id": "header",
              "type": "CONTAINER",
              "screenX": 0.0,
              "screenY": 0.0,
              "widthDp": 360.0,
              "heightDp": 100.0,
              "attrs": {
                "type": "container",
                "paddingTop": 16.0,
                "paddingBottom": 16.0,
                "paddingLeft": 16.0,
                "paddingRight": 16.0
              },
              "customAttrs": {"backgroundColor": "#FF6200EE"}
            },
            {
              "id": "title",
              "type": "TEXT",
              "screenX": 16.0,
              "screenY": 20.0,
              "widthDp": 328.0,
              "heightDp": 60.0,
              "attrs": {
                "type": "text",
                "fontSize": 24.0,
                "color": "#FFFFFFFF",
                "fontWeight": "bold"
              },
              "customAttrs": {}
            }
          ]
        }
        """.trimIndent()

        val document = Json.decodeFromString(DesignDocument.serializer(), exampleJson)

        assertEquals("1.0", document.version)
        assertEquals("Login Screen v1.0", document.metadata.name)
        assertEquals("header", document.anchorNodeId)
        assertEquals(2, document.nodes.size)

        assertTrue(DesignDocumentValidator.isValid(document))
    }
}
