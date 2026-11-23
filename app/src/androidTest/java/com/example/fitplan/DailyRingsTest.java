package com.example.fitplan;

import static com.example.fitplan.Store.object;
import static com.example.fitplan.Store.put;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

@RunWith(AndroidJUnit4.class)
public class DailyRingsTest {
    private final long now = System.currentTimeMillis();
    private final TimeZone zone = TimeZone.getDefault();

    private String day(long time) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(zone);
        return f.format(new Date(time));
    }

    private JSONObject plan(String date, double weight) {
        return object(
                "id",
                Store.id(),
                "name",
                "[Test] Ring target",
                "dailyPlanDay",
                date,
                "minutes",
                30,
                "rows",
                new JSONArray()
                        .put(
                                object(
                                        "exerciseId",
                                        "builtin-0",
                                        "part",
                                        "chest",
                                        "sets",
                                        3,
                                        "reps",
                                        10,
                                        "weight",
                                        weight,
                                        "userConfirmed",
                                        true)));
    }

    private ReportEngine.Targets read(JSONObject plan) {
        return DailyTargetReader.read(new JSONArray().put(plan), now, zone);
    }

    @Test
    public void onlyTodaysTemplateSuppliesTargetsIncludingRenamedLegacyPlans() {
        assertFalse(read(plan(day(ReportEngine.shiftDays(now, -1, zone)), 40)).hasPlan);
        JSONObject p = plan(day(now), 40);
        put(p, "name", "Renamed by user");
        p.remove("dailyPlanDay");
        put(p, "checkin", object("day", day(now)));
        ReportEngine.Targets target = read(p);
        assertTrue(target.hasPlan);
        assertEquals(3, target.sets, 0);
        assertEquals(1200, target.volume, 0);
        assertEquals(30, target.minutes, 0);
        p.remove("checkin");
        assertFalse(read(p).hasPlan);
    }

    @Test
    public void pendingWeightsAndBodyweightNeverInventVolumeTargets() {
        JSONObject p = plan(day(now), 0);
        assertEquals(0, read(p).volume, 0);
        assertFalse(read(p).volumePending);
        JSONObject row = p.optJSONArray("rows").optJSONObject(0);
        put(row, "userConfirmed", false);
        put(row, "needsWeightConfirmation", true);
        assertTrue(read(p).volumePending);
        assertEquals(3, read(p).sets, 0);
        PlanEdits.confirm(row, 20, 4, 8, now);
        assertEquals(640, read(p).volume, 0);
        assertEquals(4, read(p).sets, 0);
        put(row, "sets", -3);
        assertEquals(0, read(p).sets, 0);
        assertEquals(0, read(p).volume, 0);
    }

    @Test
    public void latestSavedTemplateIsUsedAndOldDatesAreIgnored() {
        JSONObject newer = plan(day(now), 20), older = plan(day(now), 40);
        ReportEngine.Targets target =
                DailyTargetReader.read(
                        new JSONArray()
                                .put(plan(day(ReportEngine.shiftDays(now, 1, zone)), 100))
                                .put(newer)
                                .put(older),
                        now,
                        zone);
        assertEquals(newer.optString("id"), target.planId);
        assertEquals(600, target.volume, 0);
        assertFalse(DailyTargetReader.read(new JSONArray(), now, zone).hasPlan);
    }

    private void ready(ActivityScenario<MainActivity> scenario) {
        for (int i = 0; i < 100; i++) {
            boolean[] ready = {false};
            scenario.onActivity(a -> ready[0] = a.content.findViewWithTag("today_rings") != null);
            if (ready[0]) return;
            android.os.SystemClock.sleep(50);
        }
        fail("Ring report timed out");
    }

    private void screenshot(Context context, String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        android.os.SystemClock.sleep(3500);
        Bitmap image =
                InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        try (FileOutputStream out =
                new FileOutputStream(new File(context.getExternalFilesDir(null), name + ".png"))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            image.recycle();
        }
    }

    @Test
    public void bilingualRingsRefreshTargetsWithoutModifyingHistory() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String language =
                context.getSharedPreferences("preferences", 0).getString("language", "zh");
        JSONObject p = plan(day(now), 40);
        p.optJSONArray("rows")
                .put(
                        object(
                                "exerciseId",
                                "builtin-1",
                                "part",
                                "chest",
                                "sets",
                                3,
                                "reps",
                                10,
                                "weight",
                                20,
                                "userConfirmed",
                                true));
        JSONArray logs = new JSONArray();
        for (int i = 0; i < 4; i++) logs.put(object("done", true, "weight", 30, "reps", 10));
        JSONObject history =
                object(
                        "id",
                        Store.id(),
                        "name",
                        "[Test] Ring preview",
                        "started",
                        now - 22 * 60000,
                        "finished",
                        now,
                        "rows",
                        new JSONArray()
                                .put(
                                        object(
                                                "exerciseId",
                                                "builtin-0",
                                                "part",
                                                "chest",
                                                "logs",
                                                logs)));
        try (Store store = new Store(context)) {
            store.save("plan", p);
            store.save("history", history);
            String before = history.toString();
            try (ActivityScenario<MainActivity> scenario =
                    ActivityScenario.launch(MainActivity.class)) {
                for (String lang : new String[] {"zh", "en"}) {
                    scenario.onActivity(a -> {
                        a.setLanguage(lang);
                        a.show("home");
                    });
                    ready(scenario);
                    scenario.onActivity(a -> {
                        TextView goal = a.content.findViewWithTag("today_sets_target");
                        assertEquals(
                                a.s("ring_goal", "6", a.s("ring_sets_unit"), a.num(4.0 / 6 * 100)),
                                goal.getText().toString());
                        ActivityRingsView rings = a.content.findViewWithTag("today_rings");
                        assertTrue(
                                rings.getContentDescription()
                                        .toString()
                                        .contains(a.s("volume_unit")));
                        assertNotNull(a.content.findViewWithTag("ring_plan_action"));
                    });
                    screenshot(context, "v064-rings-" + lang);
                }
                put(p.optJSONArray("rows").optJSONObject(0), "sets", 5);
                store.save("plan", p);
                scenario.onActivity(a -> a.show("home"));
                ready(scenario);
                scenario.onActivity(a -> {
                    TextView goal = a.content.findViewWithTag("today_sets_target");
                    assertEquals(
                            a.s("ring_goal", "8", a.s("ring_sets_unit"), "50"),
                            goal.getText().toString());
                    a.content.findViewWithTag("ring_plan_action").performClick();
                    assertEquals("planDetail", a.page);
                    assertEquals(before, a.find("history", history.optString("id")).toString());
                });
                store.delete(p.optString("id"));
                scenario.onActivity(a -> a.show("home"));
                ready(scenario);
                scenario.onActivity(a -> {
                    TextView goal = a.content.findViewWithTag("today_sets_target");
                    assertEquals(a.s("ring_no_target"), goal.getText().toString());
                });
                screenshot(context, "v064-rings-no-target");
            } finally {
                store.delete(p.optString("id"));
                store.delete(history.optString("id"));
                context.getSharedPreferences("preferences", 0)
                        .edit()
                        .putString("language", language)
                        .commit();
            }
        }
    }
}
