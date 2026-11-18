package com.example.fitplan;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.PathParser;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/** Draws both views in one coordinate system so drawing and touch use the same transform. */
final class MuscleMapView extends View {
    interface Listener {
        void select(String muscle);
    }

    private static final class Shape {
        final String muscle;
        final Path path;
        final int color;
        final Region hit = new Region();

        Shape(String muscle, Path path) {
            this.muscle = muscle;
            this.path = path;
            color =
                    muscle.equals("outline")
                            ? 0xff42494c
                            : MuscleCatalog.hasPrimary(muscle) ? 0xffa4afae : 0xff657072;
            hit.setPath(path, new Region(0, 0, 1454, 1280));
        }
    }

    private final List<Shape> shapes = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Listener listener;
    private String selected = "";
    private float scale, dx, dy;

    MuscleMapView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        // The adjacent native spinner provides the same controls for accessibility.
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                context.getAssets().open("muscle_paths.json"), "UTF-8"))) {
            JSONArray paths = new JSONArray(reader.readLine());
            for (int i = 0; i < paths.length(); i++) {
                JSONArray row = paths.getJSONArray(i);
                boolean front = row.getString(0).equals("front");
                String muscle = row.getString(1);
                Path path = PathParser.createPathFromPathData(row.getString(2));
                path.offset(front ? 0 : 9, -95);
                if (muscle.equals("front_delt") || muscle.equals("rear_delt")) {
                    // Split off the lateral edge of the shoulder on both views.
                    RectF box = new RectF();
                    path.computeBounds(box, true);
                    boolean left = box.centerX() < (front ? 364 : 1091);
                    float cut =
                            left ? box.left + box.width() * .38f : box.right - box.width() * .38f;
                    Path edge = new Path();
                    edge.addRect(
                            left ? box.left - 1 : cut,
                            box.top - 1,
                            left ? cut : box.right + 1,
                            box.bottom + 1,
                            Path.Direction.CW);
                    edge.op(path, Path.Op.INTERSECT);
                    path.op(edge, Path.Op.DIFFERENCE);
                    shapes.add(new Shape("side_delt", edge));
                }
                shapes.add(new Shape(muscle, path));
            }
        } catch (IOException | JSONException e) {
            throw new IllegalStateException("Invalid bundled muscle map", e);
        }
    }

    void highlight(String muscle) {
        selected = muscle;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        scale = Math.min(w / 1454f, h / 1280f);
        dx = (w - scale * 1454) / 2;
        dy = (h - scale * 1280) / 2;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);
        for (Shape shape : shapes) {
            boolean active = shape.muscle.equals(selected);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(active ? MainActivity.ACCENT : shape.color);
            canvas.drawPath(shape.path, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(active ? 4 : 2);
            paint.setColor(active ? 0xffedffca : 0xff272e30);
            canvas.drawPath(shape.path, paint);
        }
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP && scale > 0) {
            int x = (int) ((e.getX() - dx) / scale), y = (int) ((e.getY() - dy) / scale);
            for (int i = shapes.size() - 1; i >= 0; i--) {
                Shape shape = shapes.get(i);
                if (shape.hit.contains(x, y) && !shape.muscle.equals("outline")) {
                    listener.select(shape.muscle);
                    performClick();
                    break;
                }
            }
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
