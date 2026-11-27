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

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

@RunWith(AndroidJUnit4.class)
public class ManualHistoryTest {
    private Context context;
    private Store database;
    private String language;
    private final List<String> ids = new ArrayList<>();
    private final long now = System.currentTimeMillis();

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

    private JSONObject exercise(String exerciseId) {
        JSONArray exercises = database.list("exercise");
        for (int i = 0; i < exercises.length(); i++)
            if (exerciseId.equals(exercises.optJSONObject(i).optString("id")))
                return exercises.optJSONObject(i);
        throw new AssertionError(exerciseId);
    }

    private JSONObject draft(long finished) {
        JSONObject draft = ManualHistory.draft(now);
        ids.add(draft.optString("id"));
        put(draft, "finished", finished);
        JSONObject row = ManualHistory.row(exercise("builtin-0"));
        row.optJSONArray("logs").put(ManualHistory.log("42.25", "12", "appropriate", now));
        row.optJSONArray("logs").put(ManualHistory.log("40", "9", "hard", now));
        draft.optJSONArray("rows").put(row);
        return draft;
    }

    private void invalid(JSONObject draft, String expected) {
        try {
            ManualHistory.build(draft, "Manual test", now);
            fail("Expected " + expected);
        } catch (ManualHistory.Invalid e) {
            assertEquals(expected, e.key);
        }
    }

    @Test
    public void actualSnapshotKeepsPerSetValuesWithoutInventingTargetsOrDuration() {
        JSONObject draft = draft(now - 86400000L);
        String original = draft.toString();
        JSONObject session = ManualHistory.build(draft, "Manual test", now);
        assertEquals(original, draft.toString());
        assertEquals("manual", session.optString("recordSource"));
        assertFalse(session.has("planId"));
        assertFalse(session.has("checkin"));
        assertFalse(session.optBoolean("durationKnown"));
        assertEquals(session.optLong("finished"), session.optLong("started"));
        JSONObject row = session.optJSONArray("rows").optJSONObject(0);
        assertTrue(row.optBoolean("actualOnly"));
        assertEquals(0, row.optInt("reps"));
        assertEquals(42.25, row.optJSONArray("logs").optJSONObject(0).optDouble("weight"), 0);
        assertEquals(9, row.optJSONArray("logs").optJSONObject(1).optInt("reps"));
        for (int i = 0; i < 2; i++) {
            assertTrue(row.optJSONArray("logs").optJSONObject(i).optBoolean("done"));
            assertTrue(row.optJSONArray("logs").optJSONObject(i).optBoolean("resolved"));
        }
        ProgressEngine.Assessment result =
                ProgressReader.assess(new JSONArray().put(session), "builtin-0");
        assertFalse(result.canIncrease);
        assertEquals(0, result.latest.met());
    }

    @Test
    public void validatesDatesDurationsEmptyRowsAndActualSetBounds() {
        invalid(ManualHistory.draft(now), "manual_need_exercise");
        JSONObject draft = draft(now + 60000);
        invalid(draft, "manual_invalid_date");
        put(draft, "finished", 0);
        invalid(draft, "manual_invalid_date");
        put(draft, "finished", now - 60000);
        for (String duration : new String[] {"0", "2.5", "-1", "abc"}) {
            put(draft, "duration", duration);
            invalid(draft, "manual_invalid_duration");
        }
        put(draft, "duration", "45");
        JSONObject session = ManualHistory.build(draft, "Manual test", now);
        assertEquals(45 * 60000L, session.optLong("finished") - session.optLong("started"));
        assertTrue(session.optBoolean("durationKnown"));
        for (String[] pair :
                new String[][] {
                    {"", "10"},
                    {"NaN", "10"},
                    {"-1", "10"},
                    {"1001", "10"},
                    {"20", "0"},
                    {"20", "1.5"},
                    {"20", "101"}
                }) {
            try {
                ManualHistory.log(pair[0], pair[1], "unknown", now);
                fail("Accepted invalid set");
            } catch (ManualHistory.Invalid expected) {
                assertEquals("manual_invalid_set", expected.key);
            }
        }
        assertEquals(0, ManualHistory.log("0", "100", "unknown", now).optDouble("weight"), 0);
        put(draft.optJSONArray("rows").optJSONObject(0), "logs", new JSONArray());
        invalid(draft, "manual_need_set");
    }

