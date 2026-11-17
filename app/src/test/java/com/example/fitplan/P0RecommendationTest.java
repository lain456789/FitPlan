package com.example.fitplan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class P0RecommendationTest {
    private Recommendation.ActualSet set(double weight, int reps, boolean done) {
        return new Recommendation.ActualSet(weight, reps, done);
    }

    @Test
    public void thirtyMinuteRecoveryPlanDoesNotRefillFreedTime() {
        Recommendation.Budget normal = Recommendation.budget(30, 3, 6, false);
        Recommendation.Budget reduced = Recommendation.budget(30, 3, 6, true);
        assertEquals(2, normal.exercises);
        assertEquals(6, normal.totalSets());
        assertEquals(normal.exercises, reduced.exercises);
        assertEquals(4, reduced.totalSets());
        assertEquals(17, reduced.estimatedMinutes);
        assertTrue(reduced.timeLimited);
    }

    @Test
    public void allSupportedBudgetsFitAndNeverIncreaseWhenReduced() {
        for (int minutes = 10; minutes <= 180; minutes++)
            for (int sets = 2; sets <= 4; sets++)
                for (int candidates = 0; candidates <= 18; candidates++) {
                    Recommendation.Budget normal =
                            Recommendation.budget(minutes, sets, candidates, false);
                    Recommendation.Budget reduced =
                            Recommendation.budget(minutes, sets, candidates, true);
                    assertEquals(normal.exercises, reduced.exercises);
                    assertTrue(reduced.totalSets() <= normal.totalSets());
                    assertTrue(normal.estimatedMinutes <= minutes);
                    assertTrue(reduced.estimatedMinutes <= minutes);
                    assertTrue(normal.exercises <= candidates);
                    if (normal.exercises > 0) assertTrue(reduced.sets >= 1);
                }
    }

    @Test
    public void tinyBudgetNeverCreatesZeroSetExercises() {
        Recommendation.Budget b = Recommendation.budget(10, 4, 6, true);
        assertEquals(1, b.exercises);
        assertEquals(1, b.sets);
        assertFalse(b.reduced);
        assertTrue(b.timeLimited);
        assertEquals(0, Recommendation.budget(7, 3, 6, true).exercises);
    }

    @Test
    public void completeComparableWorkoutUsesCommonSupportedWeight() {
        assertEquals(
                40,
                Recommendation.comparableWeight(
                        Arrays.asList(set(40, 10, true), set(45, 10, true), set(50, 10, true)),
                        10,
                        3,
                        10,
                        3),
                0);
    }

    @Test
    public void oneHeavyRepCannotSupportTenReps() {
        assertEquals(
                -1,
                Recommendation.comparableWeight(
                        Collections.singletonList(set(50, 1, true)), 10, 1, 10, 1),
                0);
    }

    @Test
    public void differentPrescriptionIsNotComparable() {
        assertEquals(
                -1,
                Recommendation.comparableWeight(
                        Collections.singletonList(set(50, 12, true)), 12, 1, 10, 1),
                0);
    }

    @Test
    public void unfinishedOrUnderTargetWorkoutIsNotComparable() {
        assertEquals(
                -1,
                Recommendation.comparableWeight(
                        Arrays.asList(set(40, 10, true), set(40, 10, false)), 10, 2, 10, 2),
                0);
        assertEquals(
                -1,
                Recommendation.comparableWeight(
                        Arrays.asList(set(40, 10, true), set(40, 8, true)), 10, 2, 10, 2),
                0);
    }

    @Test
    public void oneCompletedSetDoesNotSupportThreePlannedSets() {
        assertEquals(
                -1,
                Recommendation.comparableWeight(
                        Collections.singletonList(set(50, 10, true)), 10, 1, 10, 3),
                0);
        assertEquals(
                -1,
                Recommendation.comparableWeight(
                        Collections.singletonList(set(50, 10, true)), 10, 3, 10, 3),
                0);
    }

    @Test
    public void completeHistoryCanSupportFewerSets() {
        assertEquals(
                35,
                Recommendation.comparableWeight(
                        Arrays.asList(set(40, 10, true), set(35, 10, true), set(35, 10, true)),
                        10,
                        3,
                        10,
                        2),
                0);
    }

    @Test
    public void bodyweightIsValidAndInvalidWeightsAreRejected() {
        assertEquals(
                0,
                Recommendation.comparableWeight(
                        Collections.singletonList(set(0, 10, true)), 10, 1, 10, 1),
                0);
        for (double weight : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY})
            assertEquals(
                    -1,
                    Recommendation.comparableWeight(
                            Collections.singletonList(set(weight, 10, true)), 10, 1, 10, 1),
                    0);
        assertEquals(-1, Recommendation.comparableWeight(Collections.emptyList(), 10, 1, 10, 1), 0);
    }
}
