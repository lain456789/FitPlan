package com.example.fitplan;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Native, offline drawing. Text beside the rings supplies exact values and target states. */
final class ActivityRingsView extends View {
    static final int SETS = 0xffb8f261, VOLUME = 0xff5edce8, TIME = 0xffb9a0ff;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final float[] values;
    private final int[] colors = {SETS, VOLUME, TIME};
    private final String center;
    private float reveal = 1;
    private ValueAnimator animation;

    ActivityRingsView(
            Context context,
            ReportEngine.Summary data,
            ReportEngine.Targets targets,
            String center,
            String description) {
        super(context);
        this.center = center;
        values =
                new float[] {
                    RingProgress.fraction(data.sets, targets.sets),
                    RingProgress.fraction(data.volume, targets.volume),
                    RingProgress.fraction(data.duration / 60000.0, targets.minutes)
                };
        setContentDescription(description);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animation = ValueAnimator.ofFloat(0, 1);
        animation.setDuration(650);
        animation.addUpdateListener(value -> {
            reveal = (float) value.getAnimatedValue();
            invalidate();
        });
        animation.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animation != null) {
            animation.cancel();
            animation = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight()), cx = getWidth() / 2f, cy = getHeight() / 2f;
        float stroke = size * .068f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 3; i++) {
            float radius = size * .46f - i * size * .105f;
            bounds.set(cx - radius, cy - radius, cx + radius, cy + radius);
            int color = colors[i];
            paint.setColor(Color.argb(42, Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawOval(bounds, paint);
            float sweep = values[i] * reveal * 360;
            if (sweep > 0) {
                paint.setColor(color);
                canvas.drawArc(bounds, -90, sweep, false, paint);
                double angle = Math.toRadians(-90 + sweep);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(
                        cx + (float) Math.cos(angle) * radius,
                        cy + (float) Math.sin(angle) * radius,
                        stroke / 2,
                        paint);
                paint.setStyle(Paint.Style.STROKE);
            }
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(MainActivity.FG);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(size * .11f);
        paint.setFakeBoldText(true);
        canvas.drawText(center, cx, cy - (paint.ascent() + paint.descent()) / 2, paint);
        paint.setFakeBoldText(false);
    }
}
