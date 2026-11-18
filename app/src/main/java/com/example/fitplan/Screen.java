package com.example.fitplan;

import static com.example.fitplan.Store.object;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

abstract class Screen {
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

    void renderLogs(JSONObject session, boolean active) {
        JSONArray rows = session.optJSONArray("rows");
        for (int i = 0; i < rows.length(); i++) {
            final int rowIndex = i;
            JSONObject row = rows.optJSONObject(i);
            LinearLayout exerciseCard = card(content);
            text(exerciseCard, activity.exerciseName(row), 20);
            text(exerciseCard, s("feedback_target", row.optInt("reps")), 14);
            if (row.optBoolean("retired")) text(exerciseCard, s("replacement_retired"), 14);
            if (row.has("replacementOf")) text(exerciseCard, s("replacement_confirmed"), 14);
            JSONArray logs = row.optJSONArray("logs");
            boolean skipped = false;
            for (int j = 0; j < logs.length(); j++) {
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
                    skipped = true;
                    continue;
                }
                text(
                        exerciseCard,
                        s(
                                "log_line",
                                j + 1,
                                log.optBoolean("done") ? s("done") : s("not_done"),
                                num(log.optDouble("weight")),
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
            if (active && WorkoutAdjustments.pendingRow(row) > 0)
                button(
                        exerciseCard,
                        s("replace_remaining"),
                        () -> new TrainingScreen(activity).replaceRemaining(session, rowIndex));
            if (active
                    && !row.optBoolean("retired")
                    && (!skipped || WorkoutAdjustments.pendingRow(row) > 0))
                button(
                        exerciseCard,
                        s("add_set"),
                        () -> {
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
                            store.save("active", session);
                            show("workout");
                        });
        }
    }

    private String validEffort(String value) {
        return java.util.Arrays.asList(TrainingFeedback.EFFORTS).contains(value)
                ? value
                : "unknown";
    }
}
