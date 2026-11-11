#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_ROOT="$ROOT/android/telegram-upstream"
OVERLAY_ROOT="$ROOT/android/overlay/TMessagesProj/src/main/java"
EXPECTED_COMMIT="3de37c8cfb039452be7398909461da6288babf15"
EXPECTED_VERSION_CODE="7237"
EXPECTED_VERSION_NAME="13.1.1"

cd "$ROOT"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Run this script from a WaysGram-source Git checkout." >&2
  exit 1
fi

git submodule sync --recursive
git submodule update --init --recursive --depth=1 android/telegram-upstream

if [[ ! -f "$ANDROID_ROOT/gradlew" || ! -f "$ANDROID_ROOT/gradle.properties" ]]; then
  echo "Telegram Android submodule is incomplete. Re-run: git submodule update --init --recursive" >&2
  exit 1
fi

ACTUAL_COMMIT="$(git -C "$ANDROID_ROOT" rev-parse HEAD)"
if [[ "$ACTUAL_COMMIT" != "$EXPECTED_COMMIT" ]]; then
  echo "Unexpected Telegram upstream commit: $ACTUAL_COMMIT" >&2
  echo "Expected pinned commit: $EXPECTED_COMMIT" >&2
  exit 1
fi

grep -qx "APP_VERSION_CODE=$EXPECTED_VERSION_CODE" "$ANDROID_ROOT/gradle.properties"
grep -qx "APP_VERSION_NAME=$EXPECTED_VERSION_NAME" "$ANDROID_ROOT/gradle.properties"

if [[ -d "$OVERLAY_ROOT" ]]; then
  while IFS= read -r -d '' source_file; do
    relative_path="${source_file#"$OVERLAY_ROOT"/}"
    target_file="$ANDROID_ROOT/TMessagesProj/src/main/java/$relative_path"
    mkdir -p "$(dirname "$target_file")"
    cp "$source_file" "$target_file"
    echo "Copied WaysGram service overlay: $relative_path"
  done < <(find "$OVERLAY_ROOT" -type f -print0)
fi

echo "Android source prepared."
echo "Upstream: Telegram $EXPECTED_VERSION_NAME ($EXPECTED_VERSION_CODE)"
echo "Pinned commit: $ACTUAL_COMMIT"
python3 "$ROOT/scripts/apply-android-patches.py" --android-root "$ANDROID_ROOT"

echo "Original Telegram startup, login screens, navigation, and GiftSheet remain unchanged."
echo "A dedicated WaysGram entry point is available from Settings; its login uses the separate WaysGram API session."
echo "No APK was built."
