# WaysGram Android API contract

## API origin

The Android login screen defaults to:

```
https://bot-1793554244-6434-weterochina.bothost.tech
```

The address is editable in the login sheet. A successful login stores the selected API origin and the WaysGram Bearer token together in Android Keystore-backed encrypted preferences. The configured origin is a client default only; it does not establish that the hosting service is deployed or healthy. The host must proxy the root-level `/api/*`, `/health`, and protected `/internal/*` paths to the FastAPI service without stripping the `/api` prefix.

## Authentication

The client uses the app's own virtual-number system. Numbers are internal WaysGram identifiers, not real mobile numbers.

```http
POST /api/auth/request-code
Content-Type: application/json

{"phone":"+1XXXXXXXXXX"}
```

The number must already be assigned and verified through `@AuthWaysGram_bot`. The user requests the one-time WaysGram code in that bot.

```http
POST /api/auth/verify-code
Content-Type: application/json

{"phone":"+1XXXXXXXXXX","code":"123456"}
```

The response contains `token`, `expires_at`, and `user`. The Android client stores the token with the selected API origin. It does not request or use a Telegram personal-account login code or Telegram user session.

## Implemented client calls

All authenticated requests use:

```http
Authorization: Bearer <waysgram-session-token>
```

The current Android overlay calls these API areas:

- Profile and wallet: `GET /api/me`, `GET /api/me/wallet`.
- User lookup: `GET /api/users?q=...`.
- Personal messages: `GET /api/messages` and `POST /api/messages`.
- Groups: `GET /api/groups`, `POST /api/groups`, group history and text-post routes.
- Channels: `GET /api/channels`, `POST /api/channels`, channel history and text-post routes.
- Telegram gift catalogue: `GET /api/telegram/gifts`.
- Gift stickers: `GET /api/telegram/gifts/{gift_id}/sticker`.

The Android screens currently cover text-only messaging and the initial group/channel workflow. Media, push/WebSocket delivery, calls, complete moderation/member-management screens, and the in-app BotFather interface are not complete.

## Group and channel management

The group timeline loads `GET /api/groups/{group_id}`, including the signed-in user's role and the member list. Group management uses:

- `POST /api/groups/{group_id}/members` with `telegram_id` or `username` to add a member (owner/admin).
- `DELETE /api/groups/{group_id}/members/{telegram_id}` to remove a member or leave (subject to server role rules).
- `POST /api/groups/{group_id}/admins` with `telegram_id` to promote a member (owner only).
- `DELETE /api/groups/{group_id}/admins/{telegram_id}` to return an admin to member (owner only).

The channel timeline loads `GET /api/channels/{channel_id}/admins`, which returns `owner_telegram_id`, `my_role`, and the channel administrator list. Only the channel owner can use:

- `POST /api/channels/{channel_id}/admins` with `telegram_id` to appoint an administrator.
- `DELETE /api/channels/{channel_id}/admins/{telegram_id}` to remove an administrator.

The Android timeline now exposes these controls and lets the server enforce the final permission checks. The channel administrator list route is authenticated; it does not expose bot credentials or allow privilege changes by non-owners.

## Telegram gift catalogue

The primary server source is bot-authenticated MTProto. When `TELEGRAM_API_ID` and `TELEGRAM_API_HASH` are configured, AuthBot starts a Telethon client using `BOT_TOKEN` and calls Telegram's `payments.getStarGifts`. The MTProto session belongs to the configured bot; WaysGram users do not provide Telegram credentials, personal login codes, or account sessions.

The backend stores the gift metadata separately from the internal WaysGram gift inventory. The response identifies the source as `telegram_mtproto`, keeps prices in `telegram_stars`, and includes available metadata such as sticker information, limits, supply, upgrade and resale attributes, and auction flags when Telegram supplies them.

If MTProto credentials are absent or the MTProto bot connection is unavailable, AuthBot falls back to Bot API `getAvailableGifts`. That fallback is only the subset of gifts available to the configured bot and must not be described as the full MTProto catalogue.

Sticker bytes are retrieved server-side and streamed through the authenticated sticker proxy. The Android client never receives the bot token or raw Telegram document credentials. Catalog refresh is cached by the backend; a temporary upstream failure should not be represented as a successful fresh sync.

Telegram Stars amounts in this catalogue are not internal WaysGram Stars or Gram. The catalogue view is read-only and does not perform Telegram purchases or transfers.

## Server configuration and deployment

The AuthBot service requires its own environment configuration, including `BOT_TOKEN`, `OTP_FERNET_KEY`, `INTERNAL_API_TOKEN`, and `ADMIN_ID`. Configure `TELEGRAM_API_ID` and `TELEGRAM_API_HASH` to enable the full MTProto gift catalogue. Keep all secrets on the server and out of the Android app and Git history.

The host must run the FastAPI/Aiogram service and preserve the API paths expected above. Validate `GET /health`, authentication, profile, message, group, channel and sticker-proxy routes against the actual deployment before calling the integration operational.

No APK build is part of this source-only stage.
