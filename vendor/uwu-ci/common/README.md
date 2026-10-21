<!--
SPDX-FileCopyrightText: The uwuAOSP Project
SPDX-License-Identifier: Apache-2.0
-->

# Shared Android platform libraries

Apps built by the isolated Gradle projects use the platform `framework.jar` and `framework-res.apk`
from `platforms/`. Record their SHA-256 values in `platform.properties`. The JAR is compile-only;
never package it into an APK. The resource APK supplies platform resources to AAPT2 and is not
packaged as an application dependency.

The `uwu-17.0` branch is pinned to one Android platform build. Replace both platform files and update
their hashes together when that branch's platform inputs change. App-specific AOSP libraries remain
in each app's `system_libs` bundle.

AndroidX Maven artifacts are resolved from `prebuilts/sdk/current/androidx/m2repository` instead
of being copied into every app bundle. `platform.properties` pins the `prebuilts/sdk` checkout;
`checkout-aosp-sdk.sh` sparsely checks out that repository path for CI.
