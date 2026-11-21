package com.example.fitplan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class CoachContextBuilderTest {
    private final TimeZone zone = TimeZone.getTimeZone("Asia/Shanghai");

    private long parseTime(String text) {
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            fmt.setTimeZone(zone);
            return fmt.parse(text).getTime();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private Map<String, Object> parseContextJson(String promptBlock) {
        int start = promptBlock.indexOf("```json\n");
        int end = promptBlock.lastIndexOf("\n```");
        assertTrue("Prompt block must contain fenced JSON", start >= 0 && end > start);
        String json = promptBlock.substring(start + "```json\n".length(), end);
        Map<String, Object> root = SimpleJson.asObject(SimpleJson.parse(json));
        assertNotNull(root);
        return root;
    }

    @Test
    public void handlesEmptyDatabaseGracefullyWithoutHallucinating() {
        long now = parseTime("2026-11-21 20:00");
        CoachModels.CoachSnapshot empty =
                new CoachModels.CoachSnapshot(
                        Collections.emptyList(),
                        Collections.emptyList(),
                        CoachContextBuilder.builtInCatalogDefs(),
                        CoachModels.UserProfile.empty());

        CoachContextBuilder.BuiltContext built = CoachContextBuilder.build(empty, now, zone, "en");

        assertFalse(built.stats.hasHistory);
        assertEquals(0, built.stats.totalSessions);
        assertEquals(0, built.stats.totalCheckins);
        assertEquals(30, built.stats.exerciseCatalogCount);

        Map<String, Object> root = parseContextJson(built.promptBlock);
        Map<String, Object> coverage = SimpleJson.asObject(root.get("data_coverage"));
        assertEquals(Boolean.FALSE, coverage.get("has_history"));
        assertEquals("en", coverage.get("ui_language"));

        Map<String, Object> profile = SimpleJson.asObject(root.get("user_profile"));
        assertEquals("not_provided", profile.get("goal"));
        assertEquals("not_provided", profile.get("weight_kg"));

        List<Object> catalog = SimpleJson.asList(root.get("exercise_catalog"));
        assertEquals(30, catalog.size());
    }

    @Test
    public void capsRecentSessionsToTenAndCheckinsToSevenAndIncludesProfile() {
        long now = parseTime("2026-11-21 20:00");
        List<CoachModels.WorkoutRecord> workouts = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            long end = now - (i + 1) * 24L * 3600L * 1000L;
            List<CoachModels.SetLog> logs =
                    Arrays.asList(
                            new CoachModels.SetLog(
                                    60.0, 10, true, false, "appropriate", "none", ""),
                            new CoachModels.SetLog(
                                    60.0, 10, true, false, "appropriate", "none", ""),
                            new CoachModels.SetLog(
                                    60.0, 10, true, false, "appropriate", "none", ""));
            CoachModels.ExerciseRow row =
                    new CoachModels.ExerciseRow(
                            "builtin-9", "深蹲", "legs", 3, 10, 60.0, false, "", logs);
            workouts.add(
                    new CoachModels.WorkoutRecord(
                            "w-" + i,
                            "Leg Day " + i,
                            end - 35 * 60_000L,
                            end,
                            "completed",
                            "",
                            0,
                            Collections.singletonList(row)));
        }

        List<CoachModels.CheckinRecord> checkins = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String day = String.format(Locale.US, "2026-11-%02d", 10 + i);
            checkins.add(
                    new CoachModels.CheckinRecord(
                            "c-" + i,
                            day,
                            now - (10 - i) * 86_400_000L,
                            Collections.singletonList("legs"),
                            45,
                            1,
                            0,
                            0,
                            0,
                            "Note " + i,
                            ""));
        }

        CoachModels.UserProfile profile =
                new CoachModels.UserProfile(
                        "hypertrophy", "intermediate", "72", 4, "full_gym", "none", now);

        CoachContextBuilder.BuiltContext built =
                CoachContextBuilder.build(
                        new CoachModels.CoachSnapshot(
                                workouts,
                                checkins,
                                CoachContextBuilder.builtInCatalogDefs(),
                                profile),
                        now,
                        zone,
                        "zh");

        assertEquals(14, built.stats.totalSessions);
        assertTrue(built.stats.readyToProgressExerciseIds.contains("builtin-9"));
        assertFalse(built.stats.plateauExerciseIds.contains("builtin-9"));
        assertFalse(built.stats.hasRecentDiscomfort);

        Map<String, Object> root = parseContextJson(built.promptBlock);
        List<Object> recent10 = SimpleJson.asList(root.get("recent_10_sessions"));
        List<Object> recent7 = SimpleJson.asList(root.get("recent_7d_checkins"));
        List<Object> weeks = SimpleJson.asList(root.get("weekly_summary_8w"));
        assertEquals(10, recent10.size());
        assertEquals(7, recent7.size());
        assertEquals(8, weeks.size());

        Map<String, Object> pJson = SimpleJson.asObject(root.get("user_profile"));
        assertEquals("hypertrophy", pJson.get("goal"));
        assertEquals("72", pJson.get("weight_kg"));
        assertEquals(4, SimpleJson.asInt(pJson.get("weekly_days"), 0));
    }

    @Test
    public void detectsPlateauAndRecentDiscomfortAccurately() {
        long now = parseTime("2026-11-21 20:00");
        List<CoachModels.WorkoutRecord> workouts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            long end = now - (3 - i) * 5L * 86_400_000L;
            List<CoachModels.SetLog> benchLogs =
                    Arrays.asList(
                            new CoachModels.SetLog(
                                    55.0, 10, true, false, "appropriate", "none", ""),
                            new CoachModels.SetLog(55.0, 9, true, false, "hard", "too_hard", ""),
                            new CoachModels.SetLog(55.0, 8, true, false, "hard", "too_hard", ""));
            List<CoachModels.ExerciseRow> rows = new ArrayList<>();
            rows.add(
                    new CoachModels.ExerciseRow(
                            "builtin-0", "杠铃卧推", "chest", 3, 10, 55.0, false, "", benchLogs));
            if (i == 2) {
                List<CoachModels.SetLog> pressLogs =
                        Arrays.asList(
                                new CoachModels.SetLog(
                                        14.0, 10, true, false, "appropriate", "none", ""),
                                new CoachModels.SetLog(
                                        14.0, 0, false, false, "unknown", "discomfort", ""));
                rows.add(
                        new CoachModels.ExerciseRow(
                                "builtin-3",
                                "哑铃推举",
                                "shoulders",
                                2,
                                10,
                                14.0,
                                false,
                                "",
                                pressLogs));
            }
            workouts.add(
                    new CoachModels.WorkoutRecord(
                            "stall-" + i,
                            "Push",
                            end - 30 * 60_000L,
                            end,
                            i == 2 ? "discomfort" : "completed",
                            "",
                            0,
                            rows));
        }

        CoachContextBuilder.BuiltContext built =
                CoachContextBuilder.build(
                        new CoachModels.CoachSnapshot(
                                workouts,
                                Collections.emptyList(),
                                CoachContextBuilder.builtInCatalogDefs(),
                                CoachModels.UserProfile.empty()),
                        now,
                        zone,
                        "zh");

        assertTrue(built.stats.plateauExerciseIds.contains("builtin-0"));
        assertFalse(built.stats.readyToProgressExerciseIds.contains("builtin-0"));
        assertTrue(built.stats.hasRecentDiscomfort);
    }
}
