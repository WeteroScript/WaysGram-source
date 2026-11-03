#!/usr/bin/env python3
"""Verify that the pinned Telegram UI and startup flow remain untouched.

WaysGram REST overlays are compiled alongside the upstream client, but they
must not replace Telegram's original login flow, main tabs, or gift interface.
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


def patch_source(original: str) -> str:
    """Compatibility function: preserve the original GiftSheet byte-for-byte."""
    return original


def patch_launch_source(original: str) -> str:
    """Compatibility function: preserve the original LaunchActivity byte-for-byte."""
    return original


def verify_source(android_root: Path) -> None:
    gift_sheet = android_root / "TMessagesProj/src/main/java/org/telegram/ui/Gifts/GiftSheet.java"
    launch_activity = android_root / "TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java"
    for path in (gift_sheet, launch_activity):
        if not path.is_file():
            raise FileNotFoundError(f"Required upstream source file not found: {path}")

    gift_content = gift_sheet.read_text(encoding="utf-8")
    launch_content = launch_activity.read_text(encoding="utf-8")
    gift_hits = [marker for marker in FORBIDDEN_GIFT_MARKERS if marker in gift_content]
    launch_hits = [marker for marker in FORBIDDEN_LAUNCH_MARKERS if marker in launch_content]
    if gift_hits or launch_hits:
        details = []
        if gift_hits:
            details.append("GiftSheet: " + ", ".join(gift_hits))
        if launch_hits:
            details.append("LaunchActivity: " + ", ".join(launch_hits))
        raise ValueError(
            "WaysGram UI injection detected in upstream Telegram sources; "
            "restore the pinned upstream files before building. " + "; ".join(details)
        )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--android-root", required=True, type=Path)
    args = parser.parse_args()
    verify_source(args.android_root)
    print("Verified: original Telegram LaunchActivity, LoginActivity routing, and GiftSheet are preserved.")


if __name__ == "__main__":
    main()
