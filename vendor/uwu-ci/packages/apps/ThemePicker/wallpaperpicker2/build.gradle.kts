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

val sourceRootOverride = providers.environmentVariable("AOSP_SOURCE_ROOT")
    .orNull
    ?.let { rootProject.file(it) }
val aospRoot = providers.environmentVariable("AOSP_ROOT")
    .orNull
    ?.let { rootProject.file(it) }
    ?: rootProject.file("../../../../../../uwuaosp-17")
val sourceRoot = sourceRootOverride ?: aospRoot
val frameworkSourceRoot = sourceRoot
val wallpaperPickerSource = sourceRoot.resolve("packages/apps/WallpaperPicker2")
val aospLibs = providers.environmentVariable("AOSP_SYSTEM_LIBS_DIR")
    .orNull
    ?.let { rootProject.file(it) }
    ?: rootProject.file("system_libs")
val wallpaperDestinationSource = wallpaperPickerSource.resolve(
    "src/com/android/wallpaper/picker/customization/shared/model/WallpaperDestination.kt",
)
val standaloneSourceDir = layout.buildDirectory.dir("generated/standalone-src")
val dynamicColorsSource = frameworkSourceRoot.resolve("frameworks/libs/systemui/dynamiccolors/res")
val settingsThemeRes = frameworkSourceRoot.resolve("frameworks/base/packages/SettingsLib/SettingsTheme/res")
val collapsingToolbarRes = frameworkSourceRoot.resolve(
    "frameworks/base/packages/SettingsLib/CollapsingToolbarBaseActivity/res",
)
val frameworkResApk = providers.environmentVariable("AOSP_FRAMEWORK_RES")
    .orNull
    ?.let { rootProject.file(it) }
    ?: rootProject.file("../../../common/platforms/framework-res.apk")
val standaloneDynamicColorsRes = layout.buildDirectory.dir("generated/standalone-res/dynamiccolors")
val prepareStandaloneSources by tasks.registering {
    inputs.dir(wallpaperPickerSource.resolve("src"))
    outputs.dir(standaloneSourceDir)
    doLast {
        val sourceRoot = wallpaperPickerSource.resolve("src")
        val outputRoot = standaloneSourceDir.get().asFile
        sourceRoot.walkTopDown().filter { it.isFile }.forEach { source ->
            val output = outputRoot.resolve(source.relativeTo(sourceRoot).path)
            output.parentFile.mkdirs()
            if (source == wallpaperDestinationSource) {
                output.writeText(
                    source.readText()
                        .replace("import android.app.WallpaperManager.SetWallpaperFlags\n", "")
                        .replace(Regex("@SetWallpaperFlags\\s*"), ""),
                )
            } else {
                source.copyTo(output, overwrite = true)
            }
        }
    }
}
val prepareStandaloneDynamicColors by tasks.registering {
    inputs.dir(dynamicColorsSource)
    inputs.file(frameworkResApk)
    outputs.dir(standaloneDynamicColorsRes)
    doLast {
        val outputRoot = standaloneDynamicColorsRes.get().asFile
        val referencedColors = mutableSetOf<String>()
        dynamicColorsSource.walkTopDown().filter { it.isFile }.forEach { source ->
            val output = outputRoot.resolve(source.relativeTo(dynamicColorsSource).path)
            output.parentFile.mkdirs()
            val contents = source.readText()
            Regex("@androidprv:color/([A-Za-z0-9_]+)").findAll(contents)
                .forEach { referencedColors += it.groupValues[1] }
            output.writeText(contents.replace("@androidprv:color/", "@color/"))
        }

        val sdkRoot = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
            ?: throw GradleException("ANDROID_HOME or ANDROID_SDK_ROOT is required")
        val aapt2 = file("$sdkRoot/build-tools/37.0.0/aapt2")
        if (!aapt2.isFile || !frameworkResApk.isFile) {
            throw GradleException("Missing AAPT2 or target framework-res.apk for private color extraction")
        }
        val dump = ProcessBuilder(aapt2.absolutePath, "dump", "resources", frameworkResApk.absolutePath)
            .redirectErrorStream(true)
            .start()
        val dumpText = dump.inputStream.bufferedReader().readText()
        if (dump.waitFor() != 0) {
            throw GradleException("AAPT2 could not read target framework-res.apk")
        }
        val colorValues = mutableMapOf<String, String>()
        var currentColor: String? = null
        dumpText.lineSequence().forEach { line ->
            Regex("resource \\S+ color/([A-Za-z0-9_]+)").find(line)?.let {
                currentColor = it.groupValues[1]
            }
            if (currentColor in referencedColors) {
                Regex("\\(\\) (#[0-9a-fA-F]{6,8})").find(line)?.let {
                    colorValues[currentColor!!] = it.groupValues[1]
                }
            }
        }
        val missingColors = referencedColors - colorValues.keys
        if (missingColors.isNotEmpty()) {
            throw GradleException("Missing private framework colors: ${missingColors.sorted().joinToString()}")
        }
        val valuesDir = outputRoot.resolve("values")
        valuesDir.mkdirs()
        valuesDir.resolve("standalone-private-colors.xml").writeText(
            "<resources>\n" + colorValues.toSortedMap().entries.joinToString("\n") {
                "    <color name=\"${it.key}\">${it.value}</color>"
            } + "\n</resources>\n",
        )
    }
}

