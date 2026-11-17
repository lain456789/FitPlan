package com.example.fitplan;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RecommendationTest {
    @Test
    public void unchangedActualWeightNeverIncreases() {
        Recommendation.Result r = Recommendation.suggest(50, 40, 40, 3, false);
        assertEquals(40, r.weight, 0);
        assertEquals(3, r.sets);
    }

    @Test
    public void actualIncreaseIsFollowedWithoutExtraIncrement() {
        assertEquals(42.5, Recommendation.suggest(40, 40, 42.5, 3, false).weight, 0);
    }

    @Test
    public void actualDecreaseIsRespected() {
        assertEquals(30, Recommendation.suggest(40, 40, 30, 3, false).weight, 0);
    }

    @Test
    public void poorRecoveryReducesSetsWithoutErasingBaseline() {
        Recommendation.Result reduced = Recommendation.suggest(40, 40, 40, 3, true);
        assertEquals(2, reduced.sets);
        assertEquals(40, reduced.weight, 0);
        assertEquals(3, Recommendation.suggest(40, 40, 40, 3, false).sets);
        assertEquals(1, Recommendation.suggest(40, 40, 40, 1, true).sets);
    }

    @Test
    public void firstWorkoutUsesBaseline() {
        assertEquals(0, Recommendation.suggest(0, -1, -1, 3, false).weight, 0);
    }
}
