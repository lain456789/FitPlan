package com.example.fitplan;

import static com.example.fitplan.Store.id;
import static com.example.fitplan.Store.object;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
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
public class V2BehaviorTest {
    Context context;
    Store database;
    String originalLanguage;
    final List<String> ids = new ArrayList<>();

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

    void save(String kind, JSONObject value) {
        database.save(kind, value);
        ids.add(value.optString("id"));
    }

    View find(View root, String text) {
        if ((root instanceof TextView && ((TextView) root).getText().toString().equals(text))
                || text.contentEquals(
                        root.getContentDescription() == null ? "" : root.getContentDescription()))
            return root;
        if (root instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
                View v = find(((ViewGroup) root).getChildAt(i), text);
                if (v != null) return v;
            }
        return null;
    }

    void click(MainActivity a, String text) {
        View root =
                a.activeDialog != null && a.activeDialog.isShowing()
                        ? a.activeDialog.getWindow().getDecorView()
                        : a.getWindow().getDecorView();
        View v = find(root, text);
        assertNotNull("Missing control: " + text, v);
        assertTrue(v.performClick());
    }

    <T extends View> List<T> collect(View root, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (type.isInstance(root)) found.add(type.cast(root));
        if (root instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++)
                found.addAll(collect(((ViewGroup) root).getChildAt(i), type));
        return found;
    }

