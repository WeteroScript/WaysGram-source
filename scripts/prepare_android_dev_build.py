#!/usr/bin/env python3
"""Configure the WaysGram Android build without replacing Telegram's Firebase project.

Telegram remains the default Firebase app so its existing FCM token and delivery
continue to work. WaysGram is initialized as a secondary FirebaseApp at runtime.
"""
from __future__ import annotations

import argparse
import json
import os
import re
from pathlib import Path
from urllib.parse import urlsplit


WAYSGRAM_FIREBASE_PROJECT_ID = "waysgram-59956"
WAYSGRAM_FIREBASE_PACKAGE_NAME = "org.waysgram.messenger"


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
        r'^    private static final String DEFAULT_API_BASE_URL = .*;$',
        f'    private static final String DEFAULT_API_BASE_URL = "{value}";',
        content,
        "WaysGram default API base URL",
    )
    login_sheet.write_text(content, encoding="utf-8")


def configure_waysgram_firebase(android_root: Path) -> bool:
    """Generate secondary FirebaseOptions from the registered WaysGram Android app JSON."""
    raw_config = (os.getenv("FIREBASE_CONFIG", "").strip() or os.getenv("WAYSGRAM_FIREBASE_CONFIG_JSON", "").strip())
    required = os.getenv("WAYSGRAM_REQUIRE_FIREBASE_CONFIG", "").strip().lower() in {
        "1", "true", "yes"
    }
    if not raw_config:
        if required:
            raise ValueError(
                "FIREBASE_CONFIG is required for an Android APK build"
            )
        return False

    try:
        config = json.loads(raw_config)
    except json.JSONDecodeError as exc:
        raise ValueError("FIREBASE_CONFIG must contain valid Firebase JSON") from exc

    project_info = config.get("project_info")
    clients = config.get("client")
    if not isinstance(project_info, dict) or not isinstance(clients, list):
        raise ValueError("Firebase JSON is missing project_info or client")

    project_id = str(project_info.get("project_id") or "").strip()
    sender_id = str(project_info.get("project_number") or "").strip()
    if project_id != WAYSGRAM_FIREBASE_PROJECT_ID or not sender_id.isdigit():
        raise ValueError("Firebase JSON must belong to the WaysGram project waysgram-59956")

    matching = []
    for client in clients:
        client_info = client.get("client_info", {}) if isinstance(client, dict) else {}
        android_info = client_info.get("android_client_info", {}) if isinstance(client_info, dict) else {}
        if android_info.get("package_name") == WAYSGRAM_FIREBASE_PACKAGE_NAME:
            matching.append(client)

    if len(matching) != 1:
        raise ValueError(
            "Firebase JSON must contain exactly one Android client for "
            + WAYSGRAM_FIREBASE_PACKAGE_NAME
        )

    client_info = matching[0].get("client_info", {})
    app_id = str(client_info.get("mobilesdk_app_id") or "").strip()
    api_keys = matching[0].get("api_key", [])
    api_key = str(api_keys[0].get("current_key") or "").strip() if api_keys else ""
    if not app_id.startswith(f"1:{sender_id}:android:") or not api_key:
        raise ValueError("Firebase Android client is missing mobilesdk_app_id or current_key")

    def java_string(value: str) -> str:
        return json.dumps(value, ensure_ascii=True)

    java_source = f'''package org.telegram.messenger.waysgram;

/**
 * Build-generated public Firebase client options for WaysGram.
 * The Telegram default Firebase configuration remains untouched.
 */
public final class WaysGramFirebaseConfig {{
    public static final String APP_ID = {java_string(app_id)};
    public static final String API_KEY = {java_string(api_key)};
    public static final String PROJECT_ID = {java_string(project_id)};
    public static final String SENDER_ID = {java_string(sender_id)};

    private WaysGramFirebaseConfig() {{
    }}

    public static boolean isConfigured() {{
        return APP_ID.length() > 0 && API_KEY.length() > 0
                && PROJECT_ID.length() > 0 && SENDER_ID.length() > 0;
    }}
}}
'''
    output = (
        android_root
        / "TMessagesProj/src/main/java/org/telegram/messenger/waysgram/WaysGramFirebaseConfig.java"
    )
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(java_source, encoding="utf-8")
    return True


