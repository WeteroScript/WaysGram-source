from __future__ import annotations

import argparse
from pathlib import Path


IMPORT_ANCHOR = "import org.telegram.messenger.UserObject;"
IMPORTS = """import org.telegram.messenger.UserObject;
import org.telegram.messenger.waysgram.WaysGramGiftCatalogSheet;
import org.telegram.messenger.waysgram.WaysGramSessionStore;
import android.widget.Toast;"""

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
                Toast.makeText(context, "Сначала войдите в WaysGram", Toast.LENGTH_SHORT).show();
                return;
            }
            new WaysGramGiftCatalogSheet(context, sessionStore, resourcesProvider).show();
        });
        starsHeaderView.addView(
                waysGramCatalogButton,
                LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                        Gravity.CENTER_HORIZONTAL, 0, 8, 0, 4)
        );"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--android-root", required=True, type=Path)
    args = parser.parse_args()

    source = args.android_root / "TMessagesProj/src/main/java/org/telegram/ui/Gifts/GiftSheet.java"
    if not source.is_file():
        raise SystemExit(f"GiftSheet.java not found: {source}")

    content = source.read_text(encoding="utf-8")
    marker = "WaysGram's own catalog is separate from the signed-in Telegram account."
    if marker in content:
        print("WaysGram gift catalog entry already applied.")
        return

    if IMPORT_ANCHOR not in content:
        raise SystemExit("Cannot find GiftSheet import anchor; upstream source changed.")
    if VIEW_ANCHOR not in content:
        raise SystemExit("Cannot find GiftSheet header anchor; upstream source changed.")

    content = content.replace(IMPORT_ANCHOR, IMPORTS, 1)
    content = content.replace(VIEW_ANCHOR, VIEW_BLOCK, 1)
    source.write_text(content, encoding="utf-8")
    print("Applied WaysGram gift catalog entry to GiftSheet.java.")


if __name__ == "__main__":
    main()
