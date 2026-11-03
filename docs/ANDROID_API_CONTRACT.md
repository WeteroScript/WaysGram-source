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

## Internal BotFather

The home screen opens the WaysGram BotFather screens. The Android app uses the existing authenticated API:

- `GET /api/bots` — list the current user's bots. Tokens are never included in list responses.
- `POST /api/bots` — create a bot using `name`, `username`, and optional `about`/ `description`. The new token is returned once and shown in the creation screen.
- `GET /api/bots/{bot_id}` — read owned bot metadata.
- `GET /api/bots/{bot_id}/commands` and `PUT /api/bots/{bot_id}/commands` — read and replace up to 100 command descriptions.
- `POST /api/bots/{bot_id}/token/reset` — revoke the prior token and display the replacement once.
- `DELETE /api/bots/{bot_id}` — disable the bot and invalidate its token.

The internal bot token is a bearer credential. Store it privately; do not paste it into public chats or commit it to source control. The backend stores only its hash. Internal bot updates and replies use `GET /api/bot-api/getUpdates` and `POST /api/bot-api/sendMessage`.
The Android bot detail screen also opens a user-to-bot chat using `POST /api/bots/{bot_id}/messages` and `GET /api/bots/{bot_id}/messages?after_id=...`. Open chats poll every four seconds. The first outgoing message creates the chat/update queue. Incoming bot replies appear after a connected bot client posts them through the internal `sendMessage` route.

These bots belong to WaysGram's internal registry. They are not Telegram Bot API bots and the server does not execute arbitrary user-supplied programs. A separate controlled runtime/webhook architecture would be required before hosted custom bot code could be supported.

## Message refresh

Direct messages and group/channel timelines poll their authenticated history routes every four seconds using the incremental `after_id` cursor. This gives an open screen near-real-time text refresh without requiring a manual reload. It is HTTP polling, not WebSocket or push delivery; background notifications and delivery while the app is closed remain separate work.

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