def configured_android_package() -> str:
    """Read the Android package from the existing Firebase config secret."""
    raw_config = (os.getenv("FIREBASE_CONFIG", "").strip() or os.getenv("WAYSGRAM_FIREBASE_CONFIG_JSON", "").strip())
    if not raw_config:
        return WAYSGRAM_FIREBASE_PACKAGE_NAME
    try:
        config = json.loads(raw_config)
    except json.JSONDecodeError as exc:
        raise ValueError("FIREBASE_CONFIG must contain valid Firebase JSON") from exc
    project_info = config.get("project_info")
    clients = config.get("client")
    if not isinstance(project_info, dict) or not isinstance(clients, list):
        raise ValueError("FIREBASE_CONFIG is missing project_info or client")
    packages = []
    for client in clients:
        client_info = client.get("client_info", {}) if isinstance(client, dict) else {}
        android_info = client_info.get("android_client_info", {}) if isinstance(client_info, dict) else {}
        package_name = str(android_info.get("package_name") or "").strip()
        if package_name:
            packages.append(package_name)
    if WAYSGRAM_FIREBASE_PACKAGE_NAME not in packages:
        raise ValueError(
            "FIREBASE_CONFIG must include the registered Android package "
            + WAYSGRAM_FIREBASE_PACKAGE_NAME
        )
    return WAYSGRAM_FIREBASE_PACKAGE_NAME


def configure_debug_application_id_suffix(android_root: Path) -> None:
    """Use the registered WaysGram package for AfatDebug, without Telegram's .beta suffix."""
    build_file = android_root / "TMessagesProj_App/build.gradle"
    if not build_file.is_file():
        raise FileNotFoundError(f"Android app Gradle file not found: {build_file}")

    content = build_file.read_text(encoding="utf-8")
    beta_pattern = re.compile(r'^([ \t]*)applicationIdSuffix "\.beta"$', re.MULTILINE)
    empty_pattern = re.compile(r'^[ \t]*applicationIdSuffix ""$', re.MULTILINE)
    beta_matches = beta_pattern.findall(content)
    empty_matches = empty_pattern.findall(content)

    if len(beta_matches) == 1 and len(empty_matches) == 0:
        content = beta_pattern.sub(r'\1applicationIdSuffix ""', content, count=1)
    elif len(beta_matches) == 0 and len(empty_matches) == 1:
        pass
    else:
        raise ValueError(
            "Expected exactly one debug .beta suffix or its already-configured empty value"
        )

    build_file.write_text(content, encoding="utf-8")


def configure_android_root(android_root: Path) -> None:
    if not (android_root / "gradlew").is_file():
        raise FileNotFoundError(f"Android Gradle wrapper not found in {android_root}")

    gradle_properties = android_root / "gradle.properties"
    content = gradle_properties.read_text(encoding="utf-8")
    content = replace_one(
        r"^APP_PACKAGE=.*$",
        f"APP_PACKAGE={configured_android_package()}",
        content,
        "Android application ID",
    )
    gradle_properties.write_text(content, encoding="utf-8")
    configure_debug_application_id_suffix(android_root)
    configure_default_api_base_url(android_root)
    firebase_configured = configure_waysgram_firebase(android_root)
    patch_string_resources(android_root)

    print(
        "Configured WaysGram application label while retaining Telegram's existing "
        "Firebase default app, API settings, permissions, and manifest declarations. "
        "WaysGram secondary FCM: "
        + ("configured." if firebase_configured else "not configured in this source-only run.")
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--android-root", required=True, type=Path)
    args = parser.parse_args()
    configure_android_root(args.android_root)


if __name__ == "__main__":
    main()
