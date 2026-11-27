package com.example.fitplan;

import static com.example.fitplan.Store.object;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.text.InputType;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.target.ImageViewTarget;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

abstract class Screen {
    private static final int[] EXERCISE_IMAGES = {
        R.drawable.builtin_0,
        R.drawable.builtin_1,
        R.drawable.builtin_2,
        R.drawable.builtin_3,
        R.drawable.builtin_4,
        R.drawable.builtin_5,
        R.drawable.builtin_6,
        R.drawable.builtin_7,
        R.drawable.builtin_8,
        R.drawable.builtin_9,
        R.drawable.builtin_10,
        R.drawable.builtin_11,
        R.drawable.builtin_12,
        R.drawable.builtin_13,
        R.drawable.builtin_14,
        R.drawable.builtin_15,
        R.drawable.builtin_16,
        R.drawable.builtin_17,
        R.drawable.builtin_18,
        R.drawable.builtin_19,
        R.drawable.builtin_20,
        R.drawable.builtin_21,
        R.drawable.builtin_22,
        R.drawable.builtin_23,
        R.drawable.builtin_24,
        R.drawable.builtin_25,
        R.drawable.builtin_26,
        R.drawable.builtin_27,
        R.drawable.builtin_28,
        R.drawable.builtin_29
    };

    private static final int[] DEMOS = {
        R.raw.demo_0, R.raw.demo_1, R.raw.demo_2, R.raw.demo_3, R.raw.demo_4,
        R.raw.demo_5, R.raw.demo_6, R.raw.demo_7, R.raw.demo_8, R.raw.demo_9,
        R.raw.demo_10, R.raw.demo_11, R.raw.demo_12, R.raw.demo_13, R.raw.demo_14,
        R.raw.demo_15, R.raw.demo_16, R.raw.demo_17, R.raw.demo_18, R.raw.demo_19,
        R.raw.demo_20, R.raw.demo_21, R.raw.demo_22, R.raw.demo_23, R.raw.demo_24,
        R.raw.demo_25, R.raw.demo_26, R.raw.demo_27, R.raw.demo_28, R.raw.demo_29
    };

    final MainActivity activity;
    final Store store;
    final LinearLayout content;
    static final String[] PARTS = Catalog.PARTS;

    Screen(MainActivity activity) {
        this.activity = activity;
        store = activity.store;
        content = activity.content;
    }

    String s(String key, Object... args) {
        return activity.s(key, args);
    }

    void show(String page) {
        activity.show(page);
    }

    String num(double n) {
        return activity.num(n);
    }

    String date(long time) {
        return activity.date(time);
    }

    String actualWeight(JSONObject log) {
        double weight = log.optDouble("weight");
        return Double.isFinite(weight)
                ? java.math.BigDecimal.valueOf(weight).stripTrailingZeros().toPlainString()
                : num(weight);
    }

    String day() {
        return activity.day();
    }

    TextView text(LinearLayout parent, String value, int size) {
        return activity.text(parent, value, size);
    }

    void title(String value) {
        text(content, value, 27).setTypeface(null, Typeface.BOLD);
    }

    LinearLayout column() {
        return activity.column();
    }

    LinearLayout card(LinearLayout parent) {
        return activity.card(parent);
    }

    Button button(LinearLayout parent, String label, Runnable action) {
        return activity.button(parent, label, action);
    }

    Button backButton(LinearLayout parent, String label, Runnable action) {
        return activity.backButton(parent, label, action);
    }

