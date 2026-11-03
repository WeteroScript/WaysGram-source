from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from scripts.prepare_android_dev_build import configure_android_root


class AndroidDevBuildConfigurationTests(unittest.TestCase):
    def make_project(self, root: Path) -> None:
        (root / "gradlew").write_text("#!/bin/sh\n", encoding="utf-8")
        (root / "TMessagesProj/src/main/res/values").mkdir(parents=True)
        (root / "TMessagesProj/src/main/java/org/telegram/messenger").mkdir(parents=True)
        (root / "TMessagesProj/config/debug").mkdir(parents=True)
        (root / "gradle.properties").write_text(
            "APP_VERSION_CODE=1\nAPP_PACKAGE=org.telegram.messenger\n",
            encoding="utf-8",
        )
        (root / "TMessagesProj/src/main/res/values/strings.xml").write_text(
            '<resources><string name="AppName">Telegram</string>'
            '<string name="AppNameBeta">Telegram Beta</string></resources>',
            encoding="utf-8",
        )
        (root / "TMessagesProj_App").mkdir()
        (root / "TMessagesProj_App/build.gradle").write_text("unchanged build file\n", encoding="utf-8")
        (root / "TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java").write_text(
            "unchanged runtime settings\n", encoding="utf-8"
        )
        (root / "TMessagesProj/config/debug/AndroidManifest.xml").write_text(
            "unchanged manifest and icon\n", encoding="utf-8"
        )

    def test_keeps_original_runtime_files_and_icon(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.make_project(root)
            runtime = root / "TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java"
            build_file = root / "TMessagesProj_App/build.gradle"
            manifest = root / "TMessagesProj/config/debug/AndroidManifest.xml"
            before = [path.read_text(encoding="utf-8") for path in (runtime, build_file, manifest)]

            configure_android_root(root)

            self.assertEqual([path.read_text(encoding="utf-8") for path in (runtime, build_file, manifest)], before)
            self.assertFalse((root / "TMessagesProj/src/main/res/drawable/waysgram_launcher.xml").exists())
            props = (root / "gradle.properties").read_text(encoding="utf-8")
            self.assertIn("APP_PACKAGE=org.telegram.messenger", props)
            strings = (root / "TMessagesProj/src/main/res/values/strings.xml").read_text(encoding="utf-8")
            self.assertIn('<string name="AppName">WaysGram</string>', strings)
            self.assertIn('<string name="AppNameBeta">WaysGram Dev</string>', strings)

    def test_is_idempotent(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.make_project(root)
            configure_android_root(root)
            first = (root / "TMessagesProj/src/main/res/values/strings.xml").read_text(encoding="utf-8")
            configure_android_root(root)
            self.assertEqual(first, (root / "TMessagesProj/src/main/res/values/strings.xml").read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
