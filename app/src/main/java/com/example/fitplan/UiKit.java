package com.example.fitplan;

import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.function.IntConsumer;

/**
 * Apple Fitness-inspired OLED dark design system and reusable native component kit. Eliminates
 * fixed-height text clipping across both Chinese and English, unifies Primary / Secondary / Add (+)
 * / circular Delete / compact Back buttons, and provides iOS-style segmented controls and grouped
 * row dividers.
 */
final class UiKit {
    // True OLED pure black canvas + iOS dark system gray hierarchy
    static final int BG = 0xFF000000;
    static final int CARD = 0xFF1C1C1E;
    static final int SURFACE = 0xFF2C2C2E;
    static final int SURFACE_HIGHLIGHT = 0xFF3A3A3C;
    static final int SURFACE_DEEP = 0xFF141416;
    static final int BORDER = 0xFF2C2C2E;
    static final int BORDER_STRONG = 0xFF3A3A3C;
    static final int DIVIDER = 0xFF2C2C2E;

    static final int FG = 0xFFFFFFFF;
    static final int MUTED = 0xFF8E8E93;

    // Apple Fitness signature volt-lime + classic Activity tri-ring colors
    static final int PRIMARY = 0xFFA6FF00;
    static final int ON_PRIMARY = 0xFF000000;
    static final int RING_MOVE = 0xFFFA114F;
    static final int RING_EXERCISE = 0xFF92E82A;
    static final int RING_STAND = 0xFF00D9F5;

    static final int ADD_BG = 0xFF1A260C;
    static final int ADD_BORDER = 0xFF4D6B1A;

    static final int DANGER = 0xFFFF453A;
    static final int DANGER_BG = 0xFF2C1517;
    static final int DANGER_BORDER = 0xFF5E2428;

    static final int USER_BUBBLE = 0xFF223011;
    static final int OFFLINE_BG = 0xFF332014;
    static final int OFFLINE_BORDER = 0xFF8F522B;

    static final int CHART_SKY = 0xFF00D9F5;
    static final int CHART_LAVENDER = 0xFFBF5AF2;
    static final int CHART_TEAL = 0xFF30D158;
    static final int CHART_AMBER = 0xFFFF9F0A;

    private static final int RIPPLE_LIGHT = 0x26FFFFFF;
    private static final int RIPPLE_DARK = 0x33000000;
    private static final int RIPPLE_PRIMARY = 0x33A6FF00;
    private static final int RIPPLE_DANGER = 0x33FF453A;

    private UiKit() {}

    static GradientDrawable box(MainActivity activity, int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(activity.dp(radiusDp));
        if (stroke != 0) {
            d.setStroke(activity.dp(1), stroke);
        }
        return d;
    }

    static GradientDrawable circle(MainActivity activity, int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        if (stroke != 0) {
            d.setStroke(activity.dp(1), stroke);
        }
        return d;
    }

    static Drawable rippleBox(
            MainActivity activity, int fill, int stroke, int radiusDp, int rippleColor) {
        return new RippleDrawable(
                ColorStateList.valueOf(rippleColor), box(activity, fill, stroke, radiusDp), null);
    }

    static Drawable rippleCircle(MainActivity activity, int fill, int stroke, int rippleColor) {
        return new RippleDrawable(
                ColorStateList.valueOf(rippleColor), circle(activity, fill, stroke), null);
    }

    static GradientDrawable cardSurface(MainActivity activity) {
        return box(activity, CARD, 0, 22);
    }

