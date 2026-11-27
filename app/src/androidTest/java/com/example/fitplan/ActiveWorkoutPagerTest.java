package com.example.fitplan;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.swipeLeft;
import static androidx.test.espresso.action.ViewActions.swipeRight;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withTagValue;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.example.fitplan.Store.object;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class ActiveWorkoutPagerTest {
    @Test
    public void completedExercisesStayInThePagerAndRecordingPreservesTheSelectedPage() {
        JSONObject session = session();
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            try {
                String[] labels = new String[5];
                scenario.onActivity(a -> {
                    a.store.save("active", session);
                    a.show("dailyPlan");
                    labels[0] = a.s("continue_workout");
                    labels[1] = a.s("complete_set");
                    labels[2] = a.s("workout_exercise_done");
                    labels[3] = a.s("workout_set_progress", 1, 1);
                    labels[4] = a.s("workout_set_progress", 1, 2);
                });
                onView(withText(labels[0])).perform(scrollTo(), click());
                onView(allOf(withText(labels[1]), isDisplayed())).perform(click());
                onView(withText(labels[2])).check(matches(isDisplayed()));
                onView(withText(labels[3])).check(matches(isDisplayed()));

                onView(withTagValue(is((Object) "workout_exercise_pager"))).perform(swipeLeft());
                onView(allOf(withText(labels[1]), isDisplayed())).perform(click());
                onView(withText(labels[4])).check(matches(isDisplayed()));
                scenario.recreate();
                onView(withText(labels[4])).check(matches(isDisplayed()));

                onView(withTagValue(is((Object) "workout_exercise_pager"))).perform(swipeRight());
                onView(withText(labels[3])).check(matches(isDisplayed()));
                onView(withText(labels[2])).check(matches(isDisplayed()));
                scenario.onActivity(a -> {
                    JSONObject saved = a.store.first("active");
                    assertEquals(session.optString("id"), saved.optString("id"));
                    assertEquals(2, WorkoutAdjustments.completed(saved));
                    assertEquals(1, WorkoutAdjustments.pending(saved));
                });
            } finally {
                scenario.onActivity(a -> a.store.delete(session.optString("id")));
            }
        }
    }

    @Test
    public void restActionsSwitchBetweenIdleAndCountdownAndRememberDuration() {
        JSONObject session = session();
        SharedPreferences preferences =
                InstrumentationRegistry.getInstrumentation()
                        .getTargetContext()
                        .getSharedPreferences("preferences", 0);
        String durationPreference = TrainingScreen.REST_SECONDS_PREFERENCE;
        boolean hadDuration = preferences.contains(durationPreference);
        int previousDuration = preferences.getInt(durationPreference, 60);
        preferences.edit().remove(durationPreference).commit();
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            try {
                String[] labels = new String[7];
                scenario.onActivity(a -> {
                    a.store.save("active", session);
                    a.show("workout");
                    labels[0] = a.s("rest_start", 60);
                    labels[1] = a.s("rest_adjust");
                    labels[2] = a.s("stop_rest");
                    labels[3] = a.s("rest_option_seconds", 90);
                    labels[4] = a.s("rest_start", 90);
                    labels[5] = a.s("rest_custom");
                    labels[6] = a.s("rest_start", 1);
                });
                onView(withId(R.id.scroller)).perform(swipeUp());
                scenario.onActivity(a -> {
                    ScrollView scroll = a.findViewById(R.id.scroller);
                    assertTrue(scroll.getScrollY() > 0);
                });
                onView(withText(labels[0]))
                        .perform(scrollTo())
                        .check(matches(isCompletelyDisplayed()));
                onView(withText(labels[1])).check(matches(isCompletelyDisplayed()));
                onView(withText(labels[2])).check(matches(not(isDisplayed())));

                scenario.onActivity(a -> clickButton(a, labels[1]));
                onView(withText(labels[3])).inRoot(isDialog()).perform(click());
                onView(withText(labels[4])).check(matches(isCompletelyDisplayed()));
                scenario.onActivity(
                        a -> assertEquals(0L, a.store.first("active").optLong("restUntil")));

                scenario.recreate();
                onView(withText(labels[4])).perform(scrollTo());
                scenario.onActivity(a -> {
                    clickButton(a, labels[4]);
                    assertTrue(
                            a.store.first("active").optLong("restUntil")
                                    > System.currentTimeMillis() + 85000);
                });
                onView(withText(labels[2]))
                        .perform(scrollTo())
                        .check(matches(isCompletelyDisplayed()));
                onView(withText(labels[1])).check(matches(not(isDisplayed())));
                onView(withText(labels[4])).check(matches(not(isDisplayed())));
                scenario.onActivity(a -> {
                    clickButton(a, labels[2]);
                    assertEquals(0L, a.store.first("active").optLong("restUntil"));
                    clickButton(a, labels[1]);
                });
                onView(withText(labels[5])).inRoot(isDialog()).perform(click());
                onView(isAssignableFrom(EditText.class))
                        .inRoot(isDialog())
                        .perform(replaceText("1"));
                scenario.onActivity(a -> {
                    a.activeDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                    assertEquals(1, preferences.getInt(durationPreference, -1));
                    assertEquals(0L, a.store.first("active").optLong("restUntil"));
                    clickButton(a, labels[6]);
                    assertTrue(
                            a.store.first("active").optLong("restUntil")
                                    > System.currentTimeMillis());
                });
                SystemClock.sleep(2200);
                onView(withText(labels[6]))
                        .perform(scrollTo())
                        .check(matches(isCompletelyDisplayed()));
                onView(withText(labels[1])).check(matches(isCompletelyDisplayed()));
                onView(withText(labels[2])).check(matches(not(isDisplayed())));
            } finally {
                scenario.onActivity(a -> a.store.delete(session.optString("id")));
            }
        } finally {
            SharedPreferences.Editor editor = preferences.edit();
            if (hadDuration) editor.putInt(durationPreference, previousDuration);
            else editor.remove(durationPreference);
            editor.commit();
        }
    }

    private void clickButton(MainActivity activity, String label) {
        ArrayList<View> matches = new ArrayList<>();
        activity.content.findViewsWithText(matches, label, View.FIND_VIEWS_WITH_TEXT);
        matches.removeIf(view ->
                !(view instanceof TextView) || !label.contentEquals(((TextView) view).getText()));
        assertEquals(1, matches.size());
        assertTrue(matches.get(0).performClick());
    }

    private JSONObject session() {
        return object(
                "id",
                Store.id(),
                "name",
                "Pager test",
                "started",
                System.currentTimeMillis(),
                "rows",
                new JSONArray().put(row("builtin-0", 1)).put(row("builtin-1", 2)));
    }

    private JSONObject row(String exercise, int sets) {
        JSONArray logs = new JSONArray();
        for (int i = 0; i < sets; i++) logs.put(object("weight", 20, "reps", 8, "done", false));
        return object(
                "rowId",
                Store.id(),
                "exerciseId",
                exercise,
                "weight",
                20,
                "sets",
                sets,
                "reps",
                8,
                "logs",
                logs);
    }
}
