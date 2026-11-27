package com.example.fitplan;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.swipeLeft;
import static androidx.test.espresso.action.ViewActions.swipeRight;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withTagValue;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.example.fitplan.Store.copy;
import static com.example.fitplan.Store.id;
import static com.example.fitplan.Store.object;
import static com.example.fitplan.Store.put;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

@RunWith(AndroidJUnit4.class)
public class WorkoutAdjustmentTest {
    private Context context;
    private Store database;
    private String originalLanguage;
    private final List<String> ids = new ArrayList<>();

    @Before
    public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        database = new Store(context);
        database.seed();
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

    private JSONObject find(String kind, String id) {
        JSONArray docs = database.list(kind);
        for (int i = 0; i < docs.length(); i++)
            if (id.equals(docs.optJSONObject(i).optString("id"))) return docs.optJSONObject(i);
        return null;
    }

    private JSONObject fixture() {
        JSONArray rows =
                new JSONArray()
                        .put(
                                object(
                                        "exerciseId",
                                        "builtin-0",
                                        "name",
                                        "杠铃卧推",
                                        "part",
                                        "chest",
                                        "weight",
                                        40,
                                        "sets",
                                        3,
                                        "reps",
                                        10,
                                        "logs",
                                        new JSONArray()
                                                .put(object("weight", 40, "reps", 8, "done", true))
                                                .put(
                                                        object(
                                                                "weight", 40, "reps", 10, "done",
                                                                false))
                                                .put(
                                                        object(
                                                                "weight", 40, "reps", 10, "done",
                                                                false))))
                        .put(
                                object(
                                        "exerciseId",
                                        "builtin-7",
                                        "name",
                                        "坐姿划船",
                                        "part",
                                        "back",
                                        "weight",
                                        30,
                                        "sets",
                                        2,
                                        "reps",
                                        10,
                                        "logs",
                                        new JSONArray()
                                                .put(
                                                        object(
                                                                "weight", 30, "reps", 10, "done",
                                                                false))
                                                .put(
                                                        object(
                                                                "weight", 30, "reps", 10, "done",
                                                                false))));
        JSONObject plan =
                object(
                        "name",
                        "[Test] Adjustment fixture",
                        "rows",
                        WorkoutAdjustments.arrayCopy(rows));
        for (int i = 0; i < plan.optJSONArray("rows").length(); i++)
            plan.optJSONArray("rows").optJSONObject(i).remove("logs");
        save("plan", plan);
        JSONObject session =
                object(
                        "planId",
                        plan.optString("id"),
                        "name",
                        plan.optString("name"),
                        "started",
                        System.currentTimeMillis() - 60000,
                        "restUntil",
                        0,
                        "rows",
                        rows);
        save("active", session);
        return session;
    }

    private JSONObject target() {
        return object("id", "builtin-16");
    }

    private JSONObject firstLog(JSONObject session) {
        return session.optJSONArray("rows").optJSONObject(0).optJSONArray("logs").optJSONObject(0);
    }

    private View findView(View root, String text) {
        if (root instanceof TextView
                && text.contentEquals(((TextView) root).getText())
                && root.isClickable()) return root;
        if (root instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
                View found = findView(((ViewGroup) root).getChildAt(i), text);
                if (found != null) return found;
            }
        return null;
    }

    private void click(MainActivity a, String text) {
        View root =
                a.activeDialog != null && a.activeDialog.isShowing()
                        ? a.activeDialog.getWindow().getDecorView()
                        : a.getWindow().getDecorView();
        View button = findView(root, text);
        assertNotNull("Missing control: " + text, button);
        assertTrue(button.performClick());
    }

    private <T extends View> List<T> collect(View root, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (type.isInstance(root)) found.add(type.cast(root));
        if (root instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++)
                found.addAll(collect(((ViewGroup) root).getChildAt(i), type));
        return found;
    }

