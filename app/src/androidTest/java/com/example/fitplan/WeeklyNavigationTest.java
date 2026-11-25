package com.example.fitplan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.DateFormat;
import java.util.Date;
import java.util.TimeZone;

@RunWith(AndroidJUnit4.class)
public class WeeklyNavigationTest {
    private View find(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText()))
            return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private View report(MainActivity a) {
        ViewGroup group = a.content.findViewWithTag("weekly_report");
        return group == null || group.getChildCount() == 0 ? null : group.getChildAt(0);
    }

    private String range(MainActivity a, long week) {
        DateFormat format = DateFormat.getDateInstance(DateFormat.MEDIUM, a.locale());
        return format.format(new Date(week))
                + " — "
                + format.format(new Date(ReportEngine.shiftDays(week, 6, TimeZone.getDefault())));
    }

    private void waitForReport(ActivityScenario<MainActivity> scenario, View previous) {
        for (int i = 0; i < 100; i++) {
            boolean[] ready = {false};
            scenario.onActivity(a -> {
                View report = report(a);
                ready[0] =
                        report instanceof ViewGroup
                                && report != previous
                                && a.content.findViewWithTag("today_rings") != null
                                && range(a, a.selectedWeek)
                                        .contentEquals(
                                                ((TextView)
                                                                a.content.findViewWithTag(
                                                                        "weekly_range"))
                                                        .getText());
            });
            if (ready[0]) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                return;
            }
            android.os.SystemClock.sleep(50);
        }
        fail("The selected week's report did not finish loading");
    }

    private void click(MainActivity a, String key) {
        View button = find(a.content, a.s(key));
        assertNotNull(key, button);
        assertTrue(key, button.isEnabled());
        assertTrue(key, button.performClick());
    }

    @Test
    public void allWeekButtonsKeepScrollRingsAndNavigationInBothLanguages() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String language =
                context.getSharedPreferences("preferences", 0).getString("language", "zh");
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            for (String lang : new String[] {"zh", "en"}) {
                scenario.onActivity(a -> {
                    a.setLanguage(lang);
                    a.selectedWeek =
                            ReportEngine.weekStart(
                                    System.currentTimeMillis(), TimeZone.getDefault());
                    a.show("home");
                });
                waitForReport(scenario, null);
                final View[] rings = new View[1], navigation = new View[1];
                final int[] position = new int[1];
                scenario.onActivity(a -> {
                    View partCard = (View) find(a.content, a.s("part_chart")).getParent();
                    assertEquals(
                            "Report cards keep the full content width",
                            a.content.getWidth()
                                    - a.content.getPaddingLeft()
                                    - a.content.getPaddingRight(),
                            partCard.getWidth());
                    ScrollView scroll = a.findViewById(R.id.scroller);
                    View range = a.content.findViewWithTag("weekly_range");
                    Rect rect = new Rect();
                    range.getDrawingRect(rect);
                    scroll.offsetDescendantRectToMyCoords(range, rect);
                    scroll.scrollTo(0, rect.top - a.dp(80));
                    position[0] = scroll.getScrollY();
                    assertTrue("Start below the top of Home", position[0] > 0);
                    rings[0] = a.content.findViewWithTag("today_rings");
                    navigation[0] = ((ViewGroup) a.findViewById(R.id.navigation)).getChildAt(0);
                });
                for (String key :
                        new String[] {
                            "previous_week", "previous_week", "next_week", "back_current"
                        }) {
                    View[] previous = new View[1];
                    long[] expectedWeek = new long[1];
                    scenario.onActivity(a -> {
                        previous[0] = report(a);
                        expectedWeek[0] =
                                key.equals("back_current")
                                        ? ReportEngine.weekStart(
                                                System.currentTimeMillis(), TimeZone.getDefault())
                                        : ReportEngine.shiftDays(
                                                a.selectedWeek,
                                                key.equals("previous_week") ? -7 : 7,
                                                TimeZone.getDefault());
                        click(a, key);
                        assertSame("Keep report visible during loading", previous[0], report(a));
                        assertEquals(
                                position[0],
                                ((ScrollView) a.findViewById(R.id.scroller)).getScrollY());
                        assertSame(rings[0], a.content.findViewWithTag("today_rings"));
                    });
                    waitForReport(scenario, previous[0]);
                    scenario.onActivity(a -> {
                        assertEquals(expectedWeek[0], a.selectedWeek);
                        assertEquals(
                                position[0],
                                ((ScrollView) a.findViewById(R.id.scroller)).getScrollY());
                        assertSame(
                                "Do not restart the ring animation",
                                rings[0],
                                a.content.findViewWithTag("today_rings"));
                        assertSame(
                                navigation[0],
                                ((ViewGroup) a.findViewById(R.id.navigation)).getChildAt(0));
                        assertEquals("home", a.page);
                    });
                }
                scenario.onActivity(a -> {
                    assertFalse(find(a.content, a.s("next_week")).isEnabled());
                    assertEquals(View.GONE, find(a.content, a.s("back_current")).getVisibility());
                });
            }
        } finally {
            context.getSharedPreferences("preferences", 0)
                    .edit()
                    .putString("language", language)
                    .commit();
        }
    }

    @Test
    public void rapidWeekChangesKeepOnlyTheLatestSelection() {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            waitForReport(scenario, null);
            View[] oldReport = new View[1], rings = new View[1];
            long[] expectedWeek = new long[1];
            scenario.onActivity(a -> {
                oldReport[0] = report(a);
                rings[0] = a.content.findViewWithTag("today_rings");
                for (String key :
                        new String[] {
                            "previous_week",
                            "previous_week",
                            "next_week",
                            "back_current",
                            "previous_week"
                        }) click(a, key);
                expectedWeek[0] =
                        ReportEngine.shiftDays(
                                ReportEngine.weekStart(
                                        System.currentTimeMillis(), TimeZone.getDefault()),
                                -7,
                                TimeZone.getDefault());
                assertEquals(expectedWeek[0], a.selectedWeek);
                assertSame(oldReport[0], report(a));
            });
            waitForReport(scenario, oldReport[0]);
            scenario.onActivity(a -> {
                assertEquals(
                        range(a, expectedWeek[0]),
                        ((TextView) a.content.findViewWithTag("weekly_range"))
                                .getText()
                                .toString());
                assertSame(rings[0], a.content.findViewWithTag("today_rings"));
            });
        }
    }

    @Test
    public void leavingHomeWhileLoadingCannotAppendAReportToAnotherPage() {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            waitForReport(scenario, null);
            scenario.onActivity(a -> {
                click(a, "previous_week");
                a.show("profile");
            });
            // A later report request is queued behind the navigation request, so its callback
            // acts as a barrier without relying on a fixed delay for background work.
            java.util.concurrent.CountDownLatch barrier =
                    new java.util.concurrent.CountDownLatch(1);
            scenario.onActivity(a ->
                    a.loadReport(
                            a.selectedWeek,
                            report -> barrier.countDown(),
                            () -> barrier.countDown()));
            try {
                assertTrue(barrier.await(10, java.util.concurrent.TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            scenario.onActivity(a -> {
                assertEquals("profile", a.page);
                assertEquals(null, a.content.findViewWithTag("weekly_report"));
                assertEquals(null, find(a.content, a.s("eight_weeks")));
            });
        }
    }
}
