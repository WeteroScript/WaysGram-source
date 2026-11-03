package org.telegram.messenger.waysgram;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

/** WaysGram direct text conversation backed by /api/messages. */
public final class WaysGramChatFragment extends BaseFragment {
    private static final int REQUEST_ATTACHMENT = 7101;
    private static final int REQUEST_SAVE_DOCUMENT = 7103;
    private final long peerId;
    private final String peerTitle;

    private WaysGramApiClient apiClient;
    private LinearLayout messagesContainer;
    private EditText messageInput;
    private TextView statusView;
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            pollNewMessages();
        }
    };
    private long lastMessageId = 0;
    private boolean pollInFlight = false;
    private boolean isScreenResumed = false;
    private String pendingSaveMediaId;

    public WaysGramChatFragment(long peerId, String peerTitle) {
        if (peerId <= 0) {
            throw new IllegalArgumentException("Invalid WaysGram peer ID");
        }
        this.peerId = peerId;
        this.peerTitle = peerTitle == null ? "" : peerTitle;
    }

    @Override
    public boolean onFragmentCreate() {
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(peerTitle.length() > 0 ? peerTitle : "Диалог");
        apiClient = new WaysGramApiClient(WaysGramSessionStore.getInstance(context));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8),
                AndroidUtilities.dp(10), AndroidUtilities.dp(8));

        statusView = new TextView(context);
        statusView.setText("Загрузка истории…");
        statusView.setTextSize(12);
        statusView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        root.addView(statusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ScrollView scroll = new ScrollView(context);
        messagesContainer = new LinearLayout(context);
        messagesContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(messagesContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        LinearLayout composer = new LinearLayout(context);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        messageInput = new EditText(context);
        messageInput.setHint("Сообщение или подпись к фото");
        messageInput.setSingleLine(false);
        messageInput.setMaxLines(4);
        composer.addView(messageInput, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));
        Button attachButton = new Button(context);
        attachButton.setText("Файл");
        attachButton.setAllCaps(false);
        composer.addView(attachButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        attachButton.setOnClickListener(view -> pickAttachment());

        Button sendButton = new Button(context);
        sendButton.setText("Отправить");
        sendButton.setAllCaps(false);
        composer.addView(sendButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(composer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        sendButton.setOnClickListener(view -> sendMessage());

        Button refreshButton = new Button(context);
        refreshButton.setText("Обновить историю");
        root.addView(refreshButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        refreshButton.setOnClickListener(view -> loadMessages());

        fragmentView = root;
        loadMessages();
        return root;
    }

    private void loadMessages() {
        apiClient.get("/api/messages?with=" + peerId, new WaysGramApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                JSONArray messages = response.optJSONArray("messages");
                messagesContainer.removeAllViews();
                int count = messages == null ? 0 : messages.length();
                long newestId = lastMessageId;
                for (int i = 0; i < count; i++) {
                    JSONObject message = messages.optJSONObject(i);
                    if (message != null) {
                        addMessage(message);
                        newestId = Math.max(newestId, message.optLong("id", 0));
                    }
                }
                lastMessageId = newestId;
                statusView.setText(count == 0 ? "История пока пуста" : "Сообщений: " + count);
                scheduleNextPoll();
            }

            @Override
            public void onError(String message, int statusCode) {
                statusView.setText(message);
                if (statusCode == 401) {
                    WaysGramSessionStore.getInstance(getParentActivity()).clearSession();
                    presentFragment(new WaysGramLoginFragment(), true);
                } else {
                    scheduleNextPoll();
                }
            }
        });
    }

    private void scheduleNextPoll() {
        scheduleNextPoll(500);
    }

    private void scheduleNextPoll(int delayMs) {
        if (apiClient != null && isScreenResumed) {
            pollHandler.removeCallbacks(pollRunnable);
            pollHandler.postDelayed(pollRunnable, delayMs);
        }
    }

    private void pollNewMessages() {
        if (apiClient == null || !isScreenResumed || pollInFlight) {
            return;
        }
        pollInFlight = true;
        apiClient.get("/api/messages?with=" + peerId + "&after_id=" + lastMessageId + "&wait_seconds=20",
                new WaysGramApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        pollInFlight = false;
                        JSONArray messages = response.optJSONArray("messages");
                        int count = messages == null ? 0 : messages.length();
                        for (int i = 0; i < count; i++) {
                            JSONObject message = messages.optJSONObject(i);
                            if (message != null) {
                                long messageId = message.optLong("id", 0);
                                if (messageId > lastMessageId) {
                                    addMessage(message);
                                    lastMessageId = messageId;
                                }
                            }
                        }
                        if (count > 0) {
                            statusView.setText("Новые сообщения: " + count);
                        }
                        scheduleNextPoll();
                    }

                    @Override
                    public void onError(String message, int statusCode) {
                        pollInFlight = false;
                        if (statusCode == 401) {
                            WaysGramSessionStore.getInstance(getParentActivity()).clearSession();
                            presentFragment(new WaysGramLoginFragment(), true);
                        } else {
                            scheduleNextPoll(4000);
                        }
                    }
                });
    }

    private void pickAttachment() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"image/*", "application/pdf", "text/plain", "text/csv"});
        startActivityForResult(intent, REQUEST_ATTACHMENT);
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_SAVE_DOCUMENT) {
            if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
                pendingSaveMediaId = null;
                return;
            }
            String mediaId = pendingSaveMediaId;
            pendingSaveMediaId = null;
            if (mediaId == null) {
                statusView.setText("Не выбран документ для сохранения.");
                return;
            }
            statusView.setText("Сохраняю документ…");
            apiClient.downloadDocumentToUri(getParentActivity().getContentResolver(), data.getData(),
                    mediaId, new WaysGramApiClient.Callback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            statusView.setText("Документ сохранён.");
                        }

                        @Override
                        public void onError(String message, int statusCode) {
                            statusView.setText(message);
                        }
                    });
            return;
        }
        if (requestCode != REQUEST_ATTACHMENT || resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            statusView.setText("Не удалось получить выбранный файл.");
            return;
        }
        String mimeType = getParentActivity().getContentResolver().getType(uri);
        statusView.setText("Загружаю вложение…");
        if (mimeType != null && mimeType.startsWith("image/")) {
            apiClient.uploadImage(getParentActivity().getContentResolver(), uri,
                    new WaysGramApiClient.Callback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            sendImage(response.optString("id", ""));
                        }

                        @Override
                        public void onError(String message, int statusCode) {
                            statusView.setText(message);
                        }
                    });
        } else {
            apiClient.uploadDocument(getParentActivity().getContentResolver(), uri,
                    new WaysGramApiClient.Callback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            sendDocument(response.optString("id", ""));
                        }

                        @Override
                        public void onError(String message, int statusCode) {
                            statusView.setText(message);
                        }
                    });
        }
    }

    private void sendImage(String mediaId) {
        if (mediaId == null || !mediaId.matches("[a-f0-9]{32}")) {
            statusView.setText("Сервер вернул некорректный ID изображения.");
            return;
        }
        String caption = messageInput.getText() == null
                ? ""
                : messageInput.getText().toString().trim();
        JSONObject payload = new JSONObject();
        try {
            payload.put("recipient_id", peerId);
            payload.put("body", caption.length() == 0 ? "Изображение" : caption);
            payload.put("media_id", mediaId);
        } catch (Exception exception) {
            statusView.setText("Не удалось подготовить изображение к отправке.");
            return;
        }
        statusView.setText("Отправляю изображение…");
        apiClient.post("/api/messages", payload, new WaysGramApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                messageInput.setText("");
                statusView.setText("Изображение отправлено.");
                loadMessages();
            }

            @Override
            public void onError(String message, int statusCode) {
                statusView.setText(message);
            }
        });
    }

    private void addMessage(JSONObject message) {
        TextView row = new TextView(getParentActivity());
        long sender = message.optLong("sender_id", 0);
        String prefix = sender == peerId ? peerTitle + ": " : "Вы: ";
        row.setText(prefix + message.optString("body", ""));
        row.setTextSize(14);
        row.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        row.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8),
                AndroidUtilities.dp(10), AndroidUtilities.dp(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = AndroidUtilities.dp(4);
        messagesContainer.addView(row, params);

        String mediaId = message.optString("media_id", "");
        String mediaType = message.optString("media_type", "");
        if (mediaId.matches("[a-f0-9]{32}") && mediaType.startsWith("image/")) {
            ImageView image = new ImageView(getParentActivity());
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setMaxHeight(AndroidUtilities.dp(260));
            image.setMaxWidth(AndroidUtilities.dp(320));
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            imageParams.bottomMargin = AndroidUtilities.dp(8);
            messagesContainer.addView(image, imageParams);
            apiClient.downloadImage(mediaId, new WaysGramApiClient.ImageCallback() {
                @Override
                public void onSuccess(Bitmap bitmap) {
                    image.setImageBitmap(bitmap);
                }

                @Override
                public void onError(String message, int statusCode) {
                    image.setContentDescription("Изображение недоступно");
                }
            });
        } else if (mediaId.matches("[a-f0-9]{32}") && !mediaType.isEmpty()) {
            String mediaName = message.optString("media_name", "document");
            Button fileButton = new Button(getParentActivity());
            fileButton.setAllCaps(false);
            fileButton.setText("Сохранить документ: " + mediaName);
            messagesContainer.addView(fileButton, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            fileButton.setOnClickListener(view -> saveDocument(mediaId, mediaName, mediaType));
        }
    }

    private void saveDocument(String mediaId, String mediaName, String mediaType) {
        if (mediaId == null || !mediaId.matches("[a-f0-9]{32}")) {
            statusView.setText("Некорректный ID документа.");
            return;
        }
        pendingSaveMediaId = mediaId;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mediaType == null || mediaType.length() == 0 ? "application/octet-stream" : mediaType);
        intent.putExtra(Intent.EXTRA_TITLE, mediaName == null || mediaName.length() == 0 ? "document" : mediaName);
        startActivityForResult(intent, REQUEST_SAVE_DOCUMENT);
    }

    private void sendDocument(String mediaId) {
        if (mediaId == null || !mediaId.matches("[a-f0-9]{32}")) {
            statusView.setText("Сервер вернул некорректный ID документа.");
            return;
        }
        String caption = messageInput.getText() == null ? "" : messageInput.getText().toString().trim();
        JSONObject payload = new JSONObject();
        try {
            payload.put("recipient_id", peerId);
            payload.put("body", caption);
            payload.put("media_id", mediaId);
        } catch (Exception exception) {
            statusView.setText("Не удалось подготовить документ к отправке.");
            return;
        }
        statusView.setText("Отправляю документ…");
        apiClient.post("/api/messages", payload, new WaysGramApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                messageInput.setText("");
                statusView.setText("Документ отправлен.");
                loadMessages();
            }

            @Override
            public void onError(String message, int statusCode) {
                statusView.setText(message);
            }
        });
    }

    private void sendMessage() {
        String body = messageInput.getText() == null ? "" : messageInput.getText().toString().trim();
        if (body.length() == 0 || body.length() > 4000) {
            statusView.setText("Сообщение должно содержать от 1 до 4000 символов");
            return;
        }
        JSONObject payload = new JSONObject();
        try {
            payload.put("recipient_id", peerId);
            payload.put("body", body);
        } catch (Exception exception) {
            statusView.setText("Не удалось подготовить сообщение");
            return;
        }
        statusView.setText("Отправляю…");
        apiClient.post("/api/messages", payload, new WaysGramApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                messageInput.setText("");
                statusView.setText("Сообщение отправлено");
                loadMessages();
            }

            @Override
            public void onError(String message, int statusCode) {
                statusView.setText(message);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        isScreenResumed = true;
        pollHandler.removeCallbacks(pollRunnable);
        pollNewMessages();
    }

    @Override
    public void onPause() {
        isScreenResumed = false;
        pollHandler.removeCallbacks(pollRunnable);
        super.onPause();
    }

    @Override
    public void onFragmentDestroy() {
        isScreenResumed = false;
        pollHandler.removeCallbacks(pollRunnable);
        if (apiClient != null) {
            apiClient.close();
            apiClient = null;
        }
        super.onFragmentDestroy();
    }
}
