package com.example.fitplan;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

final class HistoryReader {
    enum DateFilter {
        ALL("history_all_dates"),
        LAST_SEVEN_DAYS("history_last_seven_days"),
        THIS_MONTH("history_this_month"),
        CUSTOM("history_custom_range");

        final String label;

        DateFilter(String label) {
            this.label = label;
        }
    }

    static Map<Long, List<JSONObject>> byMonth(
            JSONArray records,
            DateFilter filter,
            long rangeStart,
            long rangeEnd,
            long now,
            TimeZone zone) {
        long start = Long.MIN_VALUE, end = Long.MAX_VALUE;
        long today = ReportEngine.dayStart(now, zone);
        switch (filter) {
            case LAST_SEVEN_DAYS:
                start = ReportEngine.dayStart(ReportEngine.shiftDays(today, -6, zone), zone);
                end = nextDay(today, zone);
                break;
            case THIS_MONTH:
                start = monthStart(now, zone);
                Calendar nextMonth = Calendar.getInstance(zone);
                nextMonth.setTimeInMillis(start);
                nextMonth.add(Calendar.MONTH, 1);
                end = monthStart(nextMonth.getTimeInMillis(), zone);
                break;
            case CUSTOM:
                start = ReportEngine.dayStart(rangeStart, zone);
                end = nextDay(rangeEnd, zone);
                break;
            default:
                break;
        }
        Map<Long, List<JSONObject>> months = new LinkedHashMap<>();
        for (JSONObject record : sorted(records)) {
            long finished = record.optLong("finished");
            if (finished >= start && finished < end)
                months.computeIfAbsent(monthStart(finished, zone), key -> new ArrayList<>())
                        .add(record);
        }
        return months;
    }

    private static long nextDay(long date, TimeZone zone) {
        return ReportEngine.dayStart(ReportEngine.shiftDays(date, 1, zone), zone);
    }

    private static long monthStart(long time, TimeZone zone) {
        Calendar month = Calendar.getInstance(zone);
        month.setTimeInMillis(time);
        month.set(Calendar.DAY_OF_MONTH, 1);
        return ReportEngine.dayStart(month.getTimeInMillis(), zone);
    }

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
