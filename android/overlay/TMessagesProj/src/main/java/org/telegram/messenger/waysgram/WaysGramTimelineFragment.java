package org.telegram.messenger.waysgram;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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
    public static final int TYPE_GROUP = 1;
    public static final int TYPE_CHANNEL = 2;

    private final int type;
    private final long targetId;
    private final String targetTitle;

    private WaysGramApiClient apiClient;
    private LinearLayout messagesContainer;
    private EditText messageInput;
    private TextView statusView;
    private Button sendButton;

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
        sendButton = new Button(context);
        sendButton.setText(type == TYPE_GROUP ? "Отправить" : "Опубликовать");
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
        root.addView(refreshButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        refreshButton.setOnClickListener(view -> loadMessages());

        fragmentView = root;
        loadMessages();
        return root;
    }

    private String basePath() {
        return type == TYPE_GROUP
                ? "/api/groups/" + targetId + "/messages"
                : "/api/channels/" + targetId + "/messages";
    }

    private void loadMessages() {
        apiClient.get(basePath(), new WaysGramApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                JSONArray messages = response.optJSONArray("messages");
                messagesContainer.removeAllViews();
                int count = messages == null ? 0 : messages.length();
                for (int i = 0; i < count; i++) {
                    JSONObject message = messages.optJSONObject(i);
                    if (message != null) {
                        addMessage(message);
                    }
                }
                statusView.setText(count == 0 ? "Сообщений пока нет" : "Сообщений: " + count);
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
    }

    private void addMessage(JSONObject message) {
        TextView row = new TextView(getParentActivity());
        String senderKey = type == TYPE_GROUP ? "sender_id" : "author_id";
        long sender = message.optLong(senderKey, 0);
        String prefix = "@" + sender + ": ";
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
    }

    private void sendMessage() {
        String body = messageInput.getText() == null ? "" : messageInput.getText().toString().trim();
        if (body.length() == 0 || body.length() > 4000) {
            statusView.setText("Сообщение должно содержать от 1 до 4000 символов");
            return;
        }
        JSONObject payload = new JSONObject();
        try {
            payload.put("body", body);
        } catch (Exception exception) {
            statusView.setText("Не удалось подготовить сообщение");
            return;
        }
        sendButton.setEnabled(false);
        statusView.setText(type == TYPE_GROUP ? "Отправляю…" : "Публикую…");
        apiClient.post(basePath(), payload, new WaysGramApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                messageInput.setText("");
                sendButton.setEnabled(true);
                statusView.setText(type == TYPE_GROUP ? "Сообщение отправлено" : "Публикация создана");
                loadMessages();
            }

            @Override
            public void onError(String message, int statusCode) {
                sendButton.setEnabled(true);
                statusView.setText(message);
            }
        });
    }

    @Override
    public void onFragmentDestroy() {
        if (apiClient != null) {
            apiClient.close();
            apiClient = null;
        }
        super.onFragmentDestroy();
    }
}
