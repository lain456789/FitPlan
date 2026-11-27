package com.example.fitplan;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static androidx.test.espresso.matcher.ViewMatchers.isClickable;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.example.fitplan.Store.object;

import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Point;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DailyPlanDetailTest {
    @Test
    public void exerciseThumbnailKeepsItsSizeWhenWeightIsConfirmedAndChanged() {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            JSONObject plan = dailyPlan();
            plan.optJSONArray("rows")
                    .put(
                            object(
                                    "exerciseId",
                                    "builtin-6",
                                    "part",
                                    "back",
                                    "weight",
                                    20,
                                    "sets",
                                    3,
                                    "reps",
                                    10,
                                    "referencePolicyVersion",
                                    2));
            String[] labels = new String[3];
            scenario.onActivity(a -> {
                a.store.save("plan", plan);
                a.openPlan(plan);
                labels[0] = a.s("exercise_demo", a.s("ex0"));
                labels[1] = a.s("weight_label");
                labels[2] = a.s("exercise_demo", a.s("ex6"));
            });
            try {
                Point before = thumbnailSize(labels[0]);
                Point otherBefore = thumbnailSize(labels[2]);
                assertTrue(before.x > 0 && before.y > 0);
                String[] increments = {"0", "0.5", "1"};
                for (int i = 0; i < increments.length; i++) {
                    onView(
                                    allOf(
                                            withText("+"),
                                            hasSibling(
                                                    withContentDescription(
                                                            labels[1] + " " + increments[i]))))
                            .perform(scrollTo(), click());
                    assertEquals(
                            "Thumbnail after weight increment " + (i + 1),
                            before,
                            thumbnailSize(labels[0]));
                    assertEquals(
                            "Other thumbnail after weight increment " + (i + 1),
                            otherBefore,
                            thumbnailSize(labels[2]));
                }
                scenario.onActivity(a -> {
                    JSONObject row =
                            a.find("plan", plan.optString("id"))
                                    .optJSONArray("rows")
                                    .optJSONObject(0);
                    assertEquals(1.5, row.optDouble("weight"), 0);
                    assertTrue(row.optBoolean("userConfirmed"));
                });
            } finally {
                scenario.onActivity(a -> a.store.delete(plan.optString("id")));
            }
        }
    }

    @Test
    public void exercisePickerRejectsExistingExerciseAndStillAcceptsAnother() {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            JSONObject plan = dailyPlan();
            String[] labels = new String[3];
            scenario.onActivity(a -> {
                a.store.save("plan", plan);
                a.openPlan(plan);
                labels[0] = a.s("add_exercise");
                labels[1] = a.s("ex0");
                labels[2] = a.s("ex1");
            });
            try {
                onView(withText(labels[0])).perform(click());
                selectExercise(labels[1]);
                scenario.onActivity(a -> {
                    assertTrue(a.activeDialog.isShowing());
                    assertEquals(
                            1, a.find("plan", plan.optString("id")).optJSONArray("rows").length());
                });
                selectExercise(labels[2]);
                scenario.onActivity(a -> {
                    JSONArray rows = a.find("plan", plan.optString("id")).optJSONArray("rows");
                    assertEquals(2, rows.length());
                    assertEquals("builtin-1", rows.optJSONObject(1).optString("exerciseId"));
                });
            } finally {
                scenario.onActivity(a -> a.store.delete(plan.optString("id")));
            }
        }
    }

    private void selectExercise(String name) {
        onView(allOf(isClickable(), hasDescendant(withText(name))))
                .inRoot(isDialog())
                .perform(scrollTo(), click());
    }

    private Point thumbnailSize(String description) {
        Point size = new Point();
        onView(withContentDescription(description))
                .check((view, missing) -> {
                    if (missing != null) throw missing;
                    size.set(view.getWidth(), view.getHeight());
                });
        return size;
    }

    private JSONObject dailyPlan() {
        JSONObject row =
                object(
                        "exerciseId",
                        "builtin-0",
                        "part",
                        "chest",
                        "weight",
                        0,
                        "sets",
                        3,
                        "reps",
                        10,
                        "reasonCode",
                        "first",
                        "needsWeightConfirmation",
                        true,
                        "referencePolicyVersion",
                        2);
        return object(
                "id",
                Store.id(),
                "name",
                "Daily detail test",
                "minutes",
                30,
                "checkin",
                object("parts", new JSONArray().put("chest"), "minutes", 30),
                "rows",
                new JSONArray().put(row));
    }
}