    void screenshot(String name) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        android.os.SystemClock.sleep(500);
        Bitmap b = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(b);
        try (FileOutputStream out =
                new FileOutputStream(
                        new File(
                                context.getExternalFilesDir(null),
                                name + "-api" + android.os.Build.VERSION.SDK_INT + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            throw new AssertionError(e);
        } finally {
            b.recycle();
        }
    }

    void waitReport(ActivityScenario<MainActivity> scenario) {
        long until = System.currentTimeMillis() + 8000;
        boolean[] ready = {false};
        while (System.currentTimeMillis() < until) {
            scenario.onActivity(a -> {
                View v = find(a.content, a.s("loading"));
                ready[0] = v == null || v.getVisibility() == View.GONE;
                assertNull(find(a.content, a.s("read_error")));
            });
            if (ready[0]) return;
            android.os.SystemClock.sleep(50);
        }
        fail("Report timed out");
    }

    @Test
    public void languageDraftWeekAndLegacyDisplaySurviveRecreation() {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                List<EditText> edits = collect(a.content, EditText.class);
                edits.get(0).setText("65");
                edits.get(1).setText("自定义备注 unchanged");
                collect(a.content, CheckBox.class).get(0).setChecked(true);
                a.show("profile");
                a.setLanguage("en");
                assertEquals("Profile", a.s("profile"));
                assertEquals(
                        "Barbell bench press",
                        a.exerciseName(object("exerciseId", "builtin-0", "name", "杠铃卧推")));
                assertEquals(
                        "用户动作", a.exerciseName(object("exerciseId", "custom", "name", "用户动作")));
                assertTrue(
                        a.reason(object("reason", "保持最近实际完成重量；今日恢复状态较差，减少一组，可手动调整"))
                                .startsWith("Keep"));
                assertEquals("profile", a.page);
                a.show("home");
                assertEquals("65", collect(a.content, EditText.class).get(0).getText().toString());
                assertTrue(collect(a.content, CheckBox.class).get(0).isChecked());
                a.selectedWeek =
                        ReportEngine.shiftDays(
                                ReportEngine.weekStart(
                                        System.currentTimeMillis(), TimeZone.getDefault()),
                                -7,
                                TimeZone.getDefault());
                a.show("weekly");
                a.setLanguage("zh");
                assertEquals("weekly", a.page);
            });
            scenario.recreate();
            scenario.onActivity(a -> {
                assertEquals("weekly", a.page);
                assertEquals(
                        ReportEngine.shiftDays(
                                ReportEngine.weekStart(
                                        System.currentTimeMillis(), TimeZone.getDefault()),
                                -7,
                                TimeZone.getDefault()),
                        a.selectedWeek);
                a.show("home");
                assertEquals("65", collect(a.content, EditText.class).get(0).getText().toString());
                assertEquals(
                        "自定义备注 unchanged",
                        collect(a.content, EditText.class).get(1).getText().toString());
            });
            scenario.onActivity(a -> {
                a.show("profile");
                a.setLanguage("en");
            });
        }
        try (ActivityScenario<MainActivity> next = ActivityScenario.launch(MainActivity.class)) {
            next.onActivity(a -> assertEquals("en", a.language()));
        }
    }

    @Test
    public void workoutRoundTripInEnglishPreservesSnapshots() {
        org.junit.Assume.assumeTrue(database.first("active") == null);
        JSONObject plan =
                object(
                        "name",
                        "兼容测试计划",
                        "rows",
                        new JSONArray()
                                .put(
                                        object(
                                                "exerciseId",
                                                "builtin-0",
                                                "name",
                                                "杠铃卧推",
                                                "part",
                                                "胸",
                                                "weight",
                                                20,
                                                "sets",
                                                1,
                                                "reps",
                                                10,
                                                "reason",
                                                "保持最近实际完成重量")));
        save("plan", plan);
        final String[] activeId = {null};
        final long[] rest = {0};
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                a.setLanguage("en");
                a.openPlan(plan);
                assertNotNull(find(a.content, "Barbell bench press"));
                click(a, "Start this plan");
                JSONObject active = database.first("active");
                assertNotNull(active);
                activeId[0] = active.optString("id");
                ids.add(activeId[0]);
                click(a, "Record set 1");
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> click(a, "Save"));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> {
                rest[0] = database.first("active").optLong("restUntil");
                assertTrue(rest[0] > System.currentTimeMillis());
                a.setLanguage("zh");
                assertEquals("workout", a.page);
                assertEquals(rest[0], database.first("active").optLong("restUntil"));
                a.setLanguage("en");
                click(a, "Finish and save workout");
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> click(a, "Confirm"));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> {
                assertNull(database.first("active"));
                JSONObject finished = a.find("history", activeId[0]);
                assertNotNull(finished);
                assertTrue(
                        finished.optJSONArray("rows")
                                .optJSONObject(0)
                                .optJSONArray("logs")
                                .optJSONObject(0)
                                .optBoolean("done"));
                assertFalse(
                        a.find("plan", plan.optString("id"))
                                .optJSONArray("rows")
                                .optJSONObject(0)
                                .has("logs"));
                a.openHistory(finished);
                assertNotNull(find(a.content, "Barbell bench press"));
                a.getOnBackPressedDispatcher().onBackPressed();
                assertEquals("history", a.page);
            });
        }
    }

    @Test
    public void reportGraphsBilingualAndFixtureCleanup() {
        long now = System.currentTimeMillis();
        TimeZone zone = TimeZone.getDefault();
        for (int i = 0; i < 8; i++) {
            long end = ReportEngine.shiftDays(now, -7 * i, zone) - 60000;
            JSONArray logs =
                    new JSONArray()
                            .put(object("done", true, "weight", 40 - i * 2, "reps", 10))
                            .put(object("done", true, "weight", 0, "reps", 12))
                            .put(object("done", false, "weight", 999, "reps", 99));
            save(
                    "history",
                    object(
                            "name",
                            "测试夹具 / test fixture",
                            "started",
                            end - 1800000,
                            "finished",
                            end,
                            "rows",
                            new JSONArray()
                                    .put(
                                            object(
                                                    "exerciseId",
                                                    "test-weekly-id",
                                                    "name",
                                                    "测试动作 / Test exercise",
                                                    "part",
                                                    "背",
                                                    "logs",
                                                    logs))));
        }
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> a.show("weekly"));
            waitReport(scenario);
            scenario.onActivity(a -> {
                assertNotNull(find(a.content, a.s("daily_chart")));
                assertTrue(collect(a.content, TrendChart.class).size() >= 3);
                collect(a.content, TrendChart.class).get(0).performClick();
            });
            screenshot("weekly-zh-fixture");
            scenario.onActivity(a -> a.setLanguage("en"));
            waitReport(scenario);
            screenshot("weekly-en-fixture");
            scenario.onActivity(a -> {
                View target = find(a.content, a.s("daily_chart"));
                ScrollView scroll = a.findViewById(R.id.scroller);
                android.graphics.Rect rect =
                        new android.graphics.Rect(0, 0, target.getWidth(), target.getHeight());
                scroll.offsetDescendantRectToMyCoords(target, rect);
                scroll.scrollTo(0, rect.top - a.dp(12));
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            screenshot("charts-en-fixture");
            scenario.onActivity(a -> {
                assertEquals("weekly", a.page);
                assertNotNull(find(a.content, "Eight-week trend"));
                a.show("library");
                assertNotNull(find(a.content, "Barbell bench press"));
            });
            screenshot("library-en");
            scenario.onActivity(a -> a.show("home"));
            waitReport(scenario);
            screenshot("home-en-fixture");
        }
    }
}
