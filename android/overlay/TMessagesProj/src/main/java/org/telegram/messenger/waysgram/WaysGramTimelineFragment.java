package org.telegram.messenger.waysgram;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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

/** WaysGram group/channel text timeline backed by the private REST API. */
public final class WaysGramTimelineFragment extends BaseFragment {
    private static final int REQUEST_ATTACHMENT = 7102;
    private static final int REQUEST_SAVE_DOCUMENT = 7104;
    public static final int TYPE_GROUP = 1;
    public static final int TYPE_CHANNEL = 2;

    private final int type;
    private final long targetId;
    private final String targetTitle;

    private WaysGramApiClient apiClient;
    private WaysGramCloudDraftSync cloudDraftSync;
    private WaysGramTypingIndicatorController typingIndicatorController;
    private LinearLayout messagesContainer;
    private LinearLayout managementList;
    private EditText messageInput;
    private EditText managementInput;
    private TextView statusView;
    private TextView typingView;
    private TextView managementStatusView;
    private Button sendButton;
    private Button attachButton;
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            pollNewMessages();
        }
    };
    private long lastMessageId = 0;
    private boolean pollInFlight = false;
    private boolean sendInFlight = false;
    private long lastMessageEventId = 0;
    private boolean messageEventsReady = false;
    private boolean messageEventsInitializing = false;
    private long replyToMessageId = 0;
    private boolean isScreenResumed = false;
    private String pendingSaveMediaId;
    private Button managementActionButton;

    public WaysGramTimelineFragment(int type, long targetId, String targetTitle) {
        if ((type != TYPE_GROUP && type != TYPE_CHANNEL) || targetId <= 0) {
            throw new IllegalArgumentException("Invalid WaysGram timeline target");
        }
        this.type = type;
        this.targetId = targetId;
        this.targetTitle = targetTitle == null ? "" : targetTitle;
    }

    @Override
    public boolean onFragmentCreate() {
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(targetTitle.length() > 0 ? targetTitle : (type == TYPE_GROUP ? "Группа" : "Канал"));
        apiClient = new WaysGramApiClient(WaysGramSessionStore.getInstance(context));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8),
                AndroidUtilities.dp(10), AndroidUtilities.dp(8));

        statusView = new TextView(context);
        statusView.setText("Загружаю сообщения…");
        statusView.setTextSize(12);
        statusView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        root.addView(statusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        if (type == TYPE_GROUP) {
            typingView = new TextView(context);
            typingView.setTextSize(12);
            typingView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            typingView.setVisibility(View.GONE);
            root.addView(typingView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }


        buildManagementPanel(context, root);

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
        messageInput.setHint(type == TYPE_GROUP ? "Сообщение в группу" : "Публикация в канал");
        messageInput.setMaxLines(4);
        composer.addView(messageInput, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));
        messageInput.setHint(type == TYPE_GROUP ? "Сообщение в группу" : "Публикация или подпись к вложению");
        String draftScope = type == TYPE_GROUP ? "group" : "channel";
        messageInput.setText(WaysGramDraftStore.load(context, draftScope, targetId));
        cloudDraftSync = new WaysGramCloudDraftSync(
                context, apiClient, draftScope, targetId, messageInput
        );
        cloudDraftSync.start();
        if (type == TYPE_GROUP) {
            typingIndicatorController = new WaysGramTypingIndicatorController(
                    apiClient, "group", targetId, messageInput, typingView
            );
        }
        attachButton = new Button(context);
        attachButton.setText("Файл");
        attachButton.setAllCaps(false);
        composer.addView(attachButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        attachButton.setOnClickListener(view -> pickAttachment());

        sendButton = new Button(context);
        sendButton.setText(type == TYPE_GROUP ? "Отправить" : "Опубликовать");
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
        refreshButton.setText("Обновить");
        refreshButton.setAllCaps(false);
        root.addView(refreshButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        refreshButton.setOnClickListener(view -> {
            loadMessages();
            loadManagement();
        });

        Button searchButton = new Button(context);
        searchButton.setText("Поиск по сообщениям");
        searchButton.setAllCaps(false);
        root.addView(searchButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        searchButton.setOnClickListener(view -> showMessageSearchDialog());

        fragmentView = root;
        loadManagement();
        initializeMessageEventCursor();
        return root;
    }

    private void buildManagementPanel(Context context, LinearLayout root) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(5),
                AndroidUtilities.dp(8), AndroidUtilities.dp(5));

        TextView heading = new TextView(context);
        heading.setText(type == TYPE_GROUP ? "Участники и права группы" : "Администраторы канала");
        heading.setTextSize(14);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        panel.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        managementStatusView = new TextView(context);
        managementStatusView.setText("Загружаю права доступа…");
        managementStatusView.setTextSize(12);
        managementStatusView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        panel.addView(managementStatusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        managementInput = new EditText(context);
        managementInput.setSingleLine(true);
        managementInput.setTextSize(14);
        managementInput.setHint(type == TYPE_GROUP
                ? "Telegram ID или username участника"
                : "Telegram ID нового администратора");
        if (type == TYPE_CHANNEL) {
            managementInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        }
        panel.addView(managementInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        managementActionButton = new Button(context);
        managementActionButton.setAllCaps(false);
        managementActionButton.setText(type == TYPE_GROUP ? "Добавить участника" : "Назначить администратора");
        panel.addView(managementActionButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        managementActionButton.setOnClickListener(view -> {
            if (type == TYPE_GROUP) {
                addGroupMember();
            } else {
                addChannelAdmin();
            }
        });

        managementList = new LinearLayout(context);
        managementList.setOrientation(LinearLayout.VERTICAL);
        ScrollView managementScroll = new ScrollView(context);
        managementScroll.setFillViewport(false);
        managementScroll.addView(managementList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        panel.addView(managementScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                AndroidUtilities.dp(150)
        ));

        root.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private String basePath() {
        return type == TYPE_GROUP
                ? "/api/groups/" + targetId + "/messages"
                : "/api/channels/" + targetId + "/messages";
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
        setSendInFlight(true);
        WaysGramApiClient.Callback uploadCallback = new WaysGramApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                String mediaId = response.optString("id", "");
                if (mimeType != null && mimeType.startsWith("image/")) {
                    sendImage(mediaId);
                } else {
                    sendDocument(mediaId);
                }
            }

            @Override
            public void onError(String message, int statusCode) {
                setSendInFlight(false);
                statusView.setText(message);
            }
        };
        if (mimeType != null && mimeType.startsWith("image/")) {
            apiClient.uploadImage(getParentActivity().getContentResolver(), uri, uploadCallback);
        } else {
            apiClient.uploadDocument(getParentActivity().getContentResolver(), uri, uploadCallback);
        }
    }

    private void setSendInFlight(boolean inFlight) {
        sendInFlight = inFlight;
        if (sendButton != null) {
            sendButton.setEnabled(!inFlight);
        }
        if (attachButton != null) {
            attachButton.setEnabled(!inFlight);
        }
    }

    private void sendImage(String mediaId) {
        if (mediaId == null || !mediaId.matches("[a-f0-9]{32}")) {
            setSendInFlight(false);
            statusView.setText("Сервер вернул некорректный ID изображения.");
            return;
        }
        String caption = messageInput.getText() == null
                ? ""
                : messageInput.getText().toString().trim();
        JSONObject payload = new JSONObject();
        try {
            payload.put("body", caption.length() == 0 ? "Изображение" : caption);
            payload.put("media_id", mediaId);
        } catch (Exception exception) {
            setSendInFlight(false);
            statusView.setText("Не удалось подготовить изображение к отправке.");
            return;
        }
        setSendInFlight(true);
        statusView.setText(type == TYPE_GROUP ? "Отправляю изображение в группу…" : "Публикую изображение…");
        apiClient.post(basePath(), payload, new WaysGramApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                clearTimelineDraft();
                setSendInFlight(false);
                statusView.setText(type == TYPE_GROUP ? "Изображение отправлено." : "Изображение опубликовано.");
                loadMessages();
            }

            @Override
            public void onError(String message, int statusCode) {
                setSendInFlight(false);
                statusView.setText(message);
                if (statusCode == 401) {
                    WaysGramSessionStore.getInstance(getParentActivity()).clearSession();
                    presentFragment(new WaysGramLoginFragment(), true);
                }
            }
        });
    }

    private void showMessageSearchDialog() {
        EditText queryInput = new EditText(getParentActivity());
        queryInput.setSingleLine(true);
        queryInput.setHint("Текст сообщения или публикации");
        new AlertDialog.Builder(getParentActivity())
                .setTitle(type == TYPE_GROUP ? "Поиск по группе" : "Поиск по каналу")
                .setView(queryInput)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Найти", (dialog, which) -> {
                    String query = queryInput.getText() == null
                            ? ""
                            : queryInput.getText().toString().trim();
                    if (query.isEmpty()) {
                        statusView.setText("Введите текст для поиска");
                        return;
                    }
                    if (query.length() > 128) {
                        statusView.setText("Запрос не должен превышать 128 символов");
                        return;
                    }
                    statusView.setText("Ищу сообщения…");
                    apiClient.get(basePath() + "/search?q=" + Uri.encode(query),
                            new WaysGramApiClient.Callback() {
                                @Override
                                public void onSuccess(JSONObject response) {
                                    JSONArray messages = response.optJSONArray("messages");
                                    int count = messages == null ? 0 : messages.length();
                                    if (count == 0) {
                                        statusView.setText("Совпадений не найдено");
                                        new AlertDialog.Builder(getParentActivity())
                                                .setTitle("Результаты поиска")
                                                .setMessage("По запросу «" + query + "» ничего не найдено.")
                                                .setPositiveButton("Закрыть", null)
                                                .show();
                                        return;
                                    }
                                    StringBuilder results = new StringBuilder();
                                    int displayCount = Math.min(count, 20);
                                    String senderKey = type == TYPE_GROUP ? "sender_id" : "author_id";
                                    for (int i = 0; i < displayCount; i++) {
                                        JSONObject message = messages.optJSONObject(i);
                                        if (message == null) {
                                            continue;
                                        }
                                        if (results.length() > 0) {
                                            results.append("\n\n");
                                        }
                                        results.append("@").append(message.optLong(senderKey, 0));
                                        results.append("\n").append(message.optString("body", ""));
                                    }
                                    if (count > displayCount) {
                                        results.append("\n\nПоказаны первые ").append(displayCount)
                                                .append(" из ").append(count).append(" совпадений.");
                                    }
                                    statusView.setText("Найдено сообщений: " + count);
                                    new AlertDialog.Builder(getParentActivity())
                                            .setTitle("Результаты поиска · " + count)
                                            .setMessage(results.toString())
                                            .setPositiveButton("Закрыть", null)
                                            .show();
                                }

                                @Override
                                public void onError(String message, int statusCode) {
                                    statusView.setText(message);
                                    if (statusCode == 401) {
                                        WaysGramSessionStore.getInstance(getParentActivity()).clearSession();
                                        presentFragment(new WaysGramLoginFragment(), true);
                                    }
                                }
                            });
                })
                .show();
    }

    private void initializeMessageEventCursor() {
        if (apiClient == null || messageEventsReady || messageEventsInitializing) {
            return;
        }
        messageEventsInitializing = true;
        apiClient.get("/api/events", new WaysGramApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                messageEventsInitializing = false;
                lastMessageEventId = response.optLong("latest_id", 0);
                messageEventsReady = true;
                loadMessages();
                if (isScreenResumed) {
                    pollNewMessages();
                }
            }

            @Override
            public void onError(String message, int statusCode) {
                messageEventsInitializing = false;
                messageEventsReady = false;
                if (statusCode == 401) {
                    WaysGramSessionStore.getInstance(getParentActivity()).clearSession();
                    presentFragment(new WaysGramLoginFragment(), true);
                    return;
                }
                loadMessages();
                if (isScreenResumed) {
                    scheduleNextPoll(4000);
                }
            }
        });
    }

    private void checkMessageEvents() {
        if (apiClient == null || !isScreenResumed) {
            return;
        }
        if (!messageEventsReady) {
            initializeMessageEventCursor();
            return;
        }
        apiClient.get("/api/events?after_id=" + lastMessageEventId, new WaysGramApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                JSONArray events = response.optJSONArray("events");
                String expectedScope = type == TYPE_GROUP ? "group" : "channel";
                boolean refreshCurrentTimeline = false;
                int count = events == null ? 0 : events.length();
                for (int i = 0; i < count; i++) {
                    JSONObject event = events.optJSONObject(i);
                    if (event != null && expectedScope.equals(event.optString("scope"))
                            && event.optLong("chat_id", 0) == targetId) {
                        refreshCurrentTimeline = true;
                        break;
                    }
                }
                lastMessageEventId = Math.max(
                        lastMessageEventId,
                        response.optLong("latest_id", lastMessageEventId)
                );
                if (refreshCurrentTimeline) {
                    loadMessages();
                } else {
                    scheduleNextPoll();
                }
            }

            @Override
            public void onError(String message, int statusCode) {
                if (statusCode == 401) {
                    WaysGramSessionStore.getInstance(getParentActivity()).clearSession();
                    presentFragment(new WaysGramLoginFragment(), true);
                } else {
                    scheduleNextPoll(4000);
                }
            }
        });
    }

    private void loadMessages() {
        apiClient.get(basePath(), new WaysGramApiClient.Callback() {
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
                statusView.setText(count == 0 ? "Сообщений пока нет" : "Сообщений: " + count);
                markMessagesRead();
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

    private void markMessagesRead() {
        if (apiClient == null || lastMessageId <= 0) {
            return;
        }
        JSONObject payload = new JSONObject();
        try {
            payload.put("scope", type == TYPE_GROUP ? "group" : "channel");
            payload.put("peer_id", targetId);
            payload.put("last_read_id", lastMessageId);
        } catch (Exception exception) {
            return;
        }
        apiClient.put("/api/read", payload, new WaysGramApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                // Read markers are background state; keep the timeline status unchanged.
            }

            @Override
            public void onError(String message, int statusCode) {
                if (statusCode == 401 && getParentActivity() != null) {
                    WaysGramSessionStore.getInstance(getParentActivity()).clearSession();
                    presentFragment(new WaysGramLoginFragment(), true);
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
        apiClient.getLongPoll(basePath() + "?after_id=" + lastMessageId + "&wait_seconds=20", new WaysGramApiClient.Callback() {
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
                    markMessagesRead();
                }
                checkMessageEvents();
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

    private void loadManagement() {
        if (type == TYPE_GROUP) {
            apiClient.get("/api/groups/" + targetId, new WaysGramApiClient.Callback() {
                @Override
                public void onSuccess(JSONObject response) {
                    renderGroupManagement(response);
                }

                @Override
                public void onError(String message, int statusCode) {
                    managementStatusView.setText(message);
                    managementActionButton.setEnabled(false);
                    if (statusCode == 401) {
                        WaysGramSessionStore.getInstance(getParentActivity()).clearSession();
                        presentFragment(new WaysGramLoginFragment(), true);
                    }
                }
            });
        } else {
            apiClient.get("/api/channels/" + targetId + "/admins", new WaysGramApiClient.Callback() {
                @Override
                public void onSuccess(JSONObject response) {
                    renderChannelManagement(response);
                }

                @Override
                public void onError(String message, int statusCode) {
                    managementStatusView.setText(message);
                    managementActionButton.setEnabled(false);
                    if (statusCode == 401) {
                        WaysGramSessionStore.getInstance(getParentActivity()).clearSession();
                        presentFragment(new WaysGramLoginFragment(), true);
                    }
                }
            });
        }
    }

    private void renderGroupManagement(JSONObject group) {
        String myRole = group.optString("my_role", "member");
        JSONArray members = group.optJSONArray("members");
        int count = members == null ? 0 : members.length();
        managementStatusView.setText("Ваша роль: " + roleLabel(myRole) + " · участников: " + count);
        managementActionButton.setEnabled("owner".equals(myRole) || "admin".equals(myRole));
        managementList.removeAllViews();
        long myId = group.optLong("my_telegram_id", 0);
        if (!"owner".equals(myRole) && myId > 0) {
            addSmallButton(managementList, "Покинуть группу", () -> leaveGroup(myId));
        }

        int visible = Math.min(count, 40);
        for (int i = 0; i < visible; i++) {
            JSONObject member = members.optJSONObject(i);
            if (member == null) {
                continue;
            }
            long memberId = member.optLong("telegram_id", 0);
            String role = member.optString("role", "member");
            String name = member.optString("display_name", "");
            if (name.length() == 0) {
                name = member.optString("username", "");
            }
            if (name.length() == 0) {
                name = "Пользователь";
            }
            TextView label = managementLabel(name + " · ID " + memberId + " · " + roleLabel(role));
            managementList.addView(label, matchWrap());

            boolean manager = "owner".equals(myRole) || "admin".equals(myRole);
            if (manager && !"owner".equals(role)) {
                LinearLayout actions = new LinearLayout(getParentActivity());
                actions.setOrientation(LinearLayout.HORIZONTAL);
                if ("owner".equals(myRole)) {
                    if ("admin".equals(role)) {
                        addSmallButton(actions, "Снять админа", () -> changeGroupAdmin(memberId, false));
                    } else {
                        addSmallButton(actions, "Назначить админом", () -> changeGroupAdmin(memberId, true));
                    }
                }
                addSmallButton(actions, "Удалить", () -> removeGroupMember(memberId));
                managementList.addView(actions, matchWrap());
            }
        }
        if (count > visible) {
            managementList.addView(managementLabel("Показаны первые " + visible + " участников."));
        }
    }

    private void renderChannelManagement(JSONObject channel) {
        String myRole = channel.optString("my_role", "member");
        JSONArray admins = channel.optJSONArray("admins");
        int count = admins == null ? 0 : admins.length();
        boolean owner = "owner".equals(myRole);
        managementStatusView.setText(owner
                ? "Вы владелец канала · администраторов: " + count
                : "Ваша роль: " + roleLabel(myRole));
        managementActionButton.setEnabled(owner);
        managementList.removeAllViews();

        if (count == 0) {
            managementList.addView(managementLabel("Администраторов пока нет."));
            return;
        }
        for (int i = 0; i < count && i < 40; i++) {
            JSONObject admin = admins.optJSONObject(i);
            if (admin == null) {
                continue;
            }
            long adminId = admin.optLong("telegram_id", 0);
            String name = admin.optString("display_name", "");
            if (name.length() == 0) {
                name = admin.optString("username", "");
            }
            if (name.length() == 0) {
                name = "Пользователь";
            }
            managementList.addView(managementLabel(name + " · ID " + adminId));
            if (owner) {
                LinearLayout actions = new LinearLayout(getParentActivity());
                actions.setOrientation(LinearLayout.HORIZONTAL);
                addSmallButton(actions, "Снять администратора", () -> removeChannelAdmin(adminId));
                managementList.addView(actions, matchWrap());
            }
        }
    }

    private void addGroupMember() {
        String value = managementInput.getText() == null ? "" : managementInput.getText().toString().trim();
        if (value.startsWith("@")) {
            value = value.substring(1);
        }
        if (value.isEmpty()) {
            managementStatusView.setText("Введите Telegram ID или username.");
            return;
        }
        JSONObject payload = new JSONObject();
        try {
            if (value.matches("[0-9]{1,20}")) {
                payload.put("telegram_id", Long.parseLong(value));
            } else {
                payload.put("username", value);
            }
        } catch (Exception exception) {
            managementStatusView.setText("Некорректный Telegram ID.");
            return;
        }
        managementActionButton.setEnabled(false);
        apiClient.post("/api/groups/" + targetId + "/members", payload, new WaysGramApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                managementInput.setText("");
                managementStatusView.setText(response.optBoolean("joined", false)
                        ? "Участник добавлен."
                        : "Этот пользователь уже состоит в группе.");
                loadManagement();
            }

            @Override
            public void onError(String message, int statusCode) {
                managementStatusView.setText(message);
                managementActionButton.setEnabled(true);
            }
        });
    }

    private void changeGroupAdmin(long memberId, boolean promote) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("telegram_id", memberId);
        } catch (Exception exception) {
            managementStatusView.setText("Не удалось подготовить запрос.");
            return;
        }
        String path = "/api/groups/" + targetId + "/admins";
        if (promote) {
            apiClient.post(path, payload, new WaysGramApiClient.Callback() {
                @Override
                public void onSuccess(JSONObject response) {
                    managementStatusView.setText("Права администратора выданы.");
                    loadManagement();
                }

                @Override
                public void onError(String message, int statusCode) {
                    managementStatusView.setText(message);
                }
            });
        } else {
            apiClient.delete(path + "/" + memberId, new WaysGramApiClient.Callback() {
                @Override
                public void onSuccess(JSONObject response) {
                    managementStatusView.setText("Права администратора сняты.");
                    loadManagement();
                }

                @Override
                public void onError(String message, int statusCode) {
                    managementStatusView.setText(message);
                }
            });
        }
    }

    private void leaveGroup(long myId) {
        apiClient.delete("/api/groups/" + targetId + "/members/" + myId,
                new WaysGramApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        managementStatusView.setText("Вы покинули группу.");
                        presentFragment(new WaysGramDirectoryFragment(
                                WaysGramDirectoryFragment.TYPE_GROUPS
                        ), true);
                    }

                    @Override
                    public void onError(String message, int statusCode) {
                        managementStatusView.setText(message);
                    }
                });
    }

    private void removeGroupMember(long memberId) {
        apiClient.delete("/api/groups/" + targetId + "/members/" + memberId,
                new WaysGramApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        managementStatusView.setText("Участник удалён.");
                        loadManagement();
                    }

                    @Override
                    public void onError(String message, int statusCode) {
                        managementStatusView.setText(message);
                    }
                });
    }

    private void addChannelAdmin() {
        String value = managementInput.getText() == null ? "" : managementInput.getText().toString().trim();
        if (!value.matches("[0-9]{1,20}")) {
            managementStatusView.setText("Введите числовой Telegram ID пользователя.");
            return;
        }
        JSONObject payload = new JSONObject();
        try {
            payload.put("telegram_id", Long.parseLong(value));
        } catch (Exception exception) {
            managementStatusView.setText("Некорректный Telegram ID.");
            return;
        }
        managementActionButton.setEnabled(false);
        apiClient.post("/api/channels/" + targetId + "/admins", payload, new WaysGramApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                managementInput.setText("");
                managementStatusView.setText("Администратор назначен.");
                loadManagement();
            }

            @Override
            public void onError(String message, int statusCode) {
                managementStatusView.setText(message);
                managementActionButton.setEnabled(true);
            }
        });
    }

    private void removeChannelAdmin(long adminId) {
        apiClient.delete("/api/channels/" + targetId + "/admins/" + adminId,
                new WaysGramApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        managementStatusView.setText("Администратор снят.");
                        loadManagement();
                    }

                    @Override
                    public void onError(String message, int statusCode) {
                        managementStatusView.setText(message);
                    }
                });
    }

    private TextView managementLabel(String value) {
        TextView label = new TextView(getParentActivity());
        label.setText(value);
        label.setTextSize(12);
        label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        label.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4),
                AndroidUtilities.dp(4), AndroidUtilities.dp(4));
        return label;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private void addSmallButton(LinearLayout parent, String title, Runnable action) {
        Button button = new Button(getParentActivity());
        button.setText(title);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.rightMargin = AndroidUtilities.dp(5);
        parent.addView(button, params);
    }

    private String roleLabel(String role) {
        switch (role) {
            case "owner":
                return "владелец";
            case "admin":
                return "администратор";
            default:
                return "участник";
        }
    }

    private void addMessage(JSONObject message) {
        TextView row = new TextView(getParentActivity());
        String senderKey = type == TYPE_GROUP ? "sender_id" : "author_id";
        long sender = message.optLong(senderKey, 0);
        String prefix = "@" + sender + ": ";
        long messageId = message.optLong("id", 0);
        long repliedToId = message.optLong("reply_to_id", 0);
        String body = prefix + message.optString("body", "");
        if (repliedToId > 0) {
            String quotedBody = message.optString("reply_to_body", "").trim();
            long quotedSender = message.optLong("reply_to_sender_id", 0);
            if (quotedBody.length() > 160) {
                quotedBody = quotedBody.substring(0, 160) + "…";
            }
            String quotePreview = quotedBody.isEmpty()
                    ? "Сообщение #" + repliedToId
                    : (quotedSender > 0 ? "@" + quotedSender + ": " : "") + quotedBody;
            body = "Цитата: " + quotePreview + "\n" + body;
        }
        String reactionSummary = message.optString("reaction_summary", "");
        if (!reactionSummary.isEmpty()) {
            body += "\n" + reactionSummary.replace(",", " · ").replace(":", " ");
        }
        row.setText(body);
        row.setContentDescription("Удерживайте сообщение, чтобы ответить или поставить реакцию");
        if (messageId > 0) {
            row.setOnLongClickListener(view -> {
                showMessageActions(message);
                return true;
            });
        }
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

    private void copyMessageText(String body) {
        if (body == null || body.isEmpty()) {
            statusView.setText("В сообщении нет текста для копирования");
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getParentActivity()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            statusView.setText("Буфер обмена недоступен");
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("WaysGram message", body));
        statusView.setText("Текст сообщения скопирован");
    }

    private void showReactionPicker(long messageId) {
        String[] reactions = {"👍", "❤️", "😂", "😮", "😢", "🎉", "🙏"};
        new AlertDialog.Builder(getParentActivity())
                .setTitle("Выберите реакцию")
                .setItems(reactions, (dialog, which) -> {
                    if (which >= 0 && which < reactions.length) {
                        sendReaction(basePath() + "/" + messageId + "/reactions", reactions[which]);
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void sendReaction(String path, String reaction) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("reaction", reaction);
        } catch (Exception exception) {
            statusView.setText("Не удалось подготовить реакцию");
            return;
        }
        statusView.setText("Сохраняю реакцию…");
        apiClient.post(path, payload, new WaysGramApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                statusView.setText("Реакция " + (response.optString("status").equals("removed")
                        ? "удалена"
                        : "добавлена"));
                loadMessages();
            }

            @Override
            public void onError(String message, int statusCode) {
                statusView.setText(message);
            }
        });
    }

    private String messagePath(long messageId) {
        return basePath() + "/" + messageId;
    }

    private void showMessageActions(JSONObject message) {
        long messageId = message.optLong("id", 0);
        if (messageId <= 0) {
            return;
        }
        String[] actions = {"Ответить", "Реакция", "Копировать", "Редактировать", "Удалить"};
        new AlertDialog.Builder(getParentActivity())
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        setReplyTarget(messageId);
                    } else if (which == 1) {
                        showReactionPicker(messageId);
                    } else if (which == 2) {
                        copyMessageText(message.optString("body", ""));
                    } else if (which == 3) {
                        editMessage(messageId, message.optString("body", ""));
                    } else if (which == 4) {
                        confirmDeleteMessage(messageId);
                    }
                })
                .show();
    }

    private void editMessage(long messageId, String currentBody) {
        EditText editor = new EditText(getParentActivity());
        editor.setText(currentBody);
        editor.setSelection(editor.length());
        new AlertDialog.Builder(getParentActivity())
                .setTitle("Редактировать сообщение")
                .setView(editor)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String body = editor.getText() == null ? "" : editor.getText().toString().trim();
                    if (body.length() == 0 || body.length() > 4000) {
                        statusView.setText("Сообщение должно содержать от 1 до 4000 символов");
                        return;
                    }
                    JSONObject payload = new JSONObject();
                    try {
                        payload.put("body", body);
                    } catch (Exception exception) {
                        statusView.setText("Не удалось подготовить изменение");
                        return;
                    }
                    apiClient.put(messagePath(messageId), payload, new WaysGramApiClient.Callback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            statusView.setText("Сообщение изменено");
                            loadMessages();
                        }

                        @Override
                        public void onError(String message, int statusCode) {
                            statusView.setText(message);
                        }
                    });
                })
                .show();
    }

    private void confirmDeleteMessage(long messageId) {
        new AlertDialog.Builder(getParentActivity())
                .setTitle("Удалить сообщение?")
                .setMessage("Сообщение будет удалено из переписки.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (dialog, which) ->
                        apiClient.delete(messagePath(messageId), new WaysGramApiClient.Callback() {
                            @Override
                            public void onSuccess(JSONObject response) {
                                statusView.setText("Сообщение удалено");
                                loadMessages();
                            }

                            @Override
                            public void onError(String message, int statusCode) {
                                statusView.setText(message);
                            }
                        }))
                .show();
    }

    private void setReplyTarget(long messageId) {
        replyToMessageId = messageId;
        statusView.setText("Ответ на сообщение #" + messageId + " · нажмите сюда, чтобы отменить");
        statusView.setOnClickListener(view -> clearReplyTarget());
    }

    private void clearReplyTarget() {
        replyToMessageId = 0;
        statusView.setOnClickListener(null);
        statusView.setText("Ответ отменён");
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
            setSendInFlight(false);
            statusView.setText("Сервер вернул некорректный ID документа.");
            return;
        }
        String caption = messageInput.getText() == null ? "" : messageInput.getText().toString().trim();
        JSONObject payload = new JSONObject();
        try {
            payload.put("body", caption);
            payload.put("media_id", mediaId);
        } catch (Exception exception) {
            setSendInFlight(false);
            statusView.setText("Не удалось подготовить документ к отправке.");
            return;
        }
        setSendInFlight(true);
        statusView.setText(type == TYPE_GROUP ? "Отправляю документ в группу…" : "Публикую документ…");
        apiClient.post(basePath(), payload, new WaysGramApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                clearTimelineDraft();
                setSendInFlight(false);
                statusView.setText(type == TYPE_GROUP ? "Документ отправлен." : "Документ опубликован.");
                loadMessages();
            }

            @Override
            public void onError(String message, int statusCode) {
                setSendInFlight(false);
                statusView.setText(message);
                if (statusCode == 401) {
                    WaysGramSessionStore.getInstance(getParentActivity()).clearSession();
                    presentFragment(new WaysGramLoginFragment(), true);
                }
            }
        });
    }

    private void clearTimelineDraft() {
        if (cloudDraftSync != null) {
            cloudDraftSync.clear();
        } else if (messageInput != null) {
            messageInput.setText("");
        }
    }

    private void sendMessage() {
        if (sendInFlight) {
            return;
        }
        String body = messageInput.getText() == null ? "" : messageInput.getText().toString().trim();
        if (body.length() == 0 || body.length() > 4000) {
            statusView.setText("Сообщение должно содержать от 1 до 4000 символов");
            return;
        }
        JSONObject payload = new JSONObject();
        try {
            payload.put("body", body);
            if (replyToMessageId > 0) {
                payload.put("reply_to_id", replyToMessageId);
            }
        } catch (Exception exception) {
            statusView.setText("Не удалось подготовить сообщение");
            return;
        }
        setSendInFlight(true);
        statusView.setText(type == TYPE_GROUP ? "Отправляю…" : "Публикую…");
        apiClient.post(basePath(), payload, new WaysGramApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                clearTimelineDraft();
                replyToMessageId = 0;
                statusView.setOnClickListener(null);
                setSendInFlight(false);
                statusView.setText(type == TYPE_GROUP ? "Сообщение отправлено" : "Публикация создана");
                loadMessages();
            }

            @Override
            public void onError(String message, int statusCode) {
                setSendInFlight(false);
                statusView.setText(message);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        isScreenResumed = true;
        if (typingIndicatorController != null) {
            typingIndicatorController.startPolling();
        }
        pollHandler.removeCallbacks(pollRunnable);
        if (messageEventsReady) {
            pollNewMessages();
        } else {
            initializeMessageEventCursor();
        }
    }

    @Override
    public void onPause() {
        isScreenResumed = false;
        pollHandler.removeCallbacks(pollRunnable);
        if (typingIndicatorController != null) {
            typingIndicatorController.stopPolling();
        }
        if (cloudDraftSync != null) {
            cloudDraftSync.flush();
        }
        super.onPause();
    }

    @Override
    public void onFragmentDestroy() {
        isScreenResumed = false;
        pollHandler.removeCallbacks(pollRunnable);
        if (typingIndicatorController != null) {
            typingIndicatorController.close();
            typingIndicatorController = null;
        }
        if (cloudDraftSync != null) {
            cloudDraftSync.close();
            cloudDraftSync = null;
        }
        if (apiClient != null) {
            apiClient.close();
            apiClient = null;
        }
        super.onFragmentDestroy();
    }
}
