# WaysGram source audit

Audit date: 2026-11-03

## 1. Provided Android archive

The uploaded `Telegram-master.zip` expands to an Android project whose README identifies it as the official Telegram for Android source. The source contains Android modules such as `TMessagesProj`, `TMessagesProj_App` and the Gradle wrapper.

It is not a WaysGram-specific source tree: the package namespace is `org.telegram.messenger`, the default application label in `TMessagesProj/src/main/res/values/strings.xml` is `Telegram`, and `BuildVars.java` contains Telegram's API application ID/hash and Telegram store links. The archive does not contain a WaysGram-specific brand package or WaysGram icon set.

The supplied upstream README asks forks to obtain their own API ID, make the unofficial status clear, and not use the standard Telegram logo. These items must be resolved before an APK is distributed.

## 2. Android login

`TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java` contains the Telegram MTProto login flow, including calls to `TL_auth_sendCode` and `TL_auth_signIn`. It is not connected to WaysGram AuthBot.

For an independent WaysGram client, the login screen can retain its layout, but the network actions must be replaced with the WaysGram API flow:
- `POST /api/auth/request-code`
- retrieve the code using the authenticated user's `Get code` action in `@AuthWaysGram_bot`
- `POST /api/auth/verify-code`
- store the returned WaysGram session token securely and refresh it using `POST /api/auth/refresh`

The client must not ask for or forward a real Telegram login code.

A text search of the supplied source did not find a visible `Тестировать backend` / `Test backend` control. This does not prove that no debug control can be created dynamically, but there is no matching UI string in the scanned Android source.

## 3. Gift catalogue and auctions in the archive

The archive contains generic gift and Stars UI assets, such as:
- `TMessagesProj/src/main/res/raw/gift.json`
- `TMessagesProj/src/main/res/raw/premium_gift.json`
- `TMessagesProj/src/main/res/raw/gift_upgrade.json`
- `TMessagesProj/src/main/res/raw/gift_crafting.json`
- drawable icons for gifts and Stars

These are animation / UI resources, not a complete gift catalogue with IDs, titles, prices, supply, images and auction eligibility.

The gift list is retrieved remotely in the Android source: `StarsController.getStarGiftsRemote(...)` sends `TL_stars.getStarGifts` through the Telegram MTProto `ConnectionsManager`. There is no full static product list in the provided archive to seed the independent WaysGram SQL catalogue.

The Android source also includes `GiftAuctionController.java`, but its state and bid flow call Telegram payment/MTProto methods, including `TL_payments.TL_getStarGiftAuctionState` and `TL_inputInvoiceStarGiftAuctionBid`. That is client-side integration with Telegram's server, not an independent auction server or local auction database.

Therefore the full catalogue cannot be reconstructed from this ZIP alone. The actual source catalogue data (or an authorized export containing gift IDs, names, image assets, prices, supplies and auction eligibility) is still required.

## 4. Build environment

The archive specifies:
- Android Studio 2025.1.4
- Android SDK 36
- Android NDK 27.2.12479018
- Gradle wrapper 8.13
- Android Gradle Plugin 8.13.2

The provided wrapper attempted to download Gradle 8.13, but the execution environment could not resolve `services.gradle.org` (`UnknownHostException`). No Android SDK / NDK installation was detected in the execution environment. Therefore no APK build has been completed in this environment.

The archive also contains Git submodule declarations. A complete checkout needs its submodules, Google/Firebase configuration and the app's own API/build credentials. Do not reuse the sample Telegram API credentials for a distributed fork.

## 5. AuthBot repository findings

The AuthBot repository already contains a FastAPI + Aiogram service and a shared SQLite database:
- virtual WaysGram identifiers use the `+1` plus ten digits pattern and are explicitly not real carrier phone numbers;
- one-time application codes are encrypted at rest and delivered via the Telegram bot;
- successful app login creates a 30-day session; only a SHA-256 hash of the session token is stored;
- API coverage includes profiles, personal messages, text channels, internal Stars/Gram balances and gifts;
- admin access is restricted by `ADMIN_ID`.

Current server-side limitations in that repository are documented in its README. Full groups, bot execution, media upload, push/WebSocket delivery and calls are not yet implemented.

## 6. Changes already committed to WaysGram-AuthBot

- New accounts receive one-time opening balances of 5,000 internal Stars and 15 internal Gram; subsequent profile syncs do not repeat the grant.
- Admin gift list renamed to "Подарки" and supports pagination through all database records.
- Admin can create and cancel system gift auctions. Auction gifts are reserved from catalogue supply.
- API supports listing auctions and placing Stars bids. A higher bid refunds the previous leader; after the auction end, the next auction-list request settles the auction and transfers the reserved gift to the winner. If there are no bids, supply is restored.
- Termux helper script and setup instructions were added.

GitHub Actions test runs for the latest AuthBot changes are linked from the repository's Actions tab. The successful run IDs and conclusions are visible there.

## 7. Remaining work before a functional APK

1. Import the correct WaysGram-specific UI/source or confirm that the provided Telegram client source is the intended visual base.
2. Replace MTProto authentication with AuthBot login while preserving the screen layout.
3. Replace Telegram server-dependent message, contact, group, channel, Stars, gift and auction calls with the private WaysGram API.
4. Implement the missing groups and bot-management/runtime APIs.
5. Import the actual complete gift catalogue and its image assets.
6. Set an app package ID, signing configuration, own icon and own app/service credentials.
7. Add Android CI, build and install APK, then perform end-to-end tests against the backend.
