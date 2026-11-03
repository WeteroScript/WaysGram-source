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

## 4. Telegram gift catalog source and WaysGram integration

The supplied Telegram Android client obtains its StarGift list through MTProto:
- `StarsController.loadStarGifts()` restores the local cache.
- `StarsController.getStarGiftsRemote(hash)` sends `TL_stars.getStarGifts`, corresponding to `payments.getStarGifts`.

AuthBot now has a bot-authenticated MTProto implementation in `telegram_mtproto.py`. When `TELEGRAM_API_ID` and `TELEGRAM_API_HASH` are configured, it creates a Telethon client with `start(bot_token=BOT_TOKEN)`, verifies that the authenticated identity is a bot, and requests `functions.payments.GetStarGiftsRequest(hash=0)`. It stores the returned metadata, including Telegram Stars price, supply/limited flags, sticker document metadata, upgrade/resale fields and auction metadata where present.

The Android app does not authenticate a Telegram user to retrieve this catalogue. It calls the WaysGram API instead:
- `GET /api/telegram/gifts` — authenticated catalogue response.
- `GET /api/telegram/gifts/{gift_id}/sticker` — authenticated sticker proxy; the MTProto bot session and document credentials remain server-side.
- `POST /internal/v1/telegram/gifts/sync` — forced refresh protected by `X-Internal-Token`.

If bot-authenticated MTProto is not configured or is unavailable, AuthBot falls back to Bot API `getAvailableGifts`. That fallback is limited to gifts the configured bot can send and is not the complete MTProto catalogue. Telegram Stars remain a separate currency from internal WaysGram Stars and Gram.

The Android source repository pins Telegram 13.1.1 (build 7237) as a Git submodule and adds these WaysGram overlays:
- `WaysGramSessionStore`: Android Keystore-backed encryption for the WaysGram API origin and session token.
- `WaysGramAuthClient` and `WaysGramLoginSheet`: virtual-number sign-in and one-time code verification through AuthBot.
- `WaysGramLoginFragment` and `WaysGramHomeFragment`: login, profile and wallet screens.
- `WaysGramApiClient`: authenticated JSON REST client.
- `WaysGramDirectoryFragment`, `WaysGramChatFragment`, and `WaysGramTimelineFragment`: user search, initial group/channel creation, and text-based messaging views.
- `WaysGramGiftCatalogClient` and `WaysGramGiftCatalogSheet`: read-only MTProto catalogue and sticker proxy UI.

The login screen now defaults to `https://bot-1793554244-6434-weterochina.bothost.tech`; the field remains editable. This only configures the client's API origin. The hosting domain must still run the AuthBot FastAPI service and proxy the API paths; deployment health has not been confirmed from the source repository.

The group/channel timeline now exposes member and administrator controls, and text conversations poll incremental history every four seconds while open. The home screen includes an internal BotFather UI for bot creation, listing, command configuration, token reset and deactivation. This is HTTP polling, not push/WebSocket delivery. The server does not execute arbitrary user programs. Media transfer, background notifications, contacts, calls, full hosted bot runtime, and remaining client screens are still incomplete. The Android client has not been built into an APK.

## 5. Build environment

The archive specifies:
- Android Studio 2025.1.4
- Android SDK 36
- Android NDK 27.2.12479018
- Gradle wrapper 8.13
- Android Gradle Plugin 8.13.2

The Android source is now available in the WaysGram repository through a pinned Git submodule. The source-preparation script checks out the source and overlays without invoking Gradle. The previous execution environment could not resolve `services.gradle.org` (`UnknownHostException`) and had no Android SDK/NDK, so no APK build is being attempted during this phase.

The archive also contains Git submodule declarations. A complete checkout needs its submodules, Google/Firebase configuration and the app's own API/build credentials. Do not reuse the sample Telegram API credentials for a distributed fork.

## 6. AuthBot repository findings

The AuthBot repository already contains a FastAPI + Aiogram service and a shared SQLite database:
- virtual WaysGram identifiers use the `+1` plus ten digits pattern and are explicitly not real carrier phone numbers;
- one-time application codes are encrypted at rest and delivered via the Telegram bot;
- successful app login creates a 30-day session; only a SHA-256 hash of the session token is stored;
- API coverage includes profiles, personal messages, text channels, internal Stars/Gram balances and gifts;
- admin access is restricted by `ADMIN_ID`.

The AuthBot backend now includes text group chats with owner/admin/member roles, an internal BotFather registry, per-bot command lists, and a token-authenticated text message/update API. It does not execute arbitrary user-supplied code. Media upload, push/WebSocket delivery, calls, bot webhooks and a hardened isolated bot runtime are still not implemented.

## 7. Changes already committed to WaysGram-AuthBot

- New accounts receive one-time opening balances of 5,000 internal Stars and 15 internal Gram; subsequent profile syncs do not repeat the grant.
- Admin gift list renamed to "Подарки" and supports pagination through all database records.
- Admin can create and cancel system gift auctions. Auction gifts are reserved from catalogue supply.
- Telegram gift catalogue is fetched primarily through bot-authenticated MTProto `payments.getStarGifts`; Bot API `getAvailableGifts` is only a limited fallback. The catalogue and sticker proxy are exposed through authenticated routes.
- API supports listing auctions and placing Stars bids. A higher bid refunds the previous leader; after the auction end, the next auction-list request settles the auction and transfers the reserved gift to the winner. If there are no bids, supply is restored.
- Termux helper script and setup instructions were added.

GitHub Actions test runs for the latest AuthBot changes are linked from the repository's Actions tab. The successful run IDs and conclusions are visible there.

## 8. Remaining work before a functional APK

1. Verify the hosting domain runs the AuthBot FastAPI service and correctly proxies `/api/*`, `/health`, and protected `/internal/*` paths.
2. Run end-to-end checks for virtual-number sign-in, session persistence, profile/wallet, user lookup, direct messages, group creation, channel creation, text histories and Telegram gift/sticker retrieval.
3. Add reliable real-time delivery (WebSocket or push) and reconnect/history synchronization.
4. Implement media upload and delivery, contacts, calls, full group/channel member and moderation screens, and the internal BotFather UI.
5. Review and complete authorization, rate limits, observability, database backup and production deployment hardening.
6. Set the independent app package ID, signing configuration, app icon and app/service credentials.
7. Add Android build CI and perform APK build/install tests only after the source/API integration milestones are complete.

Stake-based auction functionality is not part of the Android integration milestone. The client marketplace scope should use fixed-price listings and inventory operations.
