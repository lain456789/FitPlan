package com.example.fitplan;

import static com.example.fitplan.Store.object;

import android.graphics.Typeface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Consumer;

final class WeeklyScreen extends Screen {
    WeeklyScreen(MainActivity activity) {
        super(activity);
    }

    void render(Consumer<ReportEngine.Report> todayLoaded) {
        title(s("weekly_title"));
        TimeZone zone = TimeZone.getDefault();
        long current = ReportEngine.weekStart(System.currentTimeMillis(), zone);
        activity.selectedWeek =
                Math.min(ReportEngine.weekStart(activity.selectedWeek, zone), current);
        text(
                content,
                DateFormat.getDateInstance(DateFormat.MEDIUM, activity.locale())
                                .format(new Date(activity.selectedWeek))
                        + " — "
                        + DateFormat.getDateInstance(DateFormat.MEDIUM, activity.locale())
                                .format(
                                        new Date(
                                                ReportEngine.shiftDays(
                                                        activity.selectedWeek, 6, zone))),
                16);
        LinearLayout controls = new LinearLayout(activity);
        content.addView(controls);
        Button prev =
                button(
                        controls,
                        s("previous_week"),
                        () -> {
                            activity.selectedWeek =
                                    ReportEngine.shiftDays(activity.selectedWeek, -7, zone);
                            show("home");
                        });
        prev.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        Button next =
                button(
                        controls,
                        s("next_week"),
                        () -> {
                            activity.selectedWeek =
                                    ReportEngine.shiftDays(activity.selectedWeek, 7, zone);
                            show("home");
                        });
        next.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        next.setEnabled(activity.selectedWeek < current);
        next.setAlpha(next.isEnabled() ? 1f : .4f);
        if (activity.selectedWeek != current)
            button(
                    content,
                    s("back_current"),
                    () -> {
                        activity.selectedWeek = current;
                        show("home");
                    });
        TextView status = text(content, s("loading"), 16);
        activity.loadReport(
                activity.selectedWeek,
                report -> {
                    todayLoaded.accept(report);
                    loaded(report);
                },
                status);
    }

    private String difference(double current, double previous) {
        double d = current - previous;
        String number = (d > 0 ? "+" : "") + num(d);
        return s(previous == 0 ? "no_comparison" : "vs_previous", number);
    }

    private void tile(LinearLayout parent, String label, String value, String change, int color) {
        LinearLayout c = card(parent);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        lp.setMargins(activity.dp(3), activity.dp(5), activity.dp(3), activity.dp(5));
        c.setLayoutParams(lp);
        text(c, label, 14);
        TextView n = text(c, value, 26);
        n.setTextColor(color);
        n.setTypeface(null, Typeface.BOLD);
        text(c, change, 12);
    }

