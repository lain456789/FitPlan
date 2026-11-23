package com.example.fitplan;

import static com.example.fitplan.Store.object;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.TimeZone;

@RunWith(AndroidJUnit4.class)
public class ModuleLayoutTest {
    private String originalLanguage;

    @Before
    public void rememberLanguage() {
        originalLanguage =
                InstrumentationRegistry.getInstrumentation()
                        .getTargetContext()
                        .getSharedPreferences("preferences", 0)
                        .getString("language", "zh");
    }

    @After
    public void restoreLanguage() {
        InstrumentationRegistry.getInstrumentation()
                .getTargetContext()
                .getSharedPreferences("preferences", 0)
                .edit()
                .putString("language", originalLanguage)
                .commit();
    }

    private View find(View root, String label) {
        if (label.equals(root.getTag())
                || label.equals(String.valueOf(root.getContentDescription()))
                || root instanceof TextView && label.equals(((TextView) root).getText().toString()))
            return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View match = find(group.getChildAt(i), label);
                if (match != null) return match;
            }
        }
        return null;
    }

    private void click(MainActivity a, String key) {
        View control = find(a.getWindow().getDecorView(), a.s(key));
        assertNotNull(key, control);
        assertTrue(control.performClick());
    }

    private void waitHome(ActivityScenario<MainActivity> scenario) {
        for (int i = 0; i < 100; i++) {
            boolean[] done = {false};
            scenario.onActivity(a -> {
                assertNull(find(a.content, a.s("read_error")));
                done[0] =
                        find(a.content, "today_sets") != null
                                && find(a.content, a.s("eight_weeks")) != null;
            });
            if (done[0]) return;
            android.os.SystemClock.sleep(50);
        }
        fail("Home report did not finish");
    }

    private void snapshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        android.os.SystemClock.sleep(400);
        Bitmap image =
                InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertNotNull(image);
        try (FileOutputStream file =
                new FileOutputStream(new File(context.getExternalFilesDir(null), name + ".png"))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, file);
        } finally {
            image.recycle();
        }
    }

    @Test
    public void navigationHasCorrectOwnershipAndManualDraftReturn() {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            waitHome(scenario);
            scenario.onActivity(a -> {
                assertNull(find(a.content, a.s("parts_question")));
                assertNull(find(a.content, a.s("manual_add")));
                assertEquals(View.GONE, a.findViewById(R.id.page_actions).getVisibility());
                find(a.findViewById(R.id.navigation), a.s("training")).performClick();
                assertEquals("dailyPlan", a.page);
                assertNotNull(find(a.content, a.s("parts_question")));
                click(a, "my_plans_tab");
                click(a, "manual_add");
                assertTrue(find(a.findViewById(R.id.navigation), a.s("training")).isSelected());
                Store.put(a.manualHistoryDraft, "name", "Unchanged draft");
                a.navigateBack();
                assertEquals("plans", a.page);
                click(a, "manual_add");
            });
            scenario.recreate();
            scenario.onActivity(a -> {
                assertEquals("manualHistory", a.page);
                assertEquals("Unchanged draft", a.manualHistoryDraft.optString("name"));
                a.setLanguage("en");
                click(a, "back_training");
                assertEquals("plans", a.page);
                a.show("history");
                assertNull(find(a.content, a.s("manual_add")));
                assertNull(find(a.content, a.s("weekly")));
                a.show("home");
                a.show("progress");
                assertTrue(find(a.findViewById(R.id.navigation), a.s("home")).isSelected());
            });
            scenario.recreate();
            scenario.onActivity(a -> {
                a.navigateBack();
                assertEquals("home", a.page);
                a.show("history");
                a.show("progress");
                a.navigateBack();
                assertEquals("history", a.page);
            });
        }
    }

    @Test
    public void reportRefreshesAfterManualSaveCorrectionAndDeletion() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (Store db = new Store(context);
                ActivityScenario<MainActivity> scenario =
                        ActivityScenario.launch(MainActivity.class)) {
            waitHome(scenario);
            long now = System.currentTimeMillis();
            double[] baseVolume = {0};
            int[] baseSets = {0};
            JSONObject draft = ManualHistory.draft(now);
            String id = draft.optString("id");
            try {
                scenario.onActivity(a -> {
                    ReportEngine.Summary before =
                            ReportEngine.build(
                                            HistoryReader.read(db.list("history")),
                                            now,
                                            now,
                                            TimeZone.getDefault())
                                    .today;
                    baseVolume[0] = before.volume;
                    baseSets[0] = before.sets;
                    JSONObject row =
                            ManualHistory.row(
                                    object("id", "builtin-0", "name", "Bench", "part", "chest"));
                    row.optJSONArray("logs").put(ManualHistory.log("25", "8", "appropriate", now));
                    draft.optJSONArray("rows").put(row);
                    a.show("dailyPlan");
                    a.manualHistoryDraft = draft;
                    click(a, "manual_add");
                    click(a, "manual_save");
                    assertEquals("historyDetail", a.page);
                    assertTrue(find(a.findViewById(R.id.navigation), a.s("records")).isSelected());
                    a.show("home");
                });
                waitHome(scenario);
                scenario.onActivity(a -> {
                    assertEquals(
                            a.num(baseSets[0] + 1),
                            ((TextView) find(a.content, "today_sets")).getText().toString());
                    assertEquals(
                            a.num(baseVolume[0] + 200) + " " + a.s("volume_unit"),
                            ((TextView) find(a.content, "today_volume")).getText().toString());
                    JSONObject saved = a.find("history", id);
                    Store.put(
                            saved.optJSONArray("rows")
                                    .optJSONObject(0)
                                    .optJSONArray("logs")
                                    .optJSONObject(0),
                            "weight",
                            30);
                    db.save("history", saved);
                    a.selectedWeek =
                            ReportEngine.shiftDays(a.selectedWeek, -7, TimeZone.getDefault());
                    a.show("home");
                });
                waitHome(scenario);
                scenario.onActivity(a -> {
                    assertEquals(
                            a.num(baseVolume[0] + 240) + " " + a.s("volume_unit"),
                            ((TextView) find(a.content, "today_volume")).getText().toString());
                    db.delete(id);
                    a.show("home");
                });
                waitHome(scenario);
                scenario.onActivity(a ->
                        assertEquals(
                                a.num(baseSets[0]),
                                ((TextView) find(a.content, "today_sets")).getText().toString()));
            } finally {
                db.delete(id);
            }
        }
    }

    @Test
    public void bilingualDashboardAndTrainingScreensRemainVisible() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String original =
                context.getSharedPreferences("preferences", 0).getString("language", "zh");
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            for (String language : new String[] {"zh", "en"}) {
                scenario.onActivity(a -> {
                    a.setLanguage(language);
                    a.show("home");
                });
                waitHome(scenario);
                snapshot("v063-home-" + language);
                scenario.onActivity(a -> a.show("dailyPlan"));
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(a -> {
                    View generate = find(a.findViewById(R.id.page_actions), a.s("generate"));
                    Rect rect = new Rect();
                    assertTrue(generate.getGlobalVisibleRect(rect));
                    assertEquals(generate.getHeight(), rect.height());
                    ViewGroup nav = a.findViewById(R.id.navigation);
                    assertEquals(5, nav.getChildCount());
                    for (int i = 0; i < nav.getChildCount(); i++)
                        assertTrue(nav.getChildAt(i).getGlobalVisibleRect(rect));
                });
                snapshot("v063-training-" + language);
            }
        } finally {
            context.getSharedPreferences("preferences", 0)
                    .edit()
                    .putString("language", original)
                    .commit();
        }
    }
}
