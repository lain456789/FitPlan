package com.example.fitplan;

import static com.example.fitplan.Store.copy;
import static com.example.fitplan.Store.object;
import static com.example.fitplan.Store.put;

import android.content.res.ColorStateList;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class HomeScreen extends Screen {
    HomeScreen(MainActivity activity) {
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
        title(s("today_title"));
        text(content, activity.displayDay() + " · " + s("local_daily"), 14);
        JSONObject check = activity.homeDraft;
        if (check == null || !day().equals(check.optString("day"))) check = todayCheck();
        if (check == null) check = object();
        if (store.first("active") != null)
            button(content, s("continue_workout"), () -> show("workout"));
        LinearLayout c = card(content);
        text(c, s("parts_question"), 18);
        List<CheckBox> parts = new ArrayList<>();
        JSONArray selected = check.optJSONArray("parts");
        LinearLayout[] partColumns = null;
        for (int i = 0; i < PARTS.length; i++) {
            String part = PARTS[i];
            if (i % 2 == 0) partColumns = pair(c);
            CheckBox box = new CheckBox(activity);
            box.setText(activity.partName(part));
            box.setTag(part);
            box.setChecked(selected != null && activity.containsPart(selected, part));
            box.setMinHeight(activity.dp(48));
            partColumns[i % 2].addView(box);
            parts.add(box);
        }
        LinearLayout[] budget = pair(c);
        EditText minutes =
                input(budget[0], s("minutes_label"), check.optString("minutes", "45"), true);
        Spinner volume =
                select(
                        budget[1],
                        s("volume_label"),
                        new String[] {s("light"), s("normal"), s("high")},
                        check.optInt("volume", 1));
        LinearLayout[] recovery = pair(c);
        Spinner sleep =
                select(
                        recovery[0],
                        s("sleep_label"),
                        new String[] {s("good"), s("fair"), s("poor")},
                        check.optInt("sleep"));
        Spinner food =
                select(
                        recovery[1],
                        s("food_label"),
                        new String[] {s("adequate"), s("insufficient"), s("poor")},
                        check.optInt("food"));
        Spinner soreness =
                select(
                        c,
                        s("soreness_label"),
                        new String[] {s("none_sore"), s("mild_sore"), s("severe_sore")},
                        check.optInt("soreness"));
        EditText notes = input(c, s("notes_label"), check.optString("notes"), false);
        text(c, s("rules"), 14);
        activity.captureHome =
                () -> {
                    JSONArray picked = new JSONArray();
                    for (CheckBox box : parts)
                        if (box.isChecked()) picked.put(box.getTag().toString());
                    activity.homeDraft =
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
        LinearLayout actions = activity.findViewById(R.id.home_actions);
        actions.setVisibility(View.VISIBLE);
        Button generate =
                button(
                        actions,
                        s("generate"),
                        () -> {
                            try {
                                JSONArray targets = new JSONArray();
                                for (CheckBox box : parts)
                                    if (box.isChecked()) targets.put(box.getTag().toString());
                                if (targets.length() == 0) {
                                    toast(s("choose_part"));
                                    return;
                                }
                                int time = integer(minutes, 10, 180);
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
                                show("plans");
                            } catch (IllegalArgumentException e) {
                                toast(e.getMessage());
                            } catch (android.database.sqlite.SQLiteException e) {
                                toast(s("daily_save_error"));
                            }
                        });
        generate.setTextColor(MainActivity.BG);
        generate.setBackgroundTintList(ColorStateList.valueOf(MainActivity.ACCENT));
        activity.loadHomeSummary(content);
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
