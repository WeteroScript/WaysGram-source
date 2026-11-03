package org.telegram.messenger.waysgram;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Server-backed WaysGram directory for DMs, groups and channels. */
public final class WaysGramDirectoryFragment extends BaseFragment {
    public static final int TYPE_CONVERSATIONS = 1;
    public static final int TYPE_GROUPS = 2;
    public static final int TYPE_CHANNELS = 3;

    private final int type;
    private WaysGramApiClient apiClient;
    private LinearLayout listContainer;
    private TextView statusView;
    private EditText primaryInput;
    private EditText usernameInput;

    public WaysGramDirectoryFragment(int type) {
        this.type = type;
    }

    @Override
    public boolean onFragmentCreate() {
        return type >= TYPE_CONVERSATIONS && type <= TYPE_CHANNELS;
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(titleForType());
        apiClient = new WaysGramApiClient(
                WaysGramSessionStore.getInstance(context)
        );

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10),
                AndroidUtilities.dp(14), AndroidUtilities.dp(10));

        statusView = new TextView(context);
        statusView.setText("Загружаю…");
        statusView.setTextSize(13);
        statusView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        root.addView(statusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        addDirectoryControls(context, root);

        ScrollView scroll = new ScrollView(context);
        listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        Button refresh = new Button(context);
        refresh.setText("Обновить");
        root.addView(refresh, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        refresh.setOnClickListener(view -> loadItems());

        fragmentView = root;
        loadItems();
        return root;
    }

    private void addDirectoryControls(Context context, LinearLayout root) {
        if (type == TYPE_CONVERSATIONS) {
            primaryInput = new EditText(context);
            primaryInput.setSingleLine(true);
            primaryInput.setHint("Поиск по username или имени");
            root.addView(primaryInput, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            Button search = new Button(context);
            search.setText("Найти пользователя");
            root.addView(search, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            search.setOnClickListener(view -> searchUsers());
            return;
        }

        primaryInput = new EditText(context);
        primaryInput.setSingleLine(true);
        primaryInput.setHint(type == TYPE_GROUPS ? "Название группы" : "Название канала");
        root.addView(primaryInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        usernameInput = new EditText(context);
        usernameInput.setSingleLine(true);
        usernameInput.setHint(type == TYPE_GROUPS
                ? "Username группы (необязательно)"
                : "Username канала");
        root.addView(usernameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        Button create = new Button(context);
        create.setText(type == TYPE_GROUPS ? "Создать группу" : "Создать канал");
        root.addView(create, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        create.setOnClickListener(view -> createDirectoryItem());
    }

    private void searchUsers() {
        String query = primaryInput.getText() == null ? "" : primaryInput.getText().toString().trim();
        if (query.length() == 0) {
            statusView.setText("Введите username или имя для поиска");
            return;
        }
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            statusView.setText("Ищу пользователей…");
            apiClient.get("/api/users?q=" + encoded, new WaysGramApiClient.Callback() {
                @Override
                public void onSuccess(JSONObject response) {
                    JSONArray users = response.optJSONArray("users");
                    listContainer.removeAllViews();
                    int count = users == null ? 0 : users.length();
                    for (int i = 0; i < count; i++) {
                        JSONObject item = users.optJSONObject(i);
                        if (item != null) {
                            addItem(item);
                        }
                    }
                    statusView.setText(count == 0 ? "Пользователи не найдены" : "Найдено: " + count);
                }

                @Override
                public void onError(String message, int statusCode) {
                    statusView.setText(message);
                }
            });
        } catch (Exception exception) {
            statusView.setText("Не удалось выполнить поиск");
        }
    }

    private void createDirectoryItem() {
        String title = primaryInput.getText() == null ? "" : primaryInput.getText().toString().trim();
        String username = usernameInput.getText() == null
                ? ""
                : usernameInput.getText().toString().trim().replaceFirst("^@", "").toLowerCase(Locale.US);
        if (title.length() == 0) {
            statusView.setText("Введите название");
            return;
        }
        if (title.length() > 80) {
            statusView.setText("Название должно содержать не более 80 символов.");
            return;
        }
        if (type == TYPE_CHANNELS && username.length() == 0) {
            statusView.setText("Для канала укажите username.");
            return;
        }
        if (username.length() > 0) {
            boolean validUsername = type == TYPE_GROUPS
                    ? username.matches("[a-z][a-z0-9_]{3,31}")
                    : username.matches("[a-z][a-z0-9_]{0,31}");
            if (!validUsername) {
                statusView.setText(type == TYPE_GROUPS
                        ? "Username группы: 4–32 символа, латиница, цифры и _, первый символ — буква."
                        : "Username канала: 1–32 символа, латиница, цифры и _, первый символ — буква.");
                return;
            }
        }
        JSONObject payload = new JSONObject();
        try {
            payload.put("title", title);
            if (username.length() > 0) {
                payload.put("username", username);
            }
        } catch (Exception exception) {
            statusView.setText("Не удалось подготовить запрос");
            return;
        }
        String path = type == TYPE_GROUPS ? "/api/groups" : "/api/channels";
        statusView.setText(type == TYPE_GROUPS ? "Создаю группу…" : "Создаю канал…");
        apiClient.post(path, payload, new WaysGramApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                primaryInput.setText("");
                usernameInput.setText("");
                statusView.setText(type == TYPE_GROUPS ? "Группа создана" : "Канал создан");
                loadItems();
            }

            @Override
            public void onError(String message, int statusCode) {
                statusView.setText(message);
            }
        });
    }

    private String titleForType() {
        switch (type) {
            case TYPE_GROUPS:
                return "Группы";
            case TYPE_CHANNELS:
                return "Каналы";
            default:
                return "Сообщения";
        }
    }

    private void loadItems() {
        String path;
        String key;
        if (type == TYPE_GROUPS) {
            path = "/api/groups";
            key = "groups";
        } else if (type == TYPE_CHANNELS) {
            path = "/api/channels";
            key = "channels";
        } else {
            path = "/api/conversations";
            key = "conversations";
        }
        statusView.setText("Загружаю…");
        apiClient.get(path, new WaysGramApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                JSONArray items = response.optJSONArray(key);
                listContainer.removeAllViews();
                int count = items == null ? 0 : items.length();
                for (int i = 0; i < count; i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item != null) {
                        addItem(item);
                    }
                }
                statusView.setText(count == 0 ? "Пока нет записей" : "Найдено: " + count);
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

    private void addItem(JSONObject item) {
        String title;
        String detail = "";
        long id = item.optLong("id", 0);
        if (type == TYPE_CONVERSATIONS) {
            title = item.optString("display_name", "");
            if (title.length() == 0) {
                title = item.optString("username", "Пользователь " + id);
            }
            detail = item.optString("last_message", "");
        } else if (type == TYPE_GROUPS) {
            title = item.optString("title", "Группа " + id);
            detail = item.optString("member_count", "0") + " участников";
        } else {
            title = item.optString("title", "Канал " + id);
            String username = item.optString("username", "");
            detail = username.length() > 0 ? "@" + username : "";
        }

        Button button = new Button(getParentActivity());
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setAllCaps(false);
        button.setText(detail.length() > 0 ? title + "\n" + detail : title);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = AndroidUtilities.dp(5);
        listContainer.addView(button, params);
        final String selectedTitle = title;
        button.setOnClickListener(view -> {
            if (id <= 0) {
                return;
            }
            if (type == TYPE_CONVERSATIONS) {
                presentFragment(new WaysGramChatFragment(id, selectedTitle), false);
            } else {
                presentFragment(new WaysGramTimelineFragment(
                        type == TYPE_GROUPS ? WaysGramTimelineFragment.TYPE_GROUP : WaysGramTimelineFragment.TYPE_CHANNEL,
                        id,
                        selectedTitle
                ), false);
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