    @Test
    public void hoursAndMinutesCanBeEnteredIndependentlyOrTogether() {
        JSONObject draft = draft(now - 60000);
        for (String[] input :
                new String[][] {
                    {"2", "", "120"}, {"", "1441", "1441"}, {"1", "75", "135"}, {"0", "35", "35"}
                }) {
            put(draft, "durationHours", input[0]);
            put(draft, "duration", input[1]);
            JSONObject session = ManualHistory.build(draft, "Manual test", now);
            assertTrue(session.optBoolean("durationKnown"));
            assertEquals(
                    Long.parseLong(input[2]) * 60000,
                    session.optLong("finished") - session.optLong("started"));
        }
        put(draft, "durationHours", Long.toString(Long.MAX_VALUE));
        invalid(draft, "manual_invalid_duration");
        draft.remove("durationHours");
        put(draft, "duration", "35");
        JSONObject legacy = ManualHistory.build(draft, "Legacy minutes", now);
        assertEquals(35 * 60000L, legacy.optLong("finished") - legacy.optLong("started"));
    }

    @Test
    public void conflictingDateCanBeReselectedAndIsCheckedAgainOnSave() {
        TimeZone zone = TimeZone.getDefault();
        long firstDay = ReportEngine.shiftDays(ReportEngine.dayStart(now, zone), -3650, zone);
        while (database.hasHistoryOnDay(firstDay)
                || database.hasHistoryOnDay(ReportEngine.shiftDays(firstDay, 1, zone))) {
            firstDay = ReportEngine.shiftDays(firstDay, -2, zone);
        }
        final long secondDay = ReportEngine.shiftDays(firstDay, 1, zone);
        Calendar occupied = Calendar.getInstance(zone);
        occupied.setTimeInMillis(firstDay);
        JSONObject tracked = object("id", id(), "finished", secondDay - 1);
        ids.add(tracked.optString("id"));
        database.save("history", tracked);
        assertTrue(database.hasHistoryOnDay(firstDay));
        assertFalse(database.hasHistoryOnDay(secondDay));

        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                a.manualHistoryDraft = draft(secondDay);
                a.show("manualHistory");
                assertTrue(find(a.content, "manual_date").performClick());
                DatePickerDialog picker = (DatePickerDialog) a.activeDialog;
                picker.updateDate(
                        occupied.get(Calendar.YEAR),
                        occupied.get(Calendar.MONTH),
                        occupied.get(Calendar.DAY_OF_MONTH));
                picker.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> {
                assertNotNull(find(root(a), a.s("manual_date_taken")));
                assertEquals(secondDay, a.manualHistoryDraft.optLong("finished"));
                click(a, "manual_reselect_date");
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> {
                DatePickerDialog picker = (DatePickerDialog) a.activeDialog;
                picker.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> {
                assertEquals(secondDay, a.manualHistoryDraft.optLong("finished"));
                JSONObject lateRecord = object("id", id(), "finished", secondDay + 60000);
                ids.add(lateRecord.optString("id"));
                database.save("history", lateRecord);
                click(a, "manual_save");
                assertNotNull(find(root(a), a.s("manual_date_taken")));
                assertNotNull(a.manualHistoryDraft);
                assertNull(find("history", a.manualHistoryDraft.optString("id")));
                assertNotNull(find("history", lateRecord.optString("id")));
            });
        }
    }

    @Test
    public void backdatedRecordUsesWorkoutDateForReportsAndSortsByFinishedTime() {
        TimeZone zone = TimeZone.getTimeZone("Asia/Shanghai");
        long week = ReportEngine.weekStart(now, zone);
        long finished = ReportEngine.shiftDays(week, -7, zone) + 5 * 60000;
        JSONObject draft = draft(finished);
        put(draft, "duration", "10");
        JSONObject session = ManualHistory.build(draft, "Past workout", now);
        database.save("history", session);
        JSONObject newer = object("id", id(), "finished", finished + 86400000L);
        List<JSONObject> sorted = HistoryReader.sorted(new JSONArray().put(session).put(newer));
        assertEquals(newer.optString("id"), sorted.get(0).optString("id"));
        ReportEngine.Summary report =
                ReportEngine.aggregate(
                        HistoryReader.read(new JSONArray().put(session)),
                        finished - 5 * 60000,
                        week,
                        zone);
        assertEquals(1, report.sessions);
        assertEquals(2, report.sets);
        assertEquals(42.25 * 12 + 40 * 9, report.volume, 0);
        assertEquals(2, report.daily[0]);
        assertEquals(10 * 60000L, report.duration);
        assertEquals(
                0,
                ReportEngine.aggregate(
                                HistoryReader.read(new JSONArray().put(session)), week, now, zone)
                        .sessions);
    }

