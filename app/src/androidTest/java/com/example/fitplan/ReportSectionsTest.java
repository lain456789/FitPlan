package com.example.fitplan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;

@RunWith(AndroidJUnit4.class)
public class ReportSectionsTest {
    private static final String[] KEYS = {
        "part_chart", "daily_chart", "eight_weeks", "exercise_trend"
    };

    private View view(MainActivity a, String type, String key) {
        View result = a.content.findViewWithTag("report_" + type + "_" + key);
        assertNotNull(type + " " + key, result);
        return result;
    }

    private View report(MainActivity a) {
        ViewGroup area = a.content.findViewWithTag("weekly_report");
        return area == null || area.getChildCount() == 0 ? null : area.getChildAt(0);
    }

    private void ready(ActivityScenario<MainActivity> scenario, View old) {
        for (int i = 0; i < 100; i++) {
            boolean[] ready = {false};
            scenario.onActivity(a ->
                    ready[0] =
                            report(a) != old
                                    && a.content.findViewWithTag("report_toggle_exercise_trend")
                                            != null);
            if (ready[0]) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                return;
            }
            android.os.SystemClock.sleep(50);
        }
        fail("Report sections did not load");
    }

    private void state(MainActivity a, String key, boolean expanded) {
        assertEquals(
                key, expanded ? View.VISIBLE : View.GONE, view(a, "body", key).getVisibility());
        assertEquals(key, expanded ? 180f : 0f, view(a, "arrow", key).getRotation(), .001f);
        assertEquals(
                a.s(expanded ? "collapse_section" : "expand_section", a.s(key)),
                view(a, "toggle", key).getContentDescription());
    }

    private <T extends View> T findType(View root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                T found = findType(group.getChildAt(i), type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private View findText(View root, String label) {
        if (root instanceof TextView && label.contentEquals(((TextView) root).getText()))
            return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findText(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        android.os.SystemClock.sleep(150);
        Bitmap bitmap =
                InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File directory =
                InstrumentationRegistry.getInstrumentation()
                        .getTargetContext()
                        .getExternalFilesDir(null);
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        } finally {
            bitmap.recycle();
        }
    }

    @Test
    public void startsCollapsedAndEachArrowOrHeaderTogglesOnlyItsOwnSection() throws Exception {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            ready(scenario, null);
            View[] ring = new View[1];
            scenario.onActivity(a -> {
                for (String key : KEYS) state(a, key, false);
                ring[0] = a.content.findViewWithTag("today_rings");
                for (String key : KEYS) {
                    view(a, "arrow", key).performClick();
                    state(a, key, true);
                    assertTrue(((ViewGroup) view(a, "body", key)).getChildCount() > 0);
                    view(a, "toggle", key).performClick();
                    state(a, key, false);
                    assertSame(ring[0], a.content.findViewWithTag("today_rings"));
                }
                ScrollView scroll = a.findViewById(R.id.scroller);
                Rect rect = new Rect();
                view(a, "card", "part_chart").getDrawingRect(rect);
                scroll.offsetDescendantRectToMyCoords(view(a, "card", "part_chart"), rect);
                scroll.scrollTo(0, rect.top - a.dp(20));
            });
            screenshot("report-sections-collapsed");
            scenario.onActivity(a -> {
                view(a, "arrow", "part_chart").performClick();
                view(a, "arrow", "daily_chart").performClick();
                state(a, "part_chart", true);
                state(a, "daily_chart", true);
                state(a, "eight_weeks", false);
                state(a, "exercise_trend", false);
            });
            screenshot("report-sections-expanded");
            scenario.onActivity(a -> {
                view(a, "arrow", "part_chart").performClick();
                state(a, "part_chart", false);
                state(a, "daily_chart", true);
                assertSame(ring[0], a.content.findViewWithTag("today_rings"));
            });
        }
    }

    @Test
    public void expansionAndMetricSurviveWeekLanguageAndActivityRecreation() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String language =
                context.getSharedPreferences("preferences", 0).getString("language", "zh");
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            ready(scenario, null);
            Spinner[] spinner = new Spinner[1];
            View[] old = new View[1];
            scenario.onActivity(a -> {
                view(a, "arrow", "eight_weeks").performClick();
                spinner[0] = findType(view(a, "body", "eight_weeks"), Spinner.class);
                assertNotNull(spinner[0]);
                spinner[0].setSelection(2);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> {
                assertEquals("total_volume", a.metric);
                view(a, "arrow", "eight_weeks").performClick();
                view(a, "arrow", "eight_weeks").performClick();
                assertSame(spinner[0], findType(view(a, "body", "eight_weeks"), Spinner.class));
                assertEquals(2, spinner[0].getSelectedItemPosition());
                old[0] = report(a);
                findText(a.content, a.s("previous_week")).performClick();
            });
            ready(scenario, old[0]);
            scenario.onActivity(a -> {
                state(a, "eight_weeks", true);
                state(a, "daily_chart", false);
                assertEquals(
                        2,
                        findType(view(a, "body", "eight_weeks"), Spinner.class)
                                .getSelectedItemPosition());
                old[0] = report(a);
                a.setLanguage("en");
            });
            ready(scenario, old[0]);
            scenario.onActivity(a -> {
                state(a, "eight_weeks", true);
                state(a, "exercise_trend", false);
                assertEquals(
                        "Collapse Eight-week trend",
                        view(a, "toggle", "eight_weeks").getContentDescription());
                ScrollView scroll = a.findViewById(R.id.scroller);
                Rect rect = new Rect();
                View card = view(a, "card", "eight_weeks");
                card.getDrawingRect(rect);
                scroll.offsetDescendantRectToMyCoords(card, rect);
                scroll.scrollTo(0, rect.top - a.dp(20));
            });
            screenshot("report-sections-english");
            scenario.recreate();
            ready(scenario, null);
            scenario.onActivity(a -> {
                state(a, "eight_weeks", true);
                state(a, "part_chart", false);
                state(a, "daily_chart", false);
                state(a, "exercise_trend", false);
                assertEquals("total_volume", a.metric);
            });
        } finally {
            context.getSharedPreferences("preferences", 0)
                    .edit()
                    .putString("language", language)
                    .commit();
        }
    }
}
