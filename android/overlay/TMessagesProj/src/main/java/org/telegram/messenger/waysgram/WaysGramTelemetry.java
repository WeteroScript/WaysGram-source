package org.telegram.messenger.waysgram;

import android.content.Context;
import android.util.Log;

import com.posthog.PostHog;
import com.posthog.android.PostHogAndroid;
import com.posthog.android.PostHogAndroidConfig;

import org.telegram.messenger.BuildConfig;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Privacy-conscious PostHog instrumentation for WaysGram REST actions.
 *
 * No message text, account IDs, phone numbers, tokens, invite codes, or resource IDs
 * are sent. The project token and host are injected by the source-preparation build.
 */
public final class WaysGramTelemetry {
    private static final String TAG = "WaysGramTelemetry";
    private static volatile boolean initialized;

    private WaysGramTelemetry() {
    }

    public static synchronized void initialize(Context context) {
        if (initialized || context == null) {
            return;
        }
        String apiKey = BuildConfig.WAYS_GRAM_POSTHOG_API_KEY;
        String host = BuildConfig.WAYS_GRAM_POSTHOG_HOST;
        if (apiKey == null || apiKey.trim().isEmpty() || host == null || host.trim().isEmpty()) {
            if (BuildConfig.DEBUG) {
                String missing = apiKey == null || apiKey.trim().isEmpty()
                        ? "POSTHOG_API_KEY"
                        : "POSTHOG_HOST";
                Log.e(TAG, missing + " variable required by PostHog is missing or un-configured; "
                        + "events will be missed until configured");
            }
            return;
        }

        try {
            PostHogAndroidConfig config = new PostHogAndroidConfig(apiKey.trim(), host.trim());
            config.getErrorTrackingConfig().setAutoCapture(true);
            PostHogAndroid.setup(context.getApplicationContext(), config);
            initialized = true;
            PostHog.capture("waysgram_android_app_started", Collections.<String, Object>emptyMap());
        } catch (Exception exception) {
            Log.e(TAG, "PostHog initialization failed (" + exception.getClass().getSimpleName() + ")");
        }
    }

    public static void captureApiAction(String method, String path) {
        if (!initialized) {
            return;
        }
        String operation = operationFor(method, path);
        if (operation == null) {
            return;
        }
        Map<String, Object> properties = new HashMap<>();
        properties.put("surface", operation);
        PostHog.capture("waysgram_android_action_succeeded", properties);
    }

    public static void captureApiFailure(String method, String path, int statusCode) {
        if (!initialized || statusCode < 500 && statusCode != 0) {
            return;
        }
        String operation = operationFor(method, path);
        if (operation == null) {
            operation = "api_request";
        }
        Map<String, Object> properties = new HashMap<>();
        properties.put("surface", operation);
        properties.put("status_code", statusCode);
        PostHog.capture("waysgram_android_api_failure", properties);
    }

    public static void captureException(Throwable exception, String surface) {
        if (!initialized || exception == null) {
            return;
        }
        Map<String, Object> properties = new HashMap<>();
        properties.put("surface", surface == null ? "android" : surface);
        PostHog.captureException(exception, properties);
    }

    private static String operationFor(String method, String path) {
        if (!"POST".equalsIgnoreCase(method) || path == null) {
            return null;
        }
        if ("/api/messages".equals(path)) {
            return "direct_message";
        }
        if ("/api/channels".equals(path)) {
            return "channel_create";
        }
        if ("/api/channels/join".equals(path)) {
            return "channel_join";
        }
        if ("/api/groups".equals(path)) {
            return "group_create";
        }
        if (path.startsWith("/api/telegram/gifts/") && path.endsWith("/purchase")) {
            return "telegram_gift_purchase";
        }
        if (path.startsWith("/api/gifts/") && path.endsWith("/purchase")) {
            return "waysgram_gift_purchase";
        }
        return null;
    }
}
