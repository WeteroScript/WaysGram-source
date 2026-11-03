package org.telegram.messenger.waysgram;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only WaysGram Telegram gift catalogue sheet.
 *
 * Prices shown here are Telegram Stars returned by the configured AuthBot.
 * This screen intentionally does not send, purchase, transfer or auction gifts.
 */
public final class WaysGramGiftCatalogSheet extends BottomSheet {
    private final WaysGramGiftCatalogClient catalogClient;
    private final Theme.ResourcesProvider resourcesProvider;
    private final GiftAdapter adapter;

    private TextView statusView;
    private ProgressBar progressBar;

    public WaysGramGiftCatalogSheet(
            Context context,
            WaysGramGiftCatalogClient.SessionProvider sessionProvider,
            @Nullable Theme.ResourcesProvider resourcesProvider
    ) {
        super(context, false, resourcesProvider);
        this.resourcesProvider = resourcesProvider;
        catalogClient = new WaysGramGiftCatalogClient(sessionProvider);
        adapter = new GiftAdapter();

        setTitle("Подарки Telegram", true);
        setApplyTopPadding(false);
        setApplyBottomPadding(true);
        setCustomView(createContent(context));

        setOnDismissListener(dialog -> catalogClient.close());
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
        subtitle.setText("Каталог доступных подарков · цена в Telegram Stars");
        subtitle.setTextSize(13);
        subtitle.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourcesProvider));
        subtitle.setPadding(AndroidUtilities.dp(2), 0, AndroidUtilities.dp(2), AndroidUtilities.dp(10));
        content.addView(subtitle, new LinearLayout.LayoutParams(
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
                    loadStaticStickers(gifts);
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

    private void loadStaticStickers(List<WaysGramGiftCatalogClient.Gift> gifts) {
        for (WaysGramGiftCatalogClient.Gift gift : gifts) {
            if (gift.animatedSticker || gift.videoSticker) {
                continue;
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
                            adapter.setSticker(gift.id, bitmap);
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
    }

    private final class GiftAdapter extends RecyclerView.Adapter<GiftHolder> {
        private final ArrayList<WaysGramGiftCatalogClient.Gift> items = new ArrayList<>();
        private final Map<String, Bitmap> stickers = new HashMap<>();

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
            items.clear();
        }
    }

    private final class GiftHolder extends RecyclerView.ViewHolder {
        private final ImageView imageView;
        private final TextView emojiView;
        private final TextView titleView;
        private final TextView priceView;
        private final TextView stockView;

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
        }

        void bind(WaysGramGiftCatalogClient.Gift gift, @Nullable Bitmap sticker) {
            titleView.setText(gift.title);
            priceView.setText(gift.starCount + " Telegram Stars");
            if (gift.limited && gift.remainingCount != null && gift.totalCount != null) {
                stockView.setText("Осталось " + gift.remainingCount + " из " + gift.totalCount);
                stockView.setVisibility(View.VISIBLE);
            } else if (gift.premium) {
                stockView.setText("Premium");
                stockView.setVisibility(View.VISIBLE);
            } else {
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
        if (adapter != null) {
            adapter.clear();
        }
        super.dismiss();
    }
}
