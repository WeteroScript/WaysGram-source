from __future__ import annotations

import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from prepare_android_dev_build import configure_posthog_module


class PostHogAndroidBuildTests(unittest.TestCase):
    def _module(self, root: Path) -> Path:
        module = root / "TMessagesProj" / "build.gradle"
        module.parent.mkdir(parents=True)
        module.write_text(
            "dependencies {\n}\n\nandroid {\n    defaultConfig {\n        minSdkVersion 23\n    }\n}\n",
            encoding="utf-8",
        )
        return module

    def test_sdk_and_environment_fields_are_injected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            module = self._module(Path(temporary))
            with patch.dict(
                os.environ,
                {
                    "POSTHOG_API_KEY": "phc_test_project",
                    "POSTHOG_HOST": "https://eu.i.posthog.com",
                },
                clear=True,
            ):
                configure_posthog_module(Path(temporary))
            content = module.read_text(encoding="utf-8")
            self.assertIn("implementation 'com.posthog:posthog-android:3.+'", content)
            self.assertIn('buildConfigField "String", "WAYS_GRAM_POSTHOG_API_KEY", "\\"phc_test_project\\""', content)
            self.assertIn('buildConfigField "String", "WAYS_GRAM_POSTHOG_HOST", "\\"https://eu.i.posthog.com\\""', content)

    def test_repeated_configuration_updates_values_without_duplicate_dependency(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            module = self._module(Path(temporary))
            with patch.dict(os.environ, {"POSTHOG_API_KEY": "phc_first", "POSTHOG_HOST": "https://eu.i.posthog.com"}, clear=True):
                configure_posthog_module(Path(temporary))
            with patch.dict(os.environ, {"POSTHOG_API_KEY": "phc_second", "POSTHOG_HOST": "https://eu.i.posthog.com"}, clear=True):
                configure_posthog_module(Path(temporary))
            content = module.read_text(encoding="utf-8")
            self.assertEqual(content.count("com.posthog:posthog-android:"), 1)
            self.assertIn('buildConfigField "String", "WAYS_GRAM_POSTHOG_API_KEY", "\\"phc_second\\""', content)
            self.assertNotIn("phc_first", content)

    def test_missing_environment_values_are_kept_empty(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            module = self._module(Path(temporary))
            with patch.dict(os.environ, {}, clear=True):
                configure_posthog_module(Path(temporary))
            content = module.read_text(encoding="utf-8")
            self.assertIn('buildConfigField "String", "WAYS_GRAM_POSTHOG_API_KEY", "\\"\\""', content)
            self.assertIn('buildConfigField "String", "WAYS_GRAM_POSTHOG_HOST", "\\"\\""', content)


if __name__ == "__main__":
    unittest.main()
