package com.example.fitplan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure-Java personal summary aggregator and profile code helpers shared between ProfileScreen and
 * CoachScreen.
 */
public final class ProfileSummary {
    public static final String[] GOAL_CODES = {"", "hypertrophy", "strength", "fat_loss", "health"};
    public static final String[] EXP_CODES = {"", "beginner", "intermediate", "advanced"};
    public static final String[] EQUIP_CODES = {
        "", "full_gym", "dumbbell_bench_band", "home_bodyweight"
    };

    public final int totalSessions;
    public final int totalPerformedSets;
    public final int totalCheckins;
    public final long latestWorkoutTime;
    public final CoachModels.CheckinRecord latestCheckin;

    public ProfileSummary(
            int totalSessions,
            int totalPerformedSets,
            int totalCheckins,
            long latestWorkoutTime,
            CoachModels.CheckinRecord latestCheckin) {
        this.totalSessions = totalSessions;
        this.totalPerformedSets = totalPerformedSets;
        this.totalCheckins = totalCheckins;
        this.latestWorkoutTime = latestWorkoutTime;
        this.latestCheckin = latestCheckin;
    }

    public static ProfileSummary fromSnapshot(CoachModels.CoachSnapshot snapshot) {
        if (snapshot == null) {
            return new ProfileSummary(0, 0, 0, 0L, null);
        }
        int sessions = 0;
        int performedSets = 0;
        long latestTime = 0L;
        for (CoachModels.WorkoutRecord w : snapshot.workouts) {
            if (w == null || w.finished <= 0) {
                continue;
            }
            int setsInWorkout = 0;
            for (CoachModels.ExerciseRow row : w.rows) {
                for (CoachModels.SetLog log : row.logs) {
                    if (log.isPerformed()) {
                        setsInWorkout++;
                    }
                }
            }
            if (setsInWorkout > 0) {
                sessions++;
                performedSets += setsInWorkout;
                if (w.finished > latestTime) {
                    latestTime = w.finished;
                }
            }
        }

        List<CoachModels.CheckinRecord> checkins = new ArrayList<>();
        for (CoachModels.CheckinRecord c : snapshot.checkins) {
            if (c != null) {
                checkins.add(c);
            }
        }
        checkins.sort(
                Comparator.comparing((CoachModels.CheckinRecord c) -> c.day)
                        .reversed()
                        .thenComparingLong(c -> -c.created));
        CoachModels.CheckinRecord latest = checkins.isEmpty() ? null : checkins.get(0);

        return new ProfileSummary(sessions, performedSets, checkins.size(), latestTime, latest);
    }

    public static String goalLabelKey(String code) {
        if ("hypertrophy".equals(code)) {
            return "coach_goal_hypertrophy";
        }
        if ("strength".equals(code)) {
            return "coach_goal_strength";
        }
        if ("fat_loss".equals(code)) {
            return "coach_goal_fat_loss";
        }
        if ("health".equals(code)) {
            return "coach_goal_health";
        }
        return "coach_unset";
    }

    public static String expLabelKey(String code) {
        if ("beginner".equals(code)) {
            return "coach_exp_beginner";
        }
        if ("intermediate".equals(code)) {
            return "coach_exp_intermediate";
        }
        if ("advanced".equals(code)) {
            return "coach_exp_advanced";
        }
        return "coach_unset";
    }

    public static String equipLabelKey(String code) {
        if ("full_gym".equals(code)) {
            return "coach_equip_gym";
        }
        if ("dumbbell_bench_band".equals(code)) {
            return "coach_equip_dumbbell_band";
        }
        if ("home_bodyweight".equals(code)) {
            return "coach_equip_home_bw";
        }
        return "coach_unset";
    }

    public static String sleepLabelKey(int sleep) {
        return sleep == 0 ? "good" : sleep == 1 ? "fair" : "poor";
    }

    public static String foodLabelKey(int food) {
        return food == 0 ? "adequate" : food == 1 ? "insufficient" : "poor";
    }

    public static String sorenessLabelKey(int soreness) {
        return soreness == 0 ? "none_sore" : soreness == 1 ? "mild_sore" : "severe_sore";
    }

    public static int indexOf(String[] values, String target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(target)) {
                return i;
            }
        }
        return 0;
    }
}
