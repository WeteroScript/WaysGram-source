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


class GiftSheetPatcherTests(unittest.TestCase):
    def setUp(self) -> None:
        self.original = """import org.telegram.messenger.UserObject;

class GiftSheet {
    void build() {
        starsHeaderView.addView(titleStarsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 4, 0, 4, 0));
    }
}
"""

    def test_adds_catalog_entry_and_session_store_imports(self) -> None:
        updated = PATCHER.patch_source(self.original)
        self.assertIn("WaysGramGiftCatalogSheet", updated)
        self.assertIn("WaysGramSessionStore", updated)
        self.assertIn("Подарки WaysGram", updated)
        self.assertIn("WaysGramLoginSheet", updated)
        self.assertIn("new WaysGramLoginSheet(context, sessionStore, resourcesProvider).show()", updated)

    def test_patch_is_idempotent(self) -> None:
        once = PATCHER.patch_source(self.original)
        twice = PATCHER.patch_source(once)
        self.assertEqual(once, twice)
        self.assertEqual(twice.count("Подарки WaysGram"), 1)

    def test_upstream_anchor_mismatch_fails_loudly(self) -> None:
        with self.assertRaises(ValueError):
            PATCHER.patch_source("class GiftSheet {}")


if __name__ == "__main__":
    unittest.main()