    void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }

    EditText input(LinearLayout parent, String label, String initial, boolean number) {
        TextView caption = text(parent, label, 14);
        EditText edit = new EditText(activity);
        edit.setId(android.view.View.generateViewId());
        caption.setLabelFor(edit.getId());
        edit.setSingleLine(true);
        if (number)
            edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        edit.setText(initial);
        edit.setTextColor(MainActivity.FG);
        parent.addView(edit, new LinearLayout.LayoutParams(-1, -2));
        return edit;
    }

    Spinner select(LinearLayout parent, String label, String[] options, int selected) {
        return select(parent, label, options, selected, true);
    }

    Spinner select(
            LinearLayout parent, String label, String[] options, int selected, boolean showLabel) {
        Spinner spinner = new Spinner(activity);
        spinner.setId(android.view.View.generateViewId());
        if (showLabel) {
            TextView caption = text(parent, label, 14);
            caption.setLabelFor(spinner.getId());
        } else {
            spinner.setContentDescription(label);
        }
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, Math.min(selected, options.length - 1)));
        parent.addView(spinner, new LinearLayout.LayoutParams(-1, activity.dp(52)));
        return spinner;
    }

    double number(EditText edit, double min, double max) {
        try {
            double n = Double.parseDouble(edit.getText().toString());
            if (Double.isFinite(n) && n >= min && n <= max) return n;
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException(s("range_error", num(min), num(max)));
    }

    int integer(EditText edit, int min, int max) {
        double n = number(edit, min, max);
        if (n != Math.floor(n)) throw new IllegalArgumentException(s("integer_error"));
        return (int) n;
    }

    void dialog(String title, LinearLayout form, Runnable save) {
        dialog(title, form, s("save"), save);
    }

    void dialog(String title, LinearLayout form, String positive, Runnable save) {
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);
        AlertDialog d =
                new AlertDialog.Builder(activity)
                        .setTitle(title)
                        .setView(scroll)
                        .setNegativeButton(s("cancel"), null)
                        .setPositiveButton(positive, null)
                        .create();
        activity.activeDialog = d;
        d.setOnShowListener(v ->
                d.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(w -> {
                            try {
                                save.run();
                                d.dismiss();
                            } catch (IllegalArgumentException e) {
                                toast(e.getMessage());
                            }
                        }));
        d.show();
    }

    void confirm(String message, Runnable action) {
        activity.activeDialog =
                new AlertDialog.Builder(activity)
                        .setMessage(message)
                        .setNegativeButton(s("cancel"), null)
                        .setPositiveButton(s("confirm"), (d, w) -> action.run())
                        .show();
    }

    int performedSets(JSONObject session) {
        int count = 0;
        JSONArray rows = session.optJSONArray("rows");
        if (rows == null) return 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONArray logs = rows.optJSONObject(i).optJSONArray("logs");
            if (logs != null)
                for (int j = 0; j < logs.length(); j++)
                    if (logs.optJSONObject(j).optBoolean("done")) count++;
        }
        return count;
    }

    double volume(JSONObject session) {
        double total = 0;
        JSONArray rows = session.optJSONArray("rows");
        if (rows == null) return 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONArray logs = rows.optJSONObject(i).optJSONArray("logs");
            if (logs != null)
                for (int j = 0; j < logs.length(); j++) {
                    JSONObject log = logs.optJSONObject(j);
                    if (log.optBoolean("done"))
                        total += log.optDouble("weight") * log.optInt("reps");
                }
        }
        return total;
    }

    LinearLayout illustratedHeader(LinearLayout item, String id) {
        int index = Catalog.builtIn(id);
        return headerWithImage(item, index >= 0 ? staticOrDemo(index, false) : null);
    }

    View exerciseThumbnail(String id, String name) {
        int index = Catalog.builtIn(id);
        if (index >= 0) return staticOrDemo(index, true);
        CustomMedia files = CustomMedia.existing(activity, id);
        if (files == null) return null;
        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Glide.with(activity).asBitmap().load(files.thumb).diskCacheStrategy(DiskCacheStrategy.NONE).into(image);
        boolean animated = files.gif.isFile();
        return clickableThumbnail(image, animated, s(animated ? "exercise_demo" : "exercise_photo", name),
                () -> showCustomMedia(files, animated, name));
    }

    private void showCustomMedia(CustomMedia files, boolean animated, String name) {
        ImageView full = new ImageView(activity);
        full.setAdjustViewBounds(true);
        full.setMinimumHeight(activity.dp(240));
        full.setScaleType(ImageView.ScaleType.FIT_CENTER);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(name).setView(full)
                .setPositiveButton(s("demo_close"), null).create();
        dialog.setOnDismissListener(d -> Glide.with(activity.getApplicationContext()).clear(full));
        dialog.show();
        if (animated) {
            Glide.with(activity).asGif().load(files.gif).override(1024, 1024)
                    .diskCacheStrategy(DiskCacheStrategy.NONE).into(new ImageViewTarget<GifDrawable>(full) {
                        @Override protected void setResource(GifDrawable gif) {
                            if (gif != null) gif.setLoopCount(GifDrawable.LOOP_FOREVER);
                            full.setImageDrawable(gif);
                        }
                    });
        } else {
            Glide.with(activity).asBitmap().load(files.photo).override(1280, 1280)
                    .diskCacheStrategy(DiskCacheStrategy.NONE).into(full);
        }
    }

    LinearLayout headerWithImage(LinearLayout item, View thumbnail) {
        LinearLayout labels = item;
        if (thumbnail != null) {
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            labels = new LinearLayout(activity);
            labels.setOrientation(LinearLayout.VERTICAL);
            item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

            int size = activity.dp(96);
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(size, size);
            layout.setMarginStart(activity.dp(8));
            item.addView(thumbnail, layout);
        }
        return labels;
    }

    private View clickableThumbnail(ImageView image, boolean animated, String title, Runnable open) {
        FrameLayout frame = new FrameLayout(activity);
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
        if (animated) {
            TextView badge = new TextView(activity);
            badge.setText("▶");
            badge.setTextSize(12);
            badge.setTextColor(0xFFFFFFFF);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(activity.shape(0xCC262A30, 16));
            badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            int size = activity.dp(24);
            frame.addView(badge, new FrameLayout.LayoutParams(size, size, Gravity.BOTTOM | Gravity.END));
        }
        frame.setContentDescription(title);
        frame.setFocusable(true);
        frame.setOnClickListener(v -> open.run());
        return frame;
    }

    private View staticOrDemo(int index, boolean preview) {
        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageResource(EXERCISE_IMAGES[index]);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        String title = s("exercise_demo", s("ex" + index));
        return preview ? clickableThumbnail(image, true, title, () -> showDemo(index, title)) : image;
    }

    private void showDemo(int index, String title) {
        FrameLayout stage = new FrameLayout(activity);
        VideoView video = new VideoView(activity);
        stage.addView(video, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(stage)
                .setPositiveButton(s("demo_close"), null)
                .create();
        dialog.setOnDismissListener(d -> video.stopPlayback());
        video.setOnPreparedListener(player -> {
            player.setVolume(0f, 0f);
            player.setLooping(true);
        });
        dialog.show();
        video.setVideoURI(Uri.parse("android.resource://" + activity.getPackageName() + "/" + DEMOS[index]));
        video.start();
    }

    /** Fixed order for exercise pickers: by body part, then localized name, then id. */
    void sortExercises(List<JSONObject> exercises) {
        exercises.sort(
                (a, b) -> {
                    String idA = a.optString("exerciseId", a.optString("id"));
                    String idB = b.optString("exerciseId", b.optString("id"));
                    int pa = Catalog.partIndex(Catalog.exercisePart(idA, a.optString("part")));
                    int pb = Catalog.partIndex(Catalog.exercisePart(idB, b.optString("part")));
                    if (pa != pb) return Integer.compare(pa, pb);
                    String na = activity.exerciseName(a);
                    String nb = activity.exerciseName(b);
                    int cmp = na.compareToIgnoreCase(nb);
                    return cmp != 0 ? cmp : idA.compareTo(idB);
                });
    }

    void refreshAtCurrentPosition(String route) {
        ScrollView scroll = activity.findViewById(R.id.scroller);
        int position = scroll.getScrollY();
        show(route);
        scroll.post(() -> scroll.scrollTo(0, position));
    }

    void renderLogs(JSONObject session, boolean active, LinearLayout parent, Runnable onSetChange) {
        JSONArray rows = session.optJSONArray("rows");
        for (int i = 0; i < rows.length(); i++) {
            if (active && i != session.optInt("displayRow", 0)) continue;
            final int rowIndex = i;
            JSONObject row = rows.optJSONObject(i);
            LinearLayout exerciseCard = card(parent);
            LinearLayout title = exerciseCard;
            if (active) {
                text(exerciseCard, s("exercise_position", i + 1, rows.length()), 14);
                LinearLayout header = new LinearLayout(activity);
                exerciseCard.addView(header);
                title = illustratedHeader(header, row.optString("exerciseId"));
            }
            text(title, activity.exerciseName(row), 20);
            text(
                    exerciseCard,
                    row.optBoolean("actualOnly")
                            ? s("manual_actual_only")
                            : s("feedback_target", row.optInt("reps")),
                    14);
            if (row.optBoolean("retired")) text(exerciseCard, s("replacement_retired"), 14);
            if (row.has("replacementOf")) text(exerciseCard, s("replacement_confirmed"), 14);
            LinearLayout details = new LinearLayout(activity);
            details.setOrientation(LinearLayout.VERTICAL);
            exerciseCard.addView(details);
            if (!active || row.optBoolean("retired")) {
                renderSetDetails(session, rowIndex, active, details);
                continue;
            }

            JSONArray logs = row.optJSONArray("logs");
            LinearLayout controls = new LinearLayout(activity);
            exerciseCard.addView(controls);
            Button remove = button(controls, s("remove_set"), () -> {});
            Button add = button(controls, s("add_set"), () -> {});
            Runnable refresh =
                    () -> {
                        details.removeAllViews();
                        renderSetDetails(session, rowIndex, true, details);
                        remove.setEnabled(WorkoutAdjustments.pendingRow(row) > 0);
                        boolean skipped = false;
                        for (int j = 0; j < logs.length(); j++) {
                            JSONObject log = logs.optJSONObject(j);
                            if (log.optBoolean("skipped") && !log.optBoolean("done"))
                                skipped = true;
                        }
                        add.setEnabled(!skipped || remove.isEnabled());
                    };
            remove.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            add.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            Runnable saved =
                    () -> {
                        store.save("active", session);
                        refresh.run();
                        onSetChange.run();
                    };
            remove.setOnClickListener(v -> {
                for (int j = logs.length() - 1; j >= 0; j--) {
                    if (WorkoutAdjustments.isPending(logs.optJSONObject(j))) {
                        logs.remove(j);
                        saved.run();
                        return;
                    }
                }
            });
            add.setOnClickListener(v -> {
                if (logs.length() >= 20) {
                    toast(s("max_sets"));
                    return;
                }
                logs.put(
                        object(
                                "weight",
                                row.optDouble("weight"),
                                "reps",
                                row.optInt("reps"),
                                "done",
                                false));
                saved.run();
            });
            refresh.run();
        }
    }

    private void renderSetDetails(
            JSONObject session, int rowIndex, boolean active, LinearLayout exerciseCard) {
        JSONObject row = session.optJSONArray("rows").optJSONObject(rowIndex);
        JSONArray logs = row.optJSONArray("logs");
        int currentSet = -1;
        int skippedSets = 0;
        for (int j = 0; j < logs.length(); j++) {
            JSONObject log = logs.optJSONObject(j);
            if (WorkoutAdjustments.isPending(log)) {
                if (currentSet < 0) currentSet = j;
            }
            if (log.optBoolean("skipped") && !log.optBoolean("done")) skippedSets++;
        }
        if (active && currentSet < 0) text(exerciseCard, s("no_pending_sets"), 16);
        int scheduledIndex = 0;
        for (int j = 0; j < logs.length(); j++) {
            JSONObject entry = logs.optJSONObject(j);
            if (!entry.optBoolean("skipped") || entry.optBoolean("done")) scheduledIndex++;
            if (active && j != currentSet) continue;
            final int index = j;
            JSONObject log = logs.optJSONObject(j);
            if (log.optBoolean("skipped") && !log.optBoolean("done")) {
                text(
                        exerciseCard,
                        s(
                                "equipment".equals(log.optString("skipReason"))
                                        ? "set_replaced"
                                        : "set_time_cancelled",
                                j + 1),
                        14);
                continue;
            }
            if (!active && session.optBoolean("abandoned") && WorkoutAdjustments.isPending(log)) {
                text(exerciseCard, s("set_unrecorded", j + 1), 14);
                continue;
            }
            text(
                    exerciseCard,
                    s(
                            "log_line",
                            active ? scheduledIndex : j + 1,
                            active
                                    ? s("set_total", logs.length() - skippedSets)
                                    : log.optBoolean("done") ? s("done") : s("not_done"),
                            actualWeight(log),
                            log.optInt("reps")),
                    16);
            if (log.optBoolean("done") || log.optBoolean("resolved") || !active)
                text(
                        exerciseCard,
                        s(
                                "feedback_record_line",
                                s("effort_" + validEffort(log.optString("effort", "unknown"))),
                                s("cause_" + TrainingFeedback.reason(log, session))),
                        14);
            button(
                    exerciseCard,
                    s(
                            log.optBoolean("done") || log.optBoolean("resolved")
                                    ? "edit_set"
                                    : "record_set",
                            j + 1),
                    () -> new SetEntryDialog(activity).edit(session, rowIndex, index, active));
        }
        if (active) {
            java.util.ArrayList<Integer> recorded = new java.util.ArrayList<>();
            java.util.ArrayList<String> labels = new java.util.ArrayList<>();
            for (int j = 0; j < logs.length(); j++) {
                JSONObject log = logs.optJSONObject(j);
                if (!log.optBoolean("skipped")
                        && (log.optBoolean("done") || log.optBoolean("resolved"))) {
                    recorded.add(j);
                    labels.add(s("edit_set", j + 1));
                }
            }
            if (!recorded.isEmpty())
                button(
                        exerciseCard,
                        s("review_recorded"),
                        () ->
                                new AlertDialog.Builder(activity)
                                        .setTitle(s("review_recorded"))
                                        .setItems(
                                                labels.toArray(new String[0]),
                                                (dialog, which) ->
                                                        new SetEntryDialog(activity)
                                                                .edit(
                                                                        session,
                                                                        rowIndex,
                                                                        recorded.get(which),
                                                                        true))
                                        .show());
        }
        if (active && WorkoutAdjustments.pendingRow(row) > 0)
            button(
                    exerciseCard,
                    s("replace_remaining"),
                    () -> new TrainingScreen(activity).replaceRemaining(session, rowIndex));
    }

    private String validEffort(String value) {
        return java.util.Arrays.asList(TrainingFeedback.EFFORTS).contains(value)
                ? value
                : "unknown";
    }
}
