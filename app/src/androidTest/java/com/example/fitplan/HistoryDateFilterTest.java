package com.example.fitplan;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.example.fitplan.Store.object;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.AlertDialog;
import android.app.DatePickerDialog;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

@RunWith(AndroidJUnit4.class)
public class HistoryDateFilterTest {
    @Test
    public void dateFiltersUseLocalDayBoundariesAndGroupNewestMonthsFirst() {
        TimeZone zone = TimeZone.getTimeZone("America/Los_Angeles");
        long now = date(zone, Calendar.MARCH, 12, 12);
        JSONObject older = record("February", date(zone, Calendar.FEBRUARY, 28, 12));
        JSONObject early = record("Outside last seven days", date(zone, Calendar.MARCH, 5, 23));
        JSONObject boundary = record("First of last seven days", date(zone, Calendar.MARCH, 6, 0));
        JSONObject daylightSaving =
                record("Daylight saving day", date(zone, Calendar.MARCH, 8, 23));
        JSONObject latest = record("Last of last seven days", date(zone, Calendar.MARCH, 12, 23));
        JSONArray history =
                new JSONArray().put(older).put(boundary).put(latest).put(early).put(daylightSaving);

        Map<Long, List<JSONObject>> all =
                HistoryReader.byMonth(history, HistoryReader.DateFilter.ALL, 0, 0, now, zone);
        assertEquals(2, all.size());
        List<List<JSONObject>> groups = new ArrayList<>(all.values());
        assertEquals(latest, groups.get(0).get(0));
        assertEquals(older, groups.get(1).get(0));
        Map<Long, List<JSONObject>> recent =
                HistoryReader.byMonth(
                        history, HistoryReader.DateFilter.LAST_SEVEN_DAYS, 0, 0, now, zone);
        List<JSONObject> recentMonth = recent.values().iterator().next();
        assertEquals(3, recentMonth.size());
        assertEquals(boundary, recentMonth.get(2));
        Map<Long, List<JSONObject>> thisMonth =
                HistoryReader.byMonth(
                        history, HistoryReader.DateFilter.THIS_MONTH, 0, 0, now, zone);
        assertEquals(4, thisMonth.values().iterator().next().size());
        long onlyDay = date(zone, Calendar.MARCH, 8, 0);
        Map<Long, List<JSONObject>> custom =
                HistoryReader.byMonth(
                        history, HistoryReader.DateFilter.CUSTOM, onlyDay, onlyDay, now, zone);
        List<JSONObject> selectedDay = custom.values().iterator().next();
        assertEquals(1, selectedDay.size());
        assertEquals(daylightSaving, selectedDay.get(0));
    }

    @Test
    public void newestMonthOpensByDefaultAndDateRangeSurvivesRecreation() {
        long now = System.currentTimeMillis();
        long olderDate = ReportEngine.shiftDays(now, -40, TimeZone.getDefault());
        JSONObject recent = record("Recent history fixture", now);
        JSONObject older = record("Older history fixture", olderDate);
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            try {
                String[] labels = new String[10];
                scenario.onActivity(a -> {
                    a.store.save("history", recent);
                    a.store.save("history", older);
                    a.show("history");
                    SimpleDateFormat month =
                            new SimpleDateFormat(a.s("history_month_pattern"), a.locale());
                    labels[0] = month.format(new Date(olderDate)) + " · ";
                    labels[1] = a.s("history_date_filter");
                    labels[2] = a.s("history_last_seven_days");
                    labels[3] = a.s("history_custom_range");
                    labels[4] = a.s("history_start_date", "");
                    labels[5] = a.s("history_end_date", "");
                    labels[6] = a.s("history_apply_dates");
                    labels[7] = a.s("history_this_month");
                    labels[8] = a.s("correct_record");
                    labels[9] = "← " + a.s("back_history");
                });
                onView(withText(recent.optString("name")))
                        .perform(scrollTo())
                        .check(matches(isDisplayed()));
                onView(withText(older.optString("name"))).check(doesNotExist());
                onView(withText(startsWith(labels[0]))).perform(scrollTo(), click());
                onView(withText(older.optString("name")))
                        .perform(scrollTo())
                        .check(matches(isDisplayed()));
                scenario.recreate();
                onView(withText(older.optString("name")))
                        .check(matches(withText(older.optString("name"))));

                chooseFilter(labels[1], labels[2]);
                onView(withText(recent.optString("name")))
                        .check(matches(withText(recent.optString("name"))));
                onView(withText(startsWith(labels[0]))).check(doesNotExist());
                chooseFilter(labels[1], labels[7]);
                onView(withText(startsWith(labels[0]))).check(doesNotExist());

                chooseFilter(labels[1], labels[3]);
                selectDate(scenario, labels[5], olderDate);
                onView(withText(labels[6])).inRoot(isDialog()).perform(click());
                scenario.onActivity(a -> {
                    assertTrue(a.activeDialog.isShowing());
                    assertEquals(HistoryReader.DateFilter.THIS_MONTH, a.historyDateFilter);
                });
                selectDate(scenario, labels[4], olderDate);
                onView(withText(labels[6])).inRoot(isDialog()).perform(click());
                onView(withText(recent.optString("name"))).check(doesNotExist());
                onView(withText(older.optString("name")))
                        .perform(scrollTo())
                        .check(matches(isDisplayed()));
                onView(allOf(withText(labels[8]), hasSibling(withText(older.optString("name")))))
                        .perform(scrollTo(), click());
                onView(withText(labels[9])).perform(scrollTo(), click());
                onView(withText(recent.optString("name"))).check(doesNotExist());
                scenario.recreate();
                onView(withText(recent.optString("name"))).check(doesNotExist());
                onView(withText(older.optString("name")))
                        .check(matches(withText(older.optString("name"))));
            } finally {
                scenario.onActivity(a -> {
                    a.store.delete(recent.optString("id"));
                    a.store.delete(older.optString("id"));
                });
            }
        }
    }

    private void chooseFilter(String control, String option) {
        onView(withText(control)).perform(scrollTo(), click());
        onView(withText(option)).inRoot(isDialog()).perform(click());
    }

    private void selectDate(ActivityScenario<MainActivity> scenario, String label, long time) {
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(time);
        onView(withText(startsWith(label))).inRoot(isDialog()).perform(click());
        scenario.onActivity(a -> {
            DatePickerDialog picker = (DatePickerDialog) a.activeDialog;
            picker.updateDate(
                    date.get(Calendar.YEAR),
                    date.get(Calendar.MONTH),
                    date.get(Calendar.DAY_OF_MONTH));
            picker.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
    }

    private JSONObject record(String name, long finished) {
        return object(
                "id",
                Store.id(),
                "name",
                name,
                "finished",
                finished,
                "started",
                finished - 60000,
                "rows",
                new JSONArray());
    }

    private long date(TimeZone zone, int month, int day, int hour) {
        Calendar value = Calendar.getInstance(zone);
        value.clear();
        value.set(2026, month, day, hour, 0);
        return value.getTimeInMillis();
    }
}
