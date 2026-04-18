plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.clienttools.sdk"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)

    // Nanohttpd for HTTP server
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Kotlin serialization
    implementation(libs.kotlinx.serialization.json)

    // Testing
    testImplementation(kotlin("test"))
}
