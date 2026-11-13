from __future__ import annotations

import json
import os
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

from scripts.prepare_android_dev_build import configure_android_root, configure_waysgram_firebase


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
        (root / "TMessagesProj_App/build.gradle").write_text(
            'android {\n'
            '    buildTypes {\n'
            '        debug {\n'
            '            applicationIdSuffix ".beta"\n'
            '        }\n'
            '        standalone {\n'
            '            applicationIdSuffix ".web"\n'
            '        }\n'
            '    }\n'
            '}\n',
            encoding="utf-8",
        )
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
            before = [path.read_text(encoding="utf-8") for path in (runtime, manifest)]

            configure_android_root(root)

            self.assertEqual([path.read_text(encoding="utf-8") for path in (runtime, manifest)], before)
            gradle_build = build_file.read_text(encoding="utf-8")
            self.assertIn('applicationIdSuffix ""', gradle_build)
            self.assertNotIn('applicationIdSuffix ".beta"', gradle_build)
            self.assertIn('applicationIdSuffix ".web"', gradle_build)
            self.assertFalse((root / "TMessagesProj/src/main/res/drawable/waysgram_launcher.xml").exists())
            props = (root / "gradle.properties").read_text(encoding="utf-8")
            self.assertIn("APP_PACKAGE=org.waysgram.messenger", props)
            strings = (root / "TMessagesProj/src/main/res/values/strings.xml").read_text(encoding="utf-8")
            self.assertIn('<string name="AppName">WaysGram</string>', strings)
            self.assertIn('<string name="AppNameBeta">WaysGram Dev</string>', strings)

    def test_generates_waysgram_secondary_firebase_options(self) -> None:
        firebase_config = {
            "project_info": {
                "project_number": "123456789012",
                "project_id": "waysgram-59956",
            },
            "client": [{
                "client_info": {
                    "mobilesdk_app_id": "1:123456789012:android:abcdef012345",
                    "android_client_info": {"package_name": "org.waysgram.messenger"},
                },
                "api_key": [{"current_key": "AIzaExamplePublicClientKey"}],
            }],
        }
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.make_project(root)
            with patch.dict(os.environ, {
                "WAYSGRAM_FIREBASE_CONFIG_JSON": json.dumps(firebase_config),
            }, clear=False):
                self.assertTrue(configure_waysgram_firebase(root))
            generated = (
                root / "TMessagesProj/src/main/java/org/telegram/messenger/waysgram/"
                "WaysGramFirebaseConfig.java"
            ).read_text(encoding="utf-8")
            self.assertIn('PROJECT_ID = "waysgram-59956"', generated)
            self.assertIn('SENDER_ID = "123456789012"', generated)
            self.assertIn('APP_ID = "1:123456789012:android:abcdef012345"', generated)

    def test_firebase_config_rejects_wrong_registered_android_package(self) -> None:
        firebase_config = {
            "project_info": {
                "project_number": "123456789012",
                "project_id": "waysgram-59956",
            },
            "client": [{
                "client_info": {
                    "mobilesdk_app_id": "1:123456789012:android:abcdef012345",
                    "android_client_info": {"package_name": "org.telegram.messenger"},
                },
                "api_key": [{"current_key": "AIzaExamplePublicClientKey"}],
            }],
        }
        with tempfile.TemporaryDirectory() as temp:
            with patch.dict(os.environ, {
                "WAYSGRAM_FIREBASE_CONFIG_JSON": json.dumps(firebase_config),
            }, clear=False):
                with self.assertRaises(ValueError):
                    configure_waysgram_firebase(Path(temp))

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
