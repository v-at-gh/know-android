import org.gradle.api.tasks.util.PatternFilterable

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    kotlin("plugin.serialization") version "2.4.20"
}

android {
    namespace = "com.example.quizapp"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        applicationId = "com.example.quizapp"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
        }
        getByName("release") {
            // Exclude raw datasets for release builds to keep APK small.
            // The app will rely on assets/qap/*.qap instead.
            (assets as PatternFilterable).exclude("datasets/**", "knowledge/**")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.compose.foundation.layout)
    val lifecycle_version = "2.11.0"
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:${lifecycle_version}")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.room3.runtime)
    ksp(libs.androidx.room3.compiler)
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.17")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.latex.renderer)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}

val contentReleaseProp = providers.gradleProperty("contentRelease")

tasks.register<Copy>("syncContentAssets") {
    group = "development"
    description = "Copies content release datasets and knowledge into app/src/main/assets"

    val releasePath = contentReleaseProp.orNull
    if (releasePath != null) {
        val sourceDir = file(releasePath)
        from(sourceDir)
    } else {
        from(file("$projectDir/src/main/assets"))
    }
    into(file("$projectDir/src/main/assets"))

    doFirst {
        val path = contentReleaseProp.orNull
            ?: throw GradleException("Property 'contentRelease' is required. Usage: ./gradlew syncContentAssets -PcontentRelease=<path>")
        val dir = file(path)
        if (!dir.exists()) {
            throw GradleException("Content release directory does not exist: $dir")
        }
        val assetsDir = file("$projectDir/src/main/assets")
        delete(file("$assetsDir/datasets"))
        delete(file("$assetsDir/knowledge"))
    }
}

