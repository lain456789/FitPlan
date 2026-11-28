package com.example.fitplan;

import android.animation.ValueAnimator;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Minimalist 2-second Apple Fitness-inspired welcome screen shown on cold launch before entering
 * {@link MainActivity}.
 */
public final class WelcomeActivity extends AppCompatActivity {
    private static final long DISPLAY_DURATION_MS = 2000L;
    private static final long RING_ANIM_DURATION_MS = 1050L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ValueAnimator ringAnimator;
    private boolean navigated;
    private final Runnable enterMain = this::openMain;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(UiKit.BG);
        root.setFitsSystemWindows(true);
        root.setOnClickListener(v -> openMain());

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(-2, -2);
        centerLp.gravity = Gravity.CENTER;
        centerLp.bottomMargin = dp(24);
        root.addView(center, centerLp);

        WelcomeRingsView rings = new WelcomeRingsView(this);
        LinearLayout.LayoutParams ringLp = new LinearLayout.LayoutParams(dp(124), dp(124));
        ringLp.bottomMargin = dp(26);
        center.addView(rings, ringLp);

        TextView brand = new TextView(this);
        SpannableString title = new SpannableString("FitPlan");
        title.setSpan(new ForegroundColorSpan(UiKit.FG), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setSpan(
                new ForegroundColorSpan(UiKit.PRIMARY), 3, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        brand.setText(title);
        brand.setTextSize(36);
        brand.setTypeface(null, Typeface.BOLD);
        brand.setLetterSpacing(0.03f);
        brand.setGravity(Gravity.CENTER);
        brand.setAlpha(0f);
        brand.setTranslationY(dp(12));
        center.addView(brand, new LinearLayout.LayoutParams(-2, -2));

        TextView tagline = new TextView(this);
        tagline.setText(R.string.welcome_tagline);
        tagline.setTextSize(14);
        tagline.setTextColor(UiKit.MUTED);
        tagline.setLetterSpacing(0.10f);
        tagline.setGravity(Gravity.CENTER);
        tagline.setAlpha(0f);
        tagline.setTranslationY(dp(14));
        LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(-2, -2);
        tagLp.topMargin = dp(8);
        center.addView(tagline, tagLp);

        TextView version = new TextView(this);
        version.setText(getString(R.string.version));
        version.setTextSize(11);
        version.setTextColor(0xFF48484A);
        version.setLetterSpacing(0.08f);
        version.setAlpha(0f);
        FrameLayout.LayoutParams verLp = new FrameLayout.LayoutParams(-2, -2);
        verLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        verLp.bottomMargin = dp(28);
        root.addView(version, verLp);

        setContentView(root);

        ringAnimator = ValueAnimator.ofFloat(0f, 1f);
        ringAnimator.setDuration(RING_ANIM_DURATION_MS);
        ringAnimator.setInterpolator(new DecelerateInterpolator(2.2f));
        ringAnimator.addUpdateListener(
                a -> rings.setProgress(((Float) a.getAnimatedValue()).floatValue()));
        ringAnimator.start();

        brand.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(160L)
                .setDuration(520L)
                .setInterpolator(new DecelerateInterpolator(2f))
                .start();

        tagline.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(300L)
                .setDuration(560L)
                .setInterpolator(new DecelerateInterpolator(2f))
                .start();

        version.animate().alpha(1f).setStartDelay(450L).setDuration(450L).start();

        handler.postDelayed(enterMain, DISPLAY_DURATION_MS);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(enterMain);
        if (ringAnimator != null) {
            ringAnimator.cancel();
        }
        super.onDestroy();
    }

    private void openMain() {
        if (navigated || isFinishing()) {
            return;
        }
        navigated = true;
        handler.removeCallbacks(enterMain);
        ActivityOptions options =
                ActivityOptions.makeCustomAnimation(
                        this, android.R.anim.fade_in, android.R.anim.fade_out);
        startActivity(new Intent(this, MainActivity.class), options.toBundle());
        finish();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Minimalist animated tri-ring emblem matching the FitPlan Activity Rings palette. */
    private static final class WelcomeRingsView extends View {
        private static final int[] RING_COLORS = {
            UiKit.RING_MOVE, UiKit.RING_EXERCISE, UiKit.RING_STAND
        };
        private static final float[] TARGET_SWEEPS = {312f, 274f, 236f};
        private static final float[] STAGGER_STARTS = {0f, 0.10f, 0.20f};

        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private float progress;

        WelcomeRingsView(Context context) {
            super(context);
            trackPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setStrokeCap(Paint.Cap.ROUND);
            arcPaint.setStyle(Paint.Style.STROKE);
            arcPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        void setProgress(float value) {
            this.progress = Math.max(0f, Math.min(1f, value));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(getWidth(), getHeight());
            if (size <= 0f) {
                return;
            }
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float stroke = size * 0.105f;
            float gap = size * 0.032f;
            trackPaint.setStrokeWidth(stroke);
            arcPaint.setStrokeWidth(stroke);

            for (int i = 0; i < RING_COLORS.length; i++) {
                float radius = size / 2f - stroke / 2f - i * (stroke + gap);
                if (radius <= 0f) {
                    continue;
                }
                oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
                int color = RING_COLORS[i];
                trackPaint.setColor(
                        Color.argb(46, Color.red(color), Color.green(color), Color.blue(color)));
                canvas.drawOval(oval, trackPaint);

                float start = STAGGER_STARTS[i];
                float local = Math.max(0f, Math.min(1f, (progress - start) / (1f - start)));
                float sweep = TARGET_SWEEPS[i] * local;
                if (sweep > 0.5f) {
                    arcPaint.setColor(color);
                    canvas.drawArc(oval, -90f, sweep, false, arcPaint);
                }
            }
        }
    }
}
