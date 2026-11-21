package com.example.fitplan;

import static com.example.fitplan.Store.object;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Bridges FitPlan's local SQLite Store into pure-Java CoachModels snapshots. */
final class CoachReader {
    private static final String PROFILE_ID = "user-profile-v1";

    private CoachReader() {}

    static void saveProfile(Store store, CoachModels.UserProfile profile) {
        JSONObject obj =
                object(
                        "id",
                        PROFILE_ID,
                        "goal",
                        profile.goal,
                        "experience",
                        profile.experience,
                        "weightKg",
                        profile.weightKg,
                        "weeklyDays",
                        profile.weeklyDays,
                        "equipment",
                        profile.equipment,
                        "injuries",
                        profile.injuries,
                        "updated",
                        System.currentTimeMillis());
        store.save("profile", obj);
    }

    static CoachModels.UserProfile loadProfile(Store store) {
        JSONObject obj = store.first("profile");
        if (obj == null) {
            return CoachModels.UserProfile.empty();
        }
        return new CoachModels.UserProfile(
                obj.optString("goal", ""),
                obj.optString("experience", ""),
                obj.optString("weightKg", ""),
                obj.optInt("weeklyDays", 0),
                obj.optString("equipment", ""),
                obj.optString("injuries", ""),
                obj.optLong("updated", 0));
    }

    static CoachModels.CoachSnapshot loadSnapshot(Store store) {
        List<CoachModels.WorkoutRecord> workouts = new ArrayList<>();
        JSONArray historyArr = store.list("history");
        for (int i = 0; i < historyArr.length(); i++) {
            JSONObject obj = historyArr.optJSONObject(i);
            if (obj != null) {
                workouts.add(fromWorkoutJson(obj));
            }
        }

        List<CoachModels.CheckinRecord> checkins = new ArrayList<>();
        JSONArray checkinArr = store.list("checkin");
        for (int i = 0; i < checkinArr.length(); i++) {
            JSONObject obj = checkinArr.optJSONObject(i);
            if (obj != null) {
                checkins.add(fromCheckinJson(obj));
            }
        }

        List<CoachModels.ExerciseDef> exercises = new ArrayList<>();
        JSONArray exArr = store.list("exercise");
        for (int i = 0; i < exArr.length(); i++) {
            JSONObject obj = exArr.optJSONObject(i);
            if (obj == null) {
                continue;
            }
            String id = obj.optString("id", "");
            int builtinIdx = Catalog.builtIn(id);
            if (builtinIdx >= 0) {
                String[] row = Catalog.EXERCISES[builtinIdx];
                exercises.add(
                        new CoachModels.ExerciseDef(
                                id,
                                row[0],
                                CoachContextBuilder.builtInEnName(id, row[0]),
                                row[1],
                                row[2],
                                Arrays.asList(Catalog.equipment(id)),
                                false));
            } else {
                String name = obj.optString("name", id);
                String part = Catalog.exercisePart(id, obj.optString("part", ""));
                exercises.add(
                        new CoachModels.ExerciseDef(
                                id, name, name, part, "", new ArrayList<>(), true));
            }
        }
        if (exercises.isEmpty()) {
            exercises = CoachContextBuilder.builtInCatalogDefs();
        }

        return new CoachModels.CoachSnapshot(workouts, checkins, exercises, loadProfile(store));
    }

    private static CoachModels.WorkoutRecord fromWorkoutJson(JSONObject obj) {
        List<CoachModels.ExerciseRow> rows = new ArrayList<>();
        JSONArray rowArr = obj.optJSONArray("rows");
        if (rowArr != null) {
            for (int i = 0; i < rowArr.length(); i++) {
                JSONObject r = rowArr.optJSONObject(i);
                if (r == null) {
                    continue;
                }
                List<CoachModels.SetLog> logs = new ArrayList<>();
                JSONArray logArr = r.optJSONArray("logs");
                if (logArr != null) {
                    for (int s = 0; s < logArr.length(); s++) {
                        JSONObject l = logArr.optJSONObject(s);
                        if (l == null) {
                            continue;
                        }
                        logs.add(
                                new CoachModels.SetLog(
                                        l.optDouble("weight", -1),
                                        l.optInt("reps", 0),
                                        l.optBoolean("done", false),
                                        l.optBoolean("skipped", false),
                                        l.optString("effort", "unknown"),
                                        l.optString("incompleteReason", "unknown"),
                                        l.optString("skipReason", "")));
                    }
                }
                rows.add(
                        new CoachModels.ExerciseRow(
                                r.optString("exerciseId", ""),
                                r.optString("name", ""),
                                r.optString("part", ""),
                                r.optInt("sets", logs.size()),
                                r.optInt("reps", 0),
                                r.optDouble("weight", 0),
                                r.optBoolean("retired", false),
                                r.optString("replacementOf", ""),
                                logs));
            }
        }
        JSONArray adjustments = obj.optJSONArray("adjustments");
        int adjustmentCount =
                adjustments != null ? adjustments.length() : obj.optInt("adjustmentCount", 0);
        return new CoachModels.WorkoutRecord(
                obj.optString("id", ""),
                obj.optString("name", ""),
                obj.optLong("started", 0),
                obj.optLong("finished", 0),
                obj.optString("endReason", ""),
                obj.optString("demoDataset", ""),
                adjustmentCount,
                rows);
    }

    private static CoachModels.CheckinRecord fromCheckinJson(JSONObject obj) {
        List<String> parts = new ArrayList<>();
        JSONArray partArr = obj.optJSONArray("parts");
        if (partArr != null) {
            for (int i = 0; i < partArr.length(); i++) {
                String code = Catalog.partCode(partArr.optString(i, ""));
                if (!"unknown".equals(code)) {
                    parts.add(code);
                }
            }
        }
        return new CoachModels.CheckinRecord(
                obj.optString("id", ""),
                obj.optString("day", ""),
                obj.optLong("created", 0),
                parts,
                obj.optInt("minutes", 45),
                obj.optInt("volume", 1),
                obj.optInt("sleep", 0),
                obj.optInt("food", 0),
                obj.optInt("soreness", 0),
                obj.optString("notes", ""),
                obj.optString("demoDataset", ""));
    }
}
