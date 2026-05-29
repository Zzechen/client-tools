plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
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

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.Zzechen"
                artifactId = "client-tools-noop"
                version = project.properties["CLIENT_TOOLS_VERSION"] as String
            }
        }
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.core)
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
}
