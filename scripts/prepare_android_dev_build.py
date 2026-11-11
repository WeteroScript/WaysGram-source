#!/usr/bin/env python3
"""Configure the WaysGram application identity and optional PostHog SDK."""

from __future__ import annotations

import argparse
import json
import os
import re
from pathlib import Path
from urllib.parse import urlsplit


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
        dependency_pattern = re.compile(r"^dependencies \{$", flags=re.MULTILINE)
        content, count = dependency_pattern.subn(
            lambda match: match.group(0) + "\n    implementation 'com.posthog:posthog-android:3.+'",
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
            r'^\s*buildConfigField "String", "' + re.escape(field_name) + r'", .*?$',
            flags=re.MULTILINE,
        )
        content, count = field_pattern.subn(lambda _match: line, content, count=1)
        if count == 0:
            default_config_pattern = re.compile(r"^    defaultConfig \{$", flags=re.MULTILINE)
            content, inserted = default_config_pattern.subn(
                lambda match: match.group(0) + "\n" + line,
                content,
                count=1,
            )
            if inserted != 1:
                raise ValueError("Could not add PostHog BuildConfig fields to TMessagesProj")

    module_gradle.write_text(content, encoding="utf-8")



def configure_default_api_base_url(android_root: Path) -> None:
    value = os.getenv("WAYSGRAM_API_BASE_URL", "").strip().rstrip("/")
    if not value:
        return

    parsed = urlsplit(value)
    if (
        parsed.scheme not in {"http", "https"}
        or not parsed.netloc
        or parsed.path not in {"", "/"}
        or parsed.query
        or parsed.fragment
        or parsed.username
        or parsed.password
    ):
        raise ValueError("WAYSGRAM_API_BASE_URL must be an absolute HTTP(S) origin")

    login_sheet = (
        android_root
        / "TMessagesProj/src/main/java/org/telegram/messenger/waysgram/WaysGramLoginSheet.java"
    )
    if not login_sheet.is_file():
        raise FileNotFoundError(f"WaysGram login sheet not found: {login_sheet}")

    content = login_sheet.read_text(encoding="utf-8")
    content = replace_one(
        r'^    private static final String DEFAULT_API_BASE_URL = .*;    if not (android_root / "gradlew").is_file():
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
    configure_default_api_base_url(android_root)
    patch_string_resources(android_root)

    print(
        "Configured WaysGram application identity and optional PostHog Android SDK. "
        "PostHog capture is enabled only when POSTHOG_API_KEY and POSTHOG_HOST are set. "
        "WAYSGRAM_API_BASE_URL overrides the login screen API default when configured."
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--android-root", required=True, type=Path)
    args = parser.parse_args()
    configure_android_root(args.android_root)


if __name__ == "__main__":
    main()
,
        f"    private static final String DEFAULT_API_BASE_URL = {json.dumps(value)};",
        content,
        "WaysGram default API base URL",
    )
    login_sheet.write_text(content, encoding="utf-8")


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
    configure_posthog_module(android_root)
    patch_string_resources(android_root)

    print(
        "Configured WaysGram application identity and optional PostHog Android SDK. "
        "PostHog capture is enabled only when POSTHOG_API_KEY and POSTHOG_HOST are set."
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--android-root", required=True, type=Path)
    args = parser.parse_args()
    configure_android_root(args.android_root)


if __name__ == "__main__":
    main()
