#!/usr/bin/env bash
# SPDX-FileCopyrightText: The uwuAOSP Project
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
workspace_root="$(cd "$script_dir/../../.." && pwd)"
revisions_file="$workspace_root/SOURCE_REVISIONS"
destination="${1:-${AOSP_SOURCE_ROOT:-$workspace_root/aosp-sources}}"

if [[ -e "$destination" ]]; then
  echo "Refusing to replace existing path: $destination" >&2
  exit 1
fi

mkdir -p "$destination"

checkout_repo() {
  local path="$1" revision="$2" url
  case "$path" in
    packages/apps/ThemePicker)
      url="https://github.com/uwuAOSP/platform_packages_apps_ThemePicker.git"
      ;;
    packages/apps/WallpaperPicker2)
      url="https://github.com/uwuAOSP/platform_packages_apps_WallpaperPicker2.git"
      ;;
    frameworks/base)
      url="https://github.com/uwuAOSP/platform_frameworks_base.git"
      ;;
    frameworks/libs/systemui)
      url="https://android.googlesource.com/platform/frameworks/libs/systemui"
      ;;
    external/renderscript-intrinsics-replacement-toolkit)
      url="https://android.googlesource.com/platform/external/renderscript-intrinsics-replacement-toolkit"
      ;;
    *)
      echo "Unsupported source path in $revisions_file: $path" >&2
      exit 1
      ;;
  esac

  local repo_dir="$destination/$path"
  mkdir -p "$(dirname "$repo_dir")"
  git init -q "$repo_dir"
  git -C "$repo_dir" remote add origin "$url"

  case "$path" in
    frameworks/base)
      git -C "$repo_dir" sparse-checkout init --cone
      git -C "$repo_dir" sparse-checkout set \
        packages/SettingsLib/SettingsTheme/res \
        packages/SettingsLib/CollapsingToolbarBaseActivity/res \
        packages/SystemUI/customization/clocks/common
      ;;
    frameworks/libs/systemui)
      git -C "$repo_dir" sparse-checkout init --cone
      git -C "$repo_dir" sparse-checkout set dynamiccolors/res
      ;;
    external/renderscript-intrinsics-replacement-toolkit)
      git -C "$repo_dir" sparse-checkout init --cone
      git -C "$repo_dir" sparse-checkout set renderscript-toolkit/src/main/cpp
      ;;
  esac

  git -C "$repo_dir" fetch --filter=blob:none --depth=1 origin "$revision"
  git -C "$repo_dir" checkout --detach FETCH_HEAD
  echo "Checked out $path at $revision"
}

if [[ ! -f "$revisions_file" ]]; then
  echo "Missing source revision list: $revisions_file" >&2
  exit 1
fi

while IFS='=' read -r path revision; do
  [[ -z "$path" || "$path" == \#* || "$path" == manifest ]] && continue
  if [[ -z "${revision:-}" ]]; then
    echo "Malformed source revision entry: $path" >&2
    exit 1
  fi
  checkout_repo "$path" "$revision"
done < "$revisions_file"
