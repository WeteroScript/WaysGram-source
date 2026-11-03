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

## 4. Telegram gift catalog source

The supplied Android client confirms the original Telegram implementation path:

- `StarsController.loadStarGifts()` restores the local `star_gifts2` cache and refreshes it when its one-minute freshness window expires.
- `StarsController.getStarGiftsRemote(hash)` sends `TL_stars.getStarGifts` through `ConnectionsManager`.
- This is the MTProto method `payments.getStarGifts`; it returns the client's StarGift objects and associated user/chat metadata.

WaysGram AuthBot now has a separate server-side catalog integration using the official Telegram Bot API method `getAvailableGifts`:

- `GET /api/telegram/gifts` — authenticated WaysGram client route; refreshes the cache when it is older than 60 seconds.
- `GET /api/telegram/gifts/{gift_id}/sticker` — authenticated server-side sticker proxy. The AuthBot token and Telegram `file_id` remain server-side.
- `POST /internal/v1/telegram/gifts/sync` — protected server-side forced refresh; requires `X-Internal-Token`.
- Initial catalog sync runs when AuthBot starts. A stale cached snapshot remains available if Telegram's API is temporarily unreachable.

The Bot API method returns the gifts that the configured AuthBot may send. It is intentionally a bot-scoped subset, not a complete replacement for the Android client's MTProto `payments.getStarGifts` result. The Bot API catalog does not expose all MTProto-only gift titles, auction state, resale entries, or collectible attributes. A future full MTProto import would require a separate bot-authenticated MTProto client configured with Telegram API ID/hash; it must not require users to share their personal Telegram login codes or sessions.

Telegram's `star_count` remains `telegram_stars`. It is not converted into internal WaysGram Stars or Gram. The imported catalog is stored separately from WaysGram's internal `gift_catalog` and is read-only in this integration.

The source repository pins the official Android tree as submodule `android/telegram-upstream` at Telegram 13.1.1 (build 7237). WaysGram overlays now include:

- `WaysGramSessionStore`: Android Keystore AES-GCM storage for the WaysGram API base URL and bearer session token.
- `WaysGramAuthClient` and `WaysGramLoginSheet`: virtual +1 number / AuthWaysGram_bot OTP request and verification against `/api/auth/request-code` and `/api/auth/verify-code`. No Telegram personal login codes or account sessions are used.
- `WaysGramLoginFragment` and `WaysGramHomeFragment`: initial sign-in page and API-backed profile/wallet view.
- `WaysGramApiClient`: authenticated JSON REST client.
- `WaysGramDirectoryFragment` and `WaysGramChatFragment`: conversation list and direct text messages through the private WaysGram API.
- `WaysGramTimelineFragment`: group/channel text histories and posting.
- `WaysGramGiftCatalogClient` and `WaysGramGiftCatalogSheet`: read-only MTProto gift catalog presentation and sticker proxy.

The source-preparation patcher changes the upstream `LaunchActivity` startup fragment choice to open WaysGram login or home based on the WaysGram session. It also adds a separate WaysGram catalog entry to the original `GiftSheet`. This is an early UI/API slice, not full client parity: media, push/WebSocket delivery, contacts, calls, full group/channel creation and management, bot management UI, and remaining Telegram-backed screens still require migration. No APK has been built.

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
- Telegram's bot-scoped gift catalog is fetched using Bot API `getAvailableGifts`, cached for 60 seconds and exposed through authenticated catalog/sticker-proxy routes.
- API supports listing auctions and placing Stars bids. A higher bid refunds the previous leader; after the auction end, the next auction-list request settles the auction and transfers the reserved gift to the winner. If there are no bids, supply is restored.
- Termux helper script and setup instructions were added.

GitHub Actions test runs for the latest AuthBot changes are linked from the repository's Actions tab. The successful run IDs and conclusions are visible there.

## 8. Remaining work before a functional APK

1. Connect `WaysGramGiftCatalogClient` to the existing gift screen/controller and inject the WaysGram API base URL plus Bearer session.
2. Replace MTProto authentication with AuthBot login while preserving the screen layout.
3. Replace the remaining Telegram server-dependent profile, messaging, contact, group, channel and balance calls with the private WaysGram API; keep the gift catalog's Bot API source clearly separated from Telegram-only data.
4. Complete the Android UI/API integration for groups and BotFather; add a controlled bot runtime or webhook mechanism only after the security model is designed.
5. Wire the Android gift UI to `GET /api/telegram/gifts` and render its authenticated sticker-proxy URLs. The current source provides the Bot API subset; a full MTProto-only catalogue remains a separate, optional integration.
6. Set an app package ID, signing configuration, own icon and own app/service credentials.
7. Add Android CI, build and install APK, then perform end-to-end tests against the backend.
