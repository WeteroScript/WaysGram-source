# WaysGram Android API contract

## API origin

The Android login screen defaults to:

```
https://bot-1793554244-6434-weterochina.bothost.tech
```

The address is editable in the login sheet. A successful login stores the selected API origin and the WaysGram Bearer token together in Android Keystore-backed encrypted preferences. The configured origin is a client default only; it does not establish that the hosting service is deployed or healthy. The host must proxy the root-level `/api/*`, `/health`, and protected `/internal/*` paths to the FastAPI service without stripping the `/api` prefix. The login-sheet connection check validates both `GET /health` and public `GET /api/config`; it checks the WaysGram brand, API version and AuthWaysGram delivery readiness before reporting whether code delivery is configured.

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

A WaysGram session is independent of a Telegram personal-account session. Session management is scoped to the authenticated WaysGram account; revoking a session invalidates its Bearer token on the next request. Revoking other sessions preserves the token used for the revoke request.

## Implemented client calls

All authenticated requests use:

```http
Authorization: Bearer <waysgram-session-token>
```

The current Android overlay calls these API areas:

- Authentication sessions: `GET /api/auth/sessions` lists the current user's active WaysGram sessions; `DELETE /api/auth/sessions/{session_id}` revokes one session; `POST /api/auth/sessions/revoke-others` revokes every other session while preserving the current one. Responses expose opaque session IDs and timestamps only, never session tokens or token hashes.
- Profile and wallet: `GET /api/me`, `PUT /api/me`, `GET /api/me/wallet`.
- Public profiles: `GET /api/users/{user_id}` returns profile fields, up to 50 most recently acquired gifts in `gifts`, `common_groups` that both viewer and profile owner belong to, and `common_contacts` that both accounts have independently added. Shared-group and mutual-contact results are intersections of the two accounts' own relationships. The response does not expose the account phone number, balance or session credentials.
- Private contact notes: `PUT /api/contacts/{user_id}/note` with `{note}` saves a note visible only to the account that owns the contact record. `GET /api/contacts` includes the owner-specific `note`; removing the contact also removes that note. The note limit is 500 characters.
- Profile avatar: `POST /api/media/images`, `PUT /api/me/avatar`, and `DELETE /api/me/avatar`. AuthBot validates profile fields and enforces username uniqueness.
- User lookup: `GET /api/users?q=...`.
- Contacts: `GET /api/contacts`, `POST /api/contacts`, and `DELETE /api/contacts/{user_id}`. Contacts are added manually from user search; phone-book synchronization is not implemented.
- Block list: `GET /api/blocks`, `POST /api/blocks`, and `DELETE /api/blocks/{user_id}`. A block prevents either user from sending or forwarding direct messages to the other until unblocked.
- Personal messages: `GET /api/messages` and `POST /api/messages`.
- Message reactions:
  - Direct messages: `POST /api/messages/{message_id}/reactions`.
  - Groups: `POST /api/groups/{group_id}/messages/{message_id}/reactions`.
  - Channels: `POST /api/channels/{channel_id}/messages/{message_id}/reactions`.
- Cloud drafts: `GET /api/drafts?scope={dm|group|channel}&peer_id={id}`, `PUT /api/drafts` with `{scope, peer_id, body}`, and `DELETE /api/drafts` with the same query parameters. Drafts are private to their owner and scoped to a conversation; group and private-channel access is checked before reading or saving. Android direct chats and group/channel timelines load the cloud copy on open, debounce updates while typing, clear it after successful send, and keep a local per-account fallback for offline use.
- Pinned messages: `GET /api/pins?scope={dm|group|channel}&peer_id={id}`, `POST /api/pins` with `{scope, peer_id, message_id}`, and `DELETE /api/pins` with those three values as query parameters. Both participants can pin in direct chats; only group owners/admins can change group pins; only channel owners/admins can change channel pins. Members/viewers with access can read the pin list. The Android UI is not yet wired to these routes.
- Typing indicators: `PUT /api/typing` with `{scope: "dm"|"group", peer_id, is_typing}` updates or clears the current user's temporary typing state; `GET /api/typing?scope={dm|group}&peer_id={id}` returns active other participants. The server expires states after 8 seconds and checks direct-chat blocks and group membership. Android direct chats and group timelines now publish typing changes, refresh active typists every 2.5 seconds and clear local typing state when the screen pauses. Channels do not expose typing indicators.
- Conversation change events: `GET /api/events` to get the current event cursor, then `GET /api/events?after_id={event_id}` for authorized edits, deletions, reaction changes and read markers. The Android chat/timeline clients check this cursor after each bounded message long-poll response and refresh only the open conversation when one of its messages changes.
- Forward a direct message into another conversation: `POST /api/messages/{message_id}/forward` with `recipient_id` and optional `Idempotency-Key`. The server requires access to the source conversation and stores the original message/user attribution.
- Search within a personal conversation: `GET /api/messages/search?with={peer_id}&q={text}` (up to 100 matches, scoped to the authenticated conversation).
- Groups: `GET /api/groups`, `POST /api/groups`, group history, text-post and message-search routes.
- Channels: `GET /api/channels`, `POST /api/channels`, channel history, text-post and message-search routes.
- Telegram gift catalogue and WaysGram storefront: `GET /api/telegram/gifts?all=1`.
- Storefront purchase: `POST /api/telegram/gifts/{gift_id}/purchase` with optional `Idempotency-Key`.
- Owned gift inventory: `GET /api/me/gifts`.
- Gift stickers: `GET /api/telegram/gifts/{gift_id}/sticker`.
- Image upload: `POST /api/media/images`.
- Document upload: `POST /api/media/documents` for PDF, TXT, and CSV.
- Shared media history: `GET /api/media/shared?scope={dm|group|channel}&peer_id={id}` returns attachments in the authorized conversation. Optional `kind=image|video|audio|document`, `before_id`, and `limit` (1–100) support filtering and cursor pagination. Each result includes the message ID, sender, media ID, MIME type, display name, timestamp, and authenticated download path. Access to groups and private channels is enforced.
- Authenticated attachment download: `GET /api/media/{media_id}`.

