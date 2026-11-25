package com.example.fitplan;

import static com.example.fitplan.Store.object;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

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
        TextView caption = text(parent, label, 14);
        Spinner spinner = new Spinner(activity);
        spinner.setId(android.view.View.generateViewId());
        caption.setLabelFor(spinner.getId());
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
        LinearLayout labels = item;
        if (index >= 0 && index < EXERCISE_IMAGES.length) {
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            labels = new LinearLayout(activity);
            labels.setOrientation(LinearLayout.VERTICAL);
            item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

            ImageView image = new ImageView(activity);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setImageResource(EXERCISE_IMAGES[index]);
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            int size = activity.dp(96);
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(size, size);
            layout.setMarginStart(activity.dp(8));
            item.addView(image, layout);
        }
        return labels;
    }

    int exerciseImage(String id) {
        int index = Catalog.builtIn(id);
        return index >= 0 && index < EXERCISE_IMAGES.length ? EXERCISE_IMAGES[index] : 0;
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
