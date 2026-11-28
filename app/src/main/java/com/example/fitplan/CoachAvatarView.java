package com.example.fitplan;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.FutureTarget;
import com.bumptech.glide.signature.ObjectKey;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Framed circular avatar view for the AI Fitness Coach, featuring a neon-lime double ring, optional
 * live status indicator dot (online / thinking / offline), and local custom-photo support.
 */
final class CoachAvatarView extends FrameLayout {
    static final int STATUS_NONE = 0;
    static final int STATUS_ONLINE = 1;
    static final int STATUS_OFFLINE = 2;
    static final int STATUS_THINKING = 3;

    private static final int RING_FILL = Color.rgb(22, 28, 20);
    private static final int STATUS_ONLINE_COLOR = MainActivity.ACCENT;
    private static final int STATUS_OFFLINE_COLOR = Color.rgb(230, 138, 62);
    private static final int STATUS_THINKING_COLOR = Color.rgb(255, 213, 79);
    private static final String CUSTOM_AVATAR_NAME = "coach_avatar_custom.webp";

    private final View statusDot;
    private final int dotCutoutColor;

    CoachAvatarView(MainActivity activity, int sizeDp, boolean showStatusDot, int cutoutColor) {
        super(activity);
        this.dotCutoutColor = cutoutColor;
        int sizePx = activity.dp(sizeDp);
        int ringPadding = activity.dp(sizeDp >= 44 ? 3 : 2);
        int ringStroke = activity.dp(sizeDp >= 44 ? 2 : 1);

        FrameLayout ring = new FrameLayout(activity);
        GradientDrawable ringBg = new GradientDrawable();
        ringBg.setShape(GradientDrawable.OVAL);
        ringBg.setColor(RING_FILL);
        ringBg.setStroke(ringStroke, MainActivity.ACCENT);
        ring.setBackground(ringBg);
        ring.setPadding(ringPadding, ringPadding, ringPadding, ringPadding);
        addView(ring, new LayoutParams(sizePx, sizePx));

        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        ring.addView(image, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        File custom = customAvatarFile(activity);
        if (custom.isFile()) {
            Glide.with(activity)
                    .load(custom)
                    .signature(new ObjectKey(custom.lastModified()))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .circleCrop()
                    .placeholder(R.drawable.coach_avatar_default)
                    .error(R.drawable.coach_avatar_default)
                    .into(image);
        } else {
            Glide.with(activity).load(R.drawable.coach_avatar_default).circleCrop().into(image);
        }

        if (showStatusDot) {
            int dotSize = activity.dp(sizeDp >= 48 ? 14 : 11);
            statusDot = new View(activity);
            LayoutParams dotLp = new LayoutParams(dotSize, dotSize);
            dotLp.gravity = Gravity.BOTTOM | Gravity.END;
            addView(statusDot, dotLp);
            setStatus(STATUS_ONLINE);
        } else {
            statusDot = null;
        }
    }

    void setStatus(int status) {
        if (statusDot == null) {
            return;
        }
        if (status == STATUS_NONE) {
            statusDot.setVisibility(View.GONE);
            return;
        }
        statusDot.setVisibility(View.VISIBLE);
        int fill =
                status == STATUS_OFFLINE
                        ? STATUS_OFFLINE_COLOR
                        : status == STATUS_THINKING ? STATUS_THINKING_COLOR : STATUS_ONLINE_COLOR;
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(fill);
        int strokePx = Math.max(2, Math.round(2 * getResources().getDisplayMetrics().density));
        dotBg.setStroke(strokePx, dotCutoutColor);
        statusDot.setBackground(dotBg);
    }

    static File customAvatarFile(Context context) {
        return new File(context.getFilesDir(), CUSTOM_AVATAR_NAME);
    }

    static boolean hasCustomAvatar(Context context) {
        return customAvatarFile(context).isFile();
    }

    static boolean clearCustomAvatar(Context context) {
        File file = customAvatarFile(context);
        return !file.exists() || file.delete();
    }

    // Called off the UI thread.
    static void importCustomAvatar(Context context, Uri uri) throws Exception {
        FutureTarget<Bitmap> target =
                Glide.with(context)
                        .asBitmap()
                        .load(uri)
                        .disallowHardwareConfig()
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .submit(384, 384);
        File temp = File.createTempFile("coach-avatar-", ".webp", context.getCacheDir());
        try {
            Bitmap bitmap = target.get();
            try (FileOutputStream out = new FileOutputStream(temp)) {
                if (!bitmap.compress(Bitmap.CompressFormat.WEBP, 88, out)) {
                    throw new IOException("Failed to compress avatar");
                }
            }
            File dest = customAvatarFile(context);
            if (dest.exists() && !dest.delete()) {
                throw new IOException("Failed to replace avatar");
            }
            if (!temp.renameTo(dest)) {
                throw new IOException("Failed to move avatar");
            }
        } finally {
            Glide.with(context).clear(target);
            if (temp.exists()) {
                temp.delete();
            }
        }
    }
}
