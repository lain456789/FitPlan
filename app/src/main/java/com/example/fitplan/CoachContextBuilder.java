package com.example.fitplan;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Pure-Java analytics and context serializer for the AI fitness coach. Computes 8-week summaries,
 * structural balance, per-exercise plateau and progression indicators, recent 10 session logs,
 * recent 7 daily check-ins, profile, and exercise catalog deterministically from local records.
 */
public final class CoachContextBuilder {
    public static final int RECENT_SESSIONS_LIMIT = 10;
    public static final int RECENT_CHECKINS_LIMIT = 7;
    private static final double WEIGHT_TOLERANCE = 1e-6;

    private static final String[] BUILTIN_EN_NAMES = {
        "Barbell bench press",
        "Incline dumbbell press",
        "Cable fly",
        "Dumbbell shoulder press",
        "Dumbbell lateral raise",
        "Reverse fly",
        "Lat pulldown",
        "Seated cable row",
        "One-arm dumbbell row",
        "Squat",
        "Leg press",
        "Leg curl",
        "Dumbbell curl",
        "Cable triceps pushdown",
        "Crunch",
        "Dead bug",
        "Dumbbell bench press",
        "Push-up",
        "Resistance band row",
        "Resistance band leg curl",
        "Cable biceps curl",
        "Dumbbell triceps extension",
        "Barbell overhead press",
        "Cable lateral raise",
        "Cable reverse fly",
        "Pull-up",
        "Goblet squat",
        "Bodyweight squat",
        "Dumbbell fly",
        "Incline barbell bench press"
    };

    public static final class ContextStats {
        public final int totalSessions;
        public final int sessionsLast8Weeks;
        public final int totalCheckins;
        public final int exerciseCatalogCount;
        public final boolean hasHistory;
        public final boolean containsDemoData;
        public final boolean hasRecentDiscomfort;
        public final List<String> plateauExerciseIds;
        public final List<String> readyToProgressExerciseIds;
        public final double avgWeeklyPushSets;
        public final double avgWeeklyPullSets;
        public final double avgWeeklyLegSets;

        ContextStats(
                int totalSessions,
                int sessionsLast8Weeks,
                int totalCheckins,
                int exerciseCatalogCount,
                boolean containsDemoData,
                boolean hasRecentDiscomfort,
                List<String> plateauExerciseIds,
                List<String> readyToProgressExerciseIds,
                double avgWeeklyPushSets,
                double avgWeeklyPullSets,
                double avgWeeklyLegSets) {
            this.totalSessions = totalSessions;
            this.sessionsLast8Weeks = sessionsLast8Weeks;
            this.totalCheckins = totalCheckins;
            this.exerciseCatalogCount = exerciseCatalogCount;
            this.hasHistory = totalSessions > 0;
            this.containsDemoData = containsDemoData;
            this.hasRecentDiscomfort = hasRecentDiscomfort;
            this.plateauExerciseIds = new ArrayList<>(plateauExerciseIds);
            this.readyToProgressExerciseIds = new ArrayList<>(readyToProgressExerciseIds);
            this.avgWeeklyPushSets = avgWeeklyPushSets;
            this.avgWeeklyPullSets = avgWeeklyPullSets;
            this.avgWeeklyLegSets = avgWeeklyLegSets;
        }
    }

    public static final class BuiltContext {
        public final String promptBlock;
        public final ContextStats stats;

        BuiltContext(String promptBlock, ContextStats stats) {
            this.promptBlock = promptBlock;
            this.stats = stats;
        }
    }

    private static final class ExerciseSample {
        final String sessionId;
        final String date;
        final long finished;
        final int targetSets;
        final int targetReps;
        final List<CoachModels.SetLog> logs;
        final String endReason;
        final boolean adjusted;
        final boolean duplicate;

        ExerciseSample(
                String sessionId,
                String date,
                long finished,
                int targetSets,
                int targetReps,
                List<CoachModels.SetLog> logs,
                String endReason,
                boolean adjusted,
                boolean duplicate) {
            this.sessionId = sessionId;
            this.date = date;
            this.finished = finished;
            this.targetSets = targetSets;
            this.targetReps = targetReps;
            this.logs = logs;
            this.endReason = endReason;
            this.adjusted = adjusted;
            this.duplicate = duplicate;
        }

