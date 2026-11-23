package com.example.fitplan;

import static com.example.fitplan.Store.put;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.database.sqlite.SQLiteException;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/** Draft-only entry form: the database is touched only after the final validated save. */
final class ManualHistoryScreen extends Screen {
    ManualHistoryScreen(MainActivity activity) {
        super(activity);
    }

    void render() {
        if (activity.manualHistoryDraft == null)
            activity.manualHistoryDraft = ManualHistory.draft(System.currentTimeMillis());
        JSONObject draft = activity.manualHistoryDraft;
        title(s("manual_title"));
        text(content, s("manual_help"), 14);
        button(content, s("back_history"), () -> show("history"));
        LinearLayout info = card(content);
        EditText name = input(info, s("manual_name"), draft.optString("name"), false);
        name.setTag("manual_name");
        name.setFilters(new InputFilter[] {new InputFilter.LengthFilter(80)});
        bind(name, draft, "name");
        Button date = button(info, "", () -> chooseDate(draft));
        date.setTag("manual_date");
        date.setText(
                s(
                        "manual_date",
                        DateFormat.getDateInstance(DateFormat.MEDIUM, activity.locale())
                                .format(new Date(draft.optLong("finished")))));
        Button time = button(info, "", () -> chooseTime(draft));
        time.setText(
                s(
                        "manual_time",
                        DateFormat.getTimeInstance(DateFormat.SHORT, activity.locale())
                                .format(new Date(draft.optLong("finished")))));
        EditText duration = input(info, s("manual_duration"), draft.optString("duration"), true);
        duration.setTag("manual_duration");
        duration.setInputType(InputType.TYPE_CLASS_NUMBER);
        bind(duration, draft, "duration");
        text(info, s("manual_actual_only"), 14);

        JSONArray rows = draft.optJSONArray("rows");
        for (int i = 0; i < rows.length(); i++) {
            int rowIndex = i;
            JSONObject row = rows.optJSONObject(i);
            LinearLayout item = card(content);
            LinearLayout header = new LinearLayout(activity);
            item.addView(header);
            text(
                    illustratedHeader(header, row.optString("exerciseId")),
                    activity.exerciseName(row),
                    19);
            JSONArray logs = row.optJSONArray("logs");
            if (logs.length() == 0) text(item, s("manual_need_set"), 14);
            for (int j = 0; j < logs.length(); j++) {
                int setIndex = j;
                JSONObject log = logs.optJSONObject(j);
                text(
                        item,
                        s(
                                "manual_set_line",
                                j + 1,
                                actualWeight(log),
                                log.optInt("reps"),
                                s("effort_" + log.optString("effort"))),
                        16);
                LinearLayout controls = new LinearLayout(activity);
                item.addView(controls);
                Button edit = button(controls, s("edit_set", j + 1), () -> editSet(row, setIndex));
                edit.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
                Button remove =
                        button(
                                controls,
                                s("manual_remove_set"),
                                () -> {
                                    logs.remove(setIndex);
                                    refreshAtCurrentPosition("manualHistory");
                                });
                remove.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            }
            button(
                    item,
                    s("manual_add_set"),
                    () -> {
                        if (logs.length() >= ManualHistory.MAX_SETS) toast(s("manual_set_limit"));
                        else editSet(row, -1);
                    });
            button(
                    item,
                    s("remove_exercise"),
                    () ->
                            confirm(
                                    s("manual_remove_exercise_confirm"),
                                    () -> {
                                        rows.remove(rowIndex);
                                        refreshAtCurrentPosition("manualHistory");
                                    }));
        }
        button(
                content,
                s("manual_discard"),
                () ->
                        confirm(
                                s("manual_discard_confirm"),
                                () -> {
                                    activity.manualHistoryDraft = null;
                                    show("history");
                                }));
        LinearLayout actions = activity.findViewById(R.id.home_actions);
        actions.setVisibility(View.VISIBLE);
        LinearLayout actionRow = new LinearLayout(activity);
        actions.addView(actionRow);
        Button add = button(actionRow, s("add_exercise"), () -> chooseExercise(draft));
        add.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        Button save = button(actionRow, s("manual_save"), () -> save(draft));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, -2, 1);
        saveParams.setMarginStart(activity.dp(8));
        save.setLayoutParams(saveParams);
        save.setTextColor(MainActivity.BG);
        save.setBackgroundTintList(android.content.res.ColorStateList.valueOf(MainActivity.ACCENT));
    }

    private void bind(EditText input, JSONObject target, String key) {
        input.addTextChangedListener(
                new TextWatcher() {
                    public void beforeTextChanged(
                            CharSequence value, int start, int count, int after) {}

                    public void onTextChanged(
                            CharSequence value, int start, int before, int count) {
                        put(target, key, value.toString());
                    }

                    public void afterTextChanged(Editable value) {}
                });
    }

    private Calendar endTime(JSONObject draft) {
        Calendar value = Calendar.getInstance();
        value.setTimeInMillis(draft.optLong("finished"));
        return value;
    }

    private void chooseDate(JSONObject draft) {
        Calendar value = endTime(draft);
        DatePickerDialog picker =
                new DatePickerDialog(
                        activity,
                        (view, year, month, day) -> {
                            value.set(year, month, day);
                            put(draft, "finished", value.getTimeInMillis());
                            refreshAtCurrentPosition("manualHistory");
                        },
                        value.get(Calendar.YEAR),
                        value.get(Calendar.MONTH),
                        value.get(Calendar.DAY_OF_MONTH));
        picker.getDatePicker().setMaxDate(System.currentTimeMillis());
        picker.getDatePicker().setMinDate(0);
        picker.show();
    }

    private void chooseTime(JSONObject draft) {
        Calendar value = endTime(draft);
        new TimePickerDialog(
                        activity,
                        (view, hour, minute) -> {
                            value.set(Calendar.HOUR_OF_DAY, hour);
                            value.set(Calendar.MINUTE, minute);
                            value.set(Calendar.SECOND, 0);
                            value.set(Calendar.MILLISECOND, 0);
                            put(draft, "finished", value.getTimeInMillis());
                            refreshAtCurrentPosition("manualHistory");
                        },
                        value.get(Calendar.HOUR_OF_DAY),
                        value.get(Calendar.MINUTE),
                        true)
                .show();
    }

    private void chooseExercise(JSONObject draft) {
        JSONArray rows = draft.optJSONArray("rows");
        if (rows.length() >= ManualHistory.MAX_EXERCISES) {
            toast(s("manual_exercise_limit"));
            return;
        }
        JSONArray catalogue = store.list("exercise");
        List<JSONObject> matches = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        LinearLayout form = column();
        EditText search = input(form, s("manual_search"), "", false);
        ListView list = new ListView(activity);
        form.addView(list, new LinearLayout.LayoutParams(-1, activity.dp(280)));
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, labels);
        list.setAdapter(adapter);
        Runnable filter =
                () -> {
                    String query =
                            search.getText().toString().trim().toLowerCase(activity.locale());
                    matches.clear();
                    labels.clear();
                    for (int i = 0; i < catalogue.length(); i++) {
                        JSONObject exercise = catalogue.optJSONObject(i);
                        String label =
                                activity.exerciseName(exercise)
                                        + " · "
                                        + activity.partName(exercise.optString("part"));
                        if (label.toLowerCase(activity.locale()).contains(query)) {
                            matches.add(exercise);
                            labels.add(label);
                        }
                    }
                    adapter.notifyDataSetChanged();
                };
        search.addTextChangedListener(
                new TextWatcher() {
                    public void beforeTextChanged(
                            CharSequence value, int start, int count, int after) {}

                    public void onTextChanged(
                            CharSequence value, int start, int before, int count) {
                        filter.run();
                    }

                    public void afterTextChanged(Editable value) {}
                });
        filter.run();
        AlertDialog picker =
                new AlertDialog.Builder(activity)
                        .setTitle(s("choose_exercise"))
                        .setView(form)
                        .setNegativeButton(s("cancel"), null)
                        .create();
        activity.activeDialog = picker;
        list.setOnItemClickListener((parent, view, position, itemId) -> {
            rows.put(ManualHistory.row(matches.get(position)));
            picker.dismiss();
            show("manualHistory");
        });
        picker.show();
    }

    private void editSet(JSONObject row, int index) {
        JSONArray logs = row.optJSONArray("logs");
        JSONObject existing = index < 0 ? null : logs.optJSONObject(index);
        LinearLayout form = column();
        text(form, s("manual_set_help"), 14);
        EditText weight =
                input(
                        form,
                        s("actual_weight"),
                        existing == null ? "" : existing.optString("weight"),
                        true);
        weight.setTag("manual_weight");
        EditText reps =
                input(
                        form,
                        s("actual_reps"),
                        existing == null ? "" : existing.optString("reps"),
                        true);
        reps.setTag("manual_reps");
        reps.setInputType(InputType.TYPE_CLASS_NUMBER);
        String[] efforts = new String[TrainingFeedback.EFFORTS.length];
        for (int i = 0; i < efforts.length; i++)
            efforts[i] = s("effort_" + TrainingFeedback.EFFORTS[i]);
        Spinner effort =
                select(
                        form,
                        s("effort_label"),
                        efforts,
                        existing == null
                                ? 0
                                : Arrays.asList(TrainingFeedback.EFFORTS)
                                        .indexOf(existing.optString("effort", "unknown")));
        effort.setTag("manual_effort");
        dialog(
                s(
                        "set_title",
                        activity.exerciseName(row),
                        index < 0 ? logs.length() + 1 : index + 1),
                form,
                () -> {
                    try {
                        JSONObject log =
                                ManualHistory.log(
                                        weight.getText().toString(),
                                        reps.getText().toString(),
                                        TrainingFeedback.EFFORTS[effort.getSelectedItemPosition()],
                                        System.currentTimeMillis());
                        if (index < 0) logs.put(log);
                        else putLog(logs, index, log);
                        refreshAtCurrentPosition("manualHistory");
                    } catch (ManualHistory.Invalid e) {
                        throw new IllegalArgumentException(s(e.key));
                    }
                });
    }

    private void putLog(JSONArray logs, int index, JSONObject value) {
        try {
            logs.put(index, value);
        } catch (org.json.JSONException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void save(JSONObject draft) {
        if (activity.manualHistoryDraft != draft) return;
        try {
            String fallback =
                    s(
                            "manual_default_name",
                            DateFormat.getDateInstance(DateFormat.MEDIUM, activity.locale())
                                    .format(new Date(draft.optLong("finished"))));
            JSONObject session = ManualHistory.build(draft, fallback, System.currentTimeMillis());
            store.save("history", session);
            activity.manualHistoryDraft = null;
            toast(s("manual_saved"));
            activity.openHistory(session);
        } catch (ManualHistory.Invalid e) {
            toast(s(e.key));
        } catch (SQLiteException e) {
            toast(s("feedback_save_error"));
        }
    }
}
