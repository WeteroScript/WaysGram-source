package org.telegram.messenger.waysgram;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

/** WaysGram-only home screen backed by AuthBot's REST API. */
public final class WaysGramHomeFragment extends BaseFragment {
    private WaysGramSessionStore sessionStore;
    private WaysGramApiClient apiClient;
    private TextView profileView;
    private TextView walletView;
    private TextView statusView;

    @Override
    public boolean onFragmentCreate() {
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle("WaysGram");
        sessionStore = WaysGramSessionStore.getInstance(context);
        apiClient = new WaysGramApiClient(sessionStore);

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                AndroidUtilities.dp(18),
                AndroidUtilities.dp(18),
                AndroidUtilities.dp(18),
                AndroidUtilities.dp(28)
        );
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        profileView = addLabel(context, content, "Загрузка профиля…", 19, true);
        walletView = addLabel(context, content, "Баланс загружается…", 14, false);
        statusView = addLabel(context, content, "", 13, false);

        addButton(context, content, "Контакты", () ->
                presentFragment(new WaysGramDirectoryFragment(WaysGramDirectoryFragment.TYPE_CONTACTS), false)
        );
        addButton(context, content, "Сообщения", () ->
                presentFragment(new WaysGramDirectoryFragment(WaysGramDirectoryFragment.TYPE_CONVERSATIONS), false)
        );
        addButton(context, content, "Группы", () ->
                presentFragment(new WaysGramDirectoryFragment(WaysGramDirectoryFragment.TYPE_GROUPS), false)
        );
        addButton(context, content, "Каналы", () ->
                presentFragment(new WaysGramDirectoryFragment(WaysGramDirectoryFragment.TYPE_CHANNELS), false)
        );
        addButton(context, content, "BotFather · Мои боты", () ->
                presentFragment(new WaysGramBotsFragment(), false)
        );
        addButton(context, content, "Подарки Telegram", () ->
                new WaysGramGiftCatalogSheet(context, sessionStore, resourceProvider, this::loadProfile).show()
        );
        addButton(context, content, "Обновить профиль и баланс", this::loadProfile);
        addButton(context, content, "Выйти из WaysGram", this::logout);

        fragmentView = scroll;
        loadProfile();
        return scroll;
    }

    private TextView addLabel(Context context, LinearLayout parent, String text, int size, boolean bold) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = AndroidUtilities.dp(12);
        parent.addView(view, params);
        return view;
    }

    private void addButton(Context context, LinearLayout parent, String title, Runnable callback) {
        Button button = new Button(context);
        button.setText(title);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = AndroidUtilities.dp(7);
        parent.addView(button, params);
        button.setOnClickListener(view -> callback.run());
    }

    private void loadProfile() {
        if (apiClient == null) {
            return;
        }
        statusView.setText("Обновляю профиль…");
        apiClient.get("/api/me", new WaysGramApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                String name = response.optString("display_name", "");
                if (name.length() == 0) {
                    name = response.optString("username", "");
                }
                if (name.length() == 0) {
                    name = "Пользователь WaysGram";
                }
                profileView.setText(name + "\n" + response.optString("phone", ""));
                statusView.setText("Профиль загружен");
            }

            @Override
            public void onError(String message, int statusCode) {
                statusView.setText(message);
                if (statusCode == 401) {
                    sessionStore.clearSession();
                    presentFragment(new WaysGramLoginFragment(), true);
                }
            }
        });
        apiClient.get("/api/me/wallet", new WaysGramApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                walletView.setText(
                        "Stars: " + response.optLong("stars", 0)
                                + "    Gram: " + response.optLong("grams", 0)
                );
            }

            @Override
            public void onError(String message, int statusCode) {
                walletView.setText(message);
            }
        });
    }

    private void logout() {
        apiClient.post("/api/auth/logout", new JSONObject(), new WaysGramApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                sessionStore.clearSession();
                presentFragment(new WaysGramLoginFragment(), true);
            }

            @Override
            public void onError(String message, int statusCode) {
                // Clear local credentials even when the server cannot be reached.
                sessionStore.clearSession();
                Toast.makeText(getParentActivity(), "Локальная сессия очищена", Toast.LENGTH_SHORT).show();
                presentFragment(new WaysGramLoginFragment(), true);
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
