package org.telegram.messenger.waysgram;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputFilter;
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
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

/** WaysGram-only home screen backed by AuthBot's REST API. */
public final class WaysGramHomeFragment extends BaseFragment {
    private static final int REQUEST_AVATAR = 7106;
    private WaysGramSessionStore sessionStore;
    private WaysGramApiClient apiClient;
    private TextView profileView;
    private TextView walletView;
    private TextView statusView;
    private ImageView avatarView;
    private String currentDisplayName = "";
    private String currentUsername = "";
    private String currentBio = "";
    private String currentAvatarMediaId = "";

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

        avatarView = new ImageView(context);
        avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable avatarPlaceholder = new GradientDrawable();
        avatarPlaceholder.setShape(GradientDrawable.OVAL);
        avatarPlaceholder.setColor(Color.LTGRAY);
        avatarView.setBackground(avatarPlaceholder);
        avatarView.setClipToOutline(true);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(
                AndroidUtilities.dp(88), AndroidUtilities.dp(88)
        );
        avatarParams.bottomMargin = AndroidUtilities.dp(12);
        content.addView(avatarView, avatarParams);

        profileView = addLabel(context, content, "Загрузка профиля…", 19, true);
        walletView = addLabel(context, content, "Баланс загружается…", 14, false);
        statusView = addLabel(context, content, "", 13, false);

        addButton(context, content, "Редактировать профиль", this::showProfileEditor);
        addButton(context, content, "Фото профиля", this::showAvatarActions);
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
                currentDisplayName = response.optString("display_name", "");
                currentUsername = response.optString("username", "");
                currentBio = response.optString("bio", "");
                String avatarId = response.optString("avatar_media_id", "");
                String name = currentDisplayName;
                if (name.length() == 0) {
                    name = currentUsername;
                }
                if (name.length() == 0) {
                    name = "Пользователь WaysGram";
                }
                String profileText = name;
                if (!currentUsername.isEmpty()) {
                    profileText += "\n@" + currentUsername;
                }
                String phone = response.optString("phone", "");
                if (!phone.isEmpty()) {
                    profileText += "\n" + phone;
                }
                if (!currentBio.isEmpty()) {
                    profileText += "\n" + currentBio;
                }
                profileView.setText(profileText);
                if (!avatarId.equals(currentAvatarMediaId)) {
                    currentAvatarMediaId = avatarId;
                    if (avatarId.matches("[a-f0-9]{32}")) {
                        apiClient.downloadImage(avatarId, new WaysGramApiClient.ImageCallback() {
                            @Override
                            public void onSuccess(Bitmap bitmap) {
                                if (avatarView != null) {
                                    avatarView.setImageBitmap(bitmap);
                                }
                            }

                            @Override
                            public void onError(String message, int statusCode) {
                                if (avatarView != null) {
                                    avatarView.setImageDrawable(null);
                                }
                            }
                        });
                    } else {
                        avatarView.setImageDrawable(null);
                    }
                }
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

    private void showProfileEditor() {
        LinearLayout form = new LinearLayout(getParentActivity());
        form.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = AndroidUtilities.dp(20);
        form.setPadding(horizontalPadding, AndroidUtilities.dp(8), horizontalPadding, 0);

        EditText nameInput = new EditText(getParentActivity());
        nameInput.setSingleLine(true);
        nameInput.setHint("Имя и фамилия");
        nameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(60)});
        nameInput.setText(currentDisplayName);
        form.addView(nameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        EditText usernameInput = new EditText(getParentActivity());
        usernameInput.setSingleLine(true);
        usernameInput.setHint("Username без @");
        usernameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        usernameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
        usernameInput.setText(currentUsername);
        form.addView(usernameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        EditText bioInput = new EditText(getParentActivity());
        bioInput.setHint("О себе · до 140 символов");
        bioInput.setSingleLine(false);
        bioInput.setMaxLines(3);
        bioInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(140)});
        bioInput.setText(currentBio);
        form.addView(bioInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        new AlertDialog.Builder(getParentActivity())
                .setTitle("Редактировать профиль")
                .setView(form)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    JSONObject payload = new JSONObject();
                    try {
                        payload.put("display_name", nameInput.getText() == null ? "" : nameInput.getText().toString());
                        payload.put("username", usernameInput.getText() == null ? "" : usernameInput.getText().toString());
                        payload.put("bio", bioInput.getText() == null ? "" : bioInput.getText().toString());
                    } catch (Exception exception) {
                        statusView.setText("Не удалось подготовить профиль");
                        return;
                    }
                    statusView.setText("Сохраняю профиль…");
                    apiClient.put("/api/me", payload, new WaysGramApiClient.Callback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            statusView.setText("Профиль обновлён");
                            loadProfile();
                        }

                        @Override
                        public void onError(String message, int statusCode) {
                            statusView.setText(message);
                        }
                    });
                })
                .show();
    }

    private void showAvatarActions() {
        String[] actions = currentAvatarMediaId.isEmpty()
                ? new String[]{"Выбрать фото"}
                : new String[]{"Выбрать фото", "Удалить фото"};
        new AlertDialog.Builder(getParentActivity())
                .setTitle("Фото профиля")
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("image/*");
                        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                        startActivityForResult(intent, REQUEST_AVATAR);
                    } else {
                        new AlertDialog.Builder(getParentActivity())
                                .setTitle("Удалить фото профиля?")
                                .setNegativeButton("Отмена", null)
                                .setPositiveButton("Удалить", (confirm, selected) ->
                                        apiClient.delete("/api/me/avatar", new WaysGramApiClient.Callback() {
                                            @Override
                                            public void onSuccess(JSONObject response) {
                                                currentAvatarMediaId = "";
                                                avatarView.setImageDrawable(null);
                                                statusView.setText("Фото профиля удалено");
                                                loadProfile();
                                            }

                                            @Override
                                            public void onError(String message, int statusCode) {
                                                statusView.setText(message);
                                            }
                                        }))
                                .show();
                    }
                })
                .show();
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_AVATAR) {
            return;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri imageUri = data.getData();
        String mimeType = getParentActivity().getContentResolver().getType(imageUri);
        if (mimeType == null || !mimeType.startsWith("image/")) {
            statusView.setText("Выберите файл изображения");
            return;
        }
        statusView.setText("Загружаю фото профиля…");
        apiClient.uploadImage(getParentActivity().getContentResolver(), imageUri, new WaysGramApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                String mediaId = response.optString("id", "");
                if (!mediaId.matches("[a-f0-9]{32}")) {
                    statusView.setText("Сервер вернул некорректный ID изображения");
                    return;
                }
                JSONObject payload = new JSONObject();
                try {
                    payload.put("media_id", mediaId);
                } catch (Exception exception) {
                    statusView.setText("Не удалось подготовить фото профиля");
                    return;
                }
                apiClient.put("/api/me/avatar", payload, new WaysGramApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject avatar) {
                        statusView.setText("Фото профиля обновлено");
                        loadProfile();
                    }

                    @Override
                    public void onError(String message, int statusCode) {
                        statusView.setText(message);
                    }
                });
            }

            @Override
            public void onError(String message, int statusCode) {
                statusView.setText(message);
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
