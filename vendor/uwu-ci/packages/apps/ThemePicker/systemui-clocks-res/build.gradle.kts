// SPDX-FileCopyrightText: The uwuAOSP Project
// SPDX-License-Identifier: Apache-2.0

plugins {
    id("com.android.library")
}

val aospSourceRoot = providers.environmentVariable("AOSP_SOURCE_ROOT")
    .orNull
    ?.let { rootProject.file(it) }
    ?: providers.environmentVariable("AOSP_ROOT")
        .orNull
        ?.let { rootProject.file(it) }
    ?: rootProject.file("../../../../../../uwuaosp-17")
val clocksSource = aospSourceRoot.resolve("frameworks/base/packages/SystemUI/customization/clocks/common")

android {
    namespace = "com.android.systemui.customization.clocks"
    compileSdk {
        version = release(37) { minorApiLevel = 0 }
    }

    defaultConfig {
        minSdk = 37
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile(clocksSource.resolve("AndroidManifest.xml"))
            res.setSrcDirs(listOf(clocksSource.resolve("res")))
        }
    }
}
