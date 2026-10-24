import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin: no Android SDK, so the model, importer and (later) sync can be reused by a web version.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    // Optional end-to-end check against a real Money Manager backup:
    // ./gradlew :core:domain:test -Pmmbak.path=/path/to/backup.mmbak
    systemProperty("mmbak.path", findProperty("mmbak.path") ?: System.getenv("MMBAK_PATH") ?: "")
    testLogging { events("passed", "skipped", "failed"); showStandardStreams = true }
}
