package com.example.fitplan;

import static com.example.fitplan.Store.copy;
import static com.example.fitplan.Store.put;

import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.Spinner;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ProgressScreen extends Screen {
    ProgressScreen(MainActivity activity) {
        super(activity);
    }

    void render() {
        activity.recordTabs();
        title(s("progress_title"));
        text(content, s("progress_scope"), 14);
        JSONArray history = store.list("history");
        Map<String, JSONObject> exercises = new LinkedHashMap<>();
        for (int i = 0; i < history.length(); i++) {
            JSONArray rows = history.optJSONObject(i).optJSONArray("rows");
            if (rows == null) continue;
            for (int r = 0; r < rows.length(); r++) {
                JSONObject row = rows.optJSONObject(r);
                if (row != null && !row.optString("exerciseId").isEmpty())
                    exercises.putIfAbsent(row.optString("exerciseId"), row);
            }
        }
        if (exercises.isEmpty()) {
            text(content, s("progress_no_data"), 16);
            return;
        }
        List<String> ids = new ArrayList<>(exercises.keySet());
        String[] names = new String[ids.size()];
        int selected = 0;
        Map<String, Integer> duplicates = new HashMap<>();
        for (JSONObject row : exercises.values()) {
            String name = activity.exerciseName(row);
            duplicates.put(name, duplicates.getOrDefault(name, 0) + 1);
        }
        for (int i = 0; i < ids.size(); i++) {
            names[i] = activity.exerciseName(exercises.get(ids.get(i)));
            if (duplicates.get(names[i]) > 1)
                names[i] += " · " + ids.get(i).substring(0, Math.min(8, ids.get(i).length()));
            if (ids.get(i).equals(activity.exerciseId)) selected = i;
        }
        Spinner pick = select(content, s("choose_exercise"), names, selected);
        LinearLayout results = column();
        content.addView(results);
        Runnable draw =
                () -> {
                    activity.exerciseId = ids.get(pick.getSelectedItemPosition());
                    results.removeAllViews();
                    renderResult(results, ProgressReader.assess(history, activity.exerciseId));
                };
        pick.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(
                            AdapterView<?> p, android.view.View v, int pos, long id) {
                        draw.run();
                    }

                    public void onNothingSelected(AdapterView<?> p) {}
                });
        draw.run();
    }

    private void renderResult(LinearLayout parent, ProgressEngine.Assessment result) {
        LinearLayout summary = card(parent);
        text(summary, s("progress_status_" + result.status), 20);
        if (result.latest == null) {
            text(summary, s("progress_no_data"), 16);
            return;
        }
        ProgressEngine.Sample latest = result.latest;
        text(summary, s("progress_latest", date(latest.finished)), 14);
        if (latest.targetReps <= 0) {
            text(summary, s("manual_actual_only"), 14);
            text(summary, s("manual_progress_actual", latest.totalReps(), latest.performed()), 16);
        } else {
            text(
                    summary,
                    s(
                            "progress_configuration",
                            latest.weight() < 0
                                    ? s("progress_no_load")
                                    : num(latest.weight()) + " kg",
                            latest.targetSets,
                            latest.targetReps),
                    16);
            text(
                    summary,
                    s(
                            "progress_actual",
                            latest.totalReps(),
                            latest.performed(),
                            latest.met(),
                            latest.targetSets),
                    16);
        }
        text(
                summary,
                s("progress_effort", s("effort_" + TrainingFeedback.EFFORTS[latest.effort()])),
                14);
        if (result.previous != null) {
            ProgressEngine.Sample previous = result.previous;
            LinearLayout comparison = card(parent);
            text(comparison, s("progress_compare_date", date(previous.finished)), 16);
            text(
                    comparison,
                    s("progress_compare_reps", previous.totalReps(), latest.totalReps()),
                    18);
            text(
                    comparison,
                    s(
                            "progress_compare_completion",
                            previous.met(),
                            latest.met(),
                            latest.targetSets),
                    18);
            text(
                    comparison,
                    s("progress_compare_performed", previous.performed(), latest.performed()),
                    16);
            text(
                    comparison,
                    s(
                            "progress_compare_effort",
                            s("effort_" + TrainingFeedback.EFFORTS[previous.effort()]),
                            s("effort_" + TrainingFeedback.EFFORTS[latest.effort()])),
                    14);
        }
        text(parent, s("progress_sample_count", result.comparableCount), 14);
        if (latest.effort() == 0 || (result.previous != null && result.previous.effort() == 0))
            text(parent, s("progress_unknown_feedback"), 14);
        LinearLayout advice = card(parent);
        text(advice, s("progress_next_title"), 20);
        text(advice, advice(result.action, result.suggestedReps, latest.targetReps), 16);
        if (result.canIncrease) text(advice, s("progress_optional_rule"), 14);
        button(advice, s("progress_generate_next"), () -> show("home"));
    }

    private String advice(String action, int suggested, int target) {
        if (action.equals("optional_rep")) return s("advice_optional_rep", suggested);
        if (action.equals("complete_target")) return s("advice_complete_target", target);
        return s("advice_" + action);
    }

    private boolean stateAllows(JSONObject plan) {
        JSONObject check = plan.optJSONObject("checkin");
        if (check != null
                && (check.optInt("sleep") == 2
                        || check.optInt("food") > 0
                        || check.optInt("soreness") > 0)) return false;
        JSONArray checks = store.list("checkin");
        for (int i = 0; i < checks.length(); i++) {
            JSONObject today = checks.optJSONObject(i);
            if (activity.day().equals(today.optString("day")))
                return today.optInt("sleep") != 2
                        && today.optInt("food") == 0
                        && today.optInt("soreness") == 0;
        }
        return true;
    }

    void planAdvice(JSONObject plan, int index, LinearLayout parent, JSONArray history) {
        JSONObject row = plan.optJSONArray("rows").optJSONObject(index);
        ProgressEngine.Assessment result =
                ProgressReader.assess(history, row.optString("exerciseId"));
        if (result.latest == null && !row.has("progressAdvice")) return;
        text(parent, s("progress_plan_title"), 16);
        text(parent, s("progress_status_" + result.status), 14);
        if (result.latest == null) return;
        boolean applicable = ProgressReader.applies(result, row) && stateAllows(plan);
        if (applicable)
            text(parent, advice(result.action, result.suggestedReps, result.latest.targetReps), 14);
        else text(parent, s("progress_plan_mismatch"), 14);
        if (result.canIncrease && applicable)
            button(parent, s("progress_adopt"), () -> offerAdvice(plan, index));
    }

    void offerAdvice(JSONObject plan, int index) {
        JSONObject row = plan.optJSONArray("rows").optJSONObject(index);
        ProgressEngine.Assessment result =
                ProgressReader.assess(store.list("history"), row.optString("exerciseId"));
        if (!result.canIncrease || !ProgressReader.applies(result, row) || !stateAllows(plan)) {
            toast(s("progress_advice_stale"));
            refreshPlan(plan);
            return;
        }
        confirm(
                s(
                        "progress_adopt_preview",
                        num(row.optDouble("weight")),
                        row.optInt("sets"),
                        row.optInt("reps"),
                        result.suggestedReps),
                () -> {
                    try {
                        applyAdvice(plan, index);
                    } catch (Store.StaleWorkout e) {
                        toast(s("progress_advice_stale"));
                        refreshPlan(plan);
                    } catch (android.database.sqlite.SQLiteException e) {
                        toast(s("feedback_save_error"));
                    }
                });
    }

    private void refreshPlan(JSONObject plan) {
        JSONObject current = activity.find("plan", plan.optString("id"));
        if (current == null) show("plans");
        else activity.openPlan(current);
    }

    void applyAdvice(JSONObject expected, int index) {
        JSONObject row = expected.optJSONArray("rows").optJSONObject(index);
        ProgressEngine.Assessment fresh =
                ProgressReader.assess(store.list("history"), row.optString("exerciseId"));
        if (!fresh.canIncrease || !ProgressReader.applies(fresh, row) || !stateAllows(expected))
            throw new Store.StaleWorkout();
        JSONObject updated = copy(expected),
                newRow = updated.optJSONArray("rows").optJSONObject(index);
        PlanEdits.confirm(
                newRow,
                row.optDouble("weight"),
                row.optInt("sets"),
                fresh.suggestedReps,
                System.currentTimeMillis());
        put(newRow, "acceptedProgressSuggestion", ProgressReader.snapshot(fresh));
        newRow.remove("progressAdvice");
        store.savePlanEdit(expected, updated);
        activity.openPlan(updated);
    }
}