    private void idle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void screenshot(String name) {
        idle();
        android.os.SystemClock.sleep(600);
        Bitmap bitmap =
                InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        try (FileOutputStream out =
                new FileOutputStream(new File(context.getExternalFilesDir(null), name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        } catch (IOException e) {
            throw new AssertionError(e);
        } finally {
            bitmap.recycle();
        }
    }

    @Test
    public void reducingSetsProtectsRecordedSetsAndAllowsAnEmptyExerciseToRestart() {
        JSONObject session = fixture();
        String recorded = firstLog(session).toString();
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> a.show("workout"));
            idle();
            scenario.onActivity(a -> {
                View timer = a.timerLabel;
                View remove = findView(a.content, a.s("remove_set"));
                android.widget.ScrollView scroll = a.findViewById(R.id.scroller);
                scroll.scrollTo(0, a.dp(150));
                int offset = scroll.getScrollY();
                assertTrue(offset > 0);
                click(a, a.s("add_set"));
                assertTrue(remove == findView(a.content, a.s("remove_set")));
                assertTrue(timer == a.timerLabel);
                assertEquals(offset, scroll.getScrollY());
                click(a, a.s("remove_set"));
                assertTrue(remove == findView(a.content, a.s("remove_set")));
                assertEquals(offset, scroll.getScrollY());
                click(a, a.s("remove_set"));
                click(a, a.s("remove_set"));
                assertFalse(findView(a.content, a.s("remove_set")).isEnabled());
                assertEquals(
                        recorded, firstLog(find("active", session.optString("id"))).toString());
                click(a, a.s("next_exercise"));
                click(a, a.s("remove_set"));
                click(a, a.s("remove_set"));
                assertFalse(findView(a.content, a.s("remove_set")).isEnabled());
                assertTrue(
                        collect(a.content, TextView.class).stream()
                                .anyMatch(v -> a.s("no_pending_sets").contentEquals(v.getText())));
                click(a, a.s("add_set"));
                assertTrue(findView(a.content, a.s("remove_set")).isEnabled());
                assertNotNull(findView(a.content, a.s("record_set", 1)));
            });
        }
    }

    @Test
    public void creatingAndCancellingPreviewNeverChangesStoredRecords() {
        JSONObject session = fixture();
        String before = session.toString();
        WorkoutAdjustments.Preview preview =
                WorkoutAdjustments.shorten(session, 3, System.currentTimeMillis());
        assertTrue(preview.changed > 0);
        assertEquals(before, session.toString());
        assertEquals(before, find("active", session.optString("id")).toString());
        assertFalse(session.has("originalRows"));
    }

    @Test
    public void shorteningPreservesInterleavedCompletedSetAtItsOriginalIndex() {
        JSONObject session = fixture();
        JSONArray logs = session.optJSONArray("rows").optJSONObject(0).optJSONArray("logs");
        put(logs.optJSONObject(0), "done", false);
        put(logs.optJSONObject(1), "done", true);
        put(logs.optJSONObject(1), "reps", 7);
        database.save("active", session);
        String done = logs.optJSONObject(1).toString();
        WorkoutAdjustments.Preview preview =
                WorkoutAdjustments.shorten(session, 3, System.currentTimeMillis());
        database.applyAdjustment(preview);
        JSONObject after = find("active", session.optString("id"));
        JSONArray updated = after.optJSONArray("rows").optJSONObject(0).optJSONArray("logs");
        assertEquals(done, updated.optJSONObject(1).toString());
        assertTrue(updated.optJSONObject(2).optBoolean("skipped"));
        assertEquals(1, WorkoutAdjustments.pending(after));
        assertEquals(1, WorkoutAdjustments.completed(after));
        assertTrue(WorkoutAdjustments.completedUnchanged(session, after));
        assertEquals(1, after.optInt("adjustmentRevision"));
        assertEquals("time", after.optJSONArray("adjustments").optJSONObject(0).optString("type"));
        try (Store reopened = new Store(context)) {
            JSONObject persisted = null;
            JSONArray active = reopened.list("active");
            for (int i = 0; i < active.length(); i++)
                if (session.optString("id").equals(active.optJSONObject(i).optString("id")))
                    persisted = active.optJSONObject(i);
            assertNotNull(persisted);
            assertEquals(after.toString(), persisted.toString());
        }
    }

