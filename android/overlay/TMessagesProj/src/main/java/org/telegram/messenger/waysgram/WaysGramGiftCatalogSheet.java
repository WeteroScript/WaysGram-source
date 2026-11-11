package org.telegram.messenger.waysgram;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WaysGram Telegram gift storefront.
 *
 * The AuthBot admin panel controls the sale price and available stock. This
 * client displays those values and lets signed-in users purchase items into
 * their WaysGram inventory.
 */
public final class WaysGramGiftCatalogSheet extends BottomSheet {
    private final WaysGramGiftCatalogClient catalogClient;
    private final Theme.ResourcesProvider resourcesProvider;
    private final GiftAdapter adapter;
    private final Runnable onPurchaseComplete;

    private TextView statusView;
    private ProgressBar progressBar;
    private final Set<String> purchasingGiftIds = new HashSet<>();
    private AlertDialog inventoryDialog;

    public WaysGramGiftCatalogSheet(
            Context context,
            WaysGramGiftCatalogClient.SessionProvider sessionProvider,
            @Nullable Theme.ResourcesProvider resourcesProvider
    ) {
        this(context, sessionProvider, resourcesProvider, null);
    }

    public WaysGramGiftCatalogSheet(
            Context context,
            WaysGramGiftCatalogClient.SessionProvider sessionProvider,
            @Nullable Theme.ResourcesProvider resourcesProvider,
            @Nullable Runnable onPurchaseComplete
    ) {
        super(context, false, resourcesProvider);
        this.resourcesProvider = resourcesProvider;
        this.onPurchaseComplete = onPurchaseComplete;
        catalogClient = new WaysGramGiftCatalogClient(sessionProvider);
        adapter = new GiftAdapter();

        setTitle("Подарки Telegram", true);
        setApplyTopPadding(false);
        setApplyBottomPadding(true);
        setCustomView(createContent(context));

        setOnDismissListener(dialog -> {
            if (inventoryDialog != null && inventoryDialog.isShowing()) {
                inventoryDialog.dismiss();
            }
            inventoryDialog = null;
            catalogClient.close();
        });
        loadCatalog();
    }

