package com.example.fitplan;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.SeekBar;

/** Draws prescription units against the native thumb's padded travel range. */
final class PrescriptionSeekBar extends SeekBar {
    private final int minValue;
    private final int maxValue;
    private final int interval;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    PrescriptionSeekBar(Context context, int minValue, int maxValue, int interval) {
        super(context);
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.interval = interval;
        density = getResources().getDisplayMetrics().density;
        paint.setColor(MainActivity.MUTED);
        paint.setStrokeWidth(density);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(11 * density);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float top = (getHeight() + getPaddingTop() - getPaddingBottom()) / 2f + 8 * density;
        float lastLabel = -Float.MAX_VALUE;
        float gap = paint.measureText(Integer.toString(maxValue)) + 6 * density;
        for (int value = minValue; value <= maxValue; value++) {
            boolean end = value == minValue || value == maxValue;
            if (!end && value % interval != 0) continue;
            float distance = width * (value - minValue) / (maxValue - minValue);
            float x =
                    getPaddingLeft()
                            + (getLayoutDirection() == LAYOUT_DIRECTION_RTL
                                    ? width - distance
                                    : distance);
            canvas.drawLine(x, top, x, top + 4 * density, paint);
            if (end || (distance - lastLabel >= gap && width - distance >= gap)) {
                canvas.drawText(Integer.toString(value), x, top + 17 * density, paint);
                lastLabel = distance;
            }
        }
    }
}
