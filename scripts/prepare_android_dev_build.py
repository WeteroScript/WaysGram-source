#!/usr/bin/env python3
"""Configure the WaysGram application identity without modifying Telegram UI or services.

The upstream Telegram API settings, Google service integration, debug manifest,
launcher resources, permissions, and runtime behavior are left untouched.
"""
from __future__ import annotations

import argparse
import json
import os
import re
from pathlib import Path


def replace_one(pattern: str, replacement: str, content: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, content, count=1, flags=re.MULTILINE)
    if count != 1:
        raise ValueError(f"Expected exactly one {label} setting; found {count}")
    return updated


def replace_string_value(content: str, name: str, value: str) -> tuple[str, bool]:
    pattern = re.compile(
        '(<string[ ]+name="' + re.escape(name) + '">).*?(</string>)',
        flags=re.DOTALL,
    )
    updated, count = pattern.subn(
        lambda match: match.group(1) + value + match.group(2),
        content,
        count=1,
    )
    return updated, count == 1


def patch_string_resources(android_root: Path) -> None:
    values_dirs = android_root / "TMessagesProj/src/main/res"
    files = list(values_dirs.glob("values*/strings.xml"))
    if not files:
        raise FileNotFoundError(f"No strings.xml resources found under {values_dirs}")
    found_app_name = False
    found_beta_name = False
    for path in files:
        content = path.read_text(encoding="utf-8")
        content, found = replace_string_value(content, "AppName", "WaysGram")
        found_app_name = found_app_name or found
        content, found = replace_string_value(content, "AppNameBeta", "WaysGram Dev")
        found_beta_name = found_beta_name or found
        path.write_text(content, encoding="utf-8")
    if not found_app_name or not found_beta_name:
        raise ValueError("AppName/AppNameBeta resources were not found")



def configure_posthog_module(android_root: Path) -> None:
    module_gradle = android_root / "TMessagesProj/build.gradle"
    if not module_gradle.is_file():
        raise FileNotFoundError(f"Telegram library Gradle file not found: {module_gradle}")

    content = module_gradle.read_text(encoding="utf-8")
    if "com.posthog:posthog-android:" not in content:
        dependency_pattern = re.compile(r"^dependencies\\s*\\{\\s*$", flags=re.MULTILINE)
        content, count = dependency_pattern.subn(
            lambda match: match.group(0) + "\\n    implementation 'com.posthog:posthog-android:3.+'",
            content,
            count=1,
        )
        if count != 1:
            raise ValueError("Could not add the PostHog Android SDK dependency")

    values = {
        "WAYS_GRAM_POSTHOG_API_KEY": os.getenv("POSTHOG_API_KEY", "").strip(),
        "WAYS_GRAM_POSTHOG_HOST": os.getenv("POSTHOG_HOST", "").strip(),
    }
    for field_name, value in values.items():
        line = (
            f'        buildConfigField "String", "{field_name}", '
            f'{json.dumps(json.dumps(value))}'
        )
        field_pattern = re.compile(
            r'^\\s*buildConfigField "String", "' + re.escape(field_name) + r'", .*?android_root: Path) -> None:
    if not (android_root / "gradlew").is_file():
        raise FileNotFoundError(f"Android Gradle wrapper not found in {android_root}")

    gradle_properties = android_root / "gradle.properties"
    content = gradle_properties.read_text(encoding="utf-8")
    content = replace_one(
        r"^APP_PACKAGE=.*$",
        "APP_PACKAGE=org.telegram.messenger",
        content,
        "Android application ID",
    )
    gradle_properties.write_text(content, encoding="utf-8")
    configure_posthog_module(android_root)
    patch_string_resources(android_root)

    print(
        "Configured WaysGram application label while retaining the upstream Firebase-compatible package. "
        "Original Telegram UI, launcher icon, API settings, Google services, "
        "permissions, and manifest declarations were preserved."
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--android-root", required=True, type=Path)
    args = parser.parse_args()
    configure_android_root(args.android_root)


if __name__ == "__main__":
    main()
,
            flags=re.MULTILINE,
        )
        content, count = field_pattern.subn(lambda _match: line, content, count=1)
        if count == 0:
            default_config_pattern = re.compile(r"^    defaultConfig \\{\\s*$", flags=re.MULTILINE)
            content, inserted = default_config_pattern.subn(
                lambda match: match.group(0) + "\\n" + line,
                content,
                count=1,
            )
            if inserted != 1:
                raise ValueError("Could not add PostHog BuildConfig fields to TMessagesProj")

    module_gradle.write_text(content, encoding="utf-8")

def configure_android_root(android_root: Path) -> None:
    if not (android_root / "gradlew").is_file():
        raise FileNotFoundError(f"Android Gradle wrapper not found in {android_root}")

    gradle_properties = android_root / "gradle.properties"
    content = gradle_properties.read_text(encoding="utf-8")
    content = replace_one(
        r"^APP_PACKAGE=.*$",
        "APP_PACKAGE=org.telegram.messenger",
        content,
        "Android application ID",
    )
    gradle_properties.write_text(content, encoding="utf-8")
    patch_string_resources(android_root)

    print(
        "Configured WaysGram application label while retaining the upstream Firebase-compatible package. "
        "Original Telegram UI, launcher icon, API settings, Google services, "
        "permissions, and manifest declarations were preserved."
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--android-root", required=True, type=Path)
    args = parser.parse_args()
    configure_android_root(args.android_root)


if __name__ == "__main__":
    main()