    @Test
    public void savingAndCorrectingHistoryDoesNotModifyOtherDocuments() {
        JSONObject plan = object("id", id(), "name", "Manual isolation test");
        JSONObject active = object("id", id(), "name", "Independent active snapshot");
        JSONObject checkin = object("id", id(), "day", "manual-test");
        for (JSONObject doc : new JSONObject[] {plan, active, checkin})
            ids.add(doc.optString("id"));
        database.save("plan", plan);
        database.save("active", active);
        database.save("checkin", checkin);
        JSONObject session = ManualHistory.build(draft(now - 3600000), "Manual test", now);
        database.save("history", session);
        JSONObject corrected =
                TrainingFeedback.record(session, 0, 0, 45, 11, true, "appropriate", "unknown", now);
        database.saveSessionEdit("history", session, corrected, false);
        assertEquals(plan.toString(), find("plan", plan.optString("id")).toString());
        assertEquals(active.toString(), find("active", active.optString("id")).toString());
        assertEquals(checkin.toString(), find("checkin", checkin.optString("id")).toString());
        database.close();
        database = new Store(context);
        JSONObject saved = find("history", session.optString("id"));
        assertEquals(
                45,
                saved.optJSONArray("rows")
                        .optJSONObject(0)
                        .optJSONArray("logs")
                        .optJSONObject(0)
                        .optDouble("weight"),
                0);
        assertTrue(saved.optJSONArray("rows").optJSONObject(0).optBoolean("actualOnly"));
        database.delete(session.optString("id"));
        assertNull(find("history", session.optString("id")));
        assertNotNull(find("active", active.optString("id")));
    }

    @Test
    public void coachIncludesManualActualsAndMarksMissingTargets() {
        JSONObject session = ManualHistory.build(draft(now - 3600000), "Manual coach test", now);
        database.save("history", session);
        CoachContextBuilder.BuiltContext built =
                CoachContextBuilder.build(
                        CoachReader.loadSnapshot(database), now, TimeZone.getDefault(), "zh");
        assertTrue(built.promptBlock.contains("Manual coach test"));
        assertTrue(built.promptBlock.contains("not_recorded"));
        assertTrue(built.promptBlock.contains("\"met_sets\":null"));
    }

    private JSONObject find(String kind, String target) {
        JSONArray values = database.list(kind);
        for (int i = 0; i < values.length(); i++)
            if (target.equals(values.optJSONObject(i).optString("id")))
                return values.optJSONObject(i);
        return null;
    }

    private View find(View root, String text) {
        if (text.equals(root.getTag())
                || text.equals(root.getContentDescription())
                || root instanceof TextView
                        && (text.contentEquals(((TextView) root).getText())
                                || ("← " + text).contentEquals(((TextView) root).getText())))
            return root;
        if (root instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
                View match = find(((ViewGroup) root).getChildAt(i), text);
                if (match != null) return match;
            }
        return null;
    }

    private View root(MainActivity activity) {
        return activity.activeDialog != null && activity.activeDialog.isShowing()
                ? activity.activeDialog.getWindow().getDecorView()
                : activity.getWindow().getDecorView();
    }

    private void click(MainActivity activity, String key) {
        View button = find(root(activity), activity.s(key));
        assertNotNull(key, button);
        assertTrue(button.performClick());
    }