        int performedSets() {
            int count = 0;
            for (CoachModels.SetLog log : logs) {
                if (log.isPerformed()) {
                    count++;
                }
            }
            return count;
        }

        int metSets() {
            int count = 0;
            for (CoachModels.SetLog log : logs) {
                if (log.isPerformed() && log.reps >= targetReps) {
                    count++;
                }
            }
            return count;
        }

        int totalReps() {
            int sum = 0;
            for (CoachModels.SetLog log : logs) {
                if (log.isPerformed()) {
                    sum += log.reps;
                }
            }
            return sum;
        }

        double maxWeight() {
            double best = -1;
            for (CoachModels.SetLog log : logs) {
                if (log.isPerformed()) {
                    best = Math.max(best, log.weight);
                }
            }
            return best;
        }

        double uniformWeight() {
            double weight = -1;
            for (CoachModels.SetLog log : logs) {
                if (log.isPerformed()) {
                    if (weight >= 0 && Math.abs(weight - log.weight) > WEIGHT_TOLERANCE) {
                        return -1;
                    }
                    weight = log.weight;
                }
            }
            return weight;
        }

        int maxEffortRank() {
            int max = 0;
            for (CoachModels.SetLog log : logs) {
                if (log.isPerformed()) {
                    int rank = log.effortRank();
                    if (rank == 0) {
                        return 0;
                    }
                    max = Math.max(max, rank);
                }
            }
            return max;
        }

        boolean hasHardSet() {
            for (CoachModels.SetLog log : logs) {
                if (log.isPerformed() && "hard".equals(log.effort)) {
                    return true;
                }
            }
            return false;
        }

        boolean hasDiscomfort() {
            for (CoachModels.SetLog log : logs) {
                if ("discomfort".equals(log.effectiveReason(endReason))) {
                    return true;
                }
            }
            return false;
        }

        boolean hasExternalInterruption() {
            if (adjusted) {
                return true;
            }
            for (CoachModels.SetLog log : logs) {
                String reason = log.effectiveReason(endReason);
                if ((!log.done || log.reps < targetReps || log.skipped)
                        && ("time".equals(reason) || "equipment".equals(reason))) {
                    return true;
                }
            }
            return false;
        }

        boolean isUsableComparable() {
            return !adjusted
                    && !duplicate
                    && targetSets > 0
                    && targetReps > 0
                    && logs.size() == targetSets
                    && uniformWeight() >= 0
                    && !hasDiscomfort()
                    && !hasExternalInterruption();
        }

        boolean isFullyMet() {
            return isUsableComparable() && performedSets() == targetSets && metSets() == targetSets;
        }
    }

    private CoachContextBuilder() {}

    public static List<CoachModels.ExerciseDef> builtInCatalogDefs() {
        List<CoachModels.ExerciseDef> defs = new ArrayList<>();
        for (int i = 0; i < Catalog.EXERCISES.length; i++) {
            String id = "builtin-" + i;
            String[] row = Catalog.EXERCISES[i];
            String enName = i < BUILTIN_EN_NAMES.length ? BUILTIN_EN_NAMES[i] : row[0];
            defs.add(
                    new CoachModels.ExerciseDef(
                            id,
                            row[0],
                            enName,
                            row[1],
                            row[2],
                            Arrays.asList(Catalog.equipment(id)),
                            false));
        }
        return defs;
    }

    public static String builtInZhName(String id, String fallback) {
        int index = Catalog.builtIn(id);
        return index < 0 ? (fallback == null ? id : fallback) : Catalog.EXERCISES[index][0];
    }

    public static String builtInEnName(String id, String fallback) {
        int index = Catalog.builtIn(id);
        return index < 0 || index >= BUILTIN_EN_NAMES.length
                ? (fallback == null ? id : fallback)
                : BUILTIN_EN_NAMES[index];
    }

