package com.example.fitplan;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/** Read-only reference to the latest saved daily template, never an active session. */
final class DailyTargetReader {
    static ReportEngine.Targets read(JSONArray plans, long now, TimeZone zone) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(zone);
        String today = format.format(new Date(now));
        ReportEngine.Targets result = new ReportEngine.Targets();
        for (int i = 0; i < plans.length(); i++) {
            JSONObject plan = plans.optJSONObject(i);
            if (plan == null) continue;
            JSONObject check = plan.optJSONObject("checkin");
            String day =
                    plan.has("dailyPlanDay")
                            ? plan.optString("dailyPlanDay")
                            : check == null ? "" : check.optString("day");
            if (!today.equals(day)) continue;
            result.planId = plan.optString("id");
            JSONArray rows = plan.optJSONArray("rows");
            if (rows == null || rows.length() == 0) return result;
            result.hasPlan = true;
            double minutes = plan.optDouble("minutes", 0);
            if (Double.isFinite(minutes) && minutes > 0) result.minutes = minutes;
            boolean invalid = false;
            Set<String> exerciseIds = new HashSet<>();
            for (int j = 0; j < rows.length(); j++) {
                JSONObject row = rows.optJSONObject(j);
                if (row == null) {
                    invalid = true;
                    continue;
                }
                int sets = row.optInt("sets"), reps = row.optInt("reps");
                double weight = row.optDouble("weight", -1);
                if (sets < 1 || sets > 20 || reps < 1 || reps > 100) {
                    invalid = true;
                    continue;
                }
                result.sets += sets;
                String exerciseId = row.optString("exerciseId");
                if (!exerciseId.isEmpty()) exerciseIds.add(exerciseId);
                if (PlanEdits.needsConfirmation(plan, row)
                        || !Double.isFinite(weight)
                        || weight < 0
                        || weight > 1000) result.volumePending = true;
                else result.volume += weight * sets * reps;
            }
            if (invalid) {
                result.sets = 0;
                result.volumePending = true;
            }
            result.exercises = invalid ? 0 : exerciseIds.size();
            if (result.volumePending) result.volume = 0;
            return result;
        }
        return result;
    }
}
