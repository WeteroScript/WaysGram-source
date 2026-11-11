package org.telegram.messenger.waysgram;

import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

/** Public WaysGram profile with gifts and shared relationships. */
public final class WaysGramUserProfileFragment extends BaseFragment {
    private final long userId;
    private WaysGramApiClient apiClient;
    private ImageView avatarView;
    private TextView statusView;
    private TextView identityView;
    private LinearLayout groupsContainer;
    private LinearLayout contactsContainer;
    private LinearLayout giftsContainer;

    public WaysGramUserProfileFragment(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("Invalid WaysGram user ID");
        }
        this.userId = userId;
    }

    @Override
    public boolean onFragmentCreate() {
        return true;
    }

    @Override
    public View createView(android.content.Context context) {
        apiClient = new WaysGramApiClient(WaysGramSessionStore.getInstance(context));
        actionBar.setTitle("Профиль WaysGram");

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(18),
                AndroidUtilities.dp(18), AndroidUtilities.dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        avatarView = new ImageView(context);
        avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(
                AndroidUtilities.dp(88), AndroidUtilities.dp(88)
        );
        avatarParams.gravity = Gravity.CENTER_HORIZONTAL;
        avatarParams.bottomMargin = AndroidUtilities.dp(12);
        root.addView(avatarView, avatarParams);

        identityView = addLabel(context, root, "Загружаю профиль…", 18, true);
        statusView = addLabel(context, root, "", 12, false);

        Button messageButton = new Button(context);
        messageButton.setText("Написать");
        messageButton.setAllCaps(false);
        root.addView(messageButton, matchWrap());
        messageButton.setOnClickListener(view ->
                presentFragment(new WaysGramChatFragment(userId, "Диалог"), false));

        addSection(context, root, "Общие группы");
        groupsContainer = new LinearLayout(context);
        groupsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(groupsContainer, matchWrap());

        addSection(context, root, "Общие контакты");
        contactsContainer = new LinearLayout(context);
        contactsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(contactsContainer, matchWrap());

        addSection(context, root, "Подарки пользователя");
        giftsContainer = new LinearLayout(context);
        giftsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(giftsContainer, matchWrap());

        fragmentView = scroll;
        loadProfile();
        return scroll;
    }

    private void loadProfile() {
        if (apiClient == null) {
            return;
        }
        statusView.setText("Загружаю сведения…");
        apiClient.get("/api/users/" + userId, new WaysGramApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                String name = response.optString("display_name", "").trim();
                String username = response.optString("username", "").trim();
                String bio = response.optString("bio", "").trim();
                if (name.isEmpty()) {
                    name = username.isEmpty() ? "Пользователь " + userId : username;
                }
                StringBuilder identity = new StringBuilder(name);
                if (!username.isEmpty()) {
                    identity.append("\n@").append(username);
                }
                if (!bio.isEmpty()) {
                    identity.append("\n\n").append(bio);
                }
                identityView.setText(identity.toString());

                String avatarId = response.optString("avatar_media_id", "");
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
                }

                renderGroups(response.optJSONArray("common_groups"));
                renderContacts(response.optJSONArray("common_contacts"));
                renderGifts(response.optJSONArray("gifts"));
                statusView.setText("Профиль загружен");
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

    private void renderGroups(JSONArray groups) {
        groupsContainer.removeAllViews();
        int count = groups == null ? 0 : groups.length();
        if (count == 0) {
            addLabel(getParentActivity(), groupsContainer, "Общих групп нет", 13, false);
            return;
        }
        for (int i = 0; i < count; i++) {
            JSONObject group = groups.optJSONObject(i);
            if (group == null) {
                continue;
            }
            long id = group.optLong("id", 0);
            if (id <= 0) {
                continue;
            }
            String title = group.optString("title", "Группа " + id);
            int members = group.optInt("member_count", 0);
            Button item = new Button(getParentActivity());
            item.setAllCaps(false);
            item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            item.setText(title + (members > 0 ? "\n" + members + " участников" : ""));
            groupsContainer.addView(item, matchWrap());
            item.setOnClickListener(view -> presentFragment(
                    new WaysGramTimelineFragment(WaysGramTimelineFragment.TYPE_GROUP, id, title),
                    false
            ));
        }
    }

    private void renderContacts(JSONArray contacts) {
        contactsContainer.removeAllViews();
        int count = contacts == null ? 0 : contacts.length();
        if (count == 0) {
            addLabel(getParentActivity(), contactsContainer, "Общих контактов нет", 13, false);
            return;
        }
        for (int i = 0; i < count; i++) {
            JSONObject contact = contacts.optJSONObject(i);
            if (contact == null) {
                continue;
            }
            long id = contact.optLong("id", 0);
            if (id <= 0) {
                continue;
            }
            String title = contact.optString("display_name", "").trim();
            String username = contact.optString("username", "").trim();
            if (title.isEmpty()) {
                title = username.isEmpty() ? "Пользователь " + id : username;
            }
            Button item = new Button(getParentActivity());
            item.setAllCaps(false);
            item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            item.setText(username.isEmpty() ? title : title + "\n@" + username);
            contactsContainer.addView(item, matchWrap());
            item.setOnClickListener(view ->
                    presentFragment(new WaysGramUserProfileFragment(id), false));
        }
    }

    private void renderGifts(JSONArray gifts) {
        giftsContainer.removeAllViews();
        int count = gifts == null ? 0 : gifts.length();
        if (count == 0) {
            addLabel(getParentActivity(), giftsContainer, "Публичных подарков не найдено", 13, false);
            return;
        }
        int visible = Math.min(count, 50);
        for (int i = 0; i < visible; i++) {
            JSONObject gift = gifts.optJSONObject(i);
            if (gift == null) {
                continue;
            }
            String title = gift.optString("gift_name", "Подарок");
            String serial = gift.optString("serial_number", "");
            String telegramId = gift.optString("telegram_gift_id", "");
            StringBuilder text = new StringBuilder(title);
            if (!telegramId.isEmpty()) {
                text.append("\nTelegram Gift ID: ").append(telegramId);
            }
            if (!serial.isEmpty()) {
                text.append("\nСерийный номер: ").append(serial);
            }
            addLabel(getParentActivity(), giftsContainer, text.toString(), 13, false);
        }
    }

    private void addSection(android.content.Context context, LinearLayout parent, String title) {
        TextView label = addLabel(context, parent, title, 15, true);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) label.getLayoutParams();
        params.topMargin = AndroidUtilities.dp(16);
        label.setLayoutParams(params);
    }

    private TextView addLabel(android.content.Context context, LinearLayout parent,
                              String text, int size, boolean bold) {
        TextView label = new TextView(context);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        if (bold) {
            label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        label.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(5),
                AndroidUtilities.dp(4), AndroidUtilities.dp(5));
        parent.addView(label, matchWrap());
        return label;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
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