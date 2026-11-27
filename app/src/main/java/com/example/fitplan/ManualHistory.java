package com.example.fitplan;

import static com.example.fitplan.Store.id;
import static com.example.fitplan.Store.object;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;

/** Builds an independent history snapshot from actual results, without inventing a prescription. */
final class ManualHistory {
    static final int MAX_EXERCISES = 30;
    static final int MAX_SETS = 20;

    private ManualHistory() {}

    static final class Invalid extends IllegalArgumentException {
        final String key;

        Invalid(String key) {
            super(key);
            this.key = key;
        }
    }

    static JSONObject draft(long now) {
        return object(
                "id",
                id(),
                "name",
                "",
                "finished",
                now / 60000 * 60000,
                "durationHours",
                "",
                "duration",
                "",
                "rows",
                new JSONArray());
    }

    static JSONObject row(JSONObject exercise) {
        return object(
                "rowId",
                id(),
                "exerciseId",
                exercise.optString("id"),
                "name",
                exercise.optString("name"),
                "part",
                exercise.optString("part"),
                "logs",
                new JSONArray());
    }

    static JSONObject log(String weight, String reps, String effort, long now) {
        double kg;
        int count;
        try {
            kg = Double.parseDouble(weight.trim());
            double value = Double.parseDouble(reps.trim());
            if (!Double.isFinite(kg)
                    || kg < 0
                    || kg > 1000
                    || !Double.isFinite(value)
                    || value < 1
                    || value > 100
                    || value != Math.floor(value)) throw new NumberFormatException();
            count = (int) value;
        } catch (NumberFormatException e) {
            throw new Invalid("manual_invalid_set");
        }
        if (!Arrays.asList(TrainingFeedback.EFFORTS).contains(effort))
            throw new Invalid("manual_invalid_set");
        return object(
                "weight",
                kg,
                "reps",
                count,
                "done",
                true,
                "resolved",
                true,
                "effort",
                effort,
                "incompleteReason",
                "none",
                "feedbackVersion",
                1,
                "recordedAt",
                now);
    }

    private static long durationMinutes(JSONObject draft, long finished) {
        String hoursText = draft.optString("durationHours").trim();
        String minutesText = draft.optString("duration").trim();
        if (hoursText.isEmpty() && minutesText.isEmpty()) return 0;
        try {
            long hours = hoursText.isEmpty() ? 0 : Long.parseLong(hoursText);
            long minutes = minutesText.isEmpty() ? 0 : Long.parseLong(minutesText);
            long total = Math.addExact(Math.multiplyExact(hours, 60L), minutes);
            if (hours < 0 || minutes < 0 || total <= 0 || total > (finished - 1) / 60000L)
                throw new Invalid("manual_invalid_duration");
            return total;
        } catch (NumberFormatException | ArithmeticException e) {
            throw new Invalid("manual_invalid_duration");
        }
    }

    static JSONObject build(JSONObject draft, String fallbackName, long now) {
        long finished = draft.optLong("finished");
        if (finished <= 0 || finished > now) throw new Invalid("manual_invalid_date");
        long minutes = durationMinutes(draft, finished);
        long started = finished - minutes * 60000L;
        JSONArray input = draft.optJSONArray("rows");
        if (input == null || input.length() == 0) throw new Invalid("manual_need_exercise");
        if (input.length() > MAX_EXERCISES) throw new Invalid("manual_exercise_limit");
        JSONArray rows = new JSONArray();
        for (int i = 0; i < input.length(); i++) {
            JSONObject row = input.optJSONObject(i);
            JSONArray source = row == null ? null : row.optJSONArray("logs");
            if (row == null
                    || row.optString("exerciseId").isEmpty()
                    || row.optString("name").trim().isEmpty()
                    || source == null
                    || source.length() == 0) throw new Invalid("manual_need_set");
            if (source.length() > MAX_SETS) throw new Invalid("manual_set_limit");
            JSONArray logs = new JSONArray();
            for (int j = 0; j < source.length(); j++) {
                JSONObject value = source.optJSONObject(j);
                if (value == null) throw new Invalid("manual_invalid_set");
                logs.put(
                        log(
                                value.optString("weight"),
                                value.optString("reps"),
                                value.optString("effort", "unknown"),
                                now));
            }
            rows.put(
                    object(
                            "rowId",
                            row.optString("rowId", id()),
                            "exerciseId",
                            row.optString("exerciseId"),
                            "name",
                            row.optString("name"),
                            "part",
                            row.optString("part"),
                            "sets",
                            logs.length(),
                            "reps",
                            0,
                            "weight",
                            logs.optJSONObject(0).optDouble("weight"),
                            "actualOnly",
                            true,
                            "logs",
                            logs));
        }
        String name = draft.optString("name").trim();
        if (name.length() > 80) throw new Invalid("manual_name_limit");
        if (name.isEmpty()) name = fallbackName;
        String sessionId = draft.optString("id");
        if (sessionId.isEmpty()) throw new Invalid("manual_invalid_set");
        return object(
                "id",
                sessionId,
                "name",
                name,
                "autoName",
                false,
                "started",
                started,
                "finished",
                finished,
                "created",
                now,
                "recordSource",
                "manual",
                "durationKnown",
                minutes > 0,
                "endReason",
                "recorded",
                "rows",
                rows);
    }
}
