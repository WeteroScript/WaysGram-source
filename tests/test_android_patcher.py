from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PATCHER_PATH = ROOT / "scripts" / "apply-android-patches.py"
SPEC = importlib.util.spec_from_file_location("apply_android_patches", PATCHER_PATH)
assert SPEC is not None and SPEC.loader is not None
PATCHER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PATCHER)


class GiftCatalogContractTests(unittest.TestCase):
    def test_android_requests_the_full_telegram_gift_catalog(self) -> None:
        client_path = (
            ROOT
            / "android/overlay/TMessagesProj/src/main/java/org/telegram/messenger/waysgram/"
            / "WaysGramGiftCatalogClient.java"
        )
        source = client_path.read_text(encoding="utf-8")
        self.assertIn(
            'requestJson(baseUrl + "/api/telegram/gifts?all=1")',
            source,
        )



class WaysGramGiftCatalogContractTests(unittest.TestCase):
    def test_gift_stickers_load_lazily_for_visible_rows(self) -> None:
        sheet_path = (
            ROOT
            / "android/overlay/TMessagesProj/src/main/java/org/telegram/messenger/waysgram/"
            / "WaysGramGiftCatalogSheet.java"
        )
        source = sheet_path.read_text(encoding="utf-8")
        self.assertIn("requestSticker(gift)", source)
        self.assertIn("private final Set<String> requestedStickers = new HashSet<>()", source)
        self.assertIn("!requestedStickers.add(gift.id)", source)
        self.assertNotIn("loadStaticStickers(gifts)", source)


class WaysGramDirectoryContractTests(unittest.TestCase):
    def test_channels_support_private_creation_and_invite_joining(self) -> None:
        directory_path = (
            ROOT
            / "android/overlay/TMessagesProj/src/main/java/org/telegram/messenger/waysgram/"
            / "WaysGramDirectoryFragment.java"
        )
        source = directory_path.read_text(encoding="utf-8")
        self.assertIn('privateChannelCheckBox.setText("Приватный канал")', source)
        self.assertIn('payload.put("is_public", isPublic)', source)
        self.assertIn('apiClient.post("/api/channels/join"', source)
        self.assertIn('response.optString("invite_link", "")', source)
        self.assertIn("setPrimaryClip", source)

    def test_private_channel_visibility_accepts_numeric_json_flag(self) -> None:
        directory_path = (
            ROOT
            / "android/overlay/TMessagesProj/src/main/java/org/telegram/messenger/waysgram/"
            / "WaysGramDirectoryFragment.java"
        )
        source = directory_path.read_text(encoding="utf-8")
        self.assertIn("private static boolean isPublicChannel(JSONObject item)", source)
        self.assertIn("value instanceof Number", source)
        self.assertIn("((Number) value).intValue() != 0", source)
        self.assertIn("if (isPublicChannel(item))", source)

    def test_saved_messages_open_the_current_users_own_chat(self) -> None:
        directory_path = (
            ROOT
            / "android/overlay/TMessagesProj/src/main/java/org/telegram/messenger/waysgram/"
            / "WaysGramDirectoryFragment.java"
        )
        chat_path = (
            ROOT
            / "android/overlay/TMessagesProj/src/main/java/org/telegram/messenger/waysgram/"
            / "WaysGramChatFragment.java"
        )
        directory_source = directory_path.read_text(encoding="utf-8")
        chat_source = chat_path.read_text(encoding="utf-8")
        self.assertIn('"Избранное"', directory_source)
        self.assertIn('apiClient.get("/api/me"', directory_source)
        self.assertIn('recipient != peerId', chat_source)


class SourcePreservationTests(unittest.TestCase):
    def test_gift_sheet_source_is_unchanged(self) -> None:
        original = "import org.telegram.messenger.UserObject;\nclass GiftSheet {}\n"
        self.assertEqual(PATCHER.patch_source(original), original)

    def test_launch_activity_source_is_unchanged(self) -> None:
        original = (
            "class LaunchActivity {\n"
            "    return new LoginActivity();\n"
            "    return new IntroActivity();\n"
            "    MainTabsActivity mainTabsActivity = new MainTabsActivity();\n"
            "}\n"
        )
        self.assertEqual(PATCHER.patch_launch_source(original), original)

    def test_verification_fails_when_upstream_files_are_missing(self) -> None:
        with self.assertRaises(FileNotFoundError):
            PATCHER.verify_source(Path("/definitely/missing/android"))


if __name__ == "__main__":
    unittest.main()
