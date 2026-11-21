package com.example.fitplan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure-Java domain models for the local AI fitness coach context and chat turns. */
public final class CoachModels {
    private CoachModels() {}

    public static final class SetLog {
        public final double weight;
        public final int reps;
        public final boolean done;
        public final boolean skipped;
        public final String effort;
        public final String incompleteReason;
        public final String skipReason;

        public SetLog(
                double weight,
                int reps,
                boolean done,
                boolean skipped,
                String effort,
                String incompleteReason,
                String skipReason) {
            this.weight = weight;
            this.reps = reps;
            this.done = done;
            this.skipped = skipped;
            this.effort = effort == null || effort.isEmpty() ? "unknown" : effort;
            this.incompleteReason =
                    incompleteReason == null || incompleteReason.isEmpty()
                            ? "unknown"
                            : incompleteReason;
            this.skipReason = skipReason == null ? "" : skipReason;
        }

        public boolean isPerformed() {
            return done && !skipped && Double.isFinite(weight) && weight >= 0 && reps > 0;
        }

        public String effectiveReason(String sessionEndReason) {
            if (skipped) {
                return "equipment".equals(skipReason) ? "equipment" : "time";
            }
            if ("none".equals(incompleteReason)) {
                return "none";
            }
            if (!done && "unknown".equals(incompleteReason)) {
                return sessionEndReason == null || sessionEndReason.isEmpty()
                        ? "unknown"
                        : sessionEndReason;
            }
            return incompleteReason;
        }

        public int effortRank() {
            if ("easy".equals(effort)) {
                return 1;
            }
            if ("appropriate".equals(effort)) {
                return 2;
            }
            if ("hard".equals(effort)) {
                return 3;
            }
            return 0;
        }
    }

    public static final class ExerciseRow {
        public final String exerciseId;
        public final String name;
        public final String part;
        public final int targetSets;
        public final int targetReps;
        public final double targetWeight;
        public final boolean retired;
        public final String replacementOf;
        public final List<SetLog> logs;

        public ExerciseRow(
                String exerciseId,
                String name,
                String part,
                int targetSets,
                int targetReps,
                double targetWeight,
                boolean retired,
                String replacementOf,
                List<SetLog> logs) {
            this.exerciseId = exerciseId == null ? "" : exerciseId;
            this.name = name == null ? "" : name;
            this.part = Catalog.exercisePart(this.exerciseId, part);
            this.targetSets = targetSets;
            this.targetReps = targetReps;
            this.targetWeight = targetWeight;
            this.retired = retired;
            this.replacementOf = replacementOf == null ? "" : replacementOf;
            this.logs =
                    logs == null
                            ? Collections.emptyList()
                            : Collections.unmodifiableList(new ArrayList<>(logs));
        }

