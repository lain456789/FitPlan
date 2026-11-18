package com.example.fitplan;

import static com.example.fitplan.Store.copy;
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
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class InteractionImprovementsTest {
    private Context context;
    private Store database;
    private String language;
    private final List<String> ids = new ArrayList<>();
    private final List<JSONObject> histories = new ArrayList<>();
    private final long base = System.currentTimeMillis() - 180000;

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
        for (String value : ids) database.delete(value);
        database.close();
        context.getSharedPreferences("preferences", 0)
                .edit()
                .putString("language", language)
                .commit();
    }

    private void save(String kind, JSONObject doc) {
        database.save(kind, doc);
        ids.add(doc.optString("id"));
    }

    private JSONObject find(String kind, String value) {
        JSONArray docs = database.list(kind);
        for (int i = 0; i < docs.length(); i++)
            if (value.equals(docs.optJSONObject(i).optString("id"))) return docs.optJSONObject(i);
        return null;
    }

    private JSONObject row(JSONObject session) {
        return session.optJSONArray("rows").optJSONObject(0);
    }

    private JSONObject log(JSONObject session, int index) {
        return row(session).optJSONArray("logs").optJSONObject(index);
    }

    private void goodHistory() {
        for (int i = 1; i <= 3; i++) {
            JSONArray logs = new JSONArray();
            for (int j = 0; j < 3; j++)
                logs.put(
                        object(
                                "weight",
                                40,
                                "reps",
                                10,
                                "done",
                                true,
                                "resolved",
                                true,
                                "effort",
                                "appropriate",
                                "incompleteReason",
                                "none",
                                "feedbackVersion",
                                1));
            JSONObject session =
                    object(
                            "name",
                            "[Test] Fresh advice",
                            "finished",
                            base + i * 1000,
                            "started",
                            base + i * 1000 - 60000,
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
                                                    10,
                                                    "logs",
                                                    logs)));
            save("history", session);
            histories.add(session);
        }
    }

    private JSONObject plan(JSONObject advice) {
        JSONObject doc =
                object(
                        "name",
                        "[Test] Interaction plan",
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
                                                10,
                                                "userConfirmed",
                                                true)));
        if (advice != null) put(row(doc), "progressAdvice", advice);
        save("plan", doc);
        return doc;
    }

    private JSONObject active(boolean firstRecorded) {
        JSONObject plan = plan(null), session = copy(plan);
        put(session, "id", id());
        put(session, "planId", plan.optString("id"));
        put(session, "started", System.currentTimeMillis());
        put(session, "restUntil", 0);
        JSONArray logs = new JSONArray();
        for (int i = 0; i < 3; i++) logs.put(object("weight", 40, "reps", 10, "done", false));
        put(row(session), "logs", logs);
        if (firstRecorded)
            session =
                    TrainingFeedback.record(
                            session, 0, 0, 42.25, 8, true, "hard", "too_hard", base);
        save("active", session);
        return session;
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

    private View dialog(MainActivity activity) {
        return activity.activeDialog.getWindow().getDecorView();
    }

    private void click(MainActivity activity, String label) {
        View root =
                activity.activeDialog != null && activity.activeDialog.isShowing()
                        ? dialog(activity)
                        : activity.getWindow().getDecorView();
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
        android.os.SystemClock.sleep(700);
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

    private void waitSummary(ActivityScenario<MainActivity> scenario) {
        boolean[] ready = {false};
        for (int i = 0; i < 100; i++) {
            scenario.onActivity(activity -> {
                View loading = findView(activity.content, activity.s("loading"));
                ready[0] = loading == null || !loading.isShown();
                assertNull(findView(activity.content, activity.s("read_error")));
            });
            if (ready[0]) return;
            android.os.SystemClock.sleep(50);
        }
        fail("Home summary did not load");
    }

    private void makeLatestHard() {
        JSONObject latest = histories.get(histories.size() - 1);
        JSONObject changed =
                TrainingFeedback.record(latest, 0, 0, 40, 10, true, "hard", "none", base);
        database.saveSessionEdit("history", latest, changed, false);
    }

    @Test
    public void planDropsAnOutdatedSuggestionAfterHistoryEditingWithoutRewritingSnapshots() {
        goodHistory();
        JSONObject plan =
                plan(
                        ProgressReader.snapshot(
                                ProgressReader.assess(database.list("history"), "builtin-0")));
        String before = plan.toString();
        makeLatestHard();
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.openPlan(plan);
                assertNull(findView(activity.content, activity.s("progress_adopt")));
                assertNotNull(findView(activity.content, activity.s("advice_repeat")));
            });
        }
        assertEquals(before, find("plan", plan.optString("id")).toString());
    }

    @Test
    public void planShowsNewAdviceEvenWhenItsSavedSnapshotHasNoRecords() {
        JSONObject plan = plan(object("status", "no_data", "canIncrease", false));
        goodHistory();
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.openPlan(plan);
                assertNotNull(findView(activity.content, activity.s("progress_adopt")));
                assertNotNull(findView(activity.content, activity.s("advice_optional_rep", 11)));
            });
        }
        assertEquals(10, row(find("plan", plan.optString("id"))).optInt("reps"));
    }

    @Test
    public void deletingHistoryRemovesOldAdviceImmediately() {
        goodHistory();
        JSONObject plan =
                plan(
                        ProgressReader.snapshot(
                                ProgressReader.assess(database.list("history"), "builtin-0")));
        for (JSONObject history : histories) database.delete(history.optString("id"));
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.openPlan(plan);
                assertNull(findView(activity.content, activity.s("progress_adopt")));
                assertNotNull(findView(activity.content, activity.s("progress_status_no_data")));
            });
        }
    }

    @Test
    public void changedEvidenceDuringConfirmationStillCannotModifyThePlan() {
        goodHistory();
        JSONObject plan = plan(null);
        String before = plan.toString();
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.openPlan(plan);
                click(activity, activity.s("progress_adopt"));
            });
            idle();
            makeLatestHard();
            scenario.onActivity(activity -> click(activity, activity.s("confirm")));
            idle();
            scenario.onActivity(activity ->
                    assertNull(findView(activity.content, activity.s("progress_adopt"))));
        }
        assertEquals(before, find("plan", plan.optString("id")).toString());
    }

    @Test
    public void reuseValuesArePreciseAndOnlyComeFromEarlierPerformedSetsInTheSameRow() {
        JSONObject session = active(true);
        put(log(session, 1), "skipped", true);
        put(log(session, 1), "weight", 99);
        String before = session.toString();
        JSONObject values = TrainingFeedback.previousValues(row(session), 2);
        assertEquals(42.25, values.optDouble("weight"), 0);
        assertEquals(8, values.optInt("reps"));
        assertFalse(values.has("effort"));
        assertFalse(values.has("incompleteReason"));
        assertEquals(before, session.toString());
        assertNull(TrainingFeedback.previousValues(row(session), 0));
        assertNull(TrainingFeedback.previousValues(row(session), 3));
        put(log(session, 0), "weight", 0);
        assertEquals(0, TrainingFeedback.previousValues(row(session), 2).optDouble("weight"), 0);
        put(log(session, 0), "weight", -1);
        assertNull(TrainingFeedback.previousValues(row(session), 2));
    }

    @Test
    public void reusingValuesAndCancellingDoesNotSaveOrCopyDifficulty() {
        JSONObject session = active(true);
        String before = session.toString();
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.show("workout");
                click(activity, activity.s("record_set", 2));
            });
            idle();
            scenario.onActivity(activity -> {
                click(activity, activity.s("reuse_previous_set"));
                List<EditText> inputs = collect(dialog(activity), EditText.class);
                assertEquals("42.25", inputs.get(0).getText().toString());
                assertEquals("8", inputs.get(1).getText().toString());
                for (ToggleButton effort : collect(dialog(activity), ToggleButton.class))
                    assertFalse(effort.isChecked());
                assertTrue(collect(dialog(activity), Spinner.class).get(0).isShown());
                click(activity, activity.s("cancel"));
            });
        }
        assertEquals(before, find("active", session.optString("id")).toString());
    }

    @Test
    public void reusedValuesCanBeEditedAndSavedWithoutChangingEarlierRecords() {
        JSONObject session = active(true);
        String first = log(session, 0).toString();
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.show("workout");
                click(activity, activity.s("record_set", 2));
            });
            idle();
            scenario.onActivity(activity -> {
                click(activity, activity.s("reuse_previous_set"));
                collect(dialog(activity), EditText.class).get(1).setText("9");
                click(activity, activity.s("effort_appropriate"));
                collect(dialog(activity), Spinner.class).get(0).setSelection(3);
                click(activity, activity.s("save"));
            });
        }
        JSONObject saved = find("active", session.optString("id"));
        assertEquals(first, log(saved, 0).toString());
        assertEquals(42.25, log(saved, 1).optDouble("weight"), 0);
        assertEquals(9, log(saved, 1).optInt("reps"));
        assertEquals("appropriate", log(saved, 1).optString("effort"));
        assertEquals("too_hard", log(saved, 1).optString("incompleteReason"));
    }

    @Test
    public void quickDifficultyIsSingleChoiceAndCanBeClearedWithoutRequiringAReasonAtTarget() {
        JSONObject session = active(false);
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.show("workout");
                click(activity, activity.s("record_set", 1));
            });
            idle();
            scenario.onActivity(activity -> {
                assertNull(findView(dialog(activity), activity.s("reuse_previous_set")));
                assertFalse(collect(dialog(activity), Spinner.class).get(0).isShown());
                click(activity, activity.s("effort_hard"));
                click(activity, activity.s("effort_appropriate"));
                assertFalse(
                        ((ToggleButton) findView(dialog(activity), activity.s("effort_hard")))
                                .isChecked());
                assertTrue(
                        ((ToggleButton)
                                        findView(
                                                dialog(activity), activity.s("effort_appropriate")))
                                .isChecked());
                click(activity, activity.s("effort_appropriate"));
                for (ToggleButton effort : collect(dialog(activity), ToggleButton.class))
                    assertFalse(effort.isChecked());
                click(activity, activity.s("save"));
            });
        }
        JSONObject saved = find("active", session.optString("id"));
        assertTrue(log(saved, 0).optBoolean("done"));
        assertEquals("unknown", log(saved, 0).optString("effort"));
        assertEquals("none", log(saved, 0).optString("incompleteReason"));
    }

    @Test
    public void optionalDiscomfortCanStillBeRecordedWhenTheTargetWasMet() {
        JSONObject session = active(false);
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.show("workout");
                click(activity, activity.s("record_set", 1));
            });
            idle();
            scenario.onActivity(activity -> {
                click(activity, activity.s("add_set_reason"));
                Spinner reason = collect(dialog(activity), Spinner.class).get(0);
                assertTrue(reason.isShown());
                reason.setSelection(4);
                click(activity, activity.s("effort_hard"));
                click(activity, activity.s("save"));
            });
        }
        JSONObject saved = find("active", session.optString("id"));
        assertEquals("discomfort", log(saved, 0).optString("incompleteReason"));
        put(saved, "finished", System.currentTimeMillis());
        assertEquals(
                "discomfort",
                ProgressReader.assess(new JSONArray().put(saved), "builtin-0").status);
    }

    @Test
    public void markingASetUnperformedAutomaticallyShowsItsReasonAndPreservesZeroReps() {
        JSONObject session = active(false);
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.show("workout");
                click(activity, activity.s("record_set", 1));
            });
            idle();
            scenario.onActivity(activity -> {
                collect(dialog(activity), CheckBox.class).get(0).setChecked(false);
                assertTrue(collect(dialog(activity), Spinner.class).get(0).isShown());
                collect(dialog(activity), EditText.class).get(1).setText("0");
                collect(dialog(activity), Spinner.class).get(0).setSelection(1);
                click(activity, activity.s("save"));
            });
        }
        JSONObject saved = find("active", session.optString("id"));
        assertFalse(log(saved, 0).optBoolean("done"));
        assertTrue(log(saved, 0).optBoolean("resolved"));
        assertEquals(0, log(saved, 0).optInt("reps"));
        assertEquals("time", log(saved, 0).optString("incompleteReason"));
        assertEquals(2, WorkoutAdjustments.pending(saved));
    }

    @Test
    public void homeKeepsContinueBeforeTheFormAndItsPrimaryActionVisibleAcrossNavigation() {
        JSONObject session = active(false);
        String before = session.toString();
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            idle();
            scenario.onActivity(activity -> {
                View resume = findView(activity.content, activity.s("continue_workout"));
                View question = findView(activity.content, activity.s("parts_question"));
                int[] resumeAt = new int[2], questionAt = new int[2];
                resume.getLocationOnScreen(resumeAt);
                question.getLocationOnScreen(questionAt);
                assertTrue(resumeAt[1] < questionAt[1]);
                View generate =
                        findView(activity.findViewById(R.id.home_actions), activity.s("generate"));
                Rect visible = new Rect();
                assertTrue(generate.getGlobalVisibleRect(visible));
                assertEquals(generate.getHeight(), visible.height());
                collect(activity.content, EditText.class).get(0).setText("30");
                activity.show("plans");
                assertEquals(View.GONE, activity.findViewById(R.id.home_actions).getVisibility());
                activity.show("home");
                assertEquals(
                        "30",
                        collect(activity.content, EditText.class).get(0).getText().toString());
                click(activity, activity.s("continue_workout"));
                assertEquals("workout", activity.page);
                assertEquals(View.GONE, activity.findViewById(R.id.home_actions).getVisibility());
            });
        }
        assertEquals(before, find("active", session.optString("id")).toString());
    }

    @Test
    public void fixedGenerateActionStillUsesTheCurrentStateAndReplacesTodaysPlan() {
        Set<String> existing = new HashSet<>();
        for (String kind : new String[] {"plan", "checkin"}) {
            JSONArray docs = database.list(kind);
            for (int i = 0; i < docs.length(); i++)
                existing.add(docs.optJSONObject(i).optString("id"));
        }
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertNull(new HomeScreen(activity).todayCheck());
                collect(activity.content, CheckBox.class).get(0).setChecked(true);
                collect(activity.content, EditText.class).get(0).setText("35");
                collect(activity.content, Spinner.class).get(1).setSelection(2);
                click(activity, activity.s("generate"));
                assertEquals("plans", activity.page);
                assertEquals(View.GONE, activity.findViewById(R.id.home_actions).getVisibility());
                JSONObject check = new HomeScreen(activity).todayCheck();
                assertEquals(35, check.optInt("minutes"));
                assertEquals(2, check.optInt("sleep"));
                activity.show("home");
                collect(activity.content, EditText.class).get(0).setText("25");
                click(activity, activity.s("generate"));
                assertEquals(
                        check.optString("id"),
                        new HomeScreen(activity).todayCheck().optString("id"));
                assertEquals(25, new HomeScreen(activity).todayCheck().optInt("minutes"));
            });
        } finally {
            for (String kind : new String[] {"plan", "checkin"}) {
                JSONArray docs = database.list(kind);
                for (int i = 0; i < docs.length(); i++) {
                    String value = docs.optJSONObject(i).optString("id");
                    if (!existing.contains(value)) ids.add(value);
                }
            }
        }
    }

    @Test
    public void bilingualHomeAndQuickEntryRemainUsable() {
        active(true);
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            waitSummary(scenario);
            screenshot("flow-home-zh");
            scenario.onActivity(activity ->
                    ((ScrollView) activity.findViewById(R.id.scroller))
                            .fullScroll(View.FOCUS_DOWN));
            screenshot("flow-home-details-zh");
            scenario.onActivity(activity -> activity.setLanguage("en"));
            waitSummary(scenario);
            screenshot("flow-home-en");
            scenario.onActivity(activity ->
                    ((ScrollView) activity.findViewById(R.id.scroller))
                            .fullScroll(View.FOCUS_DOWN));
            screenshot("flow-home-details-en");
            scenario.onActivity(activity -> {
                activity.show("workout");
                click(activity, activity.s("record_set", 2));
            });
            idle();
            scenario.onActivity(activity -> {
                assertNotNull(findView(dialog(activity), activity.s("reuse_previous_set")));
                assertEquals(3, collect(dialog(activity), ToggleButton.class).size());
                assertFalse(collect(dialog(activity), Spinner.class).get(0).isShown());
            });
            screenshot("flow-entry-en");
            scenario.onActivity(activity -> click(activity, activity.s("cancel")));
            idle();
            scenario.onActivity(activity -> activity.setLanguage("zh"));
            idle();
            scenario.onActivity(activity -> click(activity, activity.s("record_set", 2)));
            idle();
            screenshot("flow-entry-zh");
        }
    }
}