    public static BuiltContext build(
            CoachModels.CoachSnapshot snapshot, long nowMillis, TimeZone zone, String uiLanguage) {
        SimpleDateFormat dayFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        dayFmt.setTimeZone(zone);

        List<CoachModels.WorkoutRecord> validWorkouts = new ArrayList<>();
        boolean containsDemo = false;
        for (CoachModels.WorkoutRecord w : snapshot.workouts) {
            if (w == null || w.finished <= 0) {
                continue;
            }
            if (performedSetsInWorkout(w) == 0 && !hasRecordedOutcome(w)) {
                continue;
            }
            validWorkouts.add(w);
            if (!w.demoDataset.isEmpty()) {
                containsDemo = true;
            }
        }
        validWorkouts.sort(
                Comparator.comparingLong((CoachModels.WorkoutRecord w) -> w.finished)
                        .reversed()
                        .thenComparing(w -> w.id));

        List<CoachModels.CheckinRecord> sortedCheckins = new ArrayList<>();
        for (CoachModels.CheckinRecord c : snapshot.checkins) {
            if (c != null) {
                sortedCheckins.add(c);
                if (!c.demoDataset.isEmpty()) {
                    containsDemo = true;
                }
            }
        }
        sortedCheckins.sort(
                Comparator.comparing((CoachModels.CheckinRecord c) -> c.day)
                        .reversed()
                        .thenComparingLong(c -> -c.created));

        long currentWeekStart = ReportEngine.weekStart(nowMillis, zone);
        long eightWeekStart = ReportEngine.shiftDays(currentWeekStart, -49, zone);
        long upperLimit = nowMillis + 1L;

        // 1. 8-week weekly summaries (oldest -> newest, Monday-Sunday local time).
        List<Map<String, Object>> weeklySummary = new ArrayList<>();
        int sessionsIn8w = 0;
        List<Map<String, Integer>> recent4wParts = new ArrayList<>();

        for (int offset = -7; offset <= 0; offset++) {
            long start = ReportEngine.shiftDays(currentWeekStart, offset * 7, zone);
            long end = Math.min(ReportEngine.shiftDays(start, 7, zone), upperLimit);
            boolean partial = offset == 0;

            int sessions = 0;
            int totalSets = 0;
            double totalVolume = 0;
            long totalDurationMs = 0;
            Set<Integer> activeDays = new HashSet<>();
            Map<String, Integer> partSets = emptyPartMap();

            for (CoachModels.WorkoutRecord w : validWorkouts) {
                if (w.finished < start || w.finished >= end) {
                    continue;
                }
                int performed = performedSetsInWorkout(w);
                if (performed == 0) {
                    continue;
                }
                sessions++;
                totalSets += performed;
                Calendar cal = Calendar.getInstance(zone);
                cal.setTimeInMillis(w.finished);
                activeDays.add((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7);
                if (w.started > 0 && w.finished >= w.started) {
                    totalDurationMs += (w.finished - w.started);
                }
                for (CoachModels.ExerciseRow row : w.rows) {
                    for (CoachModels.SetLog log : row.logs) {
                        if (log.isPerformed()) {
                            totalVolume += log.weight * log.reps;
                            String part = Catalog.exercisePart(row.exerciseId, row.part);
                            if (partSets.containsKey(part)) {
                                partSets.put(part, partSets.get(part) + 1);
                            }
                        }
                    }
                }
            }
            sessionsIn8w += sessions;
            if (offset >= -4 && offset <= -1) {
                recent4wParts.add(partSets);
            }

            Map<String, Object> weekObj = new LinkedHashMap<>();
            weekObj.put("week_start", dayFmt.format(new Date(start)));
            weekObj.put("partial_current_week", partial);
            weekObj.put("sessions", sessions);
            weekObj.put("training_days", activeDays.size());
            weekObj.put("completed_sets", totalSets);
            weekObj.put("volume_kg_reps", round1(totalVolume));
            weekObj.put("duration_minutes", Math.round(totalDurationMs / 60000.0));
            weekObj.put("sets_by_part", partSets);
            weeklySummary.add(weekObj);
        }

        int completed4wSets = 0;
        for (Map<String, Integer> m : recent4wParts) {
            for (int v : m.values()) {
                completed4wSets += v;
            }
        }
        int balanceDivisor = 4;
        if (completed4wSets == 0 && !weeklySummary.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Integer> currentParts =
                    (Map<String, Integer>)
                            weeklySummary.get(weeklySummary.size() - 1).get("sets_by_part");
            recent4wParts = new ArrayList<>();
            if (currentParts != null) {
                recent4wParts.add(currentParts);
                balanceDivisor = 1;
            }
        }

        Map<String, Object> avgParts = new LinkedHashMap<>();
        double chestAvg = 0;
        double shoulderAvg = 0;
        double backAvg = 0;
        double legAvg = 0;
        double armsAvg = 0;
        for (String part : Catalog.PARTS) {
            int sum = 0;
            for (Map<String, Integer> m : recent4wParts) {
                sum += m.getOrDefault(part, 0);
            }
            double avg = balanceDivisor > 0 ? round1(sum / (double) balanceDivisor) : 0.0;
            avgParts.put(part, avg);
            if ("chest".equals(part)) {
                chestAvg = avg;
            } else if ("shoulders".equals(part)) {
                shoulderAvg = avg;
            } else if ("back".equals(part)) {
                backAvg = avg;
            } else if ("legs".equals(part)) {
                legAvg = avg;
            } else if ("arms".equals(part)) {
                armsAvg = avg;
            }
        }
        double pushAvg = round1(chestAvg + shoulderAvg);
        double pullAvg = round1(backAvg);
        double upperAvg = round1(chestAvg + shoulderAvg + backAvg + armsAvg);

        Map<String, Object> balanceObj = new LinkedHashMap<>();
        balanceObj.put("window_weeks", balanceDivisor);
        balanceObj.put("avg_weekly_sets_by_part", avgParts);
        balanceObj.put("avg_weekly_push_sets_chest_shoulders", pushAvg);
        balanceObj.put("avg_weekly_pull_sets_back", pullAvg);
        balanceObj.put(
                "push_to_pull_ratio",
                pullAvg > 0 ? round2(pushAvg / pullAvg) : (pushAvg > 0 ? 99 : 0));
        balanceObj.put("avg_weekly_upper_sets", upperAvg);
        balanceObj.put("avg_weekly_lower_sets_legs", legAvg);

        // 2. Per-exercise analysis over last 8 weeks.
        Map<String, List<ExerciseSample>> samplesByExercise = new LinkedHashMap<>();
        Map<String, String> exerciseNames = new LinkedHashMap<>();
        Map<String, String> exerciseParts = new LinkedHashMap<>();

        for (CoachModels.WorkoutRecord w : validWorkouts) {
            if (w.finished < eightWeekStart || w.finished >= upperLimit) {
                continue;
            }
            String dateStr = dayFmt.format(new Date(w.finished));
            Map<String, Integer> idCounts = new LinkedHashMap<>();
            for (CoachModels.ExerciseRow row : w.rows) {
                if (!row.exerciseId.isEmpty()) {
                    idCounts.put(row.exerciseId, idCounts.getOrDefault(row.exerciseId, 0) + 1);
                }
            }
            for (CoachModels.ExerciseRow row : w.rows) {
                if (row.exerciseId.isEmpty()) {
                    continue;
                }
                if (!exerciseNames.containsKey(row.exerciseId)) {
                    exerciseNames.put(
                            row.exerciseId,
                            row.name.isEmpty()
                                    ? builtInZhName(row.exerciseId, row.exerciseId)
                                    : row.name);
                    exerciseParts.put(
                            row.exerciseId, Catalog.exercisePart(row.exerciseId, row.part));
                }
                boolean duplicate = idCounts.getOrDefault(row.exerciseId, 0) > 1;
                ExerciseSample sample =
                        new ExerciseSample(
                                w.id,
                                dateStr,
                                w.finished,
                                row.targetSets,
                                row.targetReps,
                                row.logs,
                                w.endReason,
                                row.isAdjusted(),
                                duplicate);
                samplesByExercise
                        .computeIfAbsent(row.exerciseId, k -> new ArrayList<>())
                        .add(sample);
            }
        }

        List<Map<String, Object>> exerciseAnalysis = new ArrayList<>();
        List<String> plateauIds = new ArrayList<>();
        List<String> readyToProgressIds = new ArrayList<>();
        boolean anyRecentDiscomfort = false;
        long recent14DaysCutoff = ReportEngine.shiftDays(dayStart(nowMillis, zone), -14, zone);

        for (Map.Entry<String, List<ExerciseSample>> entry : samplesByExercise.entrySet()) {
            String exId = entry.getKey();
            List<ExerciseSample> samples = entry.getValue();
            double maxWeight = 0;
            int totalPerformedSets = 0;
            int hardSets = 0;
            int missedSets = 0;
            int discomfortSets = 0;

            for (ExerciseSample s : samples) {
                maxWeight = Math.max(maxWeight, s.maxWeight());
                for (CoachModels.SetLog log : s.logs) {
                    if (log.isPerformed()) {
                        totalPerformedSets++;
                        if ("hard".equals(log.effort)) {
                            hardSets++;
                        }
                        if (log.reps < s.targetReps) {
                            missedSets++;
                        }
                    } else if (!log.skipped) {
                        missedSets++;
                    }
                    if ("discomfort".equals(log.effectiveReason(s.endReason))) {
                        discomfortSets++;
                        if (s.finished >= recent14DaysCutoff) {
                            anyRecentDiscomfort = true;
                        }
                    }
                }
            }

            ExerciseSample latest = samples.get(0);
            ExerciseSample oldest = samples.get(samples.size() - 1);
            double latestWeight = Math.max(0, latest.maxWeight());
            double oldestWeight = Math.max(0, oldest.maxWeight());

            boolean plateauSuspected = detectPlateau(samples);
            boolean readyToProgress = detectReadyToProgress(samples);
            if (plateauSuspected) {
                plateauIds.add(exId);
            }
            if (readyToProgress) {
                readyToProgressIds.add(exId);
            }

            List<Map<String, Object>> recentCompact = new ArrayList<>();
            for (int i = 0; i < Math.min(4, samples.size()); i++) {
                ExerciseSample s = samples.get(i);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("date", s.date);
                item.put("target", s.targetSets + "x" + s.targetReps);
                item.put("max_weight_kg", round1(Math.max(0, s.maxWeight())));
                item.put("performed_sets", s.performedSets());
                item.put("met_sets", s.metSets());
                item.put("total_reps", s.totalReps());
                item.put("has_hard_set", s.hasHardSet());
                item.put("has_discomfort", s.hasDiscomfort());
                recentCompact.add(item);
            }

            Map<String, Object> exObj = new LinkedHashMap<>();
            exObj.put("exercise_id", exId);
            exObj.put("name_zh", builtInZhName(exId, exerciseNames.get(exId)));
            exObj.put("name_en", builtInEnName(exId, exerciseNames.get(exId)));
            exObj.put("part", exerciseParts.get(exId));
            exObj.put("sessions_8w", samples.size());
            exObj.put("latest_date", latest.date);
            exObj.put("latest_weight_kg", round1(latestWeight));
            exObj.put("max_weight_8w_kg", round1(maxWeight));
            exObj.put("weight_change_8w_kg", round1(latestWeight - oldestWeight));
            exObj.put(
                    "hard_set_ratio",
                    totalPerformedSets > 0 ? round2(hardSets / (double) totalPerformedSets) : 0.0);
            exObj.put("missed_sets_8w", missedSets);
            exObj.put("discomfort_sets_8w", discomfortSets);
            exObj.put("plateau_suspected", plateauSuspected);
            exObj.put("ready_to_progress", readyToProgress);
            exObj.put("recent_sessions", recentCompact);
            exerciseAnalysis.add(exObj);
        }

        // 3. Recent 10 sessions set-by-set details.
        List<Map<String, Object>> recentSessions = new ArrayList<>();
        for (int i = 0; i < Math.min(RECENT_SESSIONS_LIMIT, validWorkouts.size()); i++) {
            CoachModels.WorkoutRecord w = validWorkouts.get(i);
            Map<String, Object> sessionObj = new LinkedHashMap<>();
            sessionObj.put("date", dayFmt.format(new Date(w.finished)));
            sessionObj.put("name", w.name);
            sessionObj.put(
                    "duration_min",
                    w.started > 0 && w.finished >= w.started
                            ? Math.round((w.finished - w.started) / 60000.0)
                            : 0);
            if (!w.endReason.isEmpty()) {
                sessionObj.put("end_reason", w.endReason);
            }
            if (w.adjustmentCount > 0) {
                sessionObj.put("adjustments", w.adjustmentCount);
            }
            List<Map<String, Object>> rowList = new ArrayList<>();
            for (CoachModels.ExerciseRow row : w.rows) {
                Map<String, Object> rowObj = new LinkedHashMap<>();
                rowObj.put("exercise_id", row.exerciseId);
                rowObj.put(
                        "name",
                        row.name.isEmpty()
                                ? builtInZhName(row.exerciseId, row.exerciseId)
                                : row.name);
                rowObj.put("part", row.part);
                rowObj.put("target", row.targetSets + "x" + row.targetReps);
                if (row.retired) {
                    rowObj.put("retired_replaced", true);
                }
                if (!row.replacementOf.isEmpty()) {
                    rowObj.put("replacement_of", row.replacementOf);
                }
                List<String> setStrings = new ArrayList<>();
                for (CoachModels.SetLog log : row.logs) {
                    StringBuilder sb = new StringBuilder();
                    if (log.skipped) {
                        sb.append("skipped(").append(log.effectiveReason(w.endReason)).append(")");
                    } else if (!log.done) {
                        sb.append("incomplete(")
                                .append(log.effectiveReason(w.endReason))
                                .append(")");
                    } else {
                        sb.append(formatNum(log.weight))
                                .append("kgx")
                                .append(log.reps)
                                .append("[")
                                .append(log.effort)
                                .append("]");
                        String reason = log.effectiveReason(w.endReason);
                        if (!"none".equals(reason) && !"unknown".equals(reason)) {
                            sb.append("(").append(reason).append(")");
                        }
                    }
                    setStrings.add(sb.toString());
                }
                rowObj.put("sets", setStrings);
                rowList.add(rowObj);
            }
            sessionObj.put("exercises", rowList);
            recentSessions.add(sessionObj);
        }

        // 4. Recent 7 days check-ins.
        List<Map<String, Object>> recentCheckins = new ArrayList<>();
        for (int i = 0; i < Math.min(RECENT_CHECKINS_LIMIT, sortedCheckins.size()); i++) {
            CoachModels.CheckinRecord c = sortedCheckins.get(i);
            Map<String, Object> checkObj = new LinkedHashMap<>();
            checkObj.put("day", c.day);
            checkObj.put("planned_parts", c.parts);
            checkObj.put("minutes", c.minutes);
            checkObj.put("volume_pref", c.volumeLabel());
            checkObj.put("sleep", c.sleepLabel());
            checkObj.put("food", c.foodLabel());
            checkObj.put("soreness", c.sorenessLabel());
            if (!c.notes.isEmpty()) {
                checkObj.put("notes", c.notes);
            }
            recentCheckins.add(checkObj);
        }

        // 5. User profile.
        CoachModels.UserProfile p = snapshot.profile;
        Map<String, Object> profileObj = new LinkedHashMap<>();
        profileObj.put("goal", p.goal.isEmpty() ? "not_provided" : p.goal);
        profileObj.put("experience", p.experience.isEmpty() ? "not_provided" : p.experience);
        profileObj.put("weight_kg", p.weightKg.isEmpty() ? "not_provided" : p.weightKg);
        profileObj.put("weekly_days", p.weeklyDays > 0 ? p.weeklyDays : "not_provided");
        profileObj.put("equipment", p.equipment.isEmpty() ? "not_provided" : p.equipment);
        profileObj.put("injuries_or_notes", p.injuries.isEmpty() ? "none_reported" : p.injuries);

        // 6. Exercise catalog.
        List<CoachModels.ExerciseDef> catalogDefs =
                snapshot.exercises.isEmpty() ? builtInCatalogDefs() : snapshot.exercises;
        List<String> catalogCompact = new ArrayList<>();
        for (CoachModels.ExerciseDef def : catalogDefs) {
            String eq =
                    def.equipment.isEmpty()
                            ? "bodyweight/unspecified"
                            : String.join(",", def.equipment);
            String line =
                    def.id
                            + " | "
                            + def.zhName
                            + (def.enName.isEmpty() || def.enName.equals(def.zhName)
                                    ? ""
                                    : " (" + def.enName + ")")
                            + " | "
                            + def.part
                            + (def.movement.isEmpty() ? "" : " | " + def.movement)
                            + " | eq:"
                            + eq;
            catalogCompact.add(line);
        }

        // 7. Data coverage metadata.
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("today", dayFmt.format(new Date(nowMillis)));
        coverage.put("timezone", zone.getID());
        coverage.put("ui_language", uiLanguage == null || uiLanguage.isEmpty() ? "zh" : uiLanguage);
        coverage.put("has_history", !validWorkouts.isEmpty());
        coverage.put("total_sessions", validWorkouts.size());
        coverage.put("sessions_last_8_weeks", sessionsIn8w);
        coverage.put("total_checkins", sortedCheckins.size());
        coverage.put("contains_demo_data", containsDemo);
        coverage.put("recent_discomfort_detected", anyRecentDiscomfort);
        coverage.put("plateau_exercises", plateauIds);
        coverage.put("ready_to_progress_exercises", readyToProgressIds);
        if (!validWorkouts.isEmpty()) {
            coverage.put(
                    "latest_session_date", dayFmt.format(new Date(validWorkouts.get(0).finished)));
            coverage.put(
                    "oldest_session_date",
                    dayFmt.format(new Date(validWorkouts.get(validWorkouts.size() - 1).finished)));
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("data_coverage", coverage);
        root.put("user_profile", profileObj);
        root.put("recent_4w_balance", balanceObj);
        root.put("weekly_summary_8w", weeklySummary);
        root.put("exercise_analysis", exerciseAnalysis);
        root.put("recent_10_sessions", recentSessions);
        root.put("recent_7d_checkins", recentCheckins);
        root.put("exercise_catalog", catalogCompact);

        String promptBlock =
                "\n\n---\n## USER_TRAINING_CONTEXT (Computed locally from FitPlan SQLite)\n```json\n"
                        + SimpleJson.stringify(root)
                        + "\n```\n";

        ContextStats stats =
                new ContextStats(
                        validWorkouts.size(),
                        sessionsIn8w,
                        sortedCheckins.size(),
                        catalogDefs.size(),
                        containsDemo,
                        anyRecentDiscomfort,
                        plateauIds,
                        readyToProgressIds,
                        pushAvg,
                        pullAvg,
                        legAvg);

        return new BuiltContext(promptBlock, stats);
    }

    private static boolean detectPlateau(List<ExerciseSample> newestFirst) {
        List<ExerciseSample> relevant = new ArrayList<>();
        for (ExerciseSample s : newestFirst) {
            if (!s.hasExternalInterruption() && s.performedSets() > 0 && s.maxWeight() > 0) {
                relevant.add(s);
            }
            if (relevant.size() == 3) {
                break;
            }
        }
        if (relevant.size() < 3) {
            return false;
        }
        double latestW = relevant.get(0).maxWeight();
        for (int i = 1; i < 3; i++) {
            if (Math.abs(relevant.get(i).maxWeight() - latestW) > WEIGHT_TOLERANCE) {
                return false;
            }
        }
        int strainedSessions = 0;
        for (ExerciseSample s : relevant) {
            boolean missedTarget = s.metSets() < s.targetSets || s.performedSets() < s.targetSets;
            if (missedTarget || s.hasHardSet()) {
                strainedSessions++;
            }
        }
        boolean repsNotImproving = relevant.get(0).totalReps() <= relevant.get(2).totalReps();
        return strainedSessions >= 2 && repsNotImproving;
    }

    private static boolean detectReadyToProgress(List<ExerciseSample> newestFirst) {
        if (newestFirst.size() < 3) {
            return false;
        }
        ExerciseSample latest = newestFirst.get(0);
        if (!latest.isFullyMet()) {
            return false;
        }
        int streak = 0;
        for (ExerciseSample s : newestFirst) {
            if (!s.isFullyMet()
                    || s.targetSets != latest.targetSets
                    || s.targetReps != latest.targetReps
                    || Math.abs(s.uniformWeight() - latest.uniformWeight()) > WEIGHT_TOLERANCE
                    || s.maxEffortRank() == 0
                    || s.maxEffortRank() > 2) {
                break;
            }
            streak++;
            if (streak >= 3) {
                return true;
            }
        }
        return false;
    }

    private static int performedSetsInWorkout(CoachModels.WorkoutRecord w) {
        int count = 0;
        for (CoachModels.ExerciseRow row : w.rows) {
            for (CoachModels.SetLog log : row.logs) {
                if (log.isPerformed()) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean hasRecordedOutcome(CoachModels.WorkoutRecord w) {
        for (CoachModels.ExerciseRow row : w.rows) {
            for (CoachModels.SetLog log : row.logs) {
                if (!"unknown".equals(log.incompleteReason) || log.skipped) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Map<String, Integer> emptyPartMap() {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String part : Catalog.PARTS) {
            map.put(part, 0);
        }
        return map;
    }

    private static long dayStart(long time, TimeZone zone) {
        Calendar c = Calendar.getInstance(zone);
        c.setTimeInMillis(time);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String formatNum(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-6) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }
}
