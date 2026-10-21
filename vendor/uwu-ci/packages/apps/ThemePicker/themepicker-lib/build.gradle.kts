// SPDX-FileCopyrightText: The uwuAOSP Project
// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
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
val aospLibs = providers.environmentVariable("AOSP_SYSTEM_LIBS_DIR")
    .orNull
    ?.let { rootProject.file(it) }
    ?: rootProject.file("system_libs")

apply(from = rootProject.file("gradle/aosp-platform.gradle"))

android {
    namespace = "com.android.themepicker"
    compileSdk {
        version = release(37) { minorApiLevel = 0 }
    }
    defaultConfig {
        minSdk = 37
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile(themePickerSource.resolve("AndroidManifest-empty.xml"))
            java.setSrcDirs(listOf(themePickerSource.resolve("src")))
            res.setSrcDirs(
                listOf(
                    themePickerSource.resolve("res"),
                    themePickerSource.resolve("res_override"),
                )
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

}

dependencies {
    implementation("com.google.dagger:hilt-android:2.57.2") {
        exclude(group = "jakarta.inject", module = "jakarta.inject-api")
        exclude(group = "com.google.code.findbugs", module = "jsr305")
        exclude(group = "javax.inject", module = "javax.inject")
    }
    kapt("com.google.dagger:hilt-compiler:2.57.2")
    implementation(project(":wallpaperpicker2"))
    implementation(project(":systemui-clocks-res"))

    // Versions match prebuilts/sdk/current/androidx/m2repository in the target AOSP build.
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.11.0-alpha03")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx-android:2.11.0-alpha03")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0-alpha03")
    implementation("androidx.recyclerview:recyclerview:1.5.0-alpha01")
    implementation("androidx.constraintlayout:constraintlayout:2.3.0-alpha01")
    implementation("androidx.core:core-animation:1.0.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("com.google.android.material:material:1.14.0-alpha99")
    implementation("androidx.compose.foundation:foundation-android:1.12.0-alpha01")
    implementation("androidx.compose.foundation:foundation-layout-android:1.12.0-alpha01")
    implementation("androidx.compose.material3:material3-android:1.5.0-alpha16")
    implementation("androidx.compose.runtime:runtime-android:1.12.0-alpha01")
    implementation("androidx.compose.ui:ui-android:1.12.0-alpha01")
    implementation("androidx.compose.ui:ui-tooling-android:1.12.0-alpha01")
    implementation("androidx.lifecycle:lifecycle-runtime-compose-android:2.11.0-alpha03")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose-android:2.11.0-alpha03")

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