    @Test
    public void replacementPreservesOriginalAndStatisticsUseOnlyActualCompletedSets() {
        JSONObject session = fixture();
        String completed = firstLog(session).toString(),
                template = find("plan", session.optString("planId")).toString();
        WorkoutAdjustments.Preview preview =
                WorkoutAdjustments.replace(
                        session, 0, target(), "barbell", 12.5, 12, System.currentTimeMillis());
        database.applyAdjustment(preview);
        JSONObject after = find("active", session.optString("id"));
        JSONArray rows = after.optJSONArray("rows");
        assertEquals(completed, firstLog(after).toString());
        assertEquals("builtin-0", rows.optJSONObject(0).optString("exerciseId"));
        assertTrue(
                rows.optJSONObject(0).optJSONArray("logs").optJSONObject(1).optBoolean("skipped"));
        JSONObject replacement = rows.optJSONObject(1);
        assertEquals("builtin-16", replacement.optString("exerciseId"));
        assertEquals(12.5, replacement.optDouble("weight"), 0);
        assertEquals(2, replacement.optJSONArray("logs").length());
        assertEquals(4, WorkoutAdjustments.pending(after));
        for (int i = 0; i < replacement.optJSONArray("logs").length(); i++)
            put(replacement.optJSONArray("logs").optJSONObject(i), "done", true);
        put(after, "finished", System.currentTimeMillis());
        database.finish(after);
        assertEquals(template, find("plan", session.optString("planId")).toString());
        ReportEngine.Summary report =
                ReportEngine.aggregate(
                        HistoryReader.read(new JSONArray().put(after)),
                        0,
                        Long.MAX_VALUE,
                        TimeZone.getDefault());
        assertEquals(3, report.sets);
        assertEquals(620, report.volume, 0);
        assertNotNull(find("history", session.optString("id")));
    }

    @Test
    public void zeroRemainingTimeKeepsCompletedRecordsAndStopsRest() {
        JSONObject session = fixture();
        put(session, "restUntil", System.currentTimeMillis() + 90000);
        database.save("active", session);
        String completed = firstLog(session).toString();
        WorkoutAdjustments.Preview preview =
                WorkoutAdjustments.shorten(session, 0, System.currentTimeMillis());
        database.applyAdjustment(preview);
        JSONObject after = find("active", session.optString("id"));
        assertEquals(0, WorkoutAdjustments.pending(after));
        assertEquals(completed, firstLog(after).toString());
        assertEquals(0, after.optLong("restUntil"));
        assertEquals(0, preview.estimatedSeconds);
        put(after, "finished", System.currentTimeMillis());
        database.finish(after);
        assertEquals(completed, firstLog(find("history", session.optString("id"))).toString());
    }

    @Test
    public void repeatedReplacementsAndShorteningKeepCompletedIdentityAndAudit() {
        JSONObject session = fixture();
        String completed = firstLog(session).toString();
        database.applyAdjustment(
                WorkoutAdjustments.replace(
                        session, 0, target(), "barbell", 12, 10, System.currentTimeMillis()));
        JSONObject first = find("active", session.optString("id"));
        database.applyAdjustment(WorkoutAdjustments.shorten(first, 3, System.currentTimeMillis()));
        JSONObject second = find("active", session.optString("id"));
        database.applyAdjustment(
                WorkoutAdjustments.replace(
                        second,
                        1,
                        object("id", "builtin-17"),
                        "dumbbell",
                        0,
                        8,
                        System.currentTimeMillis()));
        JSONObject last = find("active", session.optString("id"));
        assertEquals(completed, firstLog(last).toString());
        assertEquals(1, WorkoutAdjustments.pending(last));
        assertEquals(3, last.optJSONArray("adjustments").length());
        assertEquals(3, last.optInt("adjustmentRevision"));
        assertEquals(2, last.optJSONArray("unavailableEquipment").length());
        assertTrue(WorkoutAdjustments.completedUnchanged(session, last));
    }

