package com.example.fitplan;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.swipeLeft;
import static androidx.test.espresso.action.ViewActions.swipeRight;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasErrorText;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withTagValue;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;

import android.widget.EditText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ManualExercisePagerTest {
    @Test
    public void rejectsDuplicateAndKeepsEachExercisesSetsWhileSwipingAndRecreating() {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            String[] labels = new String[4];
            scenario.onActivity(a -> {
                a.show("manualHistory");
                JSONObject row = ManualHistory.row(a.find("exercise", "builtin-0"));
                row.optJSONArray("logs")
                        .put(ManualHistory.log("24", "9", "unknown", System.currentTimeMillis()));
                a.manualHistoryDraft.optJSONArray("rows").put(row);
                a.show("manualHistory");
                labels[0] = a.s("add_exercise");
                labels[1] = a.s("ex0");
                labels[2] = a.s("ex1");
                labels[3] = a.s("manual_duplicate_exercise");
            });

            onView(withText(labels[0])).perform(scrollTo(), click());
            onData(startsWith(labels[1] + " · ")).perform(click());
            onView(isAssignableFrom(EditText.class)).check(matches(hasErrorText(labels[3])));
            scenario.onActivity(
                    a -> assertEquals(1, a.manualHistoryDraft.optJSONArray("rows").length()));
            onData(startsWith(labels[2] + " · ")).perform(click());
            onView(withText(labels[2])).check(matches(isDisplayed()));
            onView(withText(labels[1])).check(matches(not(isDisplayed())));
            assertSelectedPage(scenario, 1);

            onView(withTagValue(is((Object) "manual_exercise_pager"))).perform(swipeRight());
            onView(withText(labels[1])).check(matches(isDisplayed()));
            onView(withText(labels[2])).check(matches(not(isDisplayed())));
            assertSelectedPage(scenario, 0);

            onView(withTagValue(is((Object) "manual_exercise_pager"))).perform(swipeLeft());
            assertSelectedPage(scenario, 1);
            scenario.recreate();
            assertSelectedPage(scenario, 1);
        }
    }

    private void assertSelectedPage(ActivityScenario<MainActivity> scenario, int index) {
        scenario.onActivity(a -> {
            assertEquals(index, a.manualHistoryDraft.optInt("exercisePage"));
            JSONArray rows = a.manualHistoryDraft.optJSONArray("rows");
            assertEquals(2, rows.length());
            assertEquals("builtin-0", rows.optJSONObject(0).optString("exerciseId"));
            assertEquals(
                    9, rows.optJSONObject(0).optJSONArray("logs").optJSONObject(0).optInt("reps"));
            assertEquals("builtin-1", rows.optJSONObject(1).optString("exerciseId"));
            assertEquals(0, rows.optJSONObject(1).optJSONArray("logs").length());
        });
    }
}
