package com.example.fitplan;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RingProgressTest {
    @Test
    public void partialAndCompleteTargets() {
        assertEquals(.5f, RingProgress.fraction(6, 12), 0);
        assertEquals(1f, RingProgress.fraction(12, 12), 0);
    }

    @Test
    public void exceededTargetsCapOnlyVisualArc() {
        assertEquals(1f, RingProgress.fraction(24, 12), 0);
    }

    @Test
    public void absentZeroAndInvalidTargetsNeverFill() {
        assertEquals(0f, RingProgress.fraction(10, 0), 0);
        assertEquals(0f, RingProgress.fraction(10, -1), 0);
        assertEquals(0f, RingProgress.fraction(10, Double.NaN), 0);
        assertEquals(0f, RingProgress.fraction(10, Double.POSITIVE_INFINITY), 0);
    }

    @Test
    public void invalidActualsNeverFill() {
        assertEquals(0f, RingProgress.fraction(-1, 10), 0);
        assertEquals(0f, RingProgress.fraction(Double.NaN, 10), 0);
        assertEquals(0f, RingProgress.fraction(Double.POSITIVE_INFINITY, 10), 0);
    }
}
