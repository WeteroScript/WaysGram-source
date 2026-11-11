# WaysGram Android observability

The Android REST overlay supports PostHog product analytics and exception tracking. The SDK is optional at runtime: without both environment variables, the app remains usable and the debug log reports that telemetry is not configured.

## Build configuration

Set these variables before running `scripts/prepare_android_dev_build.py`:

- `POSTHOG_API_KEY`: the WaysGram PostHog project token.
- `POSTHOG_HOST`: `https://eu.i.posthog.com` for the current EU project.

The preparation script adds the PostHog Android SDK dependency to the generated Telegram library module and injects both values into its generated BuildConfig. The project token is not committed to Git. For GitHub Actions builds, configure repository secret `POSTHOG_API_KEY`; the workflow reads repository variable `POSTHOG_HOST` and defaults to the EU ingestion host.

## Events and privacy

The client records app startup, successful high-level REST actions (direct message, channel/group creation, channel join, gift purchase), and server/API failures. PostHog's Android exception autocapture is enabled when configuration is present.

The event layer deliberately does not send message bodies, phone numbers, account IDs, usernames, session tokens, invite links, gift IDs, uploaded filenames, or media content. It does not enable session replay. API paths are mapped to a small set of fixed operation labels before capture.

## Verification

Build and install the debug APK with the variables configured, then perform a login and one or more supported REST actions. Confirm `waysgram_android_*` events and `$exception` events appear in the WaysGram PostHog project. Debug builds without configuration log an explicit missing-variable error; release builds remain inert until configured.
