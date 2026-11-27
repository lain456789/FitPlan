package com.example.fitplan;

import static com.example.fitplan.Store.put;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.res.ColorStateList;
import android.database.sqlite.SQLiteException;
import android.graphics.Rect;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.viewpager.widget.ViewPager;

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
        backButton(content, s("back_records"), () -> show(activity.manualReturn));
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
        text(info, s("manual_duration"), 14);
        LinearLayout duration = new LinearLayout(activity);
        info.addView(duration);
        durationField(duration, draft, "manual_hours", "durationHours", "manual_duration_hours");
        durationField(duration, draft, "manual_minutes", "duration", "manual_duration");
        text(info, s("manual_actual_only"), 14);

        renderExercises(draft);
        button(content, s("add_exercise"), () -> chooseExercise(draft));
        LinearLayout actions = activity.findViewById(R.id.page_actions);
        actions.setVisibility(View.VISIBLE);
        LinearLayout actionRow = new LinearLayout(activity);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setBaselineAligned(false);
        actions.addView(actionRow, new LinearLayout.LayoutParams(-1, -2));
        Button discard = button(actionRow, s("manual_discard"), this::confirmDiscardDraft);
        discard.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        Button save = button(actionRow, s("manual_save"), () -> save(draft));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, -2, 1);
        saveParams.setMarginStart(activity.dp(8));
        save.setLayoutParams(saveParams);
        save.setTextColor(MainActivity.BG);
        save.setBackgroundTintList(ColorStateList.valueOf(MainActivity.ACCENT));
    }

    private void confirmDiscardDraft() {
        activity.activeDialog =
                new AlertDialog.Builder(activity)
                        .setMessage(s("manual_discard_confirm"))
                        .setNegativeButton(s("cancel"), null)
                        .setPositiveButton(
                                s("manual_discard"),
                                (dialog, which) -> {
                                    activity.manualHistoryDraft = null;
                                    show(activity.manualReturn);
                                })
                        .show();
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

    private void durationField(
            LinearLayout parent, JSONObject draft, String label, String key, String tag) {
        LinearLayout field = new LinearLayout(activity);
        field.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        if (parent.getChildCount() > 0) params.setMarginStart(activity.dp(12));
        parent.addView(field, params);
        EditText edit = input(field, s(label), draft.optString(key), true);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER);
        edit.setTag(tag);
        bind(edit, draft, key);
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
                            if (store.hasHistoryOnDay(value.getTimeInMillis())) {
                                promptForAnotherDate(draft);
                                return;
                            }
                            put(draft, "finished", value.getTimeInMillis());
                            refreshAtCurrentPosition("manualHistory");
                        },
                        value.get(Calendar.YEAR),
                        value.get(Calendar.MONTH),
                        value.get(Calendar.DAY_OF_MONTH));
        picker.getDatePicker().setMaxDate(System.currentTimeMillis());
        picker.getDatePicker().setMinDate(0);
        activity.activeDialog = picker;
        picker.show();
    }

    private void promptForAnotherDate(JSONObject draft) {
        activity.activeDialog =
                new AlertDialog.Builder(activity)
                        .setMessage(s("manual_date_taken"))
                        .setPositiveButton(
                                s("manual_reselect_date"), (dialog, which) -> chooseDate(draft))
                        .setNegativeButton(s("cancel"), null)
                        .show();
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

    private void renderExercises(JSONObject draft) {
        JSONArray rows = draft.optJSONArray("rows");
        int count = rows.length();
        if (count == 0) return;

        LinearLayout section = new LinearLayout(activity);
        section.setTag("manual_exercises");
        section.setOrientation(LinearLayout.VERTICAL);
        content.addView(section);
        if (count == 1) {
            exerciseCard(section, draft, 0);
            return;
        }

        LinearLayout navigation = new LinearLayout(activity);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        section.addView(navigation);
        ManualExercisePager pager =
                new ManualExercisePager(activity, count, (page, i) -> exerciseCard(page, draft, i));
        pager.setTag("manual_exercise_pager");
        Button previous =
                pageArrow(
                        navigation,
                        "‹",
                        "previous_exercise",
                        () -> pager.setCurrentItem(pager.getCurrentItem() - 1, true));
        TextView position = text(navigation, "", 14);
        position.setTag("manual_exercise_position");
        position.setGravity(Gravity.CENTER);
        position.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        Button next =
                pageArrow(
                        navigation,
                        "›",
                        "next_exercise",
                        () -> pager.setCurrentItem(pager.getCurrentItem() + 1, true));
        section.addView(pager, new LinearLayout.LayoutParams(-1, -2));

        int selected = Math.max(0, Math.min(draft.optInt("exercisePage"), count - 1));
        ViewPager.SimpleOnPageChangeListener listener =
                new ViewPager.SimpleOnPageChangeListener() {
                    @Override
                    public void onPageSelected(int index) {
                        put(draft, "exercisePage", index);
                        position.setText(s("manual_pager_position", index + 1, count));
                        previous.setEnabled(index > 0);
                        next.setEnabled(index < count - 1);
                        previous.setAlpha(previous.isEnabled() ? 1f : .35f);
                        next.setAlpha(next.isEnabled() ? 1f : .35f);
                        pager.requestLayout();
                    }
                };
        pager.setCurrentItem(selected, false);
        listener.onPageSelected(selected);
        pager.addOnPageChangeListener(listener);
    }

    private Button pageArrow(
            LinearLayout navigation, String label, String description, Runnable action) {
        Button arrow = button(navigation, label, action);
        arrow.setContentDescription(s(description));
        arrow.setPadding(0, 0, 0, 0);
        arrow.setTextSize(22);
        arrow.setLayoutParams(new LinearLayout.LayoutParams(activity.dp(48), activity.dp(48)));
        return arrow;
    }

    private void exerciseCard(LinearLayout parent, JSONObject draft, int rowIndex) {
        JSONArray rows = draft.optJSONArray("rows");
        JSONObject row = rows.optJSONObject(rowIndex);
        LinearLayout item = card(parent);
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
                                    put(
                                            draft,
                                            "exercisePage",
                                            Math.min(rowIndex, Math.max(0, rows.length() - 1)));
                                    refreshAtCurrentPosition("manualHistory");
                                }));
    }

    private void chooseExercise(JSONObject draft) {
        JSONArray rows = draft.optJSONArray("rows");
        JSONArray catalogue = store.list("exercise");
        List<JSONObject> exercises = new ArrayList<>();
        for (int i = 0; i < catalogue.length(); i++) exercises.add(catalogue.optJSONObject(i));
        sortExercises(exercises);
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
                    for (JSONObject exercise : exercises) {
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
            JSONObject exercise = matches.get(position);
            String exerciseId = exercise.optString("id");
            for (int i = 0; i < rows.length(); i++) {
                if (exerciseId.equals(rows.optJSONObject(i).optString("exerciseId"))) {
                    search.setError(s("manual_duplicate_exercise"));
                    toast(s("manual_duplicate_exercise"));
                    return;
                }
            }
            if (rows.length() >= ManualHistory.MAX_EXERCISES) {
                toast(s("manual_exercise_limit"));
                return;
            }
            rows.put(ManualHistory.row(exercise));
            put(draft, "exercisePage", rows.length() - 1);
            picker.dismiss();
            show("manualHistory");
            View section = content.findViewWithTag("manual_exercises");
            ScrollView scroll = activity.findViewById(R.id.scroller);
            section.getViewTreeObserver()
                    .addOnGlobalLayoutListener(
                            new ViewTreeObserver.OnGlobalLayoutListener() {
                                @Override
                                public void onGlobalLayout() {
                                    scroll.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                                    if (!section.isAttachedToWindow()) return;
                                    Rect bounds = new Rect();
                                    section.getDrawingRect(bounds);
                                    scroll.offsetDescendantRectToMyCoords(section, bounds);
                                    scroll.scrollTo(0, bounds.top);
                                }
                            });
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
            store.saveManualHistory(session);
            activity.manualHistoryDraft = null;
            toast(s("manual_saved"));
            activity.openHistory(session);
        } catch (ManualHistory.Invalid e) {
            if ("manual_date_taken".equals(e.key)) promptForAnotherDate(draft);
            else toast(s(e.key));
        } catch (SQLiteException e) {
            toast(s("feedback_save_error"));
        }
    }
}
