package com.example.fitplan;

import static com.example.fitplan.Store.copy;
import static com.example.fitplan.Store.id;
import static com.example.fitplan.Store.object;
import static com.example.fitplan.Store.put;

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
import android.widget.ScrollView;
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
public class ProgressFeedbackTest {
    private Context context;
    private Store database;
    private String language;
    private final List<String> ids = new ArrayList<>();
    private final long base = System.currentTimeMillis() - 100000;

    @Before
    public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        database = new Store(context);
        database.seed();
        language = context.getSharedPreferences("preferences", 0).getString("language", "zh");
        context.getSharedPreferences("preferences", 0).edit().putString("language", "zh").commit();
    }

    @After
    public void cleanup() {
        for (String id : ids) database.delete(id);
        database.close();
        context.getSharedPreferences("preferences", 0)
                .edit()
                .putString("language", language)
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

    private JSONObject history(
            String exercise, long offset, int[] reps, int effort, String reason) {
        JSONArray logs = new JSONArray();
        for (int n : reps)
            logs.put(
                    object(
                            "weight",
                            40,
                            "reps",
                            n,
                            "done",
                            n > 0,
                            "resolved",
                            true,
                            "feedbackVersion",
                            1,
                            "effort",
                            n > 0 ? TrainingFeedback.EFFORTS[effort] : "unknown",
                            "incompleteReason",
                            n < 10 ? reason : "none"));
        JSONObject doc =
                object(
                        "name",
                        "[Test] Feedback",
                        "started",
                        base + offset - 60000,
                        "finished",
                        base + offset,
                        "rows",
                        new JSONArray()
                                .put(
                                        object(
                                                "exerciseId",
                                                exercise,
                                                "name",
                                                "测试动作",
                                                "part",
                                                "chest",
                                                "weight",
                                                40,
                                                "sets",
                                                reps.length,
                                                "reps",
                                                10,
                                                "logs",
                                                logs)));
        save("history", doc);
        return doc;
    }

    private JSONObject active() {
        JSONObject plan =
                object(
                        "name",
                        "[Test] Feedback plan",
                        "rows",
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
                                                10)));
        save("plan", plan);
        JSONObject session = copy(plan);
        put(session, "id", id());
        put(session, "planId", plan.optString("id"));
        put(session, "started", System.currentTimeMillis());
        put(session, "restUntil", 0);
        JSONArray logs = new JSONArray();
        for (int i = 0; i < 3; i++) logs.put(object("weight", 40, "reps", 10, "done", false));
        put(session.optJSONArray("rows").optJSONObject(0), "logs", logs);
        save("active", session);
        return session;
    }

    private JSONObject row(JSONObject session) {
        return session.optJSONArray("rows").optJSONObject(0);
    }

    private JSONObject log(JSONObject session, int i) {
        return row(session).optJSONArray("logs").optJSONObject(i);
    }

    private void goodHistory() {
        history("builtin-0", 1000, new int[] {10, 10, 10}, 2, "none");
        history("builtin-0", 2000, new int[] {10, 10, 10}, 1, "none");
        history("builtin-0", 3000, new int[] {10, 10, 10}, 2, "none");
    }

    private View findView(View root, String label) {
        if (root instanceof TextView && label.contentEquals(((TextView) root).getText()))
            return root;
        if (root instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
                View found = findView(((ViewGroup) root).getChildAt(i), label);
                if (found != null) return found;
            }
        return null;
    }

    private void click(MainActivity a, String label) {
        View root =
                a.activeDialog != null && a.activeDialog.isShowing()
                        ? a.activeDialog.getWindow().getDecorView()
                        : a.getWindow().getDecorView();
        View found = findView(root, label);
        assertNotNull("Missing control: " + label, found);
        assertTrue(found.performClick());
    }

    private <T extends View> List<T> collect(View root, Class<T> type) {
        List<T> result = new ArrayList<>();
        if (type.isInstance(root)) result.add(type.cast(root));
        if (root instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++)
                result.addAll(collect(((ViewGroup) root).getChildAt(i), type));
        return result;
    }

    private void idle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void screenshot(String name) {
        idle();
        android.os.SystemClock.sleep(4000);
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
    public void setFeedbackPersistsAndSameWeightRepProgressIsRecognised() {
        String exercise = "progress-" + id();
        history(exercise, 1000, new int[] {8, 8, 8}, 3, "too_hard");
        JSONObject latest = history(exercise, 2000, new int[] {9, 9, 9}, 3, "too_hard");
        ProgressEngine.Assessment result =
                ProgressReader.assess(database.list("history"), exercise);
        assertEquals("reps_improved", result.status);
        assertEquals(24, result.previous.totalReps());
        assertEquals(27, result.latest.totalReps());
        assertFalse(result.canIncrease);
        try (Store reopened = new Store(context)) {
            JSONObject actual = null;
            JSONArray all = reopened.list("history");
            for (int i = 0; i < all.length(); i++)
                if (latest.optString("id").equals(all.optJSONObject(i).optString("id")))
                    actual = all.optJSONObject(i);
            assertNotNull(actual);
            assertEquals("hard", log(actual, 0).optString("effort"));
            assertEquals("too_hard", log(actual, 0).optString("incompleteReason"));
        }
    }

    @Test
    public void missedTargetsRequireReasonAndUnperformedSetsNeedZeroReps() {
        JSONObject session = active();
        String before = session.toString();
        try {
            TrainingFeedback.record(session, 0, 0, 40, 8, true, "hard", "unknown", base);
            fail();
        } catch (TrainingFeedback.Invalid expected) {
            assertEquals("feedback_reason_required", expected.key);
        }
        try {
            TrainingFeedback.record(session, 0, 0, 40, 8, false, "hard", "too_hard", base);
            fail();
        } catch (TrainingFeedback.Invalid expected) {
            assertEquals("feedback_done_reps", expected.key);
        }
        JSONObject next =
                TrainingFeedback.record(session, 0, 0, 40, 0, false, "hard", "time", base);
        database.saveSessionEdit("active", session, next, false);
        assertEquals("unknown", log(next, 0).optString("effort"));
        assertEquals(2, WorkoutAdjustments.pending(next));
        assertEquals(before, session.toString());
    }

    @Test
    public void finishAnnotatesOnlyUnrecordedSetsAndKeepsExplicitFeedback() {
        JSONObject session = active(),
                recorded =
                        TrainingFeedback.record(
                                session, 0, 0, 40, 8, true, "hard", "too_hard", base);
        database.saveSessionEdit("active", session, recorded, false);
        String first = log(recorded, 0).toString();
        JSONObject finished = TrainingFeedback.finish(recorded, "time", System.currentTimeMillis());
        database.saveSessionEdit("active", recorded, finished, true);
        assertEquals(first, log(finished, 0).toString());
        assertEquals("time", log(finished, 1).optString("incompleteReason"));
        assertEquals(0, log(finished, 1).optInt("reps"));
        assertEquals(
                "context",
                ProgressReader.assess(new JSONArray().put(finished), "builtin-0").status);
        ReportEngine.Summary summary =
                ReportEngine.aggregate(
                        HistoryReader.read(new JSONArray().put(finished)),
                        0,
                        Long.MAX_VALUE,
                        TimeZone.getDefault());
        assertEquals(1, summary.sets);
        assertEquals(320, summary.volume, 0);
    }

    @Test
    public void editingOrDeletingHistoryRecomputesAnalysis() {
        String exercise = "progress-" + id();
        history(exercise, 1000, new int[] {8, 8, 8}, 3, "too_hard");
        JSONObject latest = history(exercise, 2000, new int[] {9, 9, 9}, 3, "too_hard");
        assertEquals(
                "reps_improved", ProgressReader.assess(database.list("history"), exercise).status);
        JSONObject changed =
                TrainingFeedback.record(latest, 0, 2, 40, 1, true, "hard", "too_hard", base);
        database.saveSessionEdit("history", latest, changed, false);
        assertEquals("review", ProgressReader.assess(database.list("history"), exercise).status);
        database.delete(latest.optString("id"));
        assertEquals(
                "insufficient", ProgressReader.assess(database.list("history"), exercise).status);
    }

    @Test
    public void legacyFieldsRemainUnknownAndUntouchedPlannedRepsAreNotActual() {
        JSONObject session = active();
        put(session, "finished", base);
        put(log(session, 0), "done", true);
        put(log(session, 0), "reps", 8);
        List<ProgressEngine.Sample> samples =
                ProgressReader.samples(new JSONArray().put(session), "builtin-0");
        assertEquals(8, samples.get(0).totalReps());
        assertEquals(0, samples.get(0).effort());
        assertEquals("unknown", TrainingFeedback.reason(log(session, 0), session));
        assertFalse(ProgressEngine.assess(samples).canIncrease);
        assertFalse(log(session, 0).has("effort"));
    }

    @Test
    public void replacementCancellationAndDuplicateExerciseRowsAreNotCompared() {
        JSONObject latest = history("progress-" + id(), 1000, new int[] {10, 10, 10}, 2, "none");
        String exercise = row(latest).optString("exerciseId");
        put(log(latest, 2), "skipped", true);
        put(log(latest, 2), "done", false);
        put(log(latest, 2), "skipReason", "time");
        assertEquals(
                "context", ProgressReader.assess(new JSONArray().put(latest), exercise).status);
        JSONObject duplicate = copy(latest);
        row(duplicate).optJSONArray("logs").optJSONObject(2).remove("skipped");
        put(log(duplicate, 2), "done", true);
        duplicate.optJSONArray("rows").put(copy(row(duplicate)));
        assertEquals(
                "incomparable",
                ProgressReader.assess(new JSONArray().put(duplicate), exercise).status);
    }

    @Test
    public void generatedPlanShowsAdviceButRequiresConfirmationToChangeReps() {
        goodHistory();
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                JSONObject check =
                        object(
                                "day",
                                a.day(),
                                "minutes",
                                45,
                                "volume",
                                1,
                                "sleep",
                                0,
                                "food",
                                0,
                                "soreness",
                                0,
                                "parts",
                                new JSONArray().put("chest"));
                JSONObject plan = new HomeScreen(a).generate(check);
                JSONObject chosen = null;
                JSONArray rows = plan.optJSONArray("rows");
                for (int i = 0; i < rows.length(); i++)
                    if ("builtin-0".equals(rows.optJSONObject(i).optString("exerciseId")))
                        chosen = rows.optJSONObject(i);
                assertNotNull(chosen);
                assertEquals(10, chosen.optInt("reps"));
                assertEquals(40, chosen.optDouble("weight"), 0);
                assertTrue(chosen.optJSONObject("progressAdvice").optBoolean("canIncrease"));
                put(plan, "rows", new JSONArray().put(chosen));
                save("plan", plan);
                new TrainingScreen(a).start(plan);
                JSONObject current = database.first("active");
                assertNotNull(current);
                ids.add(current.optString("id"));
                String activeBefore = current.toString();
                new ProgressScreen(a).applyAdvice(plan, 0);
                JSONObject stored = find("plan", plan.optString("id"));
                assertEquals(11, row(stored).optInt("reps"));
                assertEquals(40, row(stored).optDouble("weight"), 0);
                assertEquals(activeBefore, find("active", current.optString("id")).toString());
            });
        }
    }

    @Test
    public void changedHistoryInvalidatesAnOldProgressionProposal() {
        goodHistory();
        JSONObject plan =
                object(
                        "name",
                        "[Test] Next plan",
                        "rows",
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
                                                10)));
        save("plan", plan);
        String before = plan.toString();
        JSONObject latest =
                ProgressReader.snapshot(
                                ProgressReader.assess(database.list("history"), "builtin-0"))
                        .optJSONObject("latest");
        JSONObject current = find("history", latest.optString("sessionId"));
        JSONObject changed =
                TrainingFeedback.record(current, 0, 0, 40, 10, true, "hard", "none", base);
        database.saveSessionEdit("history", current, changed, false);
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                try {
                    new ProgressScreen(a).applyAdvice(plan, 0);
                    fail();
                } catch (Store.StaleWorkout expected) {
                    assertEquals(before, find("plan", plan.optString("id")).toString());
                }
            });
        }
    }

    @Test
    public void staleSetFeedbackCannotOverwriteAWorkoutAdjustment() {
        JSONObject session = active(),
                feedback =
                        TrainingFeedback.record(
                                session, 0, 0, 40, 10, true, "appropriate", "none", base);
        database.applyAdjustment(
                WorkoutAdjustments.shorten(session, 3, System.currentTimeMillis()));
        String after = find("active", session.optString("id")).toString();
        try {
            database.saveSessionEdit("active", session, feedback, false);
            fail();
        } catch (Store.StaleWorkout expected) {
            assertEquals(after, find("active", session.optString("id")).toString());
        }
    }

    @Test
    public void feedbackWriteFailureLeavesOriginalDataUntouched() {
        JSONObject session = active(),
                updated =
                        TrainingFeedback.record(
                                session, 0, 0, 40, 8, true, "hard", "too_hard", base);
        String trigger = "feedback_failure_" + id().replace("-", "");
        database.getWritableDatabase()
                .execSQL(
                        "CREATE TEMP TRIGGER "
                                + trigger
                                + " BEFORE INSERT ON documents WHEN NEW.id='"
                                + session.optString("id")
                                + "' BEGIN SELECT RAISE(ABORT,'test write failure'); END");
        try {
            try {
                database.saveSessionEdit("active", session, updated, false);
                fail();
            } catch (SQLiteException expected) {
                assertEquals(
                        session.toString(), find("active", session.optString("id")).toString());
            }
        } finally {
            database.getWritableDatabase().execSQL("DROP TRIGGER " + trigger);
        }
    }

    @Test
    public void uiRecordsPartialFeedbackAndRequiresAnEarlyFinishReason() {
        JSONObject session = active();
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                a.show("workout");
                click(a, a.s("record_set", 1));
            });
            idle();
            scenario.onActivity(a -> {
                View root = a.activeDialog.getWindow().getDecorView();
                collect(root, EditText.class).get(1).setText("8");
                collect(root, Spinner.class).get(0).setSelection(3);
                click(a, a.s("save"));
                assertTrue(a.activeDialog.isShowing());
            });
            idle();
            assertEquals(session.toString(), find("active", session.optString("id")).toString());
            scenario.onActivity(a ->
                    collect(a.activeDialog.getWindow().getDecorView(), Spinner.class)
                            .get(1)
                            .setSelection(3));
            idle();
            screenshot("progress-set-feedback");
            scenario.onActivity(a -> click(a, a.s("save")));
            idle();
            scenario.onActivity(a -> click(a, a.s("finish")));
            idle();
            scenario.onActivity(a -> {
                click(a, a.s("confirm"));
                assertTrue(a.activeDialog.isShowing());
                collect(a.activeDialog.getWindow().getDecorView(), Spinner.class)
                        .get(0)
                        .setSelection(1);
                click(a, a.s("confirm"));
            });
            idle();
            JSONObject saved = find("history", session.optString("id"));
            assertNotNull(saved);
            assertEquals("hard", log(saved, 0).optString("effort"));
            assertEquals("too_hard", log(saved, 0).optString("incompleteReason"));
            assertEquals("time", log(saved, 1).optString("incompleteReason"));
            scenario.onActivity(a -> {
                a.exerciseId = "builtin-0";
                a.show("progress");
                ((ScrollView) a.findViewById(R.id.scroller)).scrollTo(0, a.dp(320));
            });
            idle();
            screenshot("progress-context-feedback");
        }
    }

    @Test
    public void uiDisplaysSameWeightImprovementWithNumericEvidence() {
        history("builtin-0", 1000, new int[] {10, 8, 6}, 3, "too_hard");
        history("builtin-0", 2000, new int[] {10, 10, 8}, 3, "too_hard");
        history("builtin-0", 3000, new int[] {10, 10, 9}, 3, "too_hard");
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                a.exerciseId = "builtin-0";
                a.show("progress");
                assertNotNull(findView(a.content, a.s("progress_status_reps_improved")));
                assertNotNull(findView(a.content, a.s("progress_compare_reps", 28, 29)));
                assertNotNull(findView(a.content, a.s("progress_compare_completion", 2, 2, 3)));
                ((ScrollView) a.findViewById(R.id.scroller)).scrollTo(0, a.dp(320));
            });
            idle();
            screenshot("progress-same-weight");
            scenario.recreate();
            scenario.onActivity(a -> {
                assertEquals("progress", a.page);
                assertEquals("builtin-0", a.exerciseId);
                a.setLanguage("en");
                assertNotNull(findView(a.content, a.s("progress_compare_reps", 28, 29)));
                ((ScrollView) a.findViewById(R.id.scroller)).scrollTo(0, a.dp(390));
            });
            idle();
            screenshot("progress-same-weight-en");
        }
    }

    @Test
    public void uiSuggestionCanBeCancelledBeforeExplicitAdoption() {
        goodHistory();
        JSONObject plan =
                object(
                        "name",
                        "[Test] Suggested plan",
                        "rows",
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
                                                10)));
        save("plan", plan);
        String before = plan.toString();
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                a.openPlan(plan);
                click(a, a.s("progress_adopt"));
            });
            idle();
            screenshot("progress-adoption-preview");
            scenario.onActivity(a -> click(a, a.s("cancel")));
            idle();
            assertEquals(before, find("plan", plan.optString("id")).toString());
            scenario.onActivity(a -> click(a, a.s("progress_adopt")));
            idle();
            scenario.onActivity(a -> click(a, a.s("confirm")));
            idle();
            assertEquals(11, row(find("plan", plan.optString("id"))).optInt("reps"));
            assertEquals(40, row(find("plan", plan.optString("id"))).optDouble("weight"), 0);
        }
    }

    @Test
    public void zeroActualTrainingCanSaveReasonsWithoutCountingAWorkout() {
        JSONObject session = active(),
                recorded =
                        TrainingFeedback.record(
                                session, 0, 0, 40, 0, false, "unknown", "time", base);
        database.saveSessionEdit("active", session, recorded, false);
        JSONObject finished = TrainingFeedback.finish(recorded, "time", System.currentTimeMillis());
        database.saveSessionEdit("active", recorded, finished, true);
        ReportEngine.Summary summary =
                ReportEngine.aggregate(
                        HistoryReader.read(new JSONArray().put(finished)),
                        0,
                        Long.MAX_VALUE,
                        TimeZone.getDefault());
        assertEquals(0, summary.sessions);
        assertEquals(0, summary.sets);
        assertEquals(
                "context",
                ProgressReader.assess(new JSONArray().put(finished), "builtin-0").status);
    }

    @Test
    public void adjustmentsPreserveRecordedUnperformedReasons() {
        JSONObject session = active(),
                recorded =
                        TrainingFeedback.record(
                                session, 0, 0, 40, 0, false, "unknown", "time", base);
        database.saveSessionEdit("active", session, recorded, false);
        String reason = log(recorded, 0).toString();
        database.applyAdjustment(
                WorkoutAdjustments.shorten(recorded, 3, System.currentTimeMillis()));
        JSONObject current = find("active", session.optString("id"));
        assertEquals(reason, log(current, 0).toString());
        assertEquals(1, WorkoutAdjustments.pending(current));
    }
}