    @Test
    public void changedWorkoutRejectsOldPreviewWithoutLosingNewlyCompletedSet() {
        JSONObject session = fixture();
        WorkoutAdjustments.Preview preview =
                WorkoutAdjustments.shorten(session, 3, System.currentTimeMillis());
        JSONObject edited = copy(session);
        put(
                edited.optJSONArray("rows").optJSONObject(0).optJSONArray("logs").optJSONObject(1),
                "done",
                true);
        database.save("active", edited);
        try {
            database.applyAdjustment(preview);
            fail("Stale proposal applied");
        } catch (Store.StaleWorkout expected) {
            assertEquals(edited.toString(), find("active", session.optString("id")).toString());
        }
    }

    @Test
    public void applyingSamePreviewTwiceDoesNotDuplicateChanges() {
        JSONObject session = fixture();
        WorkoutAdjustments.Preview preview =
                WorkoutAdjustments.shorten(session, 3, System.currentTimeMillis());
        database.applyAdjustment(preview);
        try {
            database.applyAdjustment(preview);
            fail("Duplicate proposal applied");
        } catch (Store.StaleWorkout expected) {
            assertEquals(
                    1,
                    find("active", session.optString("id")).optJSONArray("adjustments").length());
        }
    }

    @Test
    public void completedRecordTamperingIsRejected() {
        JSONObject session = fixture();
        WorkoutAdjustments.Preview preview =
                WorkoutAdjustments.shorten(session, 3, System.currentTimeMillis());
        put(firstLog(preview.after), "weight", 999);
        try {
            database.applyAdjustment(preview);
            fail("Completed set changed");
        } catch (WorkoutAdjustments.Invalid expected) {
            assertEquals(session.toString(), find("active", session.optString("id")).toString());
        }
    }

    @Test
    public void candidatesRespectAllUnavailableEquipmentAndRejectUnsupportedChanges() {
        JSONObject session = fixture();
        List<JSONObject> alternatives =
                WorkoutAdjustments.alternatives(session, 0, "bench", database.list("exercise"));
        assertEquals(2, alternatives.size());
        assertEquals("builtin-17", alternatives.get(0).optString("id"));
        put(session, "unavailableEquipment", new JSONArray().put("dumbbell"));
        alternatives =
                WorkoutAdjustments.alternatives(session, 0, "barbell", database.list("exercise"));
        assertEquals(2, alternatives.size());
        try {
            WorkoutAdjustments.replace(
                    session,
                    0,
                    object("id", "builtin-3"),
                    "barbell",
                    10,
                    10,
                    System.currentTimeMillis());
            fail();
        } catch (WorkoutAdjustments.Invalid expected) {
        }
        try {
            WorkoutAdjustments.replace(
                    session, 0, target(), "barbell", -1, 10, System.currentTimeMillis());
            fail();
        } catch (WorkoutAdjustments.Invalid expected) {
        }
        try {
            WorkoutAdjustments.shorten(session, -1, System.currentTimeMillis());
            fail();
        } catch (WorkoutAdjustments.Invalid expected) {
        }
        JSONObject incline = copy(session);
        put(incline.optJSONArray("rows").optJSONObject(0), "exerciseId", "builtin-1");
        assertEquals(
                2,
                WorkoutAdjustments.alternatives(incline, 0, "bench", database.list("exercise"))
                        .size());
        JSONObject curls = copy(session);
        put(curls.optJSONArray("rows").optJSONObject(0), "exerciseId", "builtin-12");
        put(curls.optJSONArray("rows").optJSONObject(0), "part", "arms");
        alternatives =
                WorkoutAdjustments.alternatives(curls, 0, "dumbbell", database.list("exercise"));
        assertEquals(1, alternatives.size());
        assertEquals("builtin-20", alternatives.get(0).optString("id"));
    }