    private View createContent(Context context) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                AndroidUtilities.dp(16),
                AndroidUtilities.dp(4),
                AndroidUtilities.dp(16),
                AndroidUtilities.dp(8)
        );

        TextView subtitle = new TextView(context);
        subtitle.setText("Магазин WaysGram · цены указаны в WaysGram Stars");
        subtitle.setTextSize(13);
        subtitle.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourcesProvider));
        subtitle.setPadding(AndroidUtilities.dp(2), 0, AndroidUtilities.dp(2), AndroidUtilities.dp(10));
        content.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Button inventoryButton = new Button(context);
        inventoryButton.setAllCaps(false);
        inventoryButton.setText("Мои подарки");
        inventoryButton.setOnClickListener(view -> showMyGifts());
        content.addView(inventoryButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        FrameLayout statusContainer = new FrameLayout(context);
        statusView = new TextView(context);
        statusView.setText("Загрузка каталога…");
        statusView.setTextSize(13);
        statusView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourcesProvider));
        statusView.setGravity(Gravity.CENTER);
        statusContainer.addView(statusView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                AndroidUtilities.dp(44)
        ));

        progressBar = new ProgressBar(context);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                AndroidUtilities.dp(24),
                AndroidUtilities.dp(24),
                Gravity.CENTER
        );
        statusContainer.addView(progressBar, progressParams);
        content.addView(statusContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                AndroidUtilities.dp(44)
        ));

        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter);
        recyclerView.setClipToPadding(false);
        recyclerView.setPadding(0, 0, 0, AndroidUtilities.dp(8));

        int availableHeight = Math.max(
                AndroidUtilities.dp(260),
                Math.min(AndroidUtilities.dp(560), AndroidUtilities.displaySize.y - AndroidUtilities.dp(220))
        );
        content.addView(recyclerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                availableHeight
        ));

        return content;
    }

    private void showMyGifts() {
        if (inventoryDialog != null && inventoryDialog.isShowing()) {
            inventoryDialog.dismiss();
        }
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle("Мои подарки")
                .setMessage("Загружаю инвентарь…")
                .setPositiveButton("Закрыть", null)
                .create();
        inventoryDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (inventoryDialog == dialog) {
                inventoryDialog = null;
            }
        });
        dialog.show();
        catalogClient.loadMyGifts(new WaysGramGiftCatalogClient.InventoryCallback() {
            @Override
            public void onLoaded(org.json.JSONArray gifts) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (isDismissed() || !dialog.isShowing()) {
                        return;
                    }
                    if (gifts.length() == 0) {
                        dialog.setMessage("Инвентарь пока пуст.");
                        return;
                    }
                    StringBuilder text = new StringBuilder();
                    for (int i = 0; i < gifts.length(); i++) {
                        JSONObject gift = gifts.optJSONObject(i);
                        if (gift == null) {
                            continue;
                        }
                        if (text.length() > 0) {
                            text.append("\n\n");
                        }
                        String name = gift.optString("gift_name", "Подарок WaysGram");
                        String serial = gift.optString("serial_number", "");
                        String telegramGiftId = gift.optString("telegram_gift_id", "");
                        text.append(name);
                        if (!serial.isEmpty()) {
                            text.append("\nСерийный номер: ").append(serial);
                        }
                        if (!telegramGiftId.isEmpty()) {
                            text.append("\nTelegram Gift ID: ").append(telegramGiftId);
                        }
                    }
                    dialog.setMessage(text.length() == 0 ? "Инвентарь пока пуст." : text.toString());
                });
            }

            @Override
            public void onError(String message) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (!isDismissed() && dialog.isShowing()) {
                        dialog.setMessage("Не удалось загрузить инвентарь: " + message);
                    }
                });
            }
        });
    }

    private void loadCatalog() {
        catalogClient.loadCatalog(new WaysGramGiftCatalogClient.CatalogCallback() {
            @Override
            public void onLoaded(List<WaysGramGiftCatalogClient.Gift> gifts, boolean stale, long syncedAt) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (isDismissed()) {
                        return;
                    }
                    adapter.setItems(gifts);
                    progressBar.setVisibility(View.GONE);
                    String state = stale ? "Показан последний сохранённый каталог" : "Каталог обновлён";
                    statusView.setText(gifts.isEmpty()
                            ? state + " · подарков нет"
                            : state + " · " + gifts.size() + " подарков");
                });
            }

            @Override
            public void onError(String message) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (isDismissed()) {
                        return;
                    }
                    progressBar.setVisibility(View.GONE);
                    statusView.setText(message);
                });
            }
        });
    }

    private void showPurchaseConfirmation(WaysGramGiftCatalogClient.Gift gift) {
        if (gift == null || !gift.forSale || !purchasingGiftIds.add(gift.id)) {
            return;
        }
        adapter.notifyDataSetChanged();
        new AlertDialog.Builder(getContext())
                .setTitle("Подтвердить покупку")
                .setMessage(
                        gift.title + "\n\nЦена: " + gift.storePriceStars
                                + " WaysGram Stars\nОсталось в магазине: " + gift.storeRemaining
                )
                .setNegativeButton("Отмена", (dialog, which) -> finishPurchaseAttempt(gift.id))
                .setPositiveButton("Купить", (dialog, which) -> {
                    statusView.setText("Покупка подарка…");
                    catalogClient.purchaseGift(gift, new WaysGramGiftCatalogClient.PurchaseCallback() {
                        @Override
                        public void onPurchased(JSONObject result) {
                            AndroidUtilities.runOnUIThread(() -> {
                                if (isDismissed()) {
                                    return;
                                }
                                finishPurchaseAttempt(gift.id);
                                JSONObject purchasedGift = result.optJSONObject("gift");
                                String serial = purchasedGift == null
                                        ? ""
                                        : purchasedGift.optString("serial_number", "");
                                int balance = result.optInt("stars", -1);
                                String confirmation = "Подарок куплен"
                                        + (serial.length() > 0 ? " · " + serial : "")
                                        + (balance >= 0 ? " · баланс " + balance + " Stars" : "");
                                statusView.setText(confirmation);
                                Toast.makeText(getContext(), confirmation, Toast.LENGTH_LONG).show();
                                loadCatalog();
                                if (onPurchaseComplete != null) {
                                    onPurchaseComplete.run();
                                }
                            });
                        }

                        @Override
                        public void onError(String message) {
                            AndroidUtilities.runOnUIThread(() -> {
                                if (isDismissed()) {
                                    return;
                                }
                                finishPurchaseAttempt(gift.id);
                                statusView.setText("Покупка не выполнена: " + message);
                            });
                        }
                    });
                })
                .setOnCancelListener(dialog -> finishPurchaseAttempt(gift.id))
                .show();
    }

    private void finishPurchaseAttempt(String giftId) {
        purchasingGiftIds.remove(giftId);
        if (!isDismissed() && adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private final class GiftAdapter extends RecyclerView.Adapter<GiftHolder> {
        private final ArrayList<WaysGramGiftCatalogClient.Gift> items = new ArrayList<>();
        private final Map<String, Bitmap> stickers = new HashMap<>();
        private final Set<String> requestedStickers = new HashSet<>();

        void setItems(List<WaysGramGiftCatalogClient.Gift> gifts) {
            items.clear();
            if (gifts != null) {
                items.addAll(gifts);
            }
            notifyDataSetChanged();
        }

        void setSticker(String giftId, Bitmap bitmap) {
            Bitmap previous = stickers.put(giftId, bitmap);
            if (previous != null && previous != bitmap && !previous.isRecycled()) {
                previous.recycle();
            }
            for (int i = 0; i < items.size(); i++) {
                if (giftId.equals(items.get(i).id)) {
                    notifyItemChanged(i);
                    return;
                }
            }
        }

        @NonNull
        @Override
        public GiftHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new GiftHolder(parent.getContext());
        }

        @Override
        public void onBindViewHolder(@NonNull GiftHolder holder, int position) {
            WaysGramGiftCatalogClient.Gift gift = items.get(position);
            holder.bind(gift, stickers.get(gift.id));
            requestSticker(gift);
        }

        private void requestSticker(WaysGramGiftCatalogClient.Gift gift) {
            if (gift.animatedSticker || gift.videoSticker || gift.stickerUrl.length() == 0
                    || stickers.containsKey(gift.id) || !requestedStickers.add(gift.id)) {
                return;
            }
            catalogClient.downloadSticker(gift, new WaysGramGiftCatalogClient.StickerCallback() {
                @Override
                public void onLoaded(byte[] bytes, String mimeType) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bitmap == null) {
                        return;
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        if (!isDismissed()) {
                            setSticker(gift.id, bitmap);
                        } else {
                            bitmap.recycle();
                        }
                    });
                }

                @Override
                public void onError(String message) {
                    // Keep the emoji placeholder if Telegram cannot deliver this sticker.
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        void clear() {
            for (Bitmap bitmap : stickers.values()) {
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
            stickers.clear();
            requestedStickers.clear();
            items.clear();
        }
    }

    private final class GiftHolder extends RecyclerView.ViewHolder {
        private final ImageView imageView;
        private final TextView emojiView;
        private final TextView titleView;
        private final TextView priceView;
        private final TextView stockView;
        private final Button buyButton;

        GiftHolder(Context context) {
            super(new LinearLayout(context));
            LinearLayout row = (LinearLayout) itemView;
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(8),
                    AndroidUtilities.dp(6), AndroidUtilities.dp(8));

            FrameLayout imageBox = new FrameLayout(context);
            GradientDrawable imageBackground = new GradientDrawable();
            imageBackground.setColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
            imageBackground.setCornerRadius(AndroidUtilities.dp(14));
            imageBox.setBackground(imageBackground);
            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageBox.addView(imageView, new FrameLayout.LayoutParams(
                    AndroidUtilities.dp(64), AndroidUtilities.dp(64), Gravity.CENTER
            ));
            emojiView = new TextView(context);
            emojiView.setTextSize(30);
            emojiView.setGravity(Gravity.CENTER);
            imageBox.addView(emojiView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            row.addView(imageBox, new LinearLayout.LayoutParams(
                    AndroidUtilities.dp(72), AndroidUtilities.dp(72)
            ));

            LinearLayout details = new LinearLayout(context);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setGravity(Gravity.CENTER_VERTICAL);
            details.setPadding(AndroidUtilities.dp(12), 0, 0, 0);

            titleView = new TextView(context);
            titleView.setTextSize(15);
            titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            details.addView(titleView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            priceView = new TextView(context);
            priceView.setTextSize(14);
            priceView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            LinearLayout.LayoutParams priceParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            priceParams.topMargin = AndroidUtilities.dp(3);
            details.addView(priceView, priceParams);

            stockView = new TextView(context);
            stockView.setTextSize(12);
            stockView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourcesProvider));
            LinearLayout.LayoutParams stockParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            stockParams.topMargin = AndroidUtilities.dp(2);
            details.addView(stockView, stockParams);

            row.addView(details, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1
            ));

            buyButton = new Button(context);
            buyButton.setText("Купить");
            buyButton.setTextSize(12);
            LinearLayout.LayoutParams buyParams = new LinearLayout.LayoutParams(
                    AndroidUtilities.dp(88), AndroidUtilities.dp(48)
            );
            buyParams.leftMargin = AndroidUtilities.dp(6);
            row.addView(buyButton, buyParams);
        }

        void bind(WaysGramGiftCatalogClient.Gift gift, @Nullable Bitmap sticker) {
            titleView.setText(gift.title);
            if (gift.forSale) {
                priceView.setText("Цена: " + gift.storePriceStars + " WaysGram Stars");
                stockView.setText("В наличии: " + gift.storeRemaining);
                stockView.setVisibility(View.VISIBLE);
                buyButton.setVisibility(View.VISIBLE);
                buyButton.setEnabled(!purchasingGiftIds.contains(gift.id));
                buyButton.setText("Купить");
                buyButton.setOnClickListener(view -> showPurchaseConfirmation(gift));
            } else {
                priceView.setText("Не выставлен на продажу");
                buyButton.setVisibility(View.GONE);
                buyButton.setOnClickListener(null);
            }
            if (!gift.forSale && gift.auction) {
                stockView.setText("Аукционный тип подарка");
                stockView.setVisibility(View.VISIBLE);
            } else if (!gift.forSale && gift.soldOut) {
                stockView.setText("Распродан");
                stockView.setVisibility(View.VISIBLE);
            } else if (!gift.forSale && gift.limited && gift.remainingCount != null && gift.totalCount != null) {
                stockView.setText("Осталось " + gift.remainingCount + " из " + gift.totalCount);
                stockView.setVisibility(View.VISIBLE);
            } else if (!gift.forSale && gift.premium) {
                stockView.setText("Требуется Premium");
                stockView.setVisibility(View.VISIBLE);
            } else if (!gift.forSale) {
                stockView.setVisibility(View.GONE);
            }
            if (sticker != null && !sticker.isRecycled()) {
                imageView.setImageBitmap(sticker);
                imageView.setVisibility(View.VISIBLE);
                emojiView.setVisibility(View.GONE);
            } else {
                imageView.setImageDrawable(null);
                imageView.setVisibility(View.GONE);
                emojiView.setText(gift.emoji.length() > 0 ? gift.emoji : "🎁");
                emojiView.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void dismiss() {
        purchasingGiftIds.clear();
        if (adapter != null) {
            adapter.clear();
        }
        super.dismiss();
    }
}