    static Drawable icon(MainActivity activity, int resId, int sizeDp, int tintColor) {
        Drawable raw = ContextCompat.getDrawable(activity, resId);
        if (raw == null) {
            return null;
        }
        Drawable d = raw.mutate();
        d.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN);
        int px = activity.dp(sizeDp);
        d.setBounds(0, 0, px, px);
        return d;
    }

    /** Crisp 1px iOS-style inset horizontal divider inside grouped cards. */
    static View divider(MainActivity activity, LinearLayout parent) {
        View line = new View(activity);
        line.setBackgroundColor(DIVIDER);
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1, Math.max(1, activity.dp(1)));
        lp.topMargin = activity.dp(4);
        lp.bottomMargin = activity.dp(4);
        parent.addView(line, lp);
        return line;
    }

    /** Compact stat badge chip (e.g., "14 sets", "45 min"). */
    static TextView badge(MainActivity activity, LinearLayout parent, String label, int textColor) {
        TextView tv = new TextView(activity);
        tv.setText(label);
        tv.setTextSize(12);
        tv.setTextColor(textColor);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setBackground(box(activity, SURFACE, 0, 10));
        tv.setPadding(activity.dp(9), activity.dp(4), activity.dp(9), activity.dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginEnd(activity.dp(6));
        parent.addView(tv, lp);
        return tv;
    }

    /** Tier 1: Solid Apple volt-lime Primary CTA pill (adaptive height, never clips). */
    static Button primaryButton(
            MainActivity activity, LinearLayout parent, String label, Runnable action) {
        Button b =
                makePillButton(activity, label, PRIMARY, 0, ON_PRIMARY, RIPPLE_DARK, 0, 44, true);
        attach(activity, parent, b, true, action);
        return b;
    }

    /** Tier 2: iOS elevated dark-gray Secondary pill (adaptive height, never clips). */
    static Button secondaryButton(
            MainActivity activity,
            LinearLayout parent,
            String label,
            int iconRes,
            Runnable action) {
        Button b =
                makePillButton(activity, label, SURFACE, 0, FG, RIPPLE_LIGHT, iconRes, 42, false);
        attach(activity, parent, b, true, action);
        return b;
    }

    /** Tier 3: Unified ADD (+) pill with lime tint & subtle border. */
    static Button addButton(
            MainActivity activity,
            LinearLayout parent,
            String label,
            boolean fullWidth,
            Runnable action) {
        int iconRes = label != null && label.trim().startsWith("+") ? 0 : R.drawable.ic_add;
        Button b =
                makePillButton(
                        activity,
                        label,
                        ADD_BG,
                        ADD_BORDER,
                        PRIMARY,
                        RIPPLE_PRIMARY,
                        iconRes,
                        38,
                        true);
        attach(activity, parent, b, fullWidth, action);
        return b;
    }

    /** Tier 4A: Unified DELETE / REMOVE / DISCARD crimson pill with trash icon. */
    static Button deleteButton(
            MainActivity activity,
            LinearLayout parent,
            String label,
            boolean fullWidth,
            Runnable action) {
        Button b =
                makePillButton(
                        activity,
                        label,
                        DANGER_BG,
                        DANGER_BORDER,
                        DANGER,
                        RIPPLE_DANGER,
                        R.drawable.ic_trash,
                        40,
                        true);
        attach(activity, parent, b, fullWidth, action);
        return b;
    }

    /** Tier 4B: Compact circular crimson icon-only delete button (zero text overflow). */
    static ImageButton iconDeleteButton(
            MainActivity activity,
            LinearLayout parent,
            String contentDescription,
            Runnable action) {
        ImageButton b = new ImageButton(activity);
        b.setImageDrawable(icon(activity, R.drawable.ic_trash, 18, DANGER));
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setBackground(rippleCircle(activity, DANGER_BG, DANGER_BORDER, RIPPLE_DANGER));
        b.setContentDescription(contentDescription);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(activity.dp(42), activity.dp(42));
        parent.addView(b, lp);
        return b;
    }

    /** Tier 5: Unified compact BACK (<-) pill matching iOS sub-screen navigation. */
    static Button backButton(
            MainActivity activity, LinearLayout parent, String label, Runnable action) {
        Button b =
                makePillButton(
                        activity, "\u2190 " + label, SURFACE, 0, FG, RIPPLE_LIGHT, 0, 36, false);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(activity.dp(14), activity.dp(6), activity.dp(16), activity.dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = activity.dp(2);
        lp.bottomMargin = activity.dp(6);
        parent.addView(b, lp);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    /** Compact adaptive-height pill button for headers, chips, and inline controls. */
    static Button compactPill(
            MainActivity activity,
            String label,
            int fill,
            int stroke,
            int fg,
            int iconRes,
            boolean bold) {
        return makePillButton(activity, label, fill, stroke, fg, RIPPLE_LIGHT, iconRes, 36, bold);
    }

    /**
     * iOS-style Segmented Control pill bar: clean single-tap selection replacing native Android
     * Spinners for small option sets (2-4 items).
     */
    static LinearLayout segmentedControl(
            MainActivity activity,
            LinearLayout parent,
            String[] options,
            int initialIndex,
            IntConsumer onChanged) {
        LinearLayout track = new LinearLayout(activity);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackground(box(activity, SURFACE_DEEP, 0, 14));
        track.setPadding(activity.dp(3), activity.dp(3), activity.dp(3), activity.dp(3));
        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(-1, -2);
        trackLp.topMargin = activity.dp(4);
        trackLp.bottomMargin = activity.dp(4);
        parent.addView(track, trackLp);

        int safeInitial = Math.max(0, Math.min(initialIndex, options.length - 1));
        TextView[] items = new TextView[options.length];
        Runnable[] applyStyle = new Runnable[1];
        final int[] current = {safeInitial};

        applyStyle[0] =
                () -> {
                    for (int i = 0; i < items.length; i++) {
                        boolean active = i == current[0];
                        items[i].setBackground(
                                active ? box(activity, SURFACE_HIGHLIGHT, 0, 11) : null);
                        items[i].setTextColor(active ? FG : MUTED);
                        items[i].setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
                        items[i].setSelected(active);
                    }
                };

        for (int i = 0; i < options.length; i++) {
            final int index = i;
            TextView tv = new TextView(activity);
            tv.setText(options[i]);
            tv.setTextSize(options.length >= 4 ? 12 : 13);
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine(false);
            tv.setMaxLines(2);
            tv.setMinHeight(activity.dp(36));
            int hPad = activity.dp(options.length >= 4 ? 4 : 6);
            tv.setPadding(hPad, activity.dp(7), hPad, activity.dp(7));
            tv.setOnClickListener(v -> {
                if (current[0] != index) {
                    current[0] = index;
                    applyStyle[0].run();
                    if (onChanged != null) {
                        onChanged.accept(index);
                    }
                }
            });
            track.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));
            items[i] = tv;
        }
        applyStyle[0].run();
        return track;
    }

    private static Button makePillButton(
            MainActivity activity,
            String label,
            int fill,
            int stroke,
            int fg,
            int ripple,
            int iconRes,
            int minHeightDp,
            boolean bold) {
        Button b = new Button(activity);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(fg);
        b.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL);
        b.setBackgroundTintList(null);
        b.setBackground(rippleBox(activity, fill, stroke, 22, ripple));
        b.setGravity(Gravity.CENTER);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(activity.dp(minHeightDp));
        b.setMinimumHeight(activity.dp(minHeightDp));
        b.setPadding(activity.dp(14), activity.dp(8), activity.dp(14), activity.dp(8));
        b.setStateListAnimator(null);
        if (iconRes != 0) {
            Drawable d = icon(activity, iconRes, 16, fg);
            b.setCompoundDrawablesRelative(d, null, null, null);
            b.setCompoundDrawablePadding(activity.dp(6));
        }
        return b;
    }

    private static void attach(
            MainActivity activity,
            LinearLayout parent,
            Button b,
            boolean fullWidth,
            Runnable action) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(fullWidth ? -1 : -2, -2);
        lp.topMargin = activity.dp(4);
        lp.bottomMargin = activity.dp(4);
        parent.addView(b, lp);
        b.setOnClickListener(v -> action.run());
    }
}
