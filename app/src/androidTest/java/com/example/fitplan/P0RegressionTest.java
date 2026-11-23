package com.example.fitplan;

import static com.example.fitplan.Store.id;
import static com.example.fitplan.Store.object;
import static com.example.fitplan.Store.put;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class P0RegressionTest {
    private Context context;
    private Store database;
    private String originalLanguage;
    private final List<String> ids = new ArrayList<>();

    @Before
    public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        database = new Store(context);
        originalLanguage =
                context.getSharedPreferences("preferences", 0).getString("language", "zh");
        context.getSharedPreferences("preferences", 0).edit().putString("language", "zh").commit();
    }

    @After
    public void cleanup() {
        for (String id : ids) database.delete(id);
        database.close();
        context.getSharedPreferences("preferences", 0)
                .edit()
                .putString("language", originalLanguage)
                .commit();
    }

    private void save(String kind, JSONObject value) {
        database.save(kind, value);
        ids.add(value.optString("id"));
    }

    private JSONObject check(boolean reduced) {
        return object(
                "day",
                "2026-11-17",
                "minutes",
                30,
                "volume",
                1,
                "sleep",
                reduced ? 2 : 0,
                "food",
                0,
                "soreness",
                0,
                "parts",
                new JSONArray().put("chest").put("back"));
    }

    private int total(JSONObject plan) {
        int total = 0;
        JSONArray rows = plan.optJSONArray("rows");
        for (int i = 0; i < rows.length(); i++) total += rows.optJSONObject(i).optInt("sets");
        return total;
    }

    private View find(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText()))
            return root;
        if (root instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
                View v = find(((ViewGroup) root).getChildAt(i), text);
                if (v != null) return v;
            }
        return null;
    }

    private void click(MainActivity a, String text) {
        View root =
                a.activeDialog != null && a.activeDialog.isShowing()
                        ? a.activeDialog.getWindow().getDecorView()
                        : a.getWindow().getDecorView();
        View target = find(root, text);
        assertNotNull("Missing control: " + text, target);
        assertTrue(target.performClick());
    }

    private void edits(View root, List<EditText> result) {
        if (root instanceof EditText) result.add((EditText) root);
        if (root instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++)
                edits(((ViewGroup) root).getChildAt(i), result);
    }

    @Test
    public void reducedGenerationKeepsExercisesAndExplainsTimeLimit() {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                HomeScreen home = new HomeScreen(a);
                JSONObject normal = home.generate(check(false)),
                        reduced = home.generate(check(true));
                assertEquals(6, total(normal));
                assertEquals(4, total(reduced));
                JSONArray first = normal.optJSONArray("rows"),
                        second = reduced.optJSONArray("rows");
                assertEquals(first.length(), second.length());
                for (int i = 0; i < first.length(); i++)
                    assertEquals(
                            first.optJSONObject(i).optString("exerciseId"),
                            second.optJSONObject(i).optString("exerciseId"));
                assertTrue(reduced.optBoolean("timeLimited"));
                assertTrue(a.reason(second.optJSONObject(0)).contains(a.s("reason_time")));
                JSONObject narrow = check(false);
                put(narrow, "minutes", 10);
                JSONObject tiny = home.generate(narrow);
                save("plan", tiny);
                a.openPlan(tiny);
                assertNotNull(find(a.content, a.s("plan_missing_parts", a.partName("back"))));
            });
        }
    }

    @Test
    public void historyLookupSkipsHeavySingleRepAndPartialWorkouts() {
        String exercise = "p0-reference-" + id();
        long now = System.currentTimeMillis();
        JSONObject complete =
                object(
                        "name",
                        "P0 fixture",
                        "finished",
                        now - 3000,
                        "rows",
                        new JSONArray()
                                .put(
                                        object(
                                                "exerciseId",
                                                exercise,
                                                "reps",
                                                10,
                                                "sets",
                                                3,
                                                "logs",
                                                new JSONArray()
                                                        .put(
                                                                object(
                                                                        "weight", 40, "reps", 10,
                                                                        "done", true))
                                                        .put(
                                                                object(
                                                                        "weight", 45, "reps", 10,
                                                                        "done", true))
                                                        .put(
                                                                object(
                                                                        "weight", 50, "reps", 10,
                                                                        "done", true)))));
        JSONObject single =
                object(
                        "finished",
                        now - 2000,
                        "rows",
                        new JSONArray()
                                .put(
                                        object(
                                                "exerciseId",
                                                exercise,
                                                "reps",
                                                10,
                                                "sets",
                                                1,
                                                "logs",
                                                new JSONArray()
                                                        .put(
                                                                object(
                                                                        "weight", 80, "reps", 1,
                                                                        "done", true)))));
        JSONObject partial =
                object(
                        "finished",
                        now - 1000,
                        "rows",
                        new JSONArray()
                                .put(
                                        object(
                                                "exerciseId",
                                                exercise,
                                                "reps",
                                                10,
                                                "sets",
                                                3,
                                                "logs",
                                                new JSONArray()
                                                        .put(
                                                                object(
                                                                        "weight", 90, "reps", 10,
                                                                        "done", true)))));
        save("history", complete);
        save("history", single);
        save("history", partial);
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                HomeScreen home = new HomeScreen(a);
                JSONObject ref = home.recentReference(exercise, 10, 3);
                assertNotNull(ref);
                assertEquals(40, ref.optDouble("weight"), 0);
                assertEquals(complete.optString("id"), ref.optString("sessionId"));
                assertNull(home.recentReference(exercise, 12, 3));
                assertNull(home.recentReference(exercise, 10, 4));
            });
        }
    }

    @Test
    public void manualEditUpdatesExplanationAndPreservesOriginalAcrossRecreation() {
        JSONObject plan =
                object(
                        "name",
                        "P0 edit fixture",
                        "rows",
                        new JSONArray()
                                .put(
                                        object(
                                                "exerciseId",
                                                "builtin-0",
                                                "weight",
                                                40,
                                                "sets",
                                                3,
                                                "reps",
                                                10,
                                                "reasonCode",
                                                "same",
                                                "referencePolicyVersion",
                                                2,
                                                "reduced",
                                                true,
                                                "timeLimited",
                                                true)));
        save("plan", plan);
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                a.openPlan(plan);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> {
                View original = find(a.content, "40");
                android.widget.SeekBar slider =
                        (android.widget.SeekBar) ((ViewGroup) original.getParent()).getChildAt(1);
                assertEquals(200, slider.getMax()); // 0–100 kg in half-kilogram increments.
                long now = android.os.SystemClock.uptimeMillis();
                for (int action :
                        new int[] {
                            android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP
                        }) {
                    android.view.MotionEvent event =
                            android.view.MotionEvent.obtain(
                                    now,
                                    now,
                                    action,
                                    slider.getPaddingLeft(),
                                    slider.getHeight() / 2f,
                                    0);
                    slider.dispatchTouchEvent(event);
                    event.recycle();
                }
                assertTrue(original.isAttachedToWindow());
                assertEquals("0", ((TextView) original).getText().toString());
            });
            for (String[] edit : new String[][] {{"0", "35"}, {"3", "2"}, {"10", "8"}}) {
                scenario.onActivity(a -> click(a, edit[0]));
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(a -> {
                    List<EditText> fields = new ArrayList<>();
                    edits(a.activeDialog.getWindow().getDecorView(), fields);
                    assertEquals(1, fields.size());
                    fields.get(0).setText(edit[1]);
                    click(a, a.s("save"));
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            }
            scenario.recreate();
            scenario.onActivity(a -> {
                JSONObject stored = a.find("plan", plan.optString("id")),
                        row = stored.optJSONArray("rows").optJSONObject(0);
                assertEquals(35, row.optDouble("weight"), 0);
                assertTrue(row.optBoolean("userConfirmed"));
                assertFalse(row.has("reasonCode"));
                assertFalse(row.has("reduced"));
                assertFalse(row.has("timeLimited"));
                assertEquals(40, row.optJSONObject("originalPrescription").optDouble("weight"), 0);
                String explanation = a.reason(row);
                assertTrue(explanation.contains(a.s("reason_user", "35", 2, 8)));
                assertTrue(explanation.contains(a.s("reason_original", "40", 3, 10)));
                assertFalse(explanation.contains(a.s("reason_same")));
                PlanEdits.confirm(row, 30, 1, 6, System.currentTimeMillis());
                database.save("plan", stored);
                assertEquals(40, row.optJSONObject("originalPrescription").optDouble("weight"), 0);
                a.setLanguage("en");
                assertTrue(a.reason(row).startsWith("Confirmed by you:"));
                try (Store reopened = new Store(context)) {
                    JSONArray plans = reopened.list("plan");
                    JSONObject persisted = null;
                    for (int i = 0; i < plans.length(); i++)
                        if (stored.optString("id").equals(plans.optJSONObject(i).optString("id")))
                            persisted = plans.optJSONObject(i);
                    assertNotNull(persisted);
                    assertEquals(
                            30,
                            persisted.optJSONArray("rows").optJSONObject(0).optDouble("weight"),
                            0);
                    assertEquals(
                            40,
                            persisted
                                    .optJSONArray("rows")
                                    .optJSONObject(0)
                                    .optJSONObject("originalPrescription")
                                    .optDouble("weight"),
                            0);
                }
            });
        }
    }

    @Test
    public void legacyAndUnknownWeightsNeedExplicitConfirmation() {
        org.junit.Assume.assumeTrue(database.first("active") == null);
        JSONObject legacy =
                object(
                        "name",
                        "P0 legacy fixture",
                        "checkin",
                        check(false),
                        "rows",
                        new JSONArray()
                                .put(
                                        object(
                                                "exerciseId",
                                                "builtin-0",
                                                "weight",
                                                50,
                                                "sets",
                                                1,
                                                "reps",
                                                10,
                                                "reasonCode",
                                                "up")));
        save("plan", legacy);
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                JSONObject row = legacy.optJSONArray("rows").optJSONObject(0);
                assertTrue(PlanEdits.needsConfirmation(legacy, row));
                new TrainingScreen(a).start(legacy);
                assertNull(database.first("active"));
                assertEquals("planDetail", a.page);
                PlanEdits.confirm(row, 0, 1, 10, System.currentTimeMillis());
                database.save("plan", legacy);
                assertEquals("before_edit", row.optString("originalPrescriptionSource"));
                assertTrue(a.reason(row).contains(a.s("reason_before_edit", "50", 1, 10)));
                assertFalse(a.reason(row).contains(a.s("reason_original", "50", 1, 10)));
                assertFalse(PlanEdits.needsConfirmation(legacy, row));
                new TrainingScreen(a).start(legacy);
                JSONObject active = database.first("active");
                assertNotNull(active);
                ids.add(active.optString("id"));
                assertEquals(
                        0, active.optJSONArray("rows").optJSONObject(0).optDouble("weight"), 0);
            });
        }
    }

    @Test
    public void newMissingReferenceCannotStartAndDoesNotMutateHistory() {
        org.junit.Assume.assumeTrue(database.first("active") == null);
        JSONObject row =
                object(
                        "exerciseId",
                        "builtin-0",
                        "weight",
                        0,
                        "sets",
                        1,
                        "reps",
                        10,
                        "referencePolicyVersion",
                        2,
                        "reasonCode",
                        "first",
                        "needsWeightConfirmation",
                        true);
        JSONObject plan = object("name", "P0 unknown fixture", "rows", new JSONArray().put(row));
        save("plan", plan);
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                new TrainingScreen(a).start(plan);
                assertNull(database.first("active"));
                PlanEdits.confirm(row, 20, 1, 10, System.currentTimeMillis());
                database.save("plan", plan);
                new TrainingScreen(a).start(plan);
                JSONObject active = database.first("active");
                assertNotNull(active);
                ids.add(active.optString("id"));
                PlanEdits.confirm(row, 30, 2, 12, System.currentTimeMillis());
                database.save("plan", plan);
                assertEquals(
                        20,
                        database.first("active")
                                .optJSONArray("rows")
                                .optJSONObject(0)
                                .optDouble("weight"),
                        0);
                assertEquals(
                        1,
                        database.first("active")
                                .optJSONArray("rows")
                                .optJSONObject(0)
                                .optInt("sets"));
            });
        }
    }

    @Test
    public void invalidEditLeavesOriginalAndCurrentValuesUntouched() {
        JSONObject row = object("weight", 40, "sets", 3, "reps", 10, "reasonCode", "same");
        String before = row.toString();
        try {
            PlanEdits.confirm(row, -1, 3, 10, System.currentTimeMillis());
            fail("Invalid weight accepted");
        } catch (IllegalArgumentException expected) {
            assertEquals(before, row.toString());
        }
    }
}
