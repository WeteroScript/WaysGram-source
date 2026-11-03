from __future__ import annotations

import argparse
from pathlib import Path


IMPORT_ANCHOR = "import org.telegram.messenger.UserObject;"
IMPORTS = """import org.telegram.messenger.UserObject;
import org.telegram.messenger.waysgram.WaysGramGiftCatalogSheet;
import org.telegram.messenger.waysgram.WaysGramSessionStore;
import org.telegram.messenger.waysgram.WaysGramLoginSheet;"""

VIEW_ANCHOR = """        starsHeaderView.addView(titleStarsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 4, 0, 4, 0));"""

VIEW_BLOCK = """        starsHeaderView.addView(titleStarsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 4, 0, 4, 0));

        // WaysGram's own catalog is separate from the signed-in Telegram account.
        final TextView waysGramCatalogButton = new TextView(context);
        waysGramCatalogButton.setText("Подарки WaysGram");
        waysGramCatalogButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        waysGramCatalogButton.setGravity(Gravity.CENTER);
        waysGramCatalogButton.setTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        waysGramCatalogButton.setPadding(dp(14), dp(8), dp(14), dp(8));
        waysGramCatalogButton.setOnClickListener(view -> {
            WaysGramSessionStore sessionStore = WaysGramSessionStore.getInstance(context);
            if (!sessionStore.hasSession()) {
                new WaysGramLoginSheet(context, sessionStore, resourcesProvider, () ->
                        new WaysGramGiftCatalogSheet(context, sessionStore, resourcesProvider).show()
                ).show();
                return;
            }
            new WaysGramGiftCatalogSheet(context, sessionStore, resourcesProvider).show();
        });
        starsHeaderView.addView(
                waysGramCatalogButton,
                LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                        Gravity.CENTER_HORIZONTAL, 0, 8, 0, 4)
        );"""


LAUNCH_IMPORT_ANCHOR = "import org.telegram.messenger.UserObject;"
LAUNCH_IMPORTS = """import org.telegram.messenger.UserObject;
import org.telegram.messenger.waysgram.WaysGramHomeFragment;
import org.telegram.messenger.waysgram.WaysGramLoginFragment;
import org.telegram.messenger.waysgram.WaysGramSessionStore;"""

LAUNCH_METHOD_ANCHOR = """    private BaseFragment getClientNotActivatedFragment() {
        if (LoginActivity.loadCurrentState(false, currentAccount).getInt("currentViewNum", 0) != 0) {
            return new LoginActivity();
        }
        return new IntroActivity();
    }"""

LAUNCH_METHOD_BLOCK = """    private BaseFragment getClientNotActivatedFragment() {
        if (WaysGramSessionStore.getInstance(this).hasSession()) {
            return new WaysGramHomeFragment();
        }
        return new WaysGramLoginFragment();
    }"""

LAUNCH_STACK_ANCHOR = """            if (!UserConfig.getInstance(currentAccount).isClientActivated()) {
                actionBarLayout.addFragmentToStack(getClientNotActivatedFragment());
            } else {
                MainTabsActivity mainTabsActivity = new MainTabsActivity();
                actionBarLayout.addFragmentToStack(mainTabsActivity);
            }"""

LAUNCH_STACK_BLOCK = """            actionBarLayout.addFragmentToStack(getClientNotActivatedFragment());"""


def patch_source(original: str) -> str:
    marker = "WaysGram's own catalog is separate from the signed-in Telegram account."
    if marker in original:
        return original
    if IMPORT_ANCHOR not in original:
        raise ValueError("Cannot find GiftSheet import anchor; upstream source changed.")
    if VIEW_ANCHOR not in original:
        raise ValueError("Cannot find GiftSheet header anchor; upstream source changed.")
    updated = original.replace(IMPORT_ANCHOR, IMPORTS, 1)
    return updated.replace(VIEW_ANCHOR, VIEW_BLOCK, 1)


def patch_launch_source(original: str) -> str:
    if "WaysGramSessionStore.getInstance(this).hasSession()" in original:
        return original
    if LAUNCH_IMPORT_ANCHOR not in original:
        raise ValueError("Cannot find LaunchActivity import anchor; upstream source changed.")
    if LAUNCH_METHOD_ANCHOR not in original:
        raise ValueError("Cannot find LaunchActivity initial-fragment method; upstream source changed.")
    if LAUNCH_STACK_ANCHOR not in original:
        raise ValueError("Cannot find LaunchActivity startup fragment selection; upstream source changed.")
    updated = original.replace(LAUNCH_IMPORT_ANCHOR, LAUNCH_IMPORTS, 1)
    updated = updated.replace(LAUNCH_METHOD_ANCHOR, LAUNCH_METHOD_BLOCK, 1)
    return updated.replace(LAUNCH_STACK_ANCHOR, LAUNCH_STACK_BLOCK, 1)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--android-root", required=True, type=Path)
    args = parser.parse_args()

    source = args.android_root / "TMessagesProj/src/main/java/org/telegram/ui/Gifts/GiftSheet.java"
    if not source.is_file():
        raise SystemExit(f"GiftSheet.java not found: {source}")

    original = source.read_text(encoding="utf-8")
    try:
        updated = patch_source(original)
    except ValueError as exception:
        raise SystemExit(str(exception)) from exception
    if updated != original:
        source.write_text(updated, encoding="utf-8")
        print("Applied WaysGram gift catalog entry to GiftSheet.java.")
    else:
        print("WaysGram gift catalog entry already applied.")

    launch_source = args.android_root / "TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java"
    if not launch_source.is_file():
        raise SystemExit(f"LaunchActivity.java not found: {launch_source}")
    launch_original = launch_source.read_text(encoding="utf-8")
    try:
        launch_updated = patch_launch_source(launch_original)
    except ValueError as exception:
        raise SystemExit(str(exception)) from exception
    if launch_updated != launch_original:
        launch_source.write_text(launch_updated, encoding="utf-8")
        print("Applied WaysGram initial login/home routing to LaunchActivity.java.")
    else:
        print("WaysGram initial login/home routing already applied.")


if __name__ == "__main__":
    main()
