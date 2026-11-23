package com.example.fitplan;

import static com.example.fitplan.Store.put;

import android.content.res.ColorStateList;
import android.database.sqlite.SQLiteException;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ToggleButton;

import org.json.JSONObject;

import java.util.Arrays;

/** Edits one set without changing its session until the user saves validated values. */
final class SetEntryDialog extends Screen {
    SetEntryDialog(MainActivity activity) {
        super(activity);
    }

    void edit(JSONObject session, int rowIndex, int logIndex, boolean active) {
        JSONObject row = session.optJSONArray("rows").optJSONObject(rowIndex);
        JSONObject log = row.optJSONArray("logs").optJSONObject(logIndex);
        LinearLayout form = column();
        text(form, s("feedback_help", row.optInt("reps")), 14);
        String initialReps =
                !active && !log.optBoolean("done") && !log.has("feedbackVersion")
                        ? "0"
                        : log.optString("reps");
        EditText weight =
                input(form, s("actual_weight"), Double.toString(log.optDouble("weight")), true);
        EditText reps = input(form, s("actual_reps"), initialReps, true);
        if (active) {
            JSONObject previous = TrainingFeedback.previousValues(row, logIndex);
            if (previous != null)
                button(
                        form,
                        s("reuse_previous_set"),
                        () -> {
                            weight.setText(previous.optString("weight"));
                            reps.setText(previous.optString("reps"));
                        });
        }
        CheckBox done = new CheckBox(activity);
        done.setText(s("set_done"));
        done.setChecked(active && !log.has("feedbackVersion") || log.optBoolean("done"));
        form.addView(done);
        String initialEffort = log.optString("effort", "unknown");
        String[] effort = {
            Arrays.asList(TrainingFeedback.EFFORTS).contains(initialEffort)
                    ? initialEffort
                    : "unknown"
        };
        effortChoices(form, effort);

        String existingReason = TrainingFeedback.reason(log, session);
        boolean[] expanded = {Arrays.asList(TrainingFeedback.REASONS).indexOf(existingReason) > 0};
        LinearLayout reasonForm = new LinearLayout(activity);
        reasonForm.setOrientation(LinearLayout.VERTICAL);
        Button reasonToggle = button(form, s("add_set_reason"), () -> {});
        form.addView(reasonForm);
        String[] reasons = new String[TrainingFeedback.REASONS.length];
        for (int i = 0; i < reasons.length; i++)
            reasons[i] = s("cause_" + TrainingFeedback.REASONS[i]);
        Spinner reason =
                select(
                        reasonForm,
                        s("incomplete_reason_label"),
                        reasons,
                        Arrays.asList(TrainingFeedback.REASONS).indexOf(existingReason));
        Runnable updateReason =
                () -> {
                    boolean belowTarget = false;
                    try {
                        belowTarget =
                                Integer.parseInt(reps.getText().toString().trim())
                                        < row.optInt("reps");
                    } catch (NumberFormatException ignored) {
                        // Numeric validation remains in the save action.
                    }
                    boolean required = !done.isChecked() || belowTarget;
                    boolean visible =
                            required || expanded[0] || reason.getSelectedItemPosition() == 4;
                    reasonForm.setVisibility(visible ? View.VISIBLE : View.GONE);
                    reasonToggle.setVisibility(
                            required || reason.getSelectedItemPosition() == 4
                                    ? View.GONE
                                    : View.VISIBLE);
                    reasonToggle.setText(s(visible ? "hide_set_reason" : "add_set_reason"));
                };
        reasonToggle.setOnClickListener(view -> {
            expanded[0] = !expanded[0];
            updateReason.run();
        });
        done.setOnCheckedChangeListener((button, checked) -> updateReason.run());
        reason.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(
                            AdapterView<?> parent, View view, int position, long id) {
                        updateReason.run();
                    }

                    public void onNothingSelected(AdapterView<?> parent) {}
                });
        reps.addTextChangedListener(
                new TextWatcher() {
                    public void beforeTextChanged(
                            CharSequence value, int start, int count, int after) {}

                    public void onTextChanged(
                            CharSequence value, int start, int before, int count) {
                        updateReason.run();
                    }

                    public void afterTextChanged(Editable value) {}
                });
        updateReason.run();
        dialog(
                s("set_title", activity.exerciseName(row), logIndex + 1),
                form,
                () -> {
                    double actualWeight = number(weight, 0, 1000);
                    int actualReps = integer(reps, 0, 100);
                    try {
                        JSONObject updated =
                                TrainingFeedback.record(
                                        session,
                                        rowIndex,
                                        logIndex,
                                        actualWeight,
                                        actualReps,
                                        done.isChecked(),
                                        effort[0],
                                        TrainingFeedback.REASONS[reason.getSelectedItemPosition()],
                                        System.currentTimeMillis());
                        if (active && done.isChecked())
                            put(updated, "restUntil", System.currentTimeMillis() + 60000);
                        store.saveSessionEdit(
                                active ? "active" : "history", session, updated, false);
                        if (active) refreshAtCurrentPosition("workout");
                        else activity.openHistory(updated);
                    } catch (TrainingFeedback.Invalid e) {
                        throw new IllegalArgumentException(s(e.key));
                    } catch (Store.StaleWorkout e) {
                        activity.activeDialog.dismiss();
                        toast(s("feedback_stale"));
                        if (active) show("workout");
                        else {
                            JSONObject current = activity.find("history", session.optString("id"));
                            if (current == null) show("history");
                            else activity.openHistory(current);
                        }
                    } catch (SQLiteException e) {
                        throw new IllegalArgumentException(s("feedback_save_error"));
                    }
                });
    }

    private void effortChoices(LinearLayout parent, String[] selected) {
        text(parent, s("effort_label"), 14);
        LinearLayout options = new LinearLayout(activity);
        options.setBaselineAligned(false);
        parent.addView(options);
        ToggleButton[] buttons = new ToggleButton[3];
        for (int i = 0; i < buttons.length; i++) {
            String code = TrainingFeedback.EFFORTS[i + 1];
            ToggleButton button = new ToggleButton(activity);
            buttons[i] = button;
            button.setTextOn(s("effort_" + code));
            button.setTextOff(s("effort_" + code));
            button.setChecked(code.equals(selected[0]));
            button.setAllCaps(false);
            button.setTextSize(14);
            button.setPadding(activity.dp(4), 0, activity.dp(4), 0);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setMinHeight(activity.dp(48));
            button.setBackgroundTintList(
                    new ColorStateList(
                            new int[][] {{android.R.attr.state_checked}, {}},
                            new int[] {MainActivity.ACCENT, MainActivity.CARD}));
            button.setTextColor(
                    new ColorStateList(
                            new int[][] {{android.R.attr.state_checked}, {}},
                            new int[] {MainActivity.BG, MainActivity.FG}));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            params.setMargins(0, 0, i == buttons.length - 1 ? 0 : activity.dp(4), 0);
            options.addView(button, params);
            button.setOnClickListener(view -> {
                selected[0] = button.isChecked() ? code : "unknown";
                for (ToggleButton other : buttons) if (other != button) other.setChecked(false);
            });
        }
    }
}
