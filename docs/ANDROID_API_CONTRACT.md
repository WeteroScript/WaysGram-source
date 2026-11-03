# WaysGram Android API contract: Telegram gift catalog

Status: MTProto-first backend route and Android read-only gift UI are implemented in source. The overlay is reachable from WaysGram home and from a separate entry in the upstream gift sheet after login.

Android gift client: `WaysGramGiftCatalogClient.java`.
Encrypted WaysGram session: `WaysGramSessionStore.java`.
Read-only catalog sheet: `WaysGramGiftCatalogSheet.java`.
WaysGram auth screen stores the server-issued Bearer token in the session store after successful internal OTP verification. The client callbacks are delivered on the Android main thread by their handler.

## Catalog

Authenticated request:

```http
GET /api/telegram/gifts
Authorization: Bearer <waysgram-session-token>
```

The endpoint refreshes the persisted catalog when its last successful sync is older than 60 seconds. If Telegram is temporarily unavailable, it returns the last cached snapshot and marks it `stale: true`. On a first-ever fetch failure, the endpoint returns HTTP 503.

Example response shape:

```json
{
  "source": "telegram_bot_api",
  "scope": "gifts_available_to_authbot",
  "currency": "telegram_stars",
  "synced_at": 1793650000,
  "stale": false,
  "count": 1,
  "gifts": [
    {
      "id": "gift_123",
      "source": "telegram",
      "title": "🎁",
      "emoji": "🎁",
      "star_count": 25,
      "currency": "telegram_stars",
      "upgrade_star_count": 100,
      "is_premium": false,
      "has_colors": false,
      "is_limited": true,
      "total_count": 5000,
      "remaining_count": 4500,
      "personal_total_count": 100,
      "personal_remaining_count": 90,
      "unique_gift_variant_count": 12,
      "background": null,
      "publisher_chat": null,
      "sticker": {
        "type": "regular",
        "set_name": "TelegramGifts",
        "width": 512,
        "height": 512,
        "is_animated": false,
        "is_video": false,
        "mime_type": "image/webp",
        "url": "/api/telegram/gifts/gift_123/sticker"
      },
      "synced_at": 1793650000
    }
  ]
}
```

The title is a presentation fallback because the Bot API's `Gift` object exposes the representative sticker, not the same complete title/metadata set as MTProto `StarGift`. The client should display the sticker prominently and avoid treating the fallback as an official product name.

## Sticker loading

Every sticker URL is relative to the WaysGram API host and requires the same Bearer session:

```http
GET /api/telegram/gifts/{gift_id}/sticker
Authorization: Bearer <waysgram-session-token>
```

The route asks Telegram's Bot API for the file path and streams the sticker bytes through AuthBot. Do not put the AuthBot token or Telegram `file_id` into the Android app. Respect the returned `mime_type`; Telegram stickers can be static, animated, or video-based.

## Currency and purchase separation

- `star_count` and `upgrade_star_count` are Telegram Stars amounts.
- These values do not represent internal WaysGram Stars or Gram.
- `GET /api/telegram/gifts` is a read-only catalogue endpoint. It does not send, purchase, upgrade, transfer, or bid on a Telegram gift.
- The existing `GET /api/gifts` and `POST /api/gifts/{id}/purchase` routes continue to use the internal WaysGram Gram catalogue and inventory.

## Source limitation

The backend calls official Bot API `getAvailableGifts`, which returns gifts available for the configured AuthBot to send. It does not provide the complete MTProto `payments.getStarGifts` data model, auction flags/state, resale lots, or all collectible attributes. Do not infer missing MTProto-only fields from sticker metadata.

The provided Android source fetches the full client catalogue through `StarsController.loadStarGifts()` → `getStarGiftsRemote()` → `TL_stars.getStarGifts`. The Android UI will be wired to the new WaysGram route during the client integration phase, preserving the existing gift UI layout.
