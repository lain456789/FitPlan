package com.example.fitplan;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/** Pure Java aggregation. All intervals are [start, end), in the supplied time zone. */
public final class ReportEngine {
    public static final class SetEntry {
        public String exerciseId, name, part;
        public double weight;
        public int reps;
        public boolean done;

        public SetEntry(
                String id, String name, String part, double weight, int reps, boolean done) {
            this.exerciseId = id;
            this.name = name;
            this.part = part;
            this.weight = weight;
            this.reps = reps;
            this.done = done;
        }

        boolean valid() {
            return done && Double.isFinite(weight) && weight >= 0 && reps > 0;
        }
    }

    public static final class Session {
        public long started, finished;
        public final List<SetEntry> sets = new ArrayList<>();

        public Session(long started, long finished) {
            this.started = started;
            this.finished = finished;
        }
    }

    public static final class Summary {
        public long start, end, duration;
        public int sessions, days, sets;
        public double volume;
        public final int[] daily = new int[7];
        public final Map<String, Integer> parts = new LinkedHashMap<>();
    }

    public static final class Point {
        public final long time;
        public final double value;

        public Point(long time, double value) {
            this.time = time;
            this.value = value;
        }
    }

    public static final class ExerciseTrend {
        public final String id, name;
        public final List<Point> points = new ArrayList<>();

        ExerciseTrend(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static final class Report {
        public Summary today, current, previous;
        public boolean partial;
        public final List<Summary> weeks = new ArrayList<>();
        public final Map<String, ExerciseTrend> exercises = new LinkedHashMap<>();
    }

    public static long dayStart(long time, TimeZone zone) {
        Calendar c = Calendar.getInstance(zone);
        c.setTimeInMillis(time);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public static long weekStart(long time, TimeZone zone) {
        Calendar c = Calendar.getInstance(zone);
        c.setTimeInMillis(time);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DATE, -((c.get(Calendar.DAY_OF_WEEK) + 5) % 7));
        return c.getTimeInMillis();
    }

    public static long shiftDays(long time, int days, TimeZone zone) {
        Calendar c = Calendar.getInstance(zone);
        c.setTimeInMillis(time);
        c.add(Calendar.DATE, days);
        return c.getTimeInMillis();
    }

    public static Summary aggregate(List<Session> sessions, long start, long end, TimeZone zone) {
        Summary result = new Summary();
        result.start = start;
        result.end = end;
        Set<Integer> days = new HashSet<>();
        for (Session session : sessions) {
            if (session.finished <= 0 || session.finished < start || session.finished >= end)
                continue;
            int valid = 0;
            for (SetEntry entry : session.sets) if (entry.valid()) valid++;
            if (valid == 0) continue;
            Calendar c = Calendar.getInstance(zone);
            c.setTimeInMillis(session.finished);
            int day = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            result.sessions++;
            result.sets += valid;
            result.daily[day] += valid;
            days.add(day);
            if (session.started > 0 && session.finished >= session.started)
                result.duration += session.finished - session.started;
            for (SetEntry entry : session.sets)
                if (entry.valid()) {
                    result.volume += entry.weight * entry.reps;
                    String part = Catalog.exercisePart(entry.exerciseId, entry.part);
                    result.parts.put(
                            part, result.parts.containsKey(part) ? result.parts.get(part) + 1 : 1);
                }
        }
        result.days = days.size();
        return result;
    }

    public static Report build(List<Session> input, long selectedWeek, long now, TimeZone zone) {
        List<Session> sessions = new ArrayList<>(input);
        sessions.sort(Comparator.comparingLong(s -> s.finished));
        long currentWeek = weekStart(now, zone),
                start = Math.min(weekStart(selectedWeek, zone), currentWeek);
        long end = shiftDays(start, 7, zone);
        Report report = new Report();
        report.today = aggregate(sessions, dayStart(now, zone), now + 1, zone);
        report.partial = start == currentWeek;
        long limit = report.partial ? now + 1 : end;
        report.current = aggregate(sessions, start, limit, zone);
        report.previous =
                aggregate(
                        sessions,
                        shiftDays(start, -7, zone),
                        report.partial ? shiftDays(now, -7, zone) + 1 : start,
                        zone);
        for (int i = -7; i <= 0; i++) {
            long w = shiftDays(start, i * 7, zone);
            report.weeks.add(
                    aggregate(sessions, w, Math.min(shiftDays(w, 7, zone), now + 1), zone));
        }
        long trendStart = shiftDays(start, -49, zone);
        for (Session session : sessions) {
            if (session.finished < trendStart || session.finished >= limit || session.finished <= 0)
                continue;
            Map<String, Double> best = new LinkedHashMap<>();
            Map<String, String> names = new HashMap<>();
            for (SetEntry entry : session.sets)
                if (entry.valid() && !entry.exerciseId.isEmpty()) {
                    best.put(
                            entry.exerciseId,
                            Math.max(
                                    best.containsKey(entry.exerciseId)
                                            ? best.get(entry.exerciseId)
                                            : 0,
                                    entry.weight));
                    names.put(entry.exerciseId, entry.name);
                }
            for (String id : best.keySet()) {
                ExerciseTrend trend = report.exercises.get(id);
                if (trend == null) {
                    trend = new ExerciseTrend(id, names.get(id));
                    report.exercises.put(id, trend);
                }
                trend.points.add(new Point(session.finished, best.get(id)));
            }
        }
        return report;
    }
}
