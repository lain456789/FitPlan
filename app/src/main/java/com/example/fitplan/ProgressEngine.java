package com.example.fitplan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Describes comparable recorded performance, not a measurement of strength or recovery. */
public final class ProgressEngine {
    private static final int MAX_COMPARABLE_RECORDS = 7;
    private static final int REQUIRED_PROGRESS_STREAK = 3;
    private static final int MAX_PROGRESS_TARGET_REPS = 20;
    private static final int MAX_TARGET_SETS = 20;
    private static final int MAX_TARGET_REPS = 100;
    static final double WEIGHT_TOLERANCE_KG = 0.000001;

    public static final class SetResult {
        public final double weight;
        public final int reps, effort;
        public final boolean done, skipped;
        public final String reason;
        public final int targetReps;

        public SetResult(
                double weight, int reps, boolean done, boolean skipped, int effort, String reason) {
            this(weight, reps, done, skipped, effort, reason, 0);
        }

        SetResult(
                double weight,
                int reps,
                boolean done,
                boolean skipped,
                int effort,
                String reason,
                int targetReps) {
            this.weight = weight;
            this.reps = reps;
            this.done = done;
            this.skipped = skipped;
            this.effort = effort;
            this.reason = reason;
            this.targetReps = targetReps;
        }
    }

    public static final class Sample {
        public final String id, exerciseId;
        public final long finished;
        public final int targetSets, targetReps;
        public final List<SetResult> sets;
        public final boolean adjusted, duplicate;

        public Sample(
                String id,
                String exerciseId,
                long finished,
                int targetSets,
                int targetReps,
                List<SetResult> sets,
                boolean adjusted,
                boolean duplicate) {
            this.id = id;
            this.exerciseId = exerciseId;
            this.finished = finished;
            this.targetSets = targetSets;
            this.targetReps = targetReps;
            this.sets = new ArrayList<>(sets);
            this.adjusted = adjusted;
            this.duplicate = duplicate;
        }

        public int performed() {
            int n = 0;
            for (SetResult s : sets) if (s.done && s.reps > 0) n++;
            return n;
        }

        public int totalReps() {
            int n = 0;
            for (SetResult s : sets) if (s.done && s.reps > 0) n += s.reps;
            return n;
        }

        public int met() {
            if (targetReps <= 0) return 0;
            int n = 0;
            for (SetResult s : sets)
                if (s.done && s.reps >= (s.targetReps > 0 ? s.targetReps : targetReps)) n++;
            return n;
        }

        public int effort() {
            int max = 0;
            for (SetResult s : sets)
                if (s.done) {
                    if (s.effort < 1 || s.effort > 3) return 0;
                    max = Math.max(max, s.effort);
                }
            return max;
        }

        public boolean discomfort() {
            for (SetResult s : sets) if ("discomfort".equals(s.reason)) return true;
            return false;
        }

        public String context() {
            if (adjusted) return "adjusted";
            for (SetResult s : sets) if (s.skipped) return "adjusted";
            for (SetResult s : sets)
                if ((!s.done || s.reps < targetReps || s.skipped) && "time".equals(s.reason))
                    return "time";
            for (SetResult s : sets)
                if ((!s.done || s.reps < targetReps || s.skipped) && "equipment".equals(s.reason))
                    return "equipment";
            return "";
        }

        public double weight() {
            double weight = -1;
            for (SetResult s : sets)
                if (s.done) {
                    if (s.reps < 1 || !Double.isFinite(s.weight) || s.weight < 0) return -1;
                    if (weight >= 0 && Math.abs(weight - s.weight) > WEIGHT_TOLERANCE_KG) return -1;
                    weight = s.weight;
                }
            return weight;
        }

        public boolean usable() {
            return !adjusted
                    && !duplicate
                    && targetSets > 0
                    && targetSets <= MAX_TARGET_SETS
                    && targetReps > 0
                    && targetReps <= MAX_TARGET_REPS
                    && sets.size() == targetSets
                    && weight() >= 0
                    && !discomfort()
                    && context().isEmpty();
        }

