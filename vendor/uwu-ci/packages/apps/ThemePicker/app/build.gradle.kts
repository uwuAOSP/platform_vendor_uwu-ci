// SPDX-FileCopyrightText: The uwuAOSP Project
// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

val aospSourceRoot = providers.environmentVariable("AOSP_SOURCE_ROOT")
    .orNull
    ?.let { rootProject.file(it) }
    ?: providers.environmentVariable("AOSP_ROOT")
        .orNull
        ?.let { rootProject.file(it) }
    ?: rootProject.file("../../../../../../uwuaosp-17")
val themePickerSource = aospSourceRoot.resolve("packages/apps/ThemePicker")
val renderscriptToolkitSource = aospSourceRoot.resolve("external/renderscript-intrinsics-replacement-toolkit")
val aospLibs = providers.environmentVariable("AOSP_SYSTEM_LIBS_DIR")
    .orNull
    ?.let { rootProject.file(it) }
    ?: rootProject.file("system_libs")

apply(from = rootProject.file("gradle/aosp-platform.gradle"))

android {
    // Keep the source-manifest/Studio namespace; Soong's package_name override sets the final ID.
    namespace = "com.google.android.apps.wallpaper"
    compileSdk {
        version = release(37) { minorApiLevel = 0 }
    }
    ndkVersion = "25.0.8775105"

    defaultConfig {
        // Android.bp overrides the source manifest package to this application ID.
        applicationId = "com.android.wallpaper"
        versionCode = 37
        versionName = "17"
        minSdk = 37
        targetSdk = 37

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_static",
                    "-DCMAKE_CXX_STANDARD=17",
                )
            }
        }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile(themePickerSource.resolve("AndroidManifest.xml"))
            java.setSrcDirs(listOf(themePickerSource.resolve("src_override")))
        }
    }

    externalNativeBuild {
        cmake {
            path = rootProject.file(
                renderscriptToolkitSource.resolve("renderscript-toolkit/src/main/cpp/CMakeLists.txt")
            )
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = false
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildTypes {
        getByName("release") {
            // Match ThemePicker's Soong optimize.enabled = false.
            isMinifyEnabled = false
            isShrinkResources = false
            // Deliberately leave signingConfig unset; Soong signs the imported APK.
        }
    }

    packaging {
        jniLibs.useLegacyPackaging = false
    }

    lint {
        disable += "ExpiredTargetSdkVersion"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.8.0-alpha01")
    implementation("com.google.dagger:hilt-android:2.57.2") {
        exclude(group = "jakarta.inject", module = "jakarta.inject-api")
        exclude(group = "com.google.code.findbugs", module = "jsr305")
        exclude(group = "javax.inject", module = "javax.inject")
    }
    kapt("com.google.dagger:hilt-compiler:2.57.2")
    implementation(project(":themepicker-lib"))
    implementation(project(":wallpaperpicker2"))
    implementation(project(":wallpaperpicker2-manifest"))

    // AOSP-built runtime libraries, packaged as JARs/AARs from the same platform revision.
    implementation(fileTree(aospLibs.resolve("runtime")) {
        include("*.jar", "*.aar")
    })

    compileOnly(fileTree(aospLibs.resolve("compileOnly")) {
        include("*.jar")
    })
}

kapt {
    correctErrorTypes = true
}

hilt {
    enableAggregatingTask = false
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

val verifyAospBundle by tasks.registering {
    group = "verification"
    description = "Checks the matching AOSP dependency bundle required by this standalone build."

    doLast {
        val required = listOf(
            aospLibs.resolve("runtime"),
            aospLibs.resolve("compileOnly"),
        )
        val missing = required.filterNot { it.exists() }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing AOSP dependency bundle inputs:\n" +
                    missing.joinToString("\n") { " - ${it.path}" }
            )
        }

        val packagedLibraries = aospLibs.resolve("runtime")
            .listFiles()
            ?.any { it.extension == "jar" || it.extension == "aar" } == true
        if (!packagedLibraries) {
            throw GradleException("No runtime JAR/AAR dependencies found in ${aospLibs.resolve("runtime")}")
        }
    }
}

tasks.matching { it.name.startsWith("assemble") }.configureEach {
    dependsOn(verifyAospBundle)
}