The Android profile screen supports editing display name, username and bio, viewing the current avatar, and uploading or removing the avatar. Direct messages support text, conversation-scoped message search, static image, and PDF/TXT/CSV attachments. Group/channel timelines support text, static image and PDF/TXT/CSV attachments. Message history and send responses expose `media_id`, `media_type`, and `media_name` for attachments. Images render inline; documents are saved through Android's system document picker. The backend returns documents with `Content-Disposition: attachment` and `X-Content-Type-Options: nosniff`.

Message rows expose a long-press action menu. Users can reply to a message using `reply_to_id`; history responses include the quoted text and author so Android can render a quote preview. Users can also copy message text and add or remove reactions in direct chats, groups and channels. Supported reactions are `👍`, `❤️`, `😂`, `😮`, `😢`, `🎉` and `🙏`; each user can keep up to three different reactions on a message, and selecting an existing reaction removes it. Users can also edit their own message, request deletion, or forward a message to a contact. Drafts for direct conversations, groups and channels are synchronized to the authenticated WaysGram cloud after a short typing debounce, loaded when the conversation opens, and deleted after successful send or when the composer is cleared. A local per-account draft copy is retained as an offline fallback; cloud access remains scoped to the owner and the conversation's current membership. Forwarded messages preserve the original sender and source message ID; forwarding is checked against source-conversation access and is idempotent when the same key is retried. The same UI pattern is available in direct chats, groups and channels. The backend validates the reply target and enforces author, membership, owner and administrator permissions for edits/deletions. Both `PATCH` and `PUT` are supported by the edit API; the Android client uses `PUT` for compatibility with `HttpURLConnection`. Direct-message rows display their timestamp and show whether the peer's read marker covers the message; the UI refreshes when that marker advances.

Animated images, video, push/WebSocket delivery, calls, and background notifications are not implemented.

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
- `GET /api/bots/by-username/{username}` — look up an active public bot by username; the Android BotFather screen uses this to open chats with other users' bots.
- `POST /api/bots` — create a bot using `name`, `username`, and optional `about`/ `description`. The new token is returned once and shown in the creation screen.
- `GET /api/bots/{bot_id}` — read owned bot metadata.
- `GET /api/bots/{bot_id}/commands` and `PUT /api/bots/{bot_id}/commands` — read and replace up to 100 command descriptions.
- `POST /api/bots/{bot_id}/token/reset` — revoke the prior token and display the replacement once.
- `DELETE /api/bots/{bot_id}` — disable the bot and invalidate its token.

The internal bot token is a bearer credential. Store it privately; do not paste it into public chats or commit it to source control. The backend stores only its hash. Internal bot updates and replies use `GET /api/bot-api/getUpdates` and `POST /api/bot-api/sendMessage`.
The Android bot detail screen also opens a user-to-bot chat using `POST /api/bots/{bot_id}/messages` and `GET /api/bots/{bot_id}/messages?after_id=...`. Open chats poll every four seconds. The first outgoing message creates the chat/update queue. Incoming bot replies appear after a connected bot client posts them through the internal `sendMessage` route.

These bots belong to WaysGram's internal registry. They are not Telegram Bot API bots and the server does not execute arbitrary user-supplied programs. A separate controlled runtime/webhook architecture would be required before hosted custom bot code could be supported.

## Image attachments

Authenticated image upload:

