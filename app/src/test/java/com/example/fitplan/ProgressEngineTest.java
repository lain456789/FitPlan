package com.example.fitplan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ProgressEngineTest {
    private ProgressEngine.Sample sample(long time, int[] reps, int effort, String missed) {
        List<ProgressEngine.SetResult> logs = new ArrayList<>();
        for (int r : reps)
            logs.add(
                    new ProgressEngine.SetResult(
                            40, r, r > 0, false, r > 0 ? effort : 0, r < 10 ? missed : "none"));
        return new ProgressEngine.Sample(
                "s" + time, "exercise", time, reps.length, 10, logs, false, false);
    }

    private ProgressEngine.Assessment assess(ProgressEngine.Sample... samples) {
        return ProgressEngine.assess(Arrays.asList(samples));
    }

    @Test
    public void sameWeightAndMoreRepsIsRecognised() {
        ProgressEngine.Assessment r =
                assess(
                        sample(1, new int[] {8, 8, 8}, 3, "too_hard"),
                        sample(2, new int[] {9, 9, 9}, 3, "too_hard"));
        assertEquals("reps_improved", r.status);
        assertEquals(24, r.previous.totalReps());
        assertEquals(27, r.latest.totalReps());
        assertEquals("complete_target", r.action);
        assertFalse(r.canIncrease);
    }

    @Test
    public void moreTargetSetsCanImproveEvenAtTheSameTotalReps() {
        ProgressEngine.Assessment r =
                assess(
                        sample(1, new int[] {12, 12, 6}, 3, "too_hard"),
                        sample(2, new int[] {10, 10, 10}, 2, "none"));
        assertEquals(30, r.previous.totalReps());
        assertEquals(30, r.latest.totalReps());
        assertEquals("completion_improved", r.status);
        assertEquals(2, r.previous.met());
        assertEquals(3, r.latest.met());
    }

    @Test
    public void performedSetsAreDistinctFromTargetCompletion() {
        ProgressEngine.Assessment r =
                assess(
                        sample(1, new int[] {10, 10, 0}, 3, "too_hard"),
                        sample(2, new int[] {10, 10, 8}, 3, "too_hard"));
        assertEquals("completion_improved", r.status);
        assertEquals(2, r.latest.met());
        assertEquals(3, r.latest.performed());
    }

    @Test
    public void lowerKnownDifficultyAtTheSameOutputIsRecognised() {
        assertEquals(
                "easier",
                assess(
                                sample(1, new int[] {10, 10, 10}, 3, "none"),
                                sample(2, new int[] {10, 10, 10}, 2, "none"))
                        .status);
    }

    @Test
    public void unknownDifficultyIsNotGuessedOrUsedForProgression() {
        ProgressEngine.Assessment r =
                assess(
                        sample(1, new int[] {10, 10, 10}, 0, "none"),
                        sample(2, new int[] {10, 10, 10}, 1, "none"),
                        sample(3, new int[] {10, 10, 10}, 1, "none"));
        assertFalse(r.canIncrease);
        ProgressEngine.Assessment unknownLatest =
                assess(
                        sample(1, new int[] {10, 10, 10}, 2, "none"),
                        sample(2, new int[] {10, 10, 10}, 0, "none"));
        assertEquals("stable", unknownLatest.status);
        assertEquals(0, unknownLatest.latest.effort());
    }

    @Test
    public void timeAndEquipmentAreNotInterpretedAsReducedAbility() {
        for (String reason : new String[] {"time", "equipment"}) {
            ProgressEngine.Assessment r =
                    assess(
                            sample(1, new int[] {10, 10, 10}, 2, "none"),
                            sample(2, new int[] {10, 0, 0}, 2, reason));
            assertEquals("context", r.status);
            assertEquals(reason, r.action);
            assertFalse(r.canIncrease);
        }
    }

    @Test
    public void discomfortOverridesMetTargetsAndBlocksProgression() {
        ProgressEngine.Sample s =
                new ProgressEngine.Sample(
                        "s",
                        "exercise",
                        4,
                        1,
                        10,
                        Collections.singletonList(
                                new ProgressEngine.SetResult(40, 10, true, false, 1, "discomfort")),
                        false,
                        false);
        assertEquals("discomfort", assess(s).status);
        assertFalse(assess(s).canIncrease);
    }

    @Test
    public void differentGoalsSetCountsAndWeightsAreNotMixed() {
        ProgressEngine.Sample latest = sample(4, new int[] {10, 10, 10}, 2, "none");
        ProgressEngine.Sample otherReps =
                new ProgressEngine.Sample("a", "exercise", 1, 3, 12, latest.sets, false, false);
        ProgressEngine.Sample otherWeight =
                new ProgressEngine.Sample(
                        "b",
                        "exercise",
                        2,
                        3,
                        10,
                        Arrays.asList(
                                new ProgressEngine.SetResult(50, 10, true, false, 2, "none"),
                                new ProgressEngine.SetResult(50, 10, true, false, 2, "none"),
                                new ProgressEngine.SetResult(50, 10, true, false, 2, "none")),
                        false,
                        false);
        assertEquals(
                1,
                assess(otherReps, otherWeight, sample(3, new int[] {10, 10}, 2, "none"), latest)
                        .comparableCount);
    }

    @Test
    public void mixedPerSetWeightsAreNotFlattenedToOneMaximum() {
        ProgressEngine.Sample s =
                new ProgressEngine.Sample(
                        "s",
                        "exercise",
                        1,
                        2,
                        10,
                        Arrays.asList(
                                new ProgressEngine.SetResult(40, 10, true, false, 2, "none"),
                                new ProgressEngine.SetResult(50, 10, true, false, 2, "none")),
                        false,
                        false);
        assertEquals("incomparable", assess(s).status);
        assertEquals(-1, s.weight(), 0);
    }

    @Test
    public void threeConsecutiveCompleteAppropriateRecordsOfferOnlyOneOptionalRep() {
        ProgressEngine.Assessment r =
                assess(
                        sample(1, new int[] {10, 10, 10}, 1, "none"),
                        sample(2, new int[] {10, 10, 10}, 2, "none"),
                        sample(3, new int[] {10, 10, 10}, 2, "none"));
        assertTrue(r.canIncrease);
        assertEquals(11, r.suggestedReps);
        assertEquals(40, r.latest.weight(), 0);
        assertEquals(3, r.latest.targetSets);
    }

    @Test
    public void interveningHardOrInterruptedRecordsBreakTheProgressionStreak() {
        ProgressEngine.Sample a = sample(1, new int[] {10, 10, 10}, 1, "none"),
                b = sample(2, new int[] {10, 10, 10}, 1, "none"),
                c = sample(4, new int[] {10, 10, 10}, 1, "none");
        assertFalse(assess(a, b, sample(3, new int[] {10, 10, 10}, 3, "none"), c).canIncrease);
        assertFalse(assess(a, b, sample(3, new int[] {10, 0, 0}, 2, "time"), c).canIncrease);
    }

    @Test
    public void totalRepsUpButTargetCompletionDownIsMixed() {
        assertEquals(
                "mixed",
                assess(
                                sample(1, new int[] {10, 10, 1}, 3, "too_hard"),
                                sample(2, new int[] {9, 9, 9}, 3, "too_hard"))
                        .status);
    }

    @Test
    public void lowerRecordedOutputRequestsReviewRatherThanAutomaticWeightChanges() {
        ProgressEngine.Assessment r =
                assess(
                        sample(1, new int[] {10, 10, 10}, 2, "none"),
                        sample(2, new int[] {8, 8, 8}, 3, "too_hard"));
        assertEquals("review", r.status);
        assertEquals("review_settings", r.action);
        assertEquals(40, r.latest.weight(), 0);
    }

    @Test
    public void cancelledOrDuplicateRowsAreNotComparable() {
        ProgressEngine.Sample s = sample(1, new int[] {10, 10, 10}, 2, "none");
        assertEquals(
                "context",
                assess(new ProgressEngine.Sample("a", "exercise", 1, 3, 10, s.sets, true, false))
                        .status);
        assertEquals(
                "incomparable",
                assess(new ProgressEngine.Sample("a", "exercise", 1, 3, 10, s.sets, false, true))
                        .status);
    }

    @Test
    public void zeroActualSetsDoNotBecomePlannedRepProgress() {
        ProgressEngine.Assessment r = assess(sample(1, new int[] {0, 0, 0}, 0, "time"));
        assertEquals(0, r.latest.totalReps());
        assertEquals("context", r.status);
        assertFalse(r.canIncrease);
    }

    @Test
    public void recordsAreOrderedByFinishTimeAndTheComparisonCountIsBounded() {
        List<ProgressEngine.Sample> all = new ArrayList<>();
        for (int i = 10; i >= 1; i--) all.add(sample(i, new int[] {10, 10, 10}, 2, "none"));
        assertEquals(7, ProgressEngine.assess(all).comparableCount);
        assertEquals("s10", ProgressEngine.assess(all).latest.id);
        assertEquals("s9", ProgressEngine.assess(all).previous.id);
        assertEquals("no_data", ProgressEngine.assess(Collections.emptyList()).status);
        assertEquals("no_data", assess(sample(0, new int[] {10}, 2, "none")).status);
    }
}
