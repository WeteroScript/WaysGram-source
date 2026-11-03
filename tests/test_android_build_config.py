from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from scripts.prepare_android_dev_build import configure_android_root


class AndroidDevBuildConfigurationTests(unittest.TestCase):
    def make_project(self, root: Path) -> None:
        (root / "gradlew").write_text("#!/bin/sh\n", encoding="utf-8")
        (root / "TMessagesProj_App").mkdir()
        (root / "TMessagesProj/src/main/java/org/telegram/messenger").mkdir(parents=True)
        (root / "TMessagesProj/src/main/res/values").mkdir(parents=True)
        (root / "TMessagesProj/config/debug").mkdir(parents=True)
        (root / "gradle.properties").write_text(
            "APP_VERSION_CODE=1\nAPP_PACKAGE=org.telegram.messenger\n",
            encoding="utf-8",
        )
        (root / "TMessagesProj_App/build.gradle").write_text(
            "apply plugin: 'com.android.application'\n"
            "apply plugin: 'com.google.gms.google-services'\n",
            encoding="utf-8",
        )
        (root / "TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java").write_text(
            "public static int APP_ID = 4;\n"
            'public static String APP_HASH = "upstream";\n'
            'public static String SAFETYNET_KEY = "AIzaSy-secret";\n'
            'public static String PLAYSTORE_APP_URL = "https://example.invalid";\n'
            'public static String HUAWEI_STORE_URL = "https://example.invalid";\n'
            'public static String GOOGLE_AUTH_CLIENT_ID = "client";\n'
            'public static String HUAWEI_APP_ID = "123";\n'
            "public static boolean CHECK_UPDATES = true;\n"
            "public static boolean IS_BILLING_UNAVAILABLE = false;\n"
            "public static boolean SUPPORTS_PASSKEYS = true;\n",
            encoding="utf-8",
        )
        (root / "TMessagesProj/src/main/res/values/strings.xml").write_text(
            '<resources><string name="AppName">Telegram</string>'
            '<string name="AppNameBeta">Telegram Beta</string></resources>',
            encoding="utf-8",
        )
        manifest = (
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
            '<uses-permission android:name="com.google.android.c2dm.permission.RECEIVE"/>'
            '<application android:icon="@mipmap/ic_launcher" android:roundIcon="@mipmap/ic_launcher_round">'
            '<meta-data android:name="com.google.android.maps.v2.API_KEY" android:value="AIzaSy-test"/>'
            '<service android:name="org.telegram.messenger.GcmPushListenerService"><intent-filter/></service>'
            '</application></manifest>'
        )
        for name in ("AndroidManifest.xml", "AndroidManifest_SDK23.xml"):
            (root / "TMessagesProj/config/debug" / name).write_text(manifest, encoding="utf-8")

    def test_debug_build_is_branded_and_does_not_embed_upstream_keys(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.make_project(root)
            configure_android_root(root)

            props = (root / "gradle.properties").read_text(encoding="utf-8")
            self.assertIn("APP_PACKAGE=org.waysgram.messenger", props)

            app_gradle = (root / "TMessagesProj_App/build.gradle").read_text(encoding="utf-8")
            self.assertNotIn("google-services", app_gradle)

            build_vars = (root / "TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java").read_text(encoding="utf-8")
            self.assertIn("APP_ID = 0", build_vars)
            self.assertIn('APP_HASH = ""', build_vars)
            self.assertNotIn("AIzaSy", build_vars)
            self.assertNotIn("upstream", build_vars)

            strings = (root / "TMessagesProj/src/main/res/values/strings.xml").read_text(encoding="utf-8")
            self.assertIn('<string name="AppName">WaysGram</string>', strings)
            self.assertIn('<string name="AppNameBeta">WaysGram Dev</string>', strings)

            for name in ("AndroidManifest.xml", "AndroidManifest_SDK23.xml"):
                content = (root / "TMessagesProj/config/debug" / name).read_text(encoding="utf-8")
                self.assertNotIn("AIzaSy", content)
                self.assertNotIn("GcmPushListenerService", content)
                self.assertIn('@drawable/waysgram_launcher', content)

            self.assertTrue((root / "TMessagesProj/src/main/res/drawable/waysgram_launcher.xml").is_file())

    def test_configuration_is_idempotent(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.make_project(root)
            configure_android_root(root)
            configure_android_root(root)
            self.assertTrue((root / "TMessagesProj/src/main/res/drawable/waysgram_launcher.xml").is_file())


if __name__ == "__main__":
    unittest.main()
