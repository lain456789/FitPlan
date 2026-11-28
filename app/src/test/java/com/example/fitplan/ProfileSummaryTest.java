package com.example.fitplan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ProfileSummaryTest {
    @Test
    public void handlesEmptySnapshotGracefully() {
        CoachModels.CoachSnapshot empty =
                new CoachModels.CoachSnapshot(
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        CoachModels.UserProfile.empty());
        ProfileSummary summary = ProfileSummary.fromSnapshot(empty);
        assertEquals(0, summary.totalSessions);
        assertEquals(0, summary.totalPerformedSets);
        assertEquals(0, summary.totalCheckins);
        assertEquals(0L, summary.latestWorkoutTime);
        assertNull(summary.latestCheckin);
    }

    @Test
    public void countsOnlyPerformedSetsAndSelectsNewestCheckin() {
        CoachModels.SetLog done1 =
                new CoachModels.SetLog(60, 8, true, false, "appropriate", "", "");
        CoachModels.SetLog done2 = new CoachModels.SetLog(60, 8, true, false, "hard", "", "");
        CoachModels.SetLog skipped =
                new CoachModels.SetLog(60, 0, false, true, "unknown", "", "time");
        CoachModels.SetLog unfinished =
                new CoachModels.SetLog(60, 0, false, false, "unknown", "too_hard", "");

        CoachModels.ExerciseRow row =
                new CoachModels.ExerciseRow(
                        "builtin-0",
                        "杠铃卧推",
                        "chest",
                        4,
                        8,
                        60,
                        false,
                        "",
                        Arrays.asList(done1, done2, skipped, unfinished));

        CoachModels.WorkoutRecord valid =
                new CoachModels.WorkoutRecord(
                        "w1", "Push", 1000L, 5000L, "", "", 0, Collections.singletonList(row));
        CoachModels.WorkoutRecord emptyWorkout =
                new CoachModels.WorkoutRecord(
                        "w2",
                        "Empty",
                        6000L,
                        9000L,
                        "",
                        "",
                        0,
                        Collections.singletonList(
                                new CoachModels.ExerciseRow(
                                        "builtin-1",
                                        "上斜",
                                        "chest",
                                        1,
                                        8,
                                        20,
                                        false,
                                        "",
                                        Collections.singletonList(skipped))));

        CoachModels.CheckinRecord older =
                new CoachModels.CheckinRecord(
                        "c1",
                        "2026-11-20",
                        100L,
                        Collections.singletonList("chest"),
                        45,
                        1,
                        0,
                        0,
                        0,
                        "",
                        "");
        CoachModels.CheckinRecord newer =
                new CoachModels.CheckinRecord(
                        "c2",
                        "2026-11-27",
                        200L,
                        Collections.singletonList("back"),
                        60,
                        2,
                        1,
                        0,
                        1,
                        "",
                        "");

        CoachModels.CoachSnapshot snapshot =
                new CoachModels.CoachSnapshot(
                        Arrays.asList(valid, emptyWorkout),
                        Arrays.asList(older, newer),
                        Collections.emptyList(),
                        CoachModels.UserProfile.empty());

        ProfileSummary summary = ProfileSummary.fromSnapshot(snapshot);
        assertEquals(1, summary.totalSessions);
        assertEquals(2, summary.totalPerformedSets);
        assertEquals(2, summary.totalCheckins);
        assertEquals(5000L, summary.latestWorkoutTime);
        assertNotNull(summary.latestCheckin);
        assertEquals("2026-11-27", summary.latestCheckin.day);
    }
}