    private <T extends View> T first(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                T result = first(((ViewGroup) view).getChildAt(i), type);
                if (result != null) return result;
            }
        return null;
    }

    @Test
    public void entryCreatesDraftThenSavesMultipleSetsToEditableHistory() {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                a.show("history");
                click(a, "manual_add");
                assertEquals("manualHistory", a.page);
                ids.add(a.manualHistoryDraft.optString("id"));
                long finished = now - 86400000L;
                while (database.hasHistoryOnDay(finished)) finished -= 86400000L;
                put(a.manualHistoryDraft, "finished", finished);
                ((EditText) find(a.content, "manual_name")).setText("UI manual history test");
                ((EditText) find(a.content, "manual_duration")).setText("35");
                click(a, "add_exercise");
                first(root(a), EditText.class).setText(a.s("ex0"));
                ListView list = first(root(a), ListView.class);
                assertEquals(1, list.getAdapter().getCount());
                list.performItemClick(null, 0, 0);
                assertEquals("UI manual history test", a.manualHistoryDraft.optString("name"));
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> click(a, "manual_add_set"));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> {
                ((EditText) find(root(a), "manual_weight")).setText("42.25");
                ((EditText) find(root(a), "manual_reps")).setText("12");
                ((Spinner) find(root(a), "manual_effort")).setSelection(2);
                click(a, "save");
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> click(a, "manual_add_set"));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> {
                ((EditText) find(root(a), "manual_weight")).setText("40");
                ((EditText) find(root(a), "manual_reps")).setText("9");
                click(a, "save");
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> {
                String sessionId = a.manualHistoryDraft.optString("id");
                assertNull(find("history", sessionId));
                assertEquals(
                        a.manualHistoryDraft.toString(),
                        2,
                        a.manualHistoryDraft
                                .optJSONArray("rows")
                                .optJSONObject(0)
                                .optJSONArray("logs")
                                .length());
                ManualHistory.build(a.manualHistoryDraft, "UI test", System.currentTimeMillis());
                click(a, "manual_save");
                assertEquals(
                        a.manualHistoryDraft == null ? "saved" : a.manualHistoryDraft.toString(),
                        "historyDetail",
                        a.page);
                assertNull(a.manualHistoryDraft);
                JSONObject record = find("history", sessionId);
                assertNotNull(record);
                assertEquals("UI manual history test", record.optString("name"));
                assertEquals(
                        2,
                        record.optJSONArray("rows").optJSONObject(0).optJSONArray("logs").length());
                assertEquals(35 * 60000L, record.optLong("finished") - record.optLong("started"));
                assertNotNull(find(a.content, a.s("manual_source")));
                assertNotNull(find(a.content, a.s("manual_actual_only")));
                assertEquals(
                        "42.25",
                        new RecordsScreen(a)
                                .actualWeight(
                                        record.optJSONArray("rows")
                                                .optJSONObject(0)
                                                .optJSONArray("logs")
                                                .optJSONObject(0)));
            });
        }
    }

    @Test
    public void cancelledSetAndNavigationKeepDraftUntilExplicitDiscard() {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                a.manualHistoryDraft = draft(now - 60000);
                a.show("manualHistory");
                ((EditText) find(a.content, "manual_name")).setText("Draft survives recreation");
                click(a, "manual_add_set");
                ((EditText) find(root(a), "manual_weight")).setText("99");
                click(a, "cancel");
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> {
                assertEquals(
                        2,
                        a.manualHistoryDraft
                                .optJSONArray("rows")
                                .optJSONObject(0)
                                .optJSONArray("logs")
                                .length());
                click(a, "back_records");
                assertEquals("history", a.page);
                click(a, "manual_add");
            });
            scenario.recreate();
            scenario.onActivity(a -> {
                assertEquals("manualHistory", a.page);
                assertEquals("Draft survives recreation", a.manualHistoryDraft.optString("name"));
                assertNull(find("history", a.manualHistoryDraft.optString("id")));
                click(a, "manual_discard");
                assertNotNull(find(root(a), a.s("manual_discard_confirm")));
                click(a, "cancel");
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> {
                assertEquals("Draft survives recreation", a.manualHistoryDraft.optString("name"));
                click(a, "manual_discard");
                click(a, "manual_discard");
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> {
                assertNull(a.manualHistoryDraft);
                assertEquals("history", a.page);
            });
        }
    }

    @Test
    public void invalidFinalSavePreservesDraftAndWritesNothing() {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                a.show("manualHistory");
                String sessionId = a.manualHistoryDraft.optString("id");
                click(a, "manual_save");
                assertEquals("manualHistory", a.page);
                assertNull(find("history", sessionId));
                assertNotNull(a.manualHistoryDraft);
                a.manualHistoryDraft = draft(now + 600000);
                a.show("manualHistory");
                click(a, "manual_save");
                assertNull(find("history", a.manualHistoryDraft.optString("id")));
            });
        }
    }

    @Test
    public void bilingualHeaderAndDraftScreensHaveVisibleSaveAction() throws Exception {
        for (String lang : new String[] {"zh", "en"}) {
            context.getSharedPreferences("preferences", 0)
                    .edit()
                    .putString("language", lang)
                    .commit();
            try (ActivityScenario<MainActivity> scenario =
                    ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(a -> a.show("history"));
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(a -> {
                    View add = find(a.content, a.s("manual_add"));
                    Rect bounds = new Rect();
                    assertNotNull(add);
                    assertTrue(add.getGlobalVisibleRect(bounds));
                    assertTrue(bounds.width() >= a.dp(48));
                    assertTrue(add.getLeft() > a.content.getWidth() / 2);
                    add.performClick();
                    a.manualHistoryDraft = draft(now - 86400000L);
                    a.show("manualHistory");
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(a -> {
                    View save = find(a.getWindow().getDecorView(), a.s("manual_save"));
                    View discard = find(a.getWindow().getDecorView(), a.s("manual_discard"));
                    Rect bounds = new Rect();
                    assertNotNull(save);
                    assertNotNull(discard);
                    assertTrue(save.getGlobalVisibleRect(bounds));
                    assertTrue(bounds.height() >= a.dp(48));
                    Rect discardBounds = new Rect();
                    assertTrue(discard.getGlobalVisibleRect(discardBounds));
                    assertTrue(discardBounds.height() >= a.dp(48));
                    assertEquals(bounds.centerY(), discardBounds.centerY());
                    assertTrue(discardBounds.right < bounds.left);
                    ViewGroup actions = a.findViewById(R.id.page_actions);
                    assertEquals(1, actions.getChildCount());
                    assertEquals(actions.getChildAt(0), save.getParent());
                    assertEquals(save.getParent(), discard.getParent());
                    assertNull(find(a.content, "×"));
                    assertNotNull(find(a.content, a.s("add_exercise")));
                });
                screenshot("manual-history-" + lang);
                scenario.onActivity(a ->
                        ((ScrollView) a.findViewById(R.id.scroller)).fullScroll(View.FOCUS_DOWN));
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                screenshot("manual-history-sets-" + lang);
            }
        }
    }

    private void screenshot(String name) throws Exception {
        android.os.SystemClock.sleep(350);
        Bitmap screenshot =
                InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        try (FileOutputStream output =
                new FileOutputStream(new File(context.getExternalFilesDir(null), name + ".png"))) {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
        } finally {
            screenshot.recycle();
        }
    }

    @Test
    public void multipleExercisesRetainCustomIdentityAndRespectSetLimits() {
        JSONObject draft = draft(now - 60000);
        JSONObject custom =
                ManualHistory.row(
                        object(
                                "id",
                                "manual-custom-test",
                                "name",
                                "Custom movement",
                                "part",
                                "core"));
        for (int i = 0; i < ManualHistory.MAX_SETS; i++)
            custom.optJSONArray("logs").put(ManualHistory.log("0", "15", "unknown", now));
        draft.optJSONArray("rows").put(custom);
        JSONObject built = ManualHistory.build(draft, "Manual multi-exercise", now);
        assertEquals(2, built.optJSONArray("rows").length());
        assertEquals(
                "manual-custom-test",
                built.optJSONArray("rows").optJSONObject(1).optString("exerciseId"));
        assertEquals(20, built.optJSONArray("rows").optJSONObject(1).optJSONArray("logs").length());
        custom.optJSONArray("logs").put(ManualHistory.log("0", "10", "unknown", now));
        invalid(draft, "manual_set_limit");
        JSONArray tooMany = new JSONArray();
        for (int i = 0; i <= ManualHistory.MAX_EXERCISES; i++)
            tooMany.put(draft.optJSONArray("rows").optJSONObject(0));
        put(draft, "rows", tooMany);
        invalid(draft, "manual_exercise_limit");
    }
}
