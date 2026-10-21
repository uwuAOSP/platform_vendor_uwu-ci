#!/usr/bin/env bash
# SPDX-FileCopyrightText: The uwuAOSP Project
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
workspace_root="$(cd "$script_dir/../../.." && pwd)"
properties_file="$script_dir/platform.properties"
revision="$(sed -n 's/^androidxMavenRevision=//p' "$properties_file")"
destination="${1:-${AOSP_PREBUILTS_SDK:-$workspace_root/prebuilts/sdk}}"

if [[ -z "$revision" ]]; then
  echo "Missing androidxMavenRevision in $properties_file" >&2
  exit 1
fi

if [[ -e "$destination" ]]; then
  if git -C "$destination" rev-parse --is-inside-work-tree >/dev/null 2>&1 &&
      [[ "$(git -C "$destination" rev-parse HEAD)" == "$revision" ]] &&
      [[ -d "$destination/current/androidx/m2repository" ]] &&
      [[ -d "$destination/current/extras/material-design-x" ]]; then
    echo "AOSP prebuilts/sdk already available at $destination ($revision)"
    exit 0
  fi
  echo "Refusing to replace existing path: $destination" >&2
  exit 1
fi

mkdir -p "$(dirname "$destination")"
git init "$destination"
git -C "$destination" remote add origin https://android.googlesource.com/platform/prebuilts/sdk
git -C "$destination" sparse-checkout init --cone
git -C "$destination" sparse-checkout set \
  current/androidx/m2repository \
  current/extras/material-design-x
git -C "$destination" fetch --filter=blob:none --depth=1 origin "$revision"
git -C "$destination" checkout --detach FETCH_HEAD

if [[ ! -d "$destination/current/androidx/m2repository" ||
    ! -d "$destination/current/extras/material-design-x" ]]; then
  echo "AOSP AndroidX/Material Maven repositories missing from checkout: $destination" >&2
  exit 1
fi

echo "Checked out AOSP AndroidX/Material Maven artifacts at $revision into $destination"
