package com.example.fitplan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class ReportEngineTest {
    final TimeZone zone = TimeZone.getTimeZone("Asia/Shanghai");

    long time(String text) {
        try {
            java.text.SimpleDateFormat f =
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            f.setTimeZone(zone);
            return f.parse(text).getTime();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    ReportEngine.Session session(String date, String id, double weight, int reps, boolean done) {
        long finish = time(date);
        ReportEngine.Session s = new ReportEngine.Session(finish - 3600000, finish);
        s.sets.add(new ReportEngine.SetEntry(id, "same name", "胸", weight, reps, done));
        return s;
    }

    @Test
    public void weekStartsOnMondayAcrossYear() {
        assertEquals(
                time("2025-12-29 00:00"), ReportEngine.weekStart(time("2026-01-01 12:00"), zone));
    }

    @Test
    public void completedOnlyAndBodyweightCounts() {
        List<ReportEngine.Session> data =
                Arrays.asList(
                        session("2026-01-01 12:00", "a", 20, 10, true),
                        session("2026-01-01 14:00", "b", 0, 12, true),
                        session("2026-01-02 12:00", "c", 80, 10, false));
        ReportEngine.Report r =
                ReportEngine.build(data, time("2026-01-01 12:00"), time("2026-01-10 12:00"), zone);
        assertEquals(2, r.current.sessions);
        assertEquals(1, r.current.days);
        assertEquals(2, r.current.sets);
        assertEquals(200, r.current.volume, 0);
        assertEquals(7200000, r.current.duration);
        assertEquals(2, r.current.daily[3]);
        assertEquals(Integer.valueOf(2), r.current.parts.get("chest"));
    }

    @Test
    public void mondayBoundaryUsesFinishNotStart() {
        ReportEngine.Session s = session("2026-01-05 00:00", "a", 20, 10, true);
        ReportEngine.Report r =
                ReportEngine.build(
                        Collections.singletonList(s),
                        time("2026-01-05 00:00"),
                        time("2026-01-05 12:00"),
                        zone);
        assertEquals(1, r.current.sessions);
        assertEquals(0, r.previous.sessions);
    }

    @Test
    public void currentWeekComparisonStopsAtSameTimeLastWeek() {
        List<ReportEngine.Session> data =
                Arrays.asList(
                        session("2026-01-07 11:00", "a", 20, 10, true),
                        session("2025-12-31 11:00", "a", 20, 10, true),
                        session("2025-12-31 13:00", "a", 20, 10, true),
                        session("2026-01-08 11:00", "a", 20, 10, true));
        ReportEngine.Report r =
                ReportEngine.build(data, time("2026-01-05 00:00"), time("2026-01-07 12:00"), zone);
        assertTrue(r.partial);
        assertEquals(1, r.current.sessions);
        assertEquals(1, r.previous.sessions);
    }

    @Test
    public void historicWeekComparesWholePreviousWeek() {
        ReportEngine.Report r =
                ReportEngine.build(
                        Collections.singletonList(session("2026-01-04 23:59", "a", 10, 5, true)),
                        time("2026-01-05 00:00"),
                        time("2026-01-19 12:00"),
                        zone);
        assertFalse(r.partial);
        assertEquals(1, r.previous.sessions);
        assertEquals(0, r.current.sessions);
    }

    @Test
    public void exerciseIdentityNotNameAndNoMissingDateZeroes() {
        List<ReportEngine.Session> data =
                Arrays.asList(
                        session("2026-01-01 12:00", "one", 10, 5, true),
                        session("2026-01-03 12:00", "two", 40, 5, true),
                        session("2026-01-04 12:00", "one", 20, 5, true));
        ReportEngine.Report r =
                ReportEngine.build(data, time("2026-01-01 12:00"), time("2026-01-10 12:00"), zone);
        assertEquals(2, r.exercises.size());
        assertEquals(2, r.exercises.get("one").points.size());
        assertEquals(1, r.exercises.get("two").points.size());
        assertEquals(20, r.exercises.get("one").points.get(1).value, 0);
    }

    @Test
    public void multipleRowsOfSameExerciseUseOneMaximumPerSession() {
        ReportEngine.Session s = session("2026-01-01 12:00", "one", 10, 5, true);
        s.sets.add(new ReportEngine.SetEntry("one", "same name", "chest", 30, 5, true));
        ReportEngine.Report r =
                ReportEngine.build(
                        Collections.singletonList(s),
                        time("2026-01-01 12:00"),
                        time("2026-01-10 12:00"),
                        zone);
        assertEquals(1, r.exercises.get("one").points.size());
        assertEquals(30, r.exercises.get("one").points.get(0).value, 0);
    }

    @Test
    public void editAndDeleteRecalculateWithoutCaching() {
        List<ReportEngine.Session> list = new ArrayList<>();
        ReportEngine.Session s = session("2026-01-01 12:00", "one", 10, 5, true);
        list.add(s);
        assertEquals(
                50,
                ReportEngine.build(list, s.finished, time("2026-01-10 12:00"), zone).current.volume,
                0);
        s.sets.get(0).weight = 20;
        assertEquals(
                100,
                ReportEngine.build(list, s.finished, time("2026-01-10 12:00"), zone).current.volume,
                0);
        list.clear();
        assertEquals(
                0,
                ReportEngine.build(list, s.finished, time("2026-01-10 12:00"), zone)
                        .current
                        .sessions);
    }

    @Test
    public void emptyReportContainsEightWeeksAndNoFabricatedExercisePoints() {
        ReportEngine.Report r =
                ReportEngine.build(
                        Collections.emptyList(),
                        time("2026-01-01 12:00"),
                        time("2026-01-01 12:00"),
                        zone);
        assertEquals(8, r.weeks.size());
        assertTrue(r.exercises.isEmpty());
        assertEquals(0, r.previous.volume, 0);
    }

    @Test
    public void invalidOrUnfinishedSessionDoesNotCount() {
        ReportEngine.Session s = new ReportEngine.Session(1, 0);
        s.sets.add(new ReportEngine.SetEntry("a", "a", "chest", 20, 5, true));
        assertEquals(
                0,
                ReportEngine.build(
                                Collections.singletonList(s),
                                time("2026-01-01 12:00"),
                                time("2026-01-01 12:00"),
                                zone)
                        .current
                        .sessions);
    }

    @Test
    public void daylightSavingWeekUsesCalendarDays() {
        TimeZone ny = TimeZone.getTimeZone("America/New_York");
        Calendar c = Calendar.getInstance(ny);
        c.clear();
        c.set(2026, Calendar.MARCH, 2, 0, 0);
        long start = c.getTimeInMillis();
        assertEquals(167L * 3600000, ReportEngine.shiftDays(start, 7, ny) - start);
    }

    @Test
    public void legacyPartFallbackUsesBuiltInId() {
        assertEquals("chest", Catalog.exercisePart("builtin-0", ""));
        assertEquals("back", Catalog.partCode("背"));
        assertEquals("unknown", Catalog.exercisePart("custom", ""));
    }
}
