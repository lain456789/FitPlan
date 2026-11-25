package com.example.fitplan;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

public class CompletedExercisesTest {
    private final TimeZone zone = TimeZone.getTimeZone("UTC");
    private final long now = 1767268800000L;

    private ReportEngine.Session session(long finished) {
        return new ReportEngine.Session(finished - 60000, finished);
    }

    private ReportEngine.ExerciseEntry row(
            ReportEngine.Session session, String id, int planned, boolean... done) {
        ReportEngine.ExerciseEntry exercise = new ReportEngine.ExerciseEntry(id, planned);
        for (boolean complete : done) {
            ReportEngine.SetEntry set =
                    new ReportEngine.SetEntry(id, "Same name", "chest", 0, 10, complete);
            exercise.sets.add(set);
            session.sets.add(set);
        }
        session.exercises.add(exercise);
        return exercise;
    }

    private int count(List<ReportEngine.Session> sessions) {
        return ReportEngine.build(sessions, now, now, zone).today.completedExercises;
    }

    @Test
    public void fullBodyweightCountsButPartialMissingAndSkippedSetsDoNot() {
        ReportEngine.Session session = session(now);
        row(session, "bodyweight", 2, true, true);
        row(session, "partial", 2, true, false);
        row(session, "missing", 2, true);
        row(session, "empty", 0);
        row(session, "skipped", 1, true).hasSkippedSets = true;
        assertEquals(1, count(Collections.singletonList(session)));
        assertEquals(
                5,
                ReportEngine.build(Collections.singletonList(session), now, now, zone).today.sets);
        assertEquals(
                0,
                ReportEngine.build(Collections.singletonList(session), now, now, zone).today.volume,
                0);
    }

    @Test
    public void sameIdCountsOnceAcrossWorkoutsAndSameNamesKeepSeparateIds() {
        ReportEngine.Session first = session(now - 120000), second = session(now);
        row(first, "a", 1, true);
        row(first, "b", 1, true);
        row(second, "a", 2, true, true);
        assertEquals(2, count(Arrays.asList(first, second)));
    }

    @Test
    public void duplicateRowsMustAllBeCompleteWithinAWorkout() {
        ReportEngine.Session session = session(now);
        row(session, "a", 1, true);
        ReportEngine.ExerciseEntry second = row(session, "a", 1, false);
        assertEquals(0, count(Collections.singletonList(session)));
        second.sets.get(0).done = true;
        assertEquals(1, count(Collections.singletonList(session)));
    }

    @Test
    public void correctionDeletionAndLocalDayFilteringRecomputeCounts() {
        ReportEngine.Session today = session(now),
                old = session(now - 86400000),
                future = session(now + 1);
        ReportEngine.ExerciseEntry exercise = row(today, "a", 1, true);
        row(old, "old", 1, true);
        row(future, "future", 1, true);
        List<ReportEngine.Session> records = new ArrayList<>(Arrays.asList(today, old, future));
        assertEquals(1, count(records));
        exercise.sets.get(0).done = false;
        assertEquals(0, count(records));
        exercise.sets.get(0).done = true;
        records.remove(today);
        assertEquals(0, count(records));
    }

    @Test
    public void invalidActualsAndMissingIdentityNeverCloseAnExercise() {
        ReportEngine.Session session = session(now);
        row(session, "", 1, true);
        row(session, "invalid", 1, true).sets.get(0).weight = Double.NaN;
        row(session, "zero-reps", 1, true).sets.get(0).reps = 0;
        assertEquals(0, count(Collections.singletonList(session)));
    }
}
