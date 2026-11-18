package com.example.fitplan;

import static com.example.fitplan.Store.copy;
import static com.example.fitplan.Store.object;
import static com.example.fitplan.Store.put;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;

final class TrainingFeedback {
    static final String[] EFFORTS = {"unknown", "easy", "appropriate", "hard"};
    static final String[] REASONS = {
        "unknown", "time", "equipment", "too_hard", "discomfort", "other"
    };

    static final class Invalid extends IllegalArgumentException {
        final String key;

        Invalid(String key) {
            super(key);
            this.key = key;
        }
    }

    private TrainingFeedback() {}

    static JSONObject previousValues(JSONObject row, int logIndex) {
        JSONArray logs = row.optJSONArray("logs");
        if (logs == null || logIndex < 1 || logIndex >= logs.length()) return null;
        for (int i = logIndex - 1; i >= 0; i--) {
            JSONObject previous = logs.optJSONObject(i);
            if (previous == null || !previous.optBoolean("done") || previous.optBoolean("skipped"))
                continue;
            double weight = previous.optDouble("weight", -1);
            int reps = previous.optInt("reps");
            if (Double.isFinite(weight) && weight >= 0 && weight <= 1000 && reps > 0 && reps <= 100)
                return object("weight", weight, "reps", reps);
        }
        return null;
    }

    static String reason(JSONObject log, JSONObject session) {
        if (log.optBoolean("skipped"))
            return "equipment".equals(log.optString("skipReason")) ? "equipment" : "time";
        String reason = log.optString("incompleteReason", "unknown");
        if (reason.equals("none")) return "none";
        if (!log.optBoolean("done") && reason.equals("unknown"))
            reason = session.optString("endReason", "unknown");
        return Arrays.asList(REASONS).contains(reason) ? reason : "unknown";
    }

    static JSONObject record(
            JSONObject session,
            int rowIndex,
            int logIndex,
            double weight,
            int reps,
            boolean done,
            String effort,
            String reason,
            long now) {
        if (!Double.isFinite(weight)
                || weight < 0
                || weight > 1000
                || reps < 0
                || reps > 100
                || !Arrays.asList(EFFORTS).contains(effort)
                || reason == null
                || (!Arrays.asList(REASONS).contains(reason) && !reason.equals("none")))
            throw new Invalid("feedback_invalid");
        JSONObject next = copy(session);
        JSONArray rows = next.optJSONArray("rows");
        if (rows == null || rowIndex < 0 || rowIndex >= rows.length())
            throw new Invalid("feedback_invalid");
        JSONObject row = rows.optJSONObject(rowIndex);
        JSONArray logs = row.optJSONArray("logs");
        if (logs == null || logIndex < 0 || logIndex >= logs.length())
            throw new Invalid("feedback_invalid");
        JSONObject log = logs.optJSONObject(logIndex);
        if (log.optBoolean("skipped") && !log.optBoolean("done"))
            throw new Invalid("feedback_cancelled");
        if ((done && reps == 0) || (!done && reps > 0)) throw new Invalid("feedback_done_reps");
        boolean missed = !done || reps < row.optInt("reps");
        if (missed && (!Arrays.asList(REASONS).contains(reason) || reason.equals("unknown")))
            throw new Invalid("feedback_reason_required");
        put(log, "weight", weight);
        put(log, "reps", reps);
        put(log, "done", done);
        put(log, "resolved", true);
        put(log, "effort", done ? effort : "unknown");
        put(
                log,
                "incompleteReason",
                reason.equals("discomfort") ? reason : missed ? reason : "none");
        put(log, "feedbackVersion", 1);
        put(log, "recordedAt", now);
        if (row.optBoolean("retired") && !done) {
            put(log, "skipped", true);
            put(log, "skipReason", "equipment");
        }
        return next;
    }

    static int recorded(JSONObject session) {
        int n = 0;
        JSONArray rows = session.optJSONArray("rows");
        if (rows != null)
            for (int i = 0; i < rows.length(); i++) {
                JSONArray logs = rows.optJSONObject(i).optJSONArray("logs");
                if (logs != null)
                    for (int j = 0; j < logs.length(); j++)
                        if (logs.optJSONObject(j).optBoolean("resolved")) n++;
            }
        return n;
    }

    static JSONObject finish(JSONObject session, String reason, long now) {
        if (WorkoutAdjustments.pending(session) > 0
                && (!Arrays.asList(REASONS).contains(reason) || reason.equals("unknown")))
            throw new Invalid("feedback_end_required");
        JSONObject next = copy(session);
        put(next, "finished", now);
        put(next, "endReason", WorkoutAdjustments.pending(session) > 0 ? reason : "completed");
        JSONArray rows = next.optJSONArray("rows");
        for (int i = 0; i < rows.length(); i++) {
            JSONArray logs = rows.optJSONObject(i).optJSONArray("logs");
            for (int j = 0; j < logs.length(); j++) {
                JSONObject log = logs.optJSONObject(j);
                if (WorkoutAdjustments.isPending(log)) {
                    put(log, "reps", 0);
                    put(log, "resolved", true);
                    put(log, "effort", "unknown");
                    put(log, "incompleteReason", reason);
                    put(log, "feedbackVersion", 1);
                    put(log, "recordedAt", now);
                }
            }
        }
        return next;
    }
}
