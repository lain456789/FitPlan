package com.example.fitplan;

import java.util.List;

public final class Recommendation {
    /** Product time estimate, not a physiological load or recovery model. */
    public static final class Budget {
        public final int exercises, baselineSets, sets, estimatedMinutes;
        public final boolean timeLimited, reduced;

        Budget(int exercises, int baselineSets, int sets, boolean timeLimited) {
            this.exercises = exercises;
            this.baselineSets = baselineSets;
            this.sets = sets;
            this.timeLimited = timeLimited;
            this.reduced = sets < baselineSets;
            this.estimatedMinutes = exercises == 0 ? 0 : 5 + exercises * sets * 3;
        }

        public int totalSets() {
            return exercises * sets;
        }

        public int baselineTotalSets() {
            return exercises * baselineSets;
        }
    }

    public static Budget budget(int minutes, int requestedSets, int candidates, boolean reduce) {
        if (minutes < 0 || requestedSets < 1 || candidates < 0)
            throw new IllegalArgumentException("Invalid plan budget");
        int slots = Math.max(0, (minutes - 5) / 3);
        if (slots == 0 || candidates == 0) return new Budget(0, 0, 0, candidates > 0);
        int baselineSets = Math.min(requestedSets, slots);
        // Fix the exercise count BEFORE reducing sets. Never refill recovered time.
        int exercises = Math.min(candidates, slots / baselineSets);
        int sets = reduce ? Math.max(1, baselineSets - 1) : baselineSets;
        return new Budget(
                exercises,
                baselineSets,
                sets,
                baselineSets < requestedSets || exercises < candidates);
    }

    public static final class ActualSet {
        public final double weight;
        public final int reps;
        public final boolean done;

        public ActualSet(double weight, int reps, boolean done) {
            this.weight = weight;
            this.reps = reps;
            this.done = done;
        }
    }

    /** Conservative reference for this exact exercise; does not estimate a new maximum. */
    public static double comparableWeight(
            List<ActualSet> logs,
            int prescribedReps,
            int prescribedSets,
            int targetReps,
            int targetSets) {
        if (targetReps < 1
                || targetSets < 1
                || prescribedReps != targetReps
                || prescribedSets < targetSets
                || logs == null
                || logs.size() < prescribedSets) return -1;
        double minimum = Double.POSITIVE_INFINITY;
        for (ActualSet log : logs) {
            if (!log.done
                    || log.reps < targetReps
                    || !Double.isFinite(log.weight)
                    || log.weight < 0) return -1;
            minimum = Math.min(minimum, log.weight);
        }
        // A single heavier set never becomes the reference for every future set.
        return Double.isFinite(minimum) ? minimum : -1;
    }

    public static final class Result {
        public final double weight;
        public final int sets;
        public final String code;

        Result(double weight, int sets, String code) {
            this.weight = weight;
            this.sets = sets;
            this.code = code;
        }
    }

    public static Result suggest(
            double baseline, double previous, double latest, int sets, boolean reduced) {
        double weight = latest >= 0 ? latest : baseline;
        String code =
                latest < 0
                        ? "first"
                        : previous >= 0 && latest > previous
                                ? "up"
                                : previous >= 0 && latest < previous ? "down" : "same";
        return new Result(weight, reduced ? Math.max(1, sets - 1) : sets, code);
    }
}
