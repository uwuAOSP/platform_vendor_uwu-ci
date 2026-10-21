// SPDX-FileCopyrightText: The uwuAOSP Project
// SPDX-License-Identifier: Apache-2.0

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val aospPrebuiltsSdk = providers.environmentVariable("AOSP_PREBUILTS_SDK")
    .orNull
    ?.let { file(it) }
    ?: settingsDir.resolve("../../../../../prebuilts/sdk")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            name = "aospAndroidx"
            url = uri(aospPrebuiltsSdk.resolve("current/androidx/m2repository"))
            // AOSP sometimes publishes only the Android variant or omits older transitive
            // metadata/artifacts. Resolve those from Google's repository as a fallback.
            metadataSources {
                gradleMetadata()
                mavenPom()
                artifact()
            }
        }
        maven {
            name = "aospMaterial"
            url = uri(aospPrebuiltsSdk.resolve("current/extras/material-design-x"))
            content {
                includeGroup("com.google.android.material")
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "ThemePickerStandalone"
include(":app", ":themepicker-lib", ":wallpaperpicker2", ":wallpaperpicker2-manifest", ":systemui-clocks-res")
