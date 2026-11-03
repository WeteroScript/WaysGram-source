#!/usr/bin/env python3
"""Prepare a safe, branded WaysGram-only debug build in the Telegram source checkout.

This modifies only the disposable Android submodule working tree used by CI. It
never writes upstream Telegram credentials into the WaysGram repository.
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


def patch_build_vars(path: Path) -> None:
    content = path.read_text(encoding="utf-8")
    replacements = [
        (r"^\s*public static int APP_ID\s*=\s*[^;]+;", "    public static int APP_ID = 0;", "Telegram API ID"),
        (r'^\s*public static String APP_HASH\s*=\s*"[^"]*";', '    public static String APP_HASH = "";', "Telegram API hash"),
        (r'^\s*public static String SAFETYNET_KEY\s*=\s*"[^"]*";', '    public static String SAFETYNET_KEY = "";', "SafetyNet key"),
        (r'^\s*public static String PLAYSTORE_APP_URL\s*=\s*"[^"]*";', '    public static String PLAYSTORE_APP_URL = "";', "Play Store URL"),
        (r'^\s*public static String HUAWEI_STORE_URL\s*=\s*"[^"]*";', '    public static String HUAWEI_STORE_URL = "";', "Huawei store URL"),
        (r'^\s*public static String GOOGLE_AUTH_CLIENT_ID\s*=\s*"[^"]*";', '    public static String GOOGLE_AUTH_CLIENT_ID = "";', "Google OAuth client ID"),
        (r'^\s*public static String HUAWEI_APP_ID\s*=\s*"[^"]*";', '    public static String HUAWEI_APP_ID = "";', "Huawei app ID"),
        (r"^\s*public static boolean CHECK_UPDATES\s*=\s*[^;]+;", "    public static boolean CHECK_UPDATES = false;", "Telegram update check"),
        (r"^\s*public static boolean IS_BILLING_UNAVAILABLE\s*=\s*[^;]+;", "    public static boolean IS_BILLING_UNAVAILABLE = true;", "store billing"),
        (r"^\s*public static boolean SUPPORTS_PASSKEYS\s*=\s*[^;]+;", "    public static boolean SUPPORTS_PASSKEYS = false;", "official-app passkeys"),
    ]
    for pattern, replacement, label in replacements:
        content = replace_one(pattern, replacement, content, label)
    if "014b35b6184100b085b0d0572f9b5103" in content or "AIzaSy" in content:
        raise ValueError("An upstream API credential remains in BuildVars.java")
    path.write_text(content, encoding="utf-8")


def patch_app_gradle(path: Path) -> None:
    content = path.read_text(encoding="utf-8")
    content = re.sub(
        r"^\s*apply plugin: ['\"]com\.google\.gms\.google-services['\"]\s*\n",
        "",
        content,
        flags=re.MULTILINE,
    )
    if "com.google.gms.google-services" in content:
        raise ValueError("Google Services plugin could not be disabled for the isolated debug build")
    path.write_text(content, encoding="utf-8")


def patch_manifest(path: Path) -> None:
    if not path.is_file():
        return
    content = path.read_text(encoding="utf-8")
    content = re.sub(
        r'\s*<meta-data\s+android:name="com\.google\.android\.maps\.v2\.API_KEY"[^>]*/>',
        "",
        content,
    )
    content = re.sub(
        r'\s*<service\b[^>]*android:name="org\.telegram\.messenger\.GcmPushListenerService"[^>]*>.*?</service>',
        "",
        content,
        flags=re.DOTALL,
    )
    content = re.sub(
        r'^\s*<uses-permission\s+android:name="com\.google\.android\.c2dm\.permission\.RECEIVE"\s*/>\s*\n?',
        "",
        content,
        flags=re.MULTILINE,
    )
    content = re.sub(
        r'^\s*<uses-permission\s+android:name="com\.google\.android\.providers\.gsf\.permission\.READ_GSERVICES"\s*/>\s*\n?',
        "",
        content,
        flags=re.MULTILINE,
    )
    if "AIzaSy" in content or "GcmPushListenerService" in content:
        raise ValueError(f"Upstream Google key or Telegram push service remains in {path}")
    if path.name in {"AndroidManifest.xml", "AndroidManifest_SDK23.xml"} and path.parent.name == "debug":
        content = content.replace('android:icon="@mipmap/ic_launcher"', 'android:icon="@drawable/waysgram_launcher"')
        content = content.replace('android:roundIcon="@mipmap/ic_launcher_round"', 'android:roundIcon="@drawable/waysgram_launcher"')
    path.write_text(content, encoding="utf-8")


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
                r'(<string\s+name="AppName">).*?(</string>)',
                r'\1WaysGram\2',
                content,
                count=1,
                flags=re.DOTALL,
            )
            found_app_name = found_app_name or count == 1
        if '<string name="AppNameBeta">' in content:
            content, count = re.subn(
                r'(<string\s+name="AppNameBeta">).*?(</string>)',
                r'\1WaysGram Dev\2',
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

    patch_app_gradle(android_root / "TMessagesProj_App/build.gradle")
    patch_build_vars(android_root / "TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java")
    patch_string_resources(android_root)

    manifest_paths = [
        android_root / "TMessagesProj/src/main/AndroidManifest.xml",
        android_root / "TMessagesProj/config/debug/AndroidManifest.xml",
        android_root / "TMessagesProj/config/debug/AndroidManifest_SDK23.xml",
    ]
    for manifest in manifest_paths:
        patch_manifest(manifest)

    icon_dir = android_root / "TMessagesProj/src/main/res/drawable"
    icon_dir.mkdir(parents=True, exist_ok=True)
    icon = icon_dir / "waysgram_launcher.xml"
    icon.write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="48dp" android:height="48dp"\n'
        '    android:viewportWidth="48" android:viewportHeight="48">\n'
        '    <path android:fillColor="#0F766E" android:pathData="M8,0h32c4.42,0 8,3.58 8,8v32c0,4.42 -3.58,8 -8,8H8c-4.42,0 -8,-3.58 -8,-8V8c0,-4.42 3.58,-8 8,-8z"/>\n'
        '    <path android:fillColor="#FFFFFF" android:pathData="M12,14c0,-3.31 2.69,-6 6,-6h12c3.31,0 6,2.69 6,6v13c0,3.31 -2.69,6 -6,6H22l-8,6v-9.5c-1.25,-1.08 -2,-2.68 -2,-4.5z"/>\n'
        '    <path android:fillColor="#0F766E" android:pathData="M18,18h12v2H18zM18,23h12v2H18zM18,28h7v2h-7z"/>\n'
        '</vector>\n',
        encoding="utf-8",
    )

    if "APP_PACKAGE=org.waysgram.messenger" not in gradle_properties.read_text(encoding="utf-8"):
        raise ValueError("WaysGram application ID was not applied")
    if "WaysGram Dev" not in (android_root / "TMessagesProj/src/main/res/values/strings.xml").read_text(encoding="utf-8"):
        raise ValueError("WaysGram debug app name was not applied")
    print("Configured isolated WaysGram debug identity, stripped upstream service credentials, and set a WaysGram launcher icon.")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--android-root", required=True, type=Path)
    args = parser.parse_args()
    configure_android_root(args.android_root)


if __name__ == "__main__":
    main()
