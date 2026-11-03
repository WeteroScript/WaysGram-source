#!/usr/bin/env python3
"""Configure the WaysGram application identity without modifying Telegram UI or services.

The upstream Telegram API settings, Google service integration, debug manifest,
launcher resources, permissions, and runtime behavior are left untouched.
"""
from __future__ import annotations

import argparse
import re
from pathlib import Path


def replace_one(pattern: str, replacement: str, content: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, content, count=1, flags=re.MULTILINE)
    if count != 1:
        raise ValueError(f"Expected exactly one {label} setting; found {count}")
    return updated


def patch_string_resources(android_root: Path) -> None:
    values_dirs = android_root / "TMessagesProj/src/main/res"
    files = list(values_dirs.glob("values*/strings.xml"))
    if not files:
        raise FileNotFoundError(f"No strings.xml resources found under {values_dirs}")
    found_app_name = False
    found_beta_name = False
    for path in files:
        content = path.read_text(encoding="utf-8")
        if '<string name="AppName">' in content:
            content, count = re.subn(
                r'(<string\\s+name="AppName">).*?(</string>)',
                r'\\1WaysGram\\2',
                content,
                count=1,
                flags=re.DOTALL,
            )
            found_app_name = found_app_name or count == 1
        if '<string name="AppNameBeta">' in content:
            content, count = re.subn(
                r'(<string\\s+name="AppNameBeta">).*?(</string>)',
                r'\\1WaysGram Dev\\2',
                content,
                count=1,
                flags=re.DOTALL,
            )
            found_beta_name = found_beta_name or count == 1
        path.write_text(content, encoding="utf-8")
    if not found_app_name or not found_beta_name:
        raise ValueError("AppName/AppNameBeta resources were not found")


def configure_android_root(android_root: Path) -> None:
    if not (android_root / "gradlew").is_file():
        raise FileNotFoundError(f"Android Gradle wrapper not found in {android_root}")

    gradle_properties = android_root / "gradle.properties"
    content = gradle_properties.read_text(encoding="utf-8")
    content = replace_one(
        r"^APP_PACKAGE=.*$",
        "APP_PACKAGE=org.waysgram.messenger",
        content,
        "Android application ID",
    )
    gradle_properties.write_text(content, encoding="utf-8")
    patch_string_resources(android_root)

    print(
        "Configured WaysGram application label and package. "
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
