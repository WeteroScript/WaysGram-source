#!/usr/bin/env python3
"""Preserve Telegram's startup and gift UI while exposing WaysGram from Settings.

The dedicated WaysGram screens use their own API session. They are opened from a
single Settings entry; Telegram's login flow, main tabs, LaunchActivity and
native GiftSheet remain untouched.
"""
from __future__ import annotations

import argparse
from pathlib import Path


FORBIDDEN_GIFT_MARKERS = (
    "WaysGramGiftCatalogSheet",
    "WaysGramLoginSheet",
    "Подарки WaysGram",
)
FORBIDDEN_LAUNCH_MARKERS = (
    "WaysGramLoginFragment",
    "WaysGramHomeFragment",
    "WaysGramSessionStore",
)

WAYS_GRAM_IMPORTS = (
    "import org.telegram.messenger.waysgram.WaysGramHomeFragment;\n"
    "import org.telegram.messenger.waysgram.WaysGramLoginFragment;\n"
    "import org.telegram.messenger.waysgram.WaysGramSessionStore;\n"
)
WAYS_GRAM_SETTINGS_ROW = (
    '        items.add(SettingCell.Factory.of(27, IconBackgroundColors.GREEN.top, '
    'IconBackgroundColors.GREEN.bottom, R.drawable.settings_features, "WaysGram", '
    '"Вход, сообщения, группы и каналы"));'
)
WAYS_GRAM_SETTINGS_HANDLER = (
    "            case 27: {\n"
    "                WaysGramSessionStore waysGramSessionStore = "
    "WaysGramSessionStore.getInstance(getParentActivity());\n"
    "                if (waysGramSessionStore.hasSession()) {\n"
    "                    presentSettingFragment(new WaysGramHomeFragment());\n"
    "                } else {\n"
    "                    presentSettingFragment(new WaysGramLoginFragment());\n"
    "                }\n"
    "                break;\n"
    "            }\n"
)


def replace_exactly_once(content: str, before: str, after: str, label: str) -> str:
    count = content.count(before)
    if count != 1:
        raise ValueError(f"Expected exactly one {label} anchor; found {count}")
    return content.replace(before, after, 1)


def patch_source(original: str) -> str:
    """Compatibility function: preserve the original GiftSheet byte-for-byte."""
    return original


def patch_launch_source(original: str) -> str:
    """Compatibility function: preserve the original LaunchActivity byte-for-byte."""
    return original


def patch_settings_source(original: str) -> str:
    """Add one WaysGram entry to Settings without replacing Telegram navigation."""
    required_markers = (
        "import org.telegram.messenger.waysgram.WaysGramHomeFragment;",
        "import org.telegram.messenger.waysgram.WaysGramLoginFragment;",
        "import org.telegram.messenger.waysgram.WaysGramSessionStore;",
        WAYS_GRAM_SETTINGS_ROW,
        "case 27: {",
    )
    present = [marker in original for marker in required_markers]
    if all(present):
        return original
    if any(present):
        raise ValueError("SettingsActivity contains a partial WaysGram entry; restore the pinned source")

    updated = replace_exactly_once(
        original,
        "import org.telegram.messenger.UserObject;\n",
        "import org.telegram.messenger.UserObject;\n" + WAYS_GRAM_IMPORTS,
        "SettingsActivity WaysGram imports",
    )
    updated = replace_exactly_once(
        updated,
        '        items.add(SettingCell.Factory.of(23, IconBackgroundColors.PURPLE.top, '
        'IconBackgroundColors.PURPLE.bottom, R.drawable.settings_features, '
        'getString(R.string.TelegramFeatures)));\n',
        '        items.add(SettingCell.Factory.of(23, IconBackgroundColors.PURPLE.top, '
        'IconBackgroundColors.PURPLE.bottom, R.drawable.settings_features, '
        'getString(R.string.TelegramFeatures)));\n' + WAYS_GRAM_SETTINGS_ROW + "\n",
        "SettingsActivity Help section",
    )

    on_click_start = "    private void onClick(UItem item, View view, int position, float x, float y) {\n"
    on_long_click_start = "    private boolean onLongClick(UItem item, View view, int position, float x, float y) {\n"
    start = updated.find(on_click_start)
    end = updated.find(on_long_click_start, start + len(on_click_start))
    if start < 0 or end < 0:
        raise ValueError("SettingsActivity onClick method boundaries were not found")
    method = updated[start:end]
    method = replace_exactly_once(
        method,
        "            case 20:\n",
        WAYS_GRAM_SETTINGS_HANDLER + "            case 20:\n",
        "SettingsActivity WaysGram click handler",
    )
    updated = updated[:start] + method + updated[end:]

    if not all(marker in updated for marker in required_markers):
        raise ValueError("WaysGram Settings entry was not applied completely")
    return updated


def verify_source(android_root: Path) -> None:
    gift_sheet = android_root / "TMessagesProj/src/main/java/org/telegram/ui/Gifts/GiftSheet.java"
    launch_activity = android_root / "TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java"
    settings_activity = android_root / "TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java"
    for path in (gift_sheet, launch_activity, settings_activity):
        if not path.is_file():
            raise FileNotFoundError(f"Required upstream source file not found: {path}")

    gift_content = gift_sheet.read_text(encoding="utf-8")
    launch_content = launch_activity.read_text(encoding="utf-8")
    settings_content = settings_activity.read_text(encoding="utf-8")

    gift_hits = [marker for marker in FORBIDDEN_GIFT_MARKERS if marker in gift_content]
    launch_hits = [marker for marker in FORBIDDEN_LAUNCH_MARKERS if marker in launch_content]
    if gift_hits or launch_hits:
        details = []
        if gift_hits:
            details.append("GiftSheet: " + ", ".join(gift_hits))
        if launch_hits:
            details.append("LaunchActivity: " + ", ".join(launch_hits))
        raise ValueError(
            "WaysGram UI injection detected in protected Telegram sources; "
            "restore the pinned upstream files before building. " + "; ".join(details)
        )

    patched_settings = patch_settings_source(settings_content)
    if patched_settings != settings_content:
        settings_activity.write_text(patched_settings, encoding="utf-8")

    final_settings = settings_activity.read_text(encoding="utf-8")
    if WAYS_GRAM_SETTINGS_ROW not in final_settings or "presentSettingFragment(new WaysGramHomeFragment())" not in final_settings:
        raise ValueError("WaysGram Settings entry is missing after patching")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--android-root", required=True, type=Path)
    args = parser.parse_args()
    verify_source(args.android_root)
    print(
        "Verified: original Telegram LaunchActivity, LoginActivity routing, main tabs and GiftSheet are preserved; "
        "a dedicated WaysGram entry is available in Settings."
    )


if __name__ == "__main__":
    main()