        public boolean isAdjusted() {
            if (retired || !replacementOf.isEmpty()) {
                return true;
            }
            for (SetLog log : logs) {
                if (log.skipped) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final class WorkoutRecord {
        public final String id;
        public final String name;
        public final long started;
        public final long finished;
        public final String endReason;
        public final String demoDataset;
        public final int adjustmentCount;
        public final List<ExerciseRow> rows;

        public WorkoutRecord(
                String id,
                String name,
                long started,
                long finished,
                String endReason,
                String demoDataset,
                int adjustmentCount,
                List<ExerciseRow> rows) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
            this.started = started;
            this.finished = finished;
            this.endReason = endReason == null ? "" : endReason;
            this.demoDataset = demoDataset == null ? "" : demoDataset;
            this.adjustmentCount = Math.max(0, adjustmentCount);
            this.rows =
                    rows == null
                            ? Collections.emptyList()
                            : Collections.unmodifiableList(new ArrayList<>(rows));
        }
    }

    public static final class CheckinRecord {
        public final String id;
        public final String day;
        public final long created;
        public final List<String> parts;
        public final int minutes;
        public final int volume;
        public final int sleep;
        public final int food;
        public final int soreness;
        public final String notes;
        public final String demoDataset;

        public CheckinRecord(
                String id,
                String day,
                long created,
                List<String> parts,
                int minutes,
                int volume,
                int sleep,
                int food,
                int soreness,
                String notes,
                String demoDataset) {
            this.id = id == null ? "" : id;
            this.day = day == null ? "" : day;
            this.created = created;
            this.parts =
                    parts == null
                            ? Collections.emptyList()
                            : Collections.unmodifiableList(new ArrayList<>(parts));
            this.minutes = minutes;
            this.volume = volume;
            this.sleep = sleep;
            this.food = food;
            this.soreness = soreness;
            this.notes = notes == null ? "" : notes;
            this.demoDataset = demoDataset == null ? "" : demoDataset;
        }

        public String sleepLabel() {
            return sleep == 0 ? "good" : sleep == 1 ? "fair" : "poor";
        }

        public String foodLabel() {
            return food == 0 ? "adequate" : food == 1 ? "insufficient" : "poor";
        }

        public String sorenessLabel() {
            return soreness == 0 ? "none" : soreness == 1 ? "mild" : "severe";
        }

        public String volumeLabel() {
            return volume == 0 ? "light" : volume == 2 ? "high" : "normal";
        }
    }

    public static final class ExerciseDef {
        public final String id;
        public final String zhName;
        public final String enName;
        public final String part;
        public final String movement;
        public final List<String> equipment;
        public final boolean custom;

        public ExerciseDef(
                String id,
                String zhName,
                String enName,
                String part,
                String movement,
                List<String> equipment,
                boolean custom) {
            this.id = id == null ? "" : id;
            this.zhName = zhName == null ? "" : zhName;
            this.enName = enName == null ? "" : enName;
            this.part = Catalog.exercisePart(this.id, part);
            this.movement = movement == null ? "" : movement;
            this.equipment =
                    equipment == null
                            ? Collections.emptyList()
                            : Collections.unmodifiableList(new ArrayList<>(equipment));
            this.custom = custom;
        }
    }

    public static final class UserProfile {
        public final String goal;
        public final String experience;
        public final String weightKg;
        public final int weeklyDays;
        public final String equipment;
        public final String injuries;
        public final long updated;

        public UserProfile(
                String goal,
                String experience,
                String weightKg,
                int weeklyDays,
                String equipment,
                String injuries,
                long updated) {
            this.goal = goal == null ? "" : goal.trim();
            this.experience = experience == null ? "" : experience.trim();
            this.weightKg = weightKg == null ? "" : weightKg.trim();
            this.weeklyDays = weeklyDays;
            this.equipment = equipment == null ? "" : equipment.trim();
            this.injuries = injuries == null ? "" : injuries.trim();
            this.updated = updated;
        }

        public static UserProfile empty() {
            return new UserProfile("", "", "", 0, "", "", 0);
        }

        public boolean isEmpty() {
            return goal.isEmpty()
                    && experience.isEmpty()
                    && weightKg.isEmpty()
                    && weeklyDays <= 0
                    && equipment.isEmpty()
                    && injuries.isEmpty();
        }
    }

    public static final class CoachSnapshot {
        public final List<WorkoutRecord> workouts;
        public final List<CheckinRecord> checkins;
        public final List<ExerciseDef> exercises;
        public final UserProfile profile;

        public CoachSnapshot(
                List<WorkoutRecord> workouts,
                List<CheckinRecord> checkins,
                List<ExerciseDef> exercises,
                UserProfile profile) {
            this.workouts =
                    workouts == null
                            ? Collections.emptyList()
                            : Collections.unmodifiableList(new ArrayList<>(workouts));
            this.checkins =
                    checkins == null
                            ? Collections.emptyList()
                            : Collections.unmodifiableList(new ArrayList<>(checkins));
            this.exercises =
                    exercises == null
                            ? Collections.emptyList()
                            : Collections.unmodifiableList(new ArrayList<>(exercises));
            this.profile = profile == null ? UserProfile.empty() : profile;
        }
    }

    public static final class ChatMessage {
        public final String role;
        public final String content;
        public final boolean isError;
        public final long timestamp;

        public ChatMessage(String role, String content, boolean isError, long timestamp) {
            this.role = role;
            this.content = content == null ? "" : content;
            this.isError = isError;
            this.timestamp = timestamp;
        }
    }
}
