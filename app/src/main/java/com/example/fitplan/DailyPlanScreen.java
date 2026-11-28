package com.example.fitplan;

import static com.example.fitplan.Store.copy;
import static com.example.fitplan.Store.object;
import static com.example.fitplan.Store.put;

import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Apple Fitness-inspired daily check-in form using interactive pill chips & iOS segmented bars. */
final class DailyPlanScreen extends Screen {
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
        JSONObject check = activity.dailyDraft;
        if (check == null || !day().equals(check.optString("day"))) check = todayCheck();
        if (check == null) check = object();

        // Card 1: Body parts interactive 3x2 chip grid
        LinearLayout partsCard = card(content);
        partsCard.setTag("daily_form");
        sectionLabel(partsCard, s("parts_question"));

        List<CheckBox> parts = new ArrayList<>();
        JSONArray selected = check.optJSONArray("parts");
        LinearLayout chipRow = null;
        for (int i = 0; i < PARTS.length; i++) {
            String part = PARTS[i];
            if (i % 3 == 0) {
                chipRow = new LinearLayout(activity);
                chipRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(-1, -2);
                rLp.topMargin = activity.dp(8);
                partsCard.addView(chipRow, rLp);
            }
            CheckBox chip = new CheckBox(activity);
            chip.setButtonDrawable(null);
            chip.setText(activity.partName(part));
            chip.setTag(part);
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(14);
            chip.setMinHeight(activity.dp(42));
            chip.setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8));
            boolean isChecked = selected != null && activity.containsPart(selected, part);
            chip.setChecked(isChecked);
            stylePartChip(chip, isChecked);
            chip.setOnCheckedChangeListener((btn, checked) -> stylePartChip(chip, checked));
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i % 3 < 2) cLp.setMarginEnd(activity.dp(8));
            chipRow.addView(chip, cLp);
            parts.add(chip);
        }

        // Card 2: Time + Volume + Recovery (Sleep / Food / Soreness) as iOS segmented pills
        LinearLayout stateCard = card(content);

        // Inline duration row
        LinearLayout durRow = new LinearLayout(activity);
        durRow.setOrientation(LinearLayout.HORIZONTAL);
        durRow.setGravity(Gravity.CENTER_VERTICAL);
        stateCard.addView(durRow, new LinearLayout.LayoutParams(-1, -2));

        TextView minCap = new TextView(activity);
        minCap.setText(s("minutes_label"));
        minCap.setTextSize(15);
        minCap.setTextColor(MainActivity.FG);
        minCap.setTypeface(null, Typeface.BOLD);
        durRow.addView(minCap, new LinearLayout.LayoutParams(0, -2, 1));

        EditText minutes = new EditText(activity);
        minutes.setId(View.generateViewId());
        minCap.setLabelFor(minutes.getId());
        minutes.setSingleLine(true);
        minutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        minutes.setGravity(Gravity.CENTER);
        minutes.setTextColor(MainActivity.ACCENT);
        minutes.setTextSize(16);
        minutes.setTypeface(null, Typeface.BOLD);
        minutes.setBackground(UiKit.box(activity, UiKit.SURFACE_DEEP, UiKit.BORDER, 12));
        minutes.setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8));
        minutes.setText(check.optString("minutes", "45"));
        minutes.setTag("daily_minutes");
        watchMinutes(minutes);
        durRow.addView(minutes, new LinearLayout.LayoutParams(activity.dp(84), -2));

        UiKit.divider(activity, stateCard);

        final int[] volumeVal = {check.optInt("volume", 1)};
        sectionLabel(stateCard, s("volume_label"));
        UiKit.segmentedControl(
                activity,
                stateCard,
                new String[] {s("light"), s("normal"), s("high")},
                volumeVal[0],
                idx -> volumeVal[0] = idx);

        final int[] sleepVal = {check.optInt("sleep", 0)};
        sectionLabel(stateCard, s("sleep_label"));
        UiKit.segmentedControl(
                activity,
                stateCard,
                new String[] {s("good"), s("fair"), s("poor")},
                sleepVal[0],
                idx -> sleepVal[0] = idx);

        final int[] foodVal = {check.optInt("food", 0)};
        sectionLabel(stateCard, s("food_label"));
        UiKit.segmentedControl(
                activity,
                stateCard,
                new String[] {s("adequate"), s("insufficient"), s("poor")},
                foodVal[0],
                idx -> foodVal[0] = idx);

        final int[] sorenessVal = {check.optInt("soreness", 0)};
        sectionLabel(stateCard, s("soreness_label"));
        UiKit.segmentedControl(
                activity,
                stateCard,
                new String[] {s("none_sore"), s("mild_sore"), s("severe_sore")},
                sorenessVal[0],
                idx -> sorenessVal[0] = idx);

        UiKit.divider(activity, stateCard);

        EditText notes = new EditText(activity);
        notes.setSingleLine(true);
        notes.setHint(s("notes_label"));
        notes.setHintTextColor(MainActivity.MUTED);
        notes.setTextColor(MainActivity.FG);
        notes.setTextSize(14);
        notes.setBackground(UiKit.box(activity, UiKit.SURFACE_DEEP, UiKit.BORDER, 12));
        notes.setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10));
        notes.setText(check.optString("notes"));
        LinearLayout.LayoutParams notesLp = new LinearLayout.LayoutParams(-1, -2);
        notesLp.topMargin = activity.dp(6);
        stateCard.addView(notes, notesLp);

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
                                    volumeVal[0],
                                    "sleep",
                                    sleepVal[0],
                                    "food",
                                    foodVal[0],
                                    "soreness",
                                    sorenessVal[0],
                                    "notes",
                                    notes.getText().toString());
                };

        LinearLayout actions = activity.findViewById(R.id.page_actions);
        actions.setVisibility(View.VISIBLE);
        primaryButton(
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
                                new BigDecimal(minutes.getText().toString().trim()).intValueExact();
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
                                        volumeVal[0],
                                        "sleep",
                                        sleepVal[0],
                                        "food",
                                        foodVal[0],
                                        "soreness",
                                        sorenessVal[0],
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
    }

    private void sectionLabel(LinearLayout parent, String value) {
        TextView tv = new TextView(activity);
        tv.setText(value);
        tv.setTextSize(13);
        tv.setTextColor(MainActivity.MUTED);
        tv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = activity.dp(8);
        lp.bottomMargin = activity.dp(2);
        parent.addView(tv, lp);
    }

    private void stylePartChip(CheckBox chip, boolean checked) {
        chip.setBackground(
                UiKit.box(
                        activity,
                        checked ? UiKit.ADD_BG : UiKit.SURFACE,
                        checked ? MainActivity.ACCENT : 0,
                        14));
        chip.setTextColor(checked ? MainActivity.ACCENT : MainActivity.FG);
        chip.setTypeface(null, checked ? Typeface.BOLD : Typeface.NORMAL);
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