```http
POST /api/media/images
Authorization: Bearer <waysgram-session-token>
Content-Type: image/jpeg | image/png | image/webp

<raw image bytes>
```

The server accepts static JPEG, PNG and WebP only. It decodes and re-encodes images, strips metadata, rejects animated files, limits the incoming file to 10 MiB, limits each side to 8192 pixels and total resolution to 40 megapixels, and applies account quotas (100 MiB stored and 30 uploads per hour).

A successful upload returns an opaque `id`. Attach it to a message with `media_id`:

- `POST /api/messages`
- `POST /api/groups/{group_id}/messages`
- `POST /api/channels/{channel_id}/messages` (channel owner/admin only)

An empty caption becomes the server text label `Изображение`. The message history returns `media_id` when an attachment exists. Download through `GET /api/media/{media_id}` with the same Bearer session. Only the uploader, direct-message participants, group members, or authenticated users of a channel containing the image can retrieve it.

Files are stored in `waysgram_media` beside the configured SQLite database. Keep that directory on persistent storage and include it in backups; the Docker Compose `/data` volume covers both the database and media directory.

The Android UI opens the system image picker and displays downloaded images in the message timeline. PDF, TXT and CSV documents use a separate upload/download path and Android's system document picker. Videos and animated images remain future work.

## Message refresh

Direct messages, group/channel timelines and internal bot chats use bounded HTTP long polling with the incremental `after_id` cursor and `wait_seconds=20`. The server holds the request for up to 20 seconds and returns as soon as new messages arrive. The Android client allows a 30-second read timeout, retries after a short delay, and stops polling while the screen is paused or the app is in the background. This improves foreground delivery but is not WebSocket or push delivery; background notifications and delivery while the app is closed remain separate work.

## Telegram gift catalogue

The primary server source is bot-authenticated MTProto. When `TELEGRAM_API_ID` and `TELEGRAM_API_HASH` are configured, AuthBot starts a Telethon client using `BOT_TOKEN` and calls Telegram's `payments.getStarGifts`. The MTProto session belongs to the configured bot; WaysGram users do not provide Telegram credentials, personal login codes, or account sessions.

The backend stores the gift metadata separately from the internal WaysGram gift inventory. The response identifies the source as `telegram_mtproto`, keeps prices in `telegram_stars`, and includes available metadata such as sticker information, limits, supply, upgrade and resale attributes, and auction flags when Telegram supplies them.

If MTProto credentials are absent or the MTProto bot connection is unavailable, AuthBot falls back to Bot API `getAvailableGifts`. That fallback is only the subset of gifts available to the configured bot and must not be described as the full MTProto catalogue.

Sticker bytes are retrieved server-side and streamed through the authenticated sticker proxy. The Android client never receives the bot token or raw Telegram document credentials. Catalog refresh is cached by the backend; a temporary upstream failure should not be represented as a successful fresh sync.

The upstream Telegram catalogue fields (including `star_count` and `currency=telegram_stars`) describe Telegram's own gift metadata; they are not the shop price. AuthBot adds separate WaysGram storefront fields: `price_stars`, `remaining`, and `for_sale`. The Android shop shows a buy action only for gifts that the administrator has enabled for sale.

A purchase request to `POST /api/telegram/gifts/{gift_id}/purchase` charges the buyer's internal WaysGram Stars balance, decrements the WaysGram shop stock, and creates a WaysGram inventory record containing the original `telegram_gift_id`. `GET /api/me/gifts` returns that ID. The Android client sends an `Idempotency-Key`; retries with the same key and request return the original result rather than charging twice.

Only the AuthBot Telegram admin panel may add a catalog gift by Telegram Gift ID or change its WaysGram sale price and stock. The app REST API does not expose a price/stock mutation route. This is a WaysGram-local storefront transaction: it does not send a gift through Telegram, transfer a Telegram collectible, debit a Telegram Stars balance, or perform an on-chain NFT operation. The Telegram catalogue itself remains read-only with respect to Telegram's own assets.

## Server configuration and deployment

The AuthBot service requires its own environment configuration, including `BOT_TOKEN`, `OTP_FERNET_KEY`, `INTERNAL_API_TOKEN`, and `ADMIN_ID`. Configure `TELEGRAM_API_ID` and `TELEGRAM_API_HASH` to enable the full MTProto gift catalogue. Keep all secrets on the server and out of the Android app and Git history.

The host must run the FastAPI/Aiogram service and preserve the API paths expected above. Validate `GET /health`, authentication, profile, message, group, channel and sticker-proxy routes against the actual deployment before calling the integration operational.

The repository includes a GitHub Actions workflow for a universal debug APK. Treat an APK as available only after the workflow completes successfully and the artifact is present; source-check success alone does not establish that an APK was built.
