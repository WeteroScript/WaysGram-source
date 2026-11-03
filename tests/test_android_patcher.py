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
