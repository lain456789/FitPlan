package com.example.fitplan;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class WorkoutRulesTest {
    @Test
    public void timeBudgetKeepsCoverageInExistingOrder() {
        assertArrayEquals(new int[] {2, 2, 2}, WorkoutRules.allocate(new int[] {3, 3, 3}, 18, 0));
    }

    @Test
    public void currentRestIsIncluded() {
        assertArrayEquals(new int[] {1, 0}, WorkoutRules.allocate(new int[] {3, 2}, 4, 60));
        assertArrayEquals(new int[] {0, 0}, WorkoutRules.allocate(new int[] {3, 2}, 3, 60));
    }

    @Test
    public void zeroAndSufficientBudgets() {
        assertArrayEquals(new int[] {0, 0}, WorkoutRules.allocate(new int[] {3, 2}, 0, 0));
        assertArrayEquals(new int[] {3, 2}, WorkoutRules.allocate(new int[] {3, 2}, 180, 0));
    }

    @Test
    public void alreadyRemovedSetsAreNeverAddedBack() {
        assertArrayEquals(new int[] {0, 1, 0}, WorkoutRules.allocate(new int[] {0, 1, 0}, 60, 0));
        assertArrayEquals(new int[0], WorkoutRules.allocate(new int[0], 0, 0));
    }

    @Test
    public void allSmallBudgetsFitAndNeverIncreaseAnyExercise() {
        for (int a = 0; a <= 5; a++)
            for (int b = 0; b <= 5; b++)
                for (int c = 0; c <= 5; c++)
                    for (int minutes = 0; minutes <= 30; minutes++)
                        for (long rest : new long[] {0, 30, 90}) {
                            int[] before = {a, b, c},
                                    after = WorkoutRules.allocate(before, minutes, rest);
                            int total = 0;
                            for (int i = 0; i < 3; i++) {
                                assertTrue(after[i] >= 0 && after[i] <= before[i]);
                                total += after[i];
                            }
                            if (total > 0) assertTrue(total * 180L + rest <= minutes * 60L);
                            assertArrayEquals(after, WorkoutRules.allocate(before, minutes, rest));
                        }
    }

    @Test
    public void invalidBudgetsAreRejected() {
        for (int minutes : new int[] {-1, 181})
            try {
                WorkoutRules.allocate(new int[] {1}, minutes, 0);
                fail();
            } catch (IllegalArgumentException expected) {
            }
        try {
            WorkoutRules.allocate(new int[] {-1}, 5, 0);
            fail();
        } catch (IllegalArgumentException expected) {
        }
        try {
            WorkoutRules.allocate(new int[] {21}, 5, 0);
            fail();
        } catch (IllegalArgumentException expected) {
        }
        try {
            WorkoutRules.allocate(new int[] {1}, 5, -1);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void unavailableSecondaryEquipmentAlsoFiltersCandidates() {
        assertTrue(Catalog.canReplace("builtin-0", "builtin-16", Collections.singleton("barbell")));
        assertFalse(Catalog.canReplace("builtin-0", "builtin-16", Collections.singleton("bench")));
        assertTrue(
                Catalog.canReplace(
                        "builtin-0",
                        "builtin-17",
                        new HashSet<>(Arrays.asList("bench", "barbell", "dumbbell"))));
    }

    @Test
    public void sameBodyPartAloneDoesNotMakeAnAlternative() {
        assertFalse(Catalog.canReplace("builtin-0", "builtin-2", Collections.emptySet()));
        assertFalse(Catalog.canReplace("builtin-0", "builtin-0", Collections.emptySet()));
        assertFalse(Catalog.canReplace("custom", "builtin-16", Collections.emptySet()));
        assertFalse(Catalog.canReplace("builtin-10", "builtin-9", Collections.emptySet()));
        assertTrue(
                Catalog.canReplace(
                        "builtin-7", "builtin-18", Collections.singleton("row_machine")));
    }

    @Test
    public void stableIdsAndEquipmentMetadataRemainConsistent() {
        assertEquals("杠铃卧推", Catalog.EXERCISES[0][0]);
        assertEquals("卷腹", Catalog.EXERCISES[14][0]);
        assertFalse(Catalog.equipmentKnown("builtin-5"));
        assertTrue(Catalog.equipmentKnown("builtin-17"));
        assertEquals(0, Catalog.equipment("builtin-17").length);
        for (int i = 0; i < Catalog.EXERCISES.length; i++) {
            assertEquals(i, Catalog.builtIn("builtin-" + i));
            assertFalse(Catalog.movement("builtin-" + i).isEmpty());
            assertTrue(
                    Arrays.asList(Catalog.PARTS)
                            .contains(Catalog.exercisePart("builtin-" + i, "")));
            for (String equipment : Catalog.equipment("builtin-" + i))
                assertTrue(Arrays.asList(Catalog.EQUIPMENT).contains(equipment));
        }
    }
}
