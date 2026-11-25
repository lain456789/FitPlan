package com.example.fitplan;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class HistoryReader {
    static List<JSONObject> sorted(JSONArray records) {
        List<JSONObject> result = new ArrayList<>();
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record != null) result.add(record);
        }
        result.sort(
                Comparator.comparingLong((JSONObject value) -> value.optLong("finished"))
                        .reversed()
                        .thenComparing(value -> value.optString("id")));
        return result;
    }

    static List<ReportEngine.Session> read(JSONArray records) {
        List<ReportEngine.Session> result = new ArrayList<>();
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null) continue;
            ReportEngine.Session session =
                    new ReportEngine.Session(record.optLong("started"), record.optLong("finished"));
            JSONArray rows = record.optJSONArray("rows");
            if (rows == null) continue;
            for (int r = 0; r < rows.length(); r++) {
                JSONObject row = rows.optJSONObject(r);
                if (row == null) continue;
                JSONArray logs = row.optJSONArray("logs");
                int count = logs == null ? 0 : logs.length();
                int planned = row.optInt("sets", count);
                ReportEngine.ExerciseEntry exercise =
                        new ReportEngine.ExerciseEntry(
                                row.optString("exerciseId"),
                                planned > 0 ? Math.max(planned, count) : 0);
                session.exercises.add(exercise);
                if (logs == null) continue;
                for (int j = 0; j < logs.length(); j++) {
                    JSONObject log = logs.optJSONObject(j);
                    if (log == null) continue;
                    ReportEngine.SetEntry entry =
                            new ReportEngine.SetEntry(
                                    row.optString("exerciseId"),
                                    row.optString("name"),
                                    row.optString("part"),
                                    log.optDouble("weight", 0),
                                    log.optInt("reps"),
                                    log.optBoolean("done"));
                    session.sets.add(entry);
                    exercise.sets.add(entry);
                    exercise.hasSkippedSets |= log.optBoolean("skipped");
                }
            }
            result.add(session);
        }
        return result;
    }
}
