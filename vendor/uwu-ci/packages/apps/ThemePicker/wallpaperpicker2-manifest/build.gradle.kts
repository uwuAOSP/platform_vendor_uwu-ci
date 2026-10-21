// SPDX-FileCopyrightText: The uwuAOSP Project
// SPDX-License-Identifier: Apache-2.0

plugins {
    id("com.android.library")
}

val sourceRoot = providers.environmentVariable("AOSP_SOURCE_ROOT")
    .orNull
    ?.let { rootProject.file(it) }
    ?: providers.environmentVariable("AOSP_ROOT")
        .orNull
        ?.let { rootProject.file(it) }
    ?: rootProject.file("../../../../../../uwuaosp-17")
val wallpaperPickerSource = sourceRoot.resolve("packages/apps/WallpaperPicker2")
val standaloneManifest = layout.buildDirectory.file("generated/manifests/AndroidManifest.xml")
val generateStandaloneManifest by tasks.registering {
    val output = standaloneManifest.get().asFile
    inputs.file(wallpaperPickerSource.resolve("AndroidManifest.xml"))
    outputs.file(output)
    doLast {
        val sourceManifest = wallpaperPickerSource.resolve("AndroidManifest.xml").readText()
        val packageAttribute = Regex("(<manifest\\b[^>]*?)\\s+package=\"[^\"]*\"")
        val sanitized = packageAttribute.replaceFirst(sourceManifest, "\$1")
        output.parentFile.mkdirs()
        output.writeText(sanitized)
    }
}

android {
    namespace = "com.android.wallpaper.manifest"
    compileSdk {
        version = release(37) { minorApiLevel = 0 }
    }
    defaultConfig {
        minSdk = 37
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile(standaloneManifest)
            java.setSrcDirs(emptyList<String>())
            res.setSrcDirs(emptyList<String>())
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(generateStandaloneManifest)
}