        public boolean fullyMet() {
            return usable() && performed() == targetSets && met() == targetSets;
        }
    }

    public static final class Assessment {
        public final Sample latest, previous;
        public final String status, action;
        public final int comparableCount, suggestedReps;
        public final boolean canIncrease;

        Assessment(
                Sample latest,
                Sample previous,
                String status,
                String action,
                int count,
                boolean increase) {
            this.latest = latest;
            this.previous = previous;
            this.status = status;
            this.action = action;
            this.comparableCount = count;
            this.canIncrease = increase;
            this.suggestedReps = latest == null ? 0 : latest.targetReps + (increase ? 1 : 0);
        }
    }

    private ProgressEngine() {}

    private static boolean comparable(Sample latest, Sample candidate) {
        return candidate.usable()
                && latest.exerciseId.equals(candidate.exerciseId)
                && latest.targetSets == candidate.targetSets
                && latest.targetReps == candidate.targetReps
                && Math.abs(latest.weight() - candidate.weight()) < WEIGHT_TOLERANCE_KG;
    }

    public static Assessment assess(List<Sample> input) {
        List<Sample> samples = new ArrayList<>();
        for (Sample sample : input) if (sample != null && sample.finished > 0) samples.add(sample);
        samples.sort(
                Comparator.comparingLong((Sample s) -> s.finished)
                        .reversed()
                        .thenComparing(s -> s.id));
        if (samples.isEmpty()) return new Assessment(null, null, "no_data", "record", 0, false);
        Sample latest = samples.get(0);
        if (latest.discomfort())
            return new Assessment(latest, null, "discomfort", "review_discomfort", 0, false);
        if (!latest.context().isEmpty())
            return new Assessment(
                    latest,
                    null,
                    "context",
                    latest.context().equals("equipment")
                            ? "equipment"
                            : latest.context().equals("time") ? "time" : "adjusted",
                    0,
                    false);
        if (!latest.usable())
            return new Assessment(latest, null, "incomparable", "check_records", 0, false);
        List<Sample> matched = new ArrayList<>();
        for (Sample sample : samples)
            if (comparable(latest, sample) && matched.size() < MAX_COMPARABLE_RECORDS)
                matched.add(sample);
        Sample previous = matched.size() > 1 ? matched.get(1) : null;
        String status = "insufficient";
        if (previous != null) {
            int reps = latest.totalReps() - previous.totalReps(),
                    met = latest.met() - previous.met(),
                    done = latest.performed() - previous.performed();
            if (reps >= 0 && met >= 0 && done >= 0 && (reps > 0 || met > 0 || done > 0))
                status = met > 0 || done > 0 ? "completion_improved" : "reps_improved";
            else if (reps == 0 && met == 0 && done == 0) {
                status =
                        latest.effort() > 0
                                        && previous.effort() > 0
                                        && latest.effort() < previous.effort()
                                ? "easier"
                                : "stable";
            } else status = reps <= 0 && met <= 0 && done <= 0 ? "review" : "mixed";
        }
        int streak = 0;
        for (Sample sample : samples) {
            if (!comparable(latest, sample)
                    || !sample.fullyMet()
                    || sample.effort() == 0
                    || sample.effort() > 2) break;
            streak++;
            if (streak == REQUIRED_PROGRESS_STREAK) break;
        }
        // An opt-in course-product rule, not a validated universal prescription.
        boolean increase =
                streak >= REQUIRED_PROGRESS_STREAK && latest.targetReps < MAX_PROGRESS_TARGET_REPS;
        String action =
                increase
                        ? "optional_rep"
                        : status.equals("review") || status.equals("mixed")
                                ? "review_settings"
                                : latest.fullyMet() ? "repeat" : "complete_target";
        return new Assessment(latest, previous, status, action, matched.size(), increase);
    }
}