apply(from = rootProject.file("gradle/aosp-platform.gradle"))

android {
    namespace = "com.android.wallpaper"
    compileSdk {
        version = release(37) { minorApiLevel = 0 }
    }
    defaultConfig {
        minSdk = 37
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile(wallpaperPickerSource.resolve("AndroidManifest-empty.xml"))
            java.setSrcDirs(listOf(standaloneSourceDir))
            res.setSrcDirs(
                listOf(
                    wallpaperPickerSource.resolve("res"),
                    settingsThemeRes,
                    collapsingToolbarRes,
                    standaloneDynamicColorsRes,
                ),
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

tasks.named("preBuild").configure {
    dependsOn(prepareStandaloneSources)
    dependsOn(prepareStandaloneDynamicColors)
}

dependencies {
    implementation("com.google.dagger:hilt-android:2.57.2") {
        exclude(group = "jakarta.inject", module = "jakarta.inject-api")
        exclude(group = "com.google.code.findbugs", module = "jsr305")
        exclude(group = "javax.inject", module = "javax.inject")
    }
    kapt("com.google.dagger:hilt-compiler:2.57.2")
    // Versions match prebuilts/sdk/current/androidx/m2repository in the target AOSP build.
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.8.0-alpha01")
    implementation("androidx.cardview:cardview:1.1.0-alpha01")
    implementation("androidx.constraintlayout:constraintlayout:2.3.0-alpha01")
    implementation("androidx.core:core-animation:1.0.0")
    implementation("androidx.exifinterface:exifinterface:1.4.0-rc01")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.11.0-alpha03")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx-android:2.11.0-alpha03")
    implementation("androidx.recyclerview:recyclerview:1.5.0-alpha01")
    implementation("androidx.slice:slice-view:1.1.0-alpha02")
    implementation("androidx.transition:transition-ktx:1.7.0-rc01")
    implementation("androidx.viewpager2:viewpager2:1.2.0-alpha01")
    implementation("androidx.navigation:navigation-ui-ktx:2.10.0-alpha03")
    implementation("androidx.navigation:navigation-fragment-ktx:2.10.0-alpha03")
    implementation("androidx.preference:preference:1.3.0-alpha01")
    implementation("androidx.slice:slice-core:1.1.0-alpha02")
    implementation("com.google.android.material:material:1.14.0-alpha99")
    implementation("androidx.activity:activity-compose:1.13.0")
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
