package com.example.fitplan;

import static com.example.fitplan.Store.copy;
import static com.example.fitplan.Store.object;
import static com.example.fitplan.Store.put;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class DailyPlanScreen extends Screen {
    private static final int FORM_TEXT_SP = 16;
    private static final int MIN_MINUTES = 10;
    private static final int MAX_MINUTES = 180;

    DailyPlanScreen(MainActivity activity) {
        super(activity);
    }

    JSONObject todayCheck() {
        JSONArray checks = store.list("checkin");
        for (int i = 0; i < checks.length(); i++) {
            JSONObject c = checks.optJSONObject(i);
            if (day().equals(c.optString("day"))) return c;
        }
        return null;
    }

    void today() {
        activity.trainingTabs();
        TextView heading = text(content, s("today_title"), 24);
        heading.setTextColor(Color.WHITE);
        heading.setTypeface(null, Typeface.BOLD);
        formText(content, activity.displayDay() + " · " + s("local_daily"));
        JSONObject check = activity.dailyDraft;
        if (check == null || !day().equals(check.optString("day"))) check = todayCheck();
        if (check == null) check = object();
        LinearLayout c = card(content);
        c.setTag("daily_form");
        formText(c, s("parts_question")).setTypeface(null, Typeface.BOLD);
        List<CheckBox> parts = new ArrayList<>();
        JSONArray selected = check.optJSONArray("parts");
        LinearLayout[] partColumns = null;
        for (int i = 0; i < PARTS.length; i++) {
            String part = PARTS[i];
            if (i % 2 == 0) partColumns = pair(c);
            CheckBox box = new CheckBox(activity);
            box.setText(activity.partName(part));
            box.setTag(part);
            box.setTextColor(Color.WHITE);
            box.setTextSize(FORM_TEXT_SP);
            box.setChecked(selected != null && activity.containsPart(selected, part));
            box.setMinHeight(activity.dp(48));
            partColumns[i % 2].addView(box);
            parts.add(box);
        }
        EditText minutes = formInput(c, s("minutes_label"), check.optString("minutes", "45"), true);
        minutes.setTag("daily_minutes");
        watchMinutes(minutes);
        Spinner volume =
                formSelect(
                        c,
                        s("volume_label"),
                        new String[] {s("light"), s("normal"), s("high")},
                        check.optInt("volume", 1));
        Spinner sleep =
                formSelect(
                        c,
                        s("sleep_label"),
                        new String[] {s("good"), s("fair"), s("poor")},
                        check.optInt("sleep"));
        Spinner food =
                formSelect(
                        c,
                        s("food_label"),
                        new String[] {s("adequate"), s("insufficient"), s("poor")},
                        check.optInt("food"));
        Spinner soreness =
                formSelect(
                        c,
                        s("soreness_label"),
                        new String[] {s("none_sore"), s("mild_sore"), s("severe_sore")},
                        check.optInt("soreness"));
        EditText notes = formInput(c, s("notes_label"), check.optString("notes"), false);
        TextView rules = formText(c, s("rules"));
        rules.setPadding(0, activity.dp(20), 0, activity.dp(8));
        activity.captureDaily =
                () -> {
                    JSONArray picked = new JSONArray();
                    for (CheckBox box : parts)
                        if (box.isChecked()) picked.put(box.getTag().toString());
                    activity.dailyDraft =
                            object(
                                    "day",
                                    day(),
                                    "parts",
                                    picked,
                                    "minutes",
                                    minutes.getText().toString(),
                                    "volume",
                                    volume.getSelectedItemPosition(),
                                    "sleep",
                                    sleep.getSelectedItemPosition(),
                                    "food",
                                    food.getSelectedItemPosition(),
                                    "soreness",
                                    soreness.getSelectedItemPosition(),
                                    "notes",
                                    notes.getText().toString());
                };
        LinearLayout actions = activity.findViewById(R.id.page_actions);
        actions.setVisibility(View.VISIBLE);
        Button generate =
                button(
                        actions,
                        s("generate"),
                        () -> {
                            try {
                                String timeProblem = minutesProblem(minutes.getText().toString());
                                if (timeProblem != null) {
                                    minutes.setError(timeProblem);
                                    minutes.requestFocus();
                                    toast(timeProblem);
                                    return;
                                }
                                int time =
                                        new BigDecimal(minutes.getText().toString().trim())
                                                .intValueExact();
                                JSONArray targets = new JSONArray();
                                for (CheckBox box : parts)
                                    if (box.isChecked()) targets.put(box.getTag().toString());
                                if (targets.length() == 0) {
                                    toast(s("choose_part"));
                                    return;
                                }
                                JSONObject saved =
                                        object(
                                                "day",
                                                day(),
                                                "created",
                                                System.currentTimeMillis(),
                                                "parts",
                                                targets,
                                                "minutes",
                                                time,
                                                "volume",
                                                volume.getSelectedItemPosition(),
                                                "sleep",
                                                sleep.getSelectedItemPosition(),
                                                "food",
                                                food.getSelectedItemPosition(),
                                                "soreness",
                                                soreness.getSelectedItemPosition(),
                                                "notes",
                                                notes.getText().toString());
                                JSONObject plan = submitCheckin(saved);
                                if (plan == null) {
                                    toast(s("rest_only"));
                                    return;
                                }
                                activity.openPlan(plan);
                            } catch (IllegalArgumentException e) {
                                toast(e.getMessage());
                            } catch (android.database.sqlite.SQLiteException e) {
                                toast(s("daily_save_error"));
                            }
                        });
        generate.setTextSize(FORM_TEXT_SP);
        generate.setTextColor(MainActivity.BG);
        generate.setBackgroundTintList(ColorStateList.valueOf(MainActivity.ACCENT));
    }

    private TextView formText(LinearLayout parent, String value) {
        TextView label = text(parent, value, FORM_TEXT_SP);
        label.setTextColor(Color.WHITE);
        return label;
    }

    private LinearLayout field(LinearLayout parent, String label, View control) {
        LinearLayout field = new LinearLayout(activity);
        field.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.topMargin = activity.dp(16);
        parent.addView(field, layout);
        control.setId(View.generateViewId());
        TextView caption = formText(field, label);
        caption.setPadding(0, 0, 0, activity.dp(8));
        caption.setLabelFor(control.getId());
        control.setMinimumHeight(activity.dp(56));
        control.setBackground(activity.shape(Color.rgb(43, 48, 53), 12));
        control.setBackgroundTintList(null);
        field.addView(control, new LinearLayout.LayoutParams(-1, -2));
        return field;
    }

    private EditText formInput(LinearLayout parent, String label, String value, boolean number) {
        EditText edit = new EditText(activity);
        edit.setSingleLine(true);
        edit.setInputType(number ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        edit.setTextColor(Color.WHITE);
        edit.setHintTextColor(Color.WHITE);
        edit.setTextSize(FORM_TEXT_SP);
        edit.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        edit.setPadding(activity.dp(12), activity.dp(12), activity.dp(12), activity.dp(12));
        edit.setText(value);
        field(parent, label, edit);
        return edit;
    }

    private Spinner formSelect(LinearLayout parent, String label, String[] options, int selected) {
        Spinner spinner = new Spinner(activity);
        ArrayAdapter<String> adapter =
                new ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, options) {
                    private View style(View view) {
                        TextView label = (TextView) view;
                        label.setTextColor(Color.WHITE);
                        label.setTextSize(FORM_TEXT_SP);
                        label.setSingleLine(false);
                        label.setEllipsize(null);
                        label.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                        label.setMinHeight(activity.dp(56));
                        label.setPadding(
                                activity.dp(12), activity.dp(12), activity.dp(12), activity.dp(12));
                        return label;
                    }

                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        return style(super.getView(position, convertView, parent));
                    }

                    @Override
                    public View getDropDownView(int position, View convertView, ViewGroup parent) {
                        return style(super.getDropDownView(position, convertView, parent));
                    }
                };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, Math.min(selected, options.length - 1)));
        field(parent, label, spinner);
        // Keep the dropdown affordance when giving all fields the same background and inset.
        spinner.setBackground(activity.shape(Color.rgb(43, 48, 53), 12));
        spinner.setPadding(0, 0, activity.dp(32), 0);
        spinner.setForeground(activity.getDrawable(R.drawable.daily_field_dropdown));
        return spinner;
    }

    private String minutesProblem(String raw) {
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            if (value.compareTo(BigDecimal.valueOf(MAX_MINUTES)) > 0) return s("daily_minutes_max");
            int minutes = value.intValueExact();
            if (minutes >= MIN_MINUTES) return null;
        } catch (NumberFormatException | ArithmeticException ignored) {
        }
        return s("daily_minutes_invalid");
    }

    private void watchMinutes(EditText minutes) {
        String maximumMessage = s("daily_minutes_max");
        boolean[] aboveLimit = {
            maximumMessage.equals(minutesProblem(minutes.getText().toString()))
        };
        if (aboveLimit[0]) minutes.setError(maximumMessage);
        minutes.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable value) {
                        boolean exceeds = maximumMessage.equals(minutesProblem(value.toString()));
                        minutes.setError(exceeds ? maximumMessage : null);
                        if (exceeds && !aboveLimit[0]) toast(maximumMessage);
                        aboveLimit[0] = exceeds;
                    }
                });
    }

    private LinearLayout[] pair(LinearLayout parent) {
        LinearLayout row = new LinearLayout(activity);
        row.setBaselineAligned(false);
        parent.addView(row);
        LinearLayout[] columns = {new LinearLayout(activity), new LinearLayout(activity)};
        for (int i = 0; i < columns.length; i++) {
            columns[i].setOrientation(LinearLayout.VERTICAL);
            columns[i].setPadding(i == 0 ? 0 : activity.dp(4), 0, i == 0 ? activity.dp(4) : 0, 0);
            row.addView(columns[i], new LinearLayout.LayoutParams(0, -2, 1));
        }
        return columns;
    }

    JSONObject submitCheckin(JSONObject check) {
        JSONObject plan = check.optInt("soreness") == 2 ? null : generate(check);
        store.saveDailyPlan(check, plan);
        return plan;
    }

    JSONObject generate(JSONObject check) {
        JSONArray rows = new JSONArray(),
                candidates = new JSONArray(),
                catalogue = store.list("exercise"),
                targets = check.optJSONArray("parts");
        boolean reduced =
                check.optInt("sleep") == 2
                        || check.optInt("food") > 0
                        || check.optInt("soreness") == 1;
        int baseSets = check.optInt("volume") + 2;
        // Round robin fixes a baseline order, shared by normal and reduced plans.
        for (int round = 0; round < 3; round++) {
            for (int p = 0; p < targets.length(); p++) {
                int found = 0;
                for (int i = 0; i < catalogue.length(); i++) {
                    JSONObject ex = catalogue.optJSONObject(i);
                    if (ex.optBoolean("replacementOnly")) continue;
                    if (!targets.optString(p).equals(Catalog.partCode(ex.optString("part"))))
                        continue;
                    if (found++ != round) continue;
                    candidates.put(ex);
                    break;
                }
            }
        }
        Recommendation.Budget budget =
                Recommendation.budget(
                        check.optInt("minutes"), baseSets, candidates.length(), reduced);
        for (int i = 0; i < budget.exercises; i++) {
            JSONObject ex = candidates.optJSONObject(i),
                    reference = recentReference(ex.optString("id"), 10, budget.sets);
            JSONObject row =
                    object(
                            "exerciseId",
                            ex.optString("id"),
                            "name",
                            activity.exerciseName(ex),
                            "part",
                            Catalog.partCode(ex.optString("part")),
                            "weight",
                            reference == null ? 0 : reference.optDouble("weight"),
                            "sets",
                            budget.sets,
                            "reps",
                            10,
                            "reasonCode",
                            reference == null ? "first" : "comparable",
                            "needsWeightConfirmation",
                            reference == null,
                            "referencePolicyVersion",
                            2,
                            "reduced",
                            budget.reduced,
                            "timeLimited",
                            budget.timeLimited,
                            "reference",
                            reference == null ? JSONObject.NULL : reference);
            ProgressEngine.Assessment analysis =
                    ProgressReader.assess(store.list("history"), ex.optString("id"));
            if (analysis.latest != null) {
                JSONObject advice = ProgressReader.snapshot(analysis);
                put(advice, "stateAllowsProgression", !reduced);
                put(row, "progressAdvice", advice);
            }
            rows.put(row);
        }
        return object(
                "name",
                s("daily_plan", activity.displayDay()),
                "autoName",
                true,
                "created",
                System.currentTimeMillis(),
                "checkin",
                copy(check),
                "rows",
                rows,
                "minutes",
                check.optInt("minutes"),
                "noteCode",
                "plan_note",
                "ruleVersion",
                2,
                "baselineTotalSets",
                budget.baselineTotalSets(),
                "timeLimited",
                budget.timeLimited);
    }

    JSONObject recentReference(String exerciseId, int targetReps, int targetSets) {
        ArrayList<JSONObject> sessions = new ArrayList<>();
        JSONArray history = store.list("history");
        for (int i = 0; i < history.length(); i++) sessions.add(history.optJSONObject(i));
        sessions.sort((earlier, later) ->
                Long.compare(later.optLong("finished"), earlier.optLong("finished")));
        for (JSONObject session : sessions) {
            if (session.optLong("finished") <= 0) continue;
            JSONArray rows = session.optJSONArray("rows");
            if (rows == null) continue;
            double minimum = Double.POSITIVE_INFINITY;
            int completedSets = 0;
            for (int r = 0; r < rows.length(); r++) {
                JSONObject row = rows.optJSONObject(r);
                if (!exerciseId.equals(row.optString("exerciseId"))) continue;
                JSONArray logs = row.optJSONArray("logs");
                if (logs == null) continue;
                List<Recommendation.ActualSet> actual = new ArrayList<>();
                for (int i = 0; i < logs.length(); i++) {
                    JSONObject log = logs.optJSONObject(i);
                    if (log == null) continue;
                    actual.add(
                            new Recommendation.ActualSet(
                                    log.optDouble("weight", -1),
                                    log.optInt("reps"),
                                    log.optBoolean("done")));
                }
                double weight =
                        Recommendation.comparableWeight(
                                actual,
                                row.optInt("reps"),
                                row.optInt("sets"),
                                targetReps,
                                targetSets);
                if (weight >= 0) {
                    minimum = Math.min(minimum, weight);
                    completedSets += actual.size();
                }
            }
            if (Double.isFinite(minimum))
                return object(
                        "sessionId",
                        session.optString("id"),
                        "finished",
                        session.optLong("finished"),
                        "weight",
                        minimum,
                        "reps",
                        targetReps,
                        "sets",
                        completedSets);
        }
        return null;
    }
}
