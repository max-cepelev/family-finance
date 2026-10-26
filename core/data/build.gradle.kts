plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.familyfinance.data"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
    // Exported schemas, for migration tests.
    sourceSets["test"].assets.srcDir("$projectDir/schemas")
}

tasks.withType<Test>().configureEach {
    // Robolectric plus a full real-backup import does not fit the default 512 MB and stalls in GC.
    maxHeapSize = "2g"
    // A fresh JVM per test class: Robolectric sandboxes with a full import pile up and stall otherwise.
    forkEvery = 1
    // Optional end-to-end check against a real backup: -Pmmbak.path=/path/to/backup.mmbak
    systemProperty("mmbak.path", findProperty("mmbak.path") ?: System.getenv("MMBAK_PATH") ?: "")
    testLogging { events("passed", "skipped", "failed"); showStandardStreams = true }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(project(":core:domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
}