    private void loaded(ReportEngine.Report report) {
        ReportEngine.Summary r = report.current, p = report.previous;
        text(content, s(report.partial ? "to_date" : "full_week"), 14);
        LinearLayout row = new LinearLayout(activity);
        content.addView(row);
        tile(
                row,
                s("sessions"),
                num(r.sessions),
                difference(r.sessions, p.sessions),
                MainActivity.ACCENT);
        tile(row, s("days"), num(r.days), difference(r.days, p.days), 0xff8eb6ff);
        LinearLayout row2 = new LinearLayout(activity);
        content.addView(row2);
        tile(row2, s("completed_sets"), num(r.sets), difference(r.sets, p.sets), 0xffc1a1ff);
        tile(
                row2,
                s("total_volume"),
                num(r.volume),
                s("volume_unit") + "\n" + difference(r.volume, p.volume),
                0xff6edddd);
        LinearLayout duration = card(content);
        text(
                duration,
                s("duration") + " · " + num(r.duration / 60000.0) + " " + s("minutes_unit"),
                20);
        text(duration, difference(r.duration / 60000.0, p.duration / 60000.0), 14);
        LinearLayout summary = card(content);
        text(
                summary,
                r.sessions == 0 ? s("empty_week") : s("summary", r.sessions, r.days, r.sets),
                18);
        if (!r.parts.isEmpty()) {
            String best =
                    Collections.max(r.parts.entrySet(), Map.Entry.comparingByValue()).getKey();
            text(summary, s("part_summary", activity.partName(best), r.parts.get(best)), 16);
        }
        List<ReportEngine.Point> daily = new ArrayList<>();
        String[] dailyLabels = new String[7];
        for (int i = 0; i < 7; i++) {
            long time = ReportEngine.shiftDays(r.start, i, TimeZone.getDefault());
            daily.add(new ReportEngine.Point(time, r.daily[i]));
            dailyLabels[i] = new SimpleDateFormat("EEE", activity.locale()).format(new Date(time));
        }
        chart(s("daily_chart"), daily, dailyLabels, false, s("completed_sets"));
        LinearLayout parts = card(content);
        text(parts, s("part_chart"), 20);
        if (r.parts.isEmpty()) text(parts, s("empty_week"), 14);
        for (Map.Entry<String, Integer> entry : r.parts.entrySet()) {
            text(parts, s("part_summary", activity.partName(entry.getKey()), entry.getValue()), 16);
            ProgressBar bar =
                    new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(Math.max(1, r.sets));
            bar.setProgress(entry.getValue());
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xffc1a1ff));
            parts.addView(bar, new LinearLayout.LayoutParams(-1, activity.dp(8)));
        }
        LinearLayout trend = card(content);
        text(trend, s("eight_weeks"), 20);
        String[] metrics = {"sessions", "completed_sets", "total_volume"};
        int index =
                activity.metric.equals("completed_sets")
                        ? 1
                        : activity.metric.equals("total_volume") ? 2 : 0;
        Spinner metric =
                select(
                        trend,
                        s("metric"),
                        new String[] {s(metrics[0]), s(metrics[1]), s(metrics[2])},
                        index);
        LinearLayout chartArea = activity.column();
        trend.addView(chartArea);
        Runnable render =
                () -> {
                    activity.metric = metrics[metric.getSelectedItemPosition()];
                    chartArea.removeAllViews();
                    List<ReportEngine.Point> points = new ArrayList<>();
                    String[] labels = new String[8];
                    for (int i = 0; i < 8; i++) {
                        ReportEngine.Summary w = report.weeks.get(i);
                        points.add(
                                new ReportEngine.Point(
                                        w.start,
                                        activity.metric.equals("sessions")
                                                ? w.sessions
                                                : activity.metric.equals("completed_sets")
                                                        ? w.sets
                                                        : w.volume));
                        labels[i] = activity.shortDate(w.start);
                    }
                    chartInto(
                            chartArea,
                            points,
                            labels,
                            true,
                            activity.metric.equals("total_volume")
                                    ? s("volume_unit")
                                    : s(activity.metric));
                };
        metric.setOnItemSelectedListener(listener(render));
        render.run();
        LinearLayout exercises = card(content);
        text(exercises, s("exercise_trend"), 20);
        text(exercises, s("exercise_hint"), 14);
        button(exercises, s("progress_title"), () -> show("progress"));
        if (report.exercises.isEmpty()) text(exercises, s("no_exercise"), 16);
        else {
            List<ReportEngine.ExerciseTrend> all = new ArrayList<>(report.exercises.values());
            String[] labels = new String[all.size()];
            int selected = 0;
            Map<String, Integer> duplicates = new HashMap<>();
            for (ReportEngine.ExerciseTrend ex : all) {
                String n = activity.exerciseName(object("exerciseId", ex.id, "name", ex.name));
                duplicates.put(n, duplicates.containsKey(n) ? duplicates.get(n) + 1 : 1);
            }
            for (int i = 0; i < all.size(); i++) {
                ReportEngine.ExerciseTrend ex = all.get(i);
                labels[i] = activity.exerciseName(object("exerciseId", ex.id, "name", ex.name));
                if (duplicates.get(labels[i]) > 1)
                    labels[i] += " · " + ex.id.substring(0, Math.min(8, ex.id.length()));
                if (ex.id.equals(activity.exerciseId)) selected = i;
            }
            Spinner pick = select(exercises, s("choose_exercise"), labels, selected);
            LinearLayout area = activity.column();
            exercises.addView(area);
            Runnable draw =
                    () -> {
                        ReportEngine.ExerciseTrend ex = all.get(pick.getSelectedItemPosition());
                        activity.exerciseId = ex.id;
                        area.removeAllViews();
                        String[] dates = new String[ex.points.size()];
                        for (int i = 0; i < dates.length; i++)
                            dates[i] = activity.shortDate(ex.points.get(i).time);
                        chartInto(area, ex.points, dates, true, "kg");
                    };
            pick.setOnItemSelectedListener(listener(draw));
            draw.run();
        }
        LinearLayout help = card(content);
        text(help, s("definitions"), 18);
        text(help, s("definitions_text"), 14);
    }

    private AdapterView.OnItemSelectedListener listener(Runnable action) {
        return new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                action.run();
            }

            public void onNothingSelected(AdapterView<?> p) {}
        };
    }

    private void chart(
            String title,
            List<ReportEngine.Point> points,
            String[] labels,
            boolean line,
            String unit) {
        LinearLayout c = card(content);
        text(c, title, 20);
        chartInto(c, points, labels, line, unit);
    }

    private void chartInto(
            LinearLayout c,
            List<ReportEngine.Point> points,
            String[] labels,
            boolean line,
            String unit) {
        TextView selection = new TextView(activity);
        selection.setTextColor(MainActivity.ACCENT);
        selection.setTextSize(14);
        TrendChart view =
                new TrendChart(
                        activity,
                        points,
                        labels,
                        line,
                        activity.locale(),
                        index ->
                                selection.setText(
                                        s(
                                                        "chart_point",
                                                        labels[index],
                                                        num(points.get(index).value))
                                                + " "
                                                + unit));
        view.setContentDescription(unit + " · " + s("chart_help"));
        c.addView(view, new LinearLayout.LayoutParams(-1, activity.dp(190)));
        c.addView(selection);
        text(c, s("chart_help"), 12);
        StringBuilder data = new StringBuilder();
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) data.append("\n");
            data.append(s("chart_point", labels[i], num(points.get(i).value)))
                    .append(" ")
                    .append(unit);
        }
        text(c, data.toString(), 13);
    }
}