    @Test
    public void failedAdjustmentWriteRollsBackWithoutChangingAnyLog() {
        JSONObject session = fixture();
        WorkoutAdjustments.Preview preview =
                WorkoutAdjustments.shorten(session, 3, System.currentTimeMillis());
        String trigger = "adjust_failure_" + id().replace("-", "");
        database.getWritableDatabase()
                .execSQL(
                        "CREATE TEMP TRIGGER "
                                + trigger
                                + " BEFORE INSERT ON documents WHEN NEW.kind='active' AND NEW.id='"
                                + session.optString("id")
                                + "' BEGIN SELECT RAISE(ABORT,'test write failure'); END");
        try {
            try {
                database.applyAdjustment(preview);
                fail("Write failure ignored");
            } catch (SQLiteException expected) {
                assertEquals(
                        session.toString(), find("active", session.optString("id")).toString());
            }
        } finally {
            database.getWritableDatabase().execSQL("DROP TRIGGER " + trigger);
        }
    }

    @Test
    public void timePreviewCanBeCancelledThenAppliedThroughTheUi() {
        JSONObject session = fixture();
        String before = session.toString();
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                a.show("workout");
                click(a, a.s("shorten_remaining"));
            });
            idle();
            scenario.onActivity(a -> {
                collect(a.activeDialog.getWindow().getDecorView(), EditText.class)
                        .get(0)
                        .setText("3");
                click(a, a.s("preview_adjustment"));
            });
            idle();
            screenshot("time-adjustment-preview");
            scenario.onActivity(a -> click(a, a.s("cancel")));
            idle();
            assertEquals(before, find("active", session.optString("id")).toString());
            scenario.onActivity(a -> click(a, a.s("shorten_remaining")));
            idle();
            scenario.onActivity(a -> {
                collect(a.activeDialog.getWindow().getDecorView(), EditText.class)
                        .get(0)
                        .setText("3");
                click(a, a.s("preview_adjustment"));
            });
            idle();
            scenario.onActivity(a -> click(a, a.s("apply_adjustment")));
            idle();
            assertEquals(1, WorkoutAdjustments.pending(find("active", session.optString("id"))));
            scenario.recreate();
            scenario.onActivity(a -> {
                assertEquals("workout", a.page);
                a.setLanguage("en");
            });
            idle();
            screenshot("adjusted-workout-en");
        }
    }

    @Test
    public void editingRemainingSetsUpdatesOnlyPendingLogsAndKeepsTheirOwnTargets() {
        JSONObject session = fixture();
        String completed = firstLog(session).toString();
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> a.show("workout"));
            idle();
            scenario.onActivity(a -> {
                click(a, a.s("adjust_exercise"));
                click(a, a.s("edit_pending_sets"));
            });
            idle();
            scenario.onActivity(a -> {
                List<EditText> fields =
                        collect(a.activeDialog.getWindow().getDecorView(), EditText.class);
                fields.get(0).setText("32.5");
                fields.get(1).setText("6");
                fields.get(2).setText("1");
                click(a, a.s("preview_adjustment"));
            });
            idle();
            assertEquals(session.toString(), find("active", session.optString("id")).toString());
            scenario.onActivity(a -> click(a, a.s("apply_adjustment")));
            idle();
            JSONObject reduced = find("active", session.optString("id"));
            JSONObject row = reduced.optJSONArray("rows").optJSONObject(0);
            assertEquals(1, WorkoutAdjustments.pendingRow(row));
            assertEquals(2, row.optInt("sets"));
            JSONObject increased =
                    WorkoutAdjustments.editPending(
                                    reduced, 0, 32.5, 6, 3, System.currentTimeMillis())
                            .after;
            assertEquals(
                    3,
                    WorkoutAdjustments.pendingRow(increased.optJSONArray("rows").optJSONObject(0)));
            assertEquals(completed, firstLog(increased).toString());
            scenario.onActivity(a -> click(a, a.s("complete_set")));
            idle();
            JSONObject recorded = find("active", session.optString("id"));
            assertEquals(completed, firstLog(recorded).toString());
            row = recorded.optJSONArray("rows").optJSONObject(0);
            JSONObject newResult = row.optJSONArray("logs").optJSONObject(1);
            assertTrue(newResult.optBoolean("done"));
            assertEquals(32.5, newResult.optDouble("weight"), 0);
            assertEquals(6, newResult.optInt("reps"));
            assertEquals(
                    1,
                    ProgressReader.samples(new JSONArray().put(recorded), "builtin-0")
                            .get(0)
                            .met());
        }
    }

    @Test
    public void skippingRemainingSetsAcceptsNoReasonAndOnlyAffectsTheChosenExercise() {
        JSONObject session = fixture();
        String id = session.optString("id");
        String completed = firstLog(session).toString();
        String otherLogs =
                session.optJSONArray("rows").optJSONObject(1).optJSONArray("logs").toString();
        WorkoutAdjustments.Preview preview =
                WorkoutAdjustments.skipPending(session, 0, "unknown", System.currentTimeMillis());
        assertEquals(session.toString(), find("active", id).toString());
        database.applyAdjustment(preview);
        JSONObject saved = find("active", id);
        JSONObject exercise = saved.optJSONArray("rows").optJSONObject(0);
        JSONObject skipped = exercise.optJSONArray("logs").optJSONObject(1);
        assertTrue(skipped.optBoolean("skipped"));
        assertFalse(skipped.optBoolean("done"));
        assertFalse(skipped.has("incompleteReason"));
        assertEquals("unknown", TrainingFeedback.reason(skipped, saved));
        assertEquals(completed, firstLog(saved).toString());
        assertEquals(0, WorkoutAdjustments.pendingRow(exercise));
        assertEquals(
                otherLogs,
                saved.optJSONArray("rows").optJSONObject(1).optJSONArray("logs").toString());
    }

    @Test
    public void skippingFromTheWorkoutMenuSupportsCancelReasonAndReview() {
        JSONObject session = fixture();
        String id = session.optString("id");
        String completed = firstLog(session).toString();
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> a.show("workout"));
            idle();
            scenario.onActivity(a -> {
                click(a, a.s("adjust_exercise"));
                click(a, a.s("skip_remaining_sets"));
            });
            idle();
            scenario.onActivity(a -> click(a, a.s("cancel")));
            idle();
            assertEquals(session.toString(), find("active", id).toString());

            scenario.onActivity(a -> {
                click(a, a.s("adjust_exercise"));
                click(a, a.s("skip_remaining_sets"));
            });
            idle();
            scenario.onActivity(a -> {
                collect(a.activeDialog.getWindow().getDecorView(), Spinner.class)
                        .get(0)
                        .setSelection(2);
                click(a, a.s("confirm_skip_sets"));
            });
            idle();
            JSONObject saved = find("active", id);
            JSONObject skipped =
                    saved.optJSONArray("rows")
                            .optJSONObject(0)
                            .optJSONArray("logs")
                            .optJSONObject(1);
            assertEquals("equipment", TrainingFeedback.reason(skipped, saved));
            assertEquals(
                    "skip", saved.optJSONArray("adjustments").optJSONObject(0).optString("type"));
            assertEquals(completed, firstLog(saved).toString());
            assertEquals(2, WorkoutAdjustments.pending(saved));
            onView(withText("已跳过剩余 2 组")).check(matches(isDisplayed()));
            onView(withTagValue(is((Object) "workout_exercise_pager"))).perform(swipeLeft());
            onView(withTagValue(is((Object) "workout_exercise_pager"))).perform(swipeRight());
            onView(withText("已跳过剩余 2 组")).check(matches(isDisplayed()));
            onView(withText("跳过原因 · 器械原因")).check(matches(isDisplayed()));

            JSONObject finished =
                    TrainingFeedback.finish(saved, "time", System.currentTimeMillis());
            database.saveSessionEdit("active", saved, finished, true);
            scenario.onActivity(a -> a.openHistory(finished));
            onView(withText("第 2 组 · 已跳过")).check(matches(withText("第 2 组 · 已跳过")));
            assertEquals(completed, firstLog(find("history", id)).toString());
        }
    }

    @Test
    public void replacementUiRequiresNewWeightAndCanRecordAndFinishAfterApplying() {
        JSONObject session = fixture();
        String completed = firstLog(session).toString();
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> a.show("workout"));
            idle();
            scenario.onActivity(a -> {
                click(a, a.s("adjust_exercise"));
                click(a, a.s("replace_remaining"));
            });
            idle();
            scenario.onActivity(a -> click(a, a.s("find_replacements")));
            idle();
            scenario.onActivity(a -> {
                ListView list = a.activeDialog.getListView();
                assertTrue(list.getAdapter().getItem(0).toString().contains(a.s("ex16")));
                list.performItemClick(list.getChildAt(0), 0, list.getAdapter().getItemId(0));
            });
            idle();
            scenario.onActivity(a -> {
                List<EditText> fields =
                        collect(a.activeDialog.getWindow().getDecorView(), EditText.class);
                assertEquals("", fields.get(0).getText().toString());
                click(a, a.s("preview_adjustment"));
                assertTrue(a.activeDialog.isShowing());
            });
            idle();
            assertEquals(session.toString(), find("active", session.optString("id")).toString());
            scenario.onActivity(a -> {
                List<EditText> fields =
                        collect(a.activeDialog.getWindow().getDecorView(), EditText.class);
                fields.get(0).setText("12.5");
                fields.get(1).setText("12");
                click(a, a.s("preview_adjustment"));
            });
            idle();
            screenshot("replacement-preview");
            scenario.onActivity(a -> click(a, a.s("apply_adjustment")));
            idle();
            onView(withText("动作 2 / 3 · 左右滑动")).check(matches(isDisplayed()));
            onView(withTagValue(is((Object) "workout_exercise_pager"))).perform(swipeRight());
            onView(withText("动作 1 / 3 · 左右滑动")).check(matches(isDisplayed()));
            onView(withTagValue(is((Object) "workout_exercise_pager"))).perform(swipeLeft());
            onView(withText("动作 2 / 3 · 左右滑动")).check(matches(isDisplayed()));
            scenario.onActivity(a -> click(a, a.s("complete_set")));
            idle();
            JSONObject recorded = find("active", session.optString("id"));
            assertEquals(completed, firstLog(recorded).toString());
            assertEquals(2, WorkoutAdjustments.completed(recorded));
            scenario.onActivity(a -> click(a, a.s("finish")));
            idle();
            scenario.onActivity(a -> {
                collect(a.activeDialog.getWindow().getDecorView(), Spinner.class)
                        .get(0)
                        .setSelection(1);
                click(a, a.s("confirm"));
            });
            idle();
            JSONObject history = find("history", session.optString("id"));
            assertNotNull(history);
            assertEquals(1, history.optJSONArray("adjustments").length());
            assertEquals(completed, firstLog(history).toString());
            scenario.onActivity(a -> a.openHistory(history));
            idle();
            screenshot("adjusted-history");
        }
    }
}
