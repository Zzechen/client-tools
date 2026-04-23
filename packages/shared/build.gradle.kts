plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.clienttools.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// 生成 JSON Schema 到 mcp 和 skill/preprocess 目录
tasks.register("generateSchema") {
    doLast {
        val schemaContent = """
{
  "title": "Client Tools Shared Types",
  "description": "Shared data structures for Android/iOS SDK and MCP",
  "definitions": {
    "NodeType": {
      "type": "string",
      "enum": ["TEXT", "IMAGE", "LIST", "CONTAINER"]
    },
    "TextAttrs": {
      "type": "object",
      "properties": {
        "type": { "const": "text" },
        "fontSize": { "type": "number" },
        "color": { "type": "string" },
        "fontWeight": { "type": "string" }
      },
      "required": ["fontSize", "color", "fontWeight"]
    },
    "ImageAttrs": {
      "type": "object",
      "properties": {
        "type": { "const": "image" },
        "scaleType": { "type": "string", "default": "fitCenter" }
      }
    },
    "ListAttrs": {
      "type": "object",
      "properties": {
        "type": { "const": "list" },
        "itemSpacing": { "type": "number" },
        "orientation": { "type": "string", "enum": ["VERTICAL", "HORIZONTAL"] }
      },
      "required": ["itemSpacing", "orientation"]
    },
    "ContainerAttrs": {
      "type": "object",
      "properties": {
        "type": { "const": "container" },
        "paddingTop": { "type": "number" },
        "paddingBottom": { "type": "number" },
        "paddingLeft": { "type": "number" },
        "paddingRight": { "type": "number" }
      },
      "required": ["paddingTop", "paddingBottom", "paddingLeft", "paddingRight"]
    },
    "NodeAttrs": {
      "oneOf": [
        { "${'$'}ref": "#/definitions/TextAttrs" },
        { "${'$'}ref": "#/definitions/ImageAttrs" },
        { "${'$'}ref": "#/definitions/ListAttrs" },
        { "${'$'}ref": "#/definitions/ContainerAttrs" }
      ]
    },
    "Node": {
      "type": "object",
      "properties": {
        "id": { "type": "string" },
        "type": { "${'$'}ref": "#/definitions/NodeType" },
        "screenX": { "type": "number" },
        "screenY": { "type": "number" },
        "widthDp": { "type": "number" },
        "heightDp": { "type": "number" },
        "attrs": { "${'$'}ref": "#/definitions/NodeAttrs" }
      },
      "required": ["id", "type", "screenX", "screenY", "widthDp", "heightDp"]
    },
    "DeviceInfo": {
      "type": "object",
      "properties": {
        "screenWidthDp": { "type": "integer" },
        "screenHeightDp": { "type": "integer" },
        "density": { "type": "number" },
        "orientation": { "type": "string", "enum": ["portrait", "landscape"] }
      },
      "required": ["screenWidthDp", "screenHeightDp", "density", "orientation"]
    },
    "ApiResponse": {
      "type": "object",
      "properties": {
        "code": { "type": "integer" },
        "message": { "type": "string" },
        "sdkVersion": { "type": "integer" },
        "device": { "${'$'}ref": "#/definitions/DeviceInfo" },
        "data": { "type": "object" }
      },
      "required": ["code", "message", "sdkVersion", "device"]
    },
    "ViewProps": {
      "type": "object",
      "properties": {
        "marginTopDiffDp": { "type": "number" },
        "marginBottomDiffDp": { "type": "number" },
        "marginLeftDiffDp": { "type": "number" },
        "marginRightDiffDp": { "type": "number" },
        "paddingTopDiffDp": { "type": "number" },
        "paddingBottomDiffDp": { "type": "number" },
        "paddingLeftDiffDp": { "type": "number" },
        "paddingRightDiffDp": { "type": "number" },
        "widthDp": { "type": "number" },
        "heightDp": { "type": "number" }
      }
    },
    "ModifyViewRequest": {
      "type": "object",
      "properties": {
        "id": { "type": "string" },
        "props": { "${'$'}ref": "#/definitions/ViewProps" }
      },
      "required": ["id", "props"]
    },
    "PageChangedEvent": {
      "type": "object",
      "properties": {
        "event": { "type": "string", "default": "page_changed" },
        "activityName": { "type": "string" },
        "timestamp": { "type": "string" }
      },
      "required": ["activityName", "timestamp"]
    }
  }
}
        """.trimIndent()

        val projectRoot = project.rootProject.projectDir.parentFile
        val mcpDir = File(projectRoot, "mcp/schema")
        val skillDir = File(projectRoot, "skill/preprocess/schema")

        mcpDir.mkdirs()
        skillDir.mkdirs()

        val mcpSchema = File(mcpDir, "shared.json")
        val skillSchema = File(skillDir, "shared.json")

        mcpSchema.writeText(schemaContent)
        skillSchema.writeText(schemaContent)

        println("[OK] Schema 生成成功")
        println("  - $mcpSchema")
        println("  - $skillSchema")
    }
}
