package com.example.fitplan;

import static com.example.fitplan.Store.copy;
import static com.example.fitplan.Store.id;
import static com.example.fitplan.Store.object;
import static com.example.fitplan.Store.put;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.database.sqlite.SQLiteException;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class DailyPlanUpdateTest {
    private Context context;
    private Store database;
    private final Set<String> days = new HashSet<>();
    private final List<String> extraIds = new ArrayList<>();

    @Before
    public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        database = new Store(context);
    }

    @After
    public void cleanup() {
        for (String kind : new String[] {"checkin", "plan", "active", "history"}) {
            JSONArray docs = database.list(kind);
            for (int i = 0; i < docs.length(); i++) {
                JSONObject doc = docs.optJSONObject(i), check = doc.optJSONObject("checkin");
                if (days.contains(doc.optString("day"))
                        || days.contains(doc.optString("dailyPlanDay"))
                        || (check != null && days.contains(check.optString("day"))))
                    database.delete(doc.optString("id"));
            }
        }
        for (String id : extraIds) database.delete(id);
        database.close();
    }

    private String day() {
        String day = "daily-test-" + id();
        days.add(day);
        return day;
    }

    private JSONObject check(String day, int minutes) {
        return object(
                "day",
                day,
                "created",
                100,
                "minutes",
                minutes,
                "volume",
                1,
                "sleep",
                0,
                "food",
                0,
                "soreness",
                0,
                "parts",
                new JSONArray().put("chest").put("back"));
    }

    private JSONObject plan(String name, String exercise, int sets) {
        return object(
                "name",
                name,
                "autoName",
                true,
                "created",
                100,
                "rows",
                new JSONArray()
                        .put(
                                object(
                                        "exerciseId",
                                        exercise,
                                        "weight",
                                        20,
                                        "sets",
                                        sets,
                                        "reps",
                                        10)));
    }

    private void save(String kind, JSONObject doc) {
        database.save(kind, doc);
        extraIds.add(doc.optString("id"));
    }

    private JSONObject find(Store store, String kind, String id) {
        JSONArray docs = store.list(kind);
        for (int i = 0; i < docs.length(); i++)
            if (id.equals(docs.optJSONObject(i).optString("id"))) return docs.optJSONObject(i);
        return null;
    }

    private int count(String kind, String day) {
        int n = 0;
        JSONArray docs = database.list(kind);
        for (int i = 0; i < docs.length(); i++) {
            JSONObject doc = docs.optJSONObject(i), check = doc.optJSONObject("checkin");
            if (day.equals(doc.optString("day"))
                    || day.equals(doc.optString("dailyPlanDay"))
                    || (check != null && day.equals(check.optString("day")))) n++;
        }
        return n;
    }

    @Test
    public void repeatedGenerationReusesIdsAndSurvivesDatabaseReopen() {
        String day = day();
        JSONObject firstCheck = check(day, 30), firstPlan = plan("first", "builtin-0", 3);
        database.saveDailyPlan(firstCheck, firstPlan);
        String checkId = firstCheck.optString("id"), planId = firstPlan.optString("id");
        JSONObject nextCheck = check(day, 60), nextPlan = plan("second", "builtin-6", 2);
        put(nextCheck, "created", 200);
        put(nextPlan, "created", 200);
        database.saveDailyPlan(nextCheck, nextPlan);
        assertEquals(checkId, nextCheck.optString("id"));
        assertEquals(planId, nextPlan.optString("id"));
        assertEquals(1, count("checkin", day));
        assertEquals(1, count("plan", day));
        try (Store reopened = new Store(context)) {
            JSONObject stored = find(reopened, "plan", planId);
            assertNotNull(stored);
            assertEquals("second", stored.optString("name"));
            assertEquals(
                    "builtin-6",
                    stored.optJSONArray("rows").optJSONObject(0).optString("exerciseId"));
            assertEquals(2, stored.optJSONArray("rows").optJSONObject(0).optInt("sets"));
            assertEquals(60, stored.optJSONObject("checkin").optInt("minutes"));
            assertEquals(checkId, stored.optJSONObject("checkin").optString("id"));
            assertEquals(100, stored.optLong("created"));
            assertEquals(60, find(reopened, "checkin", checkId).optInt("minutes"));
        }
    }

    @Test
    public void legacyDuplicatesMergeWithoutChangingManualPlansOrSnapshots() {
        String day = day(), past = day();
        JSONObject older = plan("older", "builtin-0", 3), newer = plan("newer", "builtin-6", 2);
        put(older, "checkin", check(day, 30));
        put(newer, "checkin", check(day, 40));
        save("plan", older);
        save("plan", newer);
        save("checkin", check(day, 30));
        save("checkin", check(day, 40));
        JSONObject manual = plan("same name as daily", "builtin-9", 4);
        save("plan", manual);
        String manualBefore = manual.toString();
        JSONObject pastPlan = plan("past", "builtin-0", 1);
        put(pastPlan, "checkin", check(past, 20));
        save("plan", pastPlan);
        String pastBefore = pastPlan.toString();
        JSONObject history = copy(older);
        put(history, "id", id());
        put(history, "finished", 1000);
        save("history", history);
        String historyBefore = history.toString();
        JSONObject active = database.first("active");
        if (active == null) {
            active = copy(older);
            put(active, "id", id());
            put(active, "started", 900);
            save("active", active);
        }
        String activeId = active.optString("id"), activeBefore = active.toString();
        JSONObject replacement = plan("replacement", "builtin-3", 1);
        database.saveDailyPlan(check(day, 25), replacement);
        assertTrue(
                replacement.optString("id").equals(older.optString("id"))
                        || replacement.optString("id").equals(newer.optString("id")));
        assertEquals(1, count("plan", day));
        assertEquals(1, count("checkin", day));
        assertEquals(manualBefore, find(database, "plan", manual.optString("id")).toString());
        assertEquals(pastBefore, find(database, "plan", pastPlan.optString("id")).toString());
        assertEquals(historyBefore, find(database, "history", history.optString("id")).toString());
        assertEquals(activeBefore, find(database, "active", activeId).toString());
    }

    @Test
    public void renamedDailyPlanKeepsNameWhenItsContentsAreReplaced() {
        String day = day();
        JSONObject renamed = plan("my renamed daily plan", "builtin-0", 3);
        put(renamed, "autoName", false);
        put(renamed, "checkin", check(day, 30));
        save("plan", renamed);
        JSONObject replacement = plan("automatic new title", "builtin-6", 2);
        database.saveDailyPlan(check(day, 60), replacement);
        assertEquals(renamed.optString("id"), replacement.optString("id"));
        assertFalse(replacement.optBoolean("autoName"));
        assertEquals("my renamed daily plan", replacement.optString("name"));
        assertEquals(
                "builtin-6",
                replacement.optJSONArray("rows").optJSONObject(0).optString("exerciseId"));
    }

    @Test
    public void noPlanStatusRemovesOnlyThatDaysAutomaticTemplate() {
        String day = day();
        JSONObject check = check(day, 30), first = plan("daily", "builtin-0", 3);
        database.saveDailyPlan(check, first);
        JSONObject manual = plan("manual", "builtin-6", 2);
        save("plan", manual);
        String before = manual.toString();
        JSONObject history = copy(first);
        put(history, "id", id());
        save("history", history);
        String historyBefore = history.toString();
        JSONObject rest = check(day, 30);
        put(rest, "soreness", 2);
        database.saveDailyPlan(rest, null);
        assertEquals(0, count("plan", day));
        assertEquals(1, count("checkin", day));
        assertEquals(check.optString("id"), rest.optString("id"));
        assertEquals(2, find(database, "checkin", rest.optString("id")).optInt("soreness"));
        assertEquals(before, find(database, "plan", manual.optString("id")).toString());
        assertEquals(historyBefore, find(database, "history", history.optString("id")).toString());
    }

    @Test
    public void failedPlanWriteRollsBackCheckinAndLeavesOldPlanIntact() {
        String day = day();
        JSONObject initial = check(day, 30), original = plan("original", "builtin-0", 3);
        database.saveDailyPlan(initial, original);
        String checkBefore = find(database, "checkin", initial.optString("id")).toString(),
                planBefore = find(database, "plan", original.optString("id")).toString();
        String trigger = "daily_failure_" + id().replace("-", "");
        database.getWritableDatabase()
                .execSQL(
                        "CREATE TEMP TRIGGER "
                                + trigger
                                + " BEFORE INSERT ON documents WHEN NEW.kind='plan' AND NEW.id='"
                                + original.optString("id")
                                + "' BEGIN SELECT RAISE(ABORT,'test write failure'); END");
        try {
            try {
                database.saveDailyPlan(check(day, 60), plan("replacement", "builtin-6", 2));
                fail("Write failure not propagated");
            } catch (SQLiteException expected) {
                assertEquals(
                        checkBefore, find(database, "checkin", initial.optString("id")).toString());
                assertEquals(
                        planBefore, find(database, "plan", original.optString("id")).toString());
            }
        } finally {
            database.getWritableDatabase().execSQL("DROP TRIGGER " + trigger);
        }
        assertEquals(1, count("plan", day));
        assertEquals(1, count("checkin", day));
    }

    @Test
    public void foreignIdsAndInvalidDatesCannotOverwriteOtherRecords() {
        String day = day(), past = day();
        JSONObject previousCheck = check(past, 20);
        save("checkin", previousCheck);
        String before = previousCheck.toString();
        JSONObject manual = plan("manual", "builtin-9", 1);
        save("plan", manual);
        String manualBefore = manual.toString();
        JSONObject next = check(day, 30), generated = plan("daily", "builtin-0", 3);
        put(next, "id", previousCheck.optString("id"));
        put(generated, "id", manual.optString("id"));
        database.saveDailyPlan(next, generated);
        assertNotEquals(previousCheck.optString("id"), next.optString("id"));
        assertNotEquals(manual.optString("id"), generated.optString("id"));
        assertEquals(before, find(database, "checkin", previousCheck.optString("id")).toString());
        assertEquals(manualBefore, find(database, "plan", manual.optString("id")).toString());
        JSONObject mismatch = plan("mismatch", "builtin-0", 1);
        put(mismatch, "checkin", check(past, 30));
        try {
            database.saveDailyPlan(check(day, 90), mismatch);
            fail("Mismatched day accepted");
        } catch (IllegalArgumentException expected) {
            assertEquals(30, find(database, "checkin", next.optString("id")).optInt("minutes"));
        }
        try {
            database.saveDailyPlan(check("", 30), null);
            fail("Empty day accepted");
        } catch (IllegalArgumentException expected) {
            assertEquals(1, count("plan", day));
        }
    }

    @Test
    public void homeSubmissionReplacesFromLatestStateAndRestClearsStalePlan() {
        String day = day();
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                DailyPlanScreen home = new DailyPlanScreen(a);
                JSONObject first = home.submitCheckin(check(day, 30));
                String planId = first.optString("id");
                JSONObject updated = check(day, 60);
                put(updated, "parts", new JSONArray().put("back"));
                put(updated, "sleep", 2);
                JSONObject next = home.submitCheckin(updated);
                assertEquals(planId, next.optString("id"));
                assertEquals(1, count("plan", day));
                assertEquals(1, count("checkin", day));
                assertEquals(60, next.optInt("minutes"));
                JSONArray rows = next.optJSONArray("rows");
                assertTrue(rows.length() > 0);
                for (int i = 0; i < rows.length(); i++)
                    assertEquals("back", rows.optJSONObject(i).optString("part"));
                put(updated, "soreness", 2);
                assertNull(home.submitCheckin(updated));
                assertEquals(0, count("plan", day));
                assertEquals(1, count("checkin", day));
            });
        }
    }
}
