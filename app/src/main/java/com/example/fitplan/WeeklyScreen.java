package com.example.fitplan;

import static com.example.fitplan.Store.object;

import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Consumer;

final class WeeklyScreen extends Screen {
    private TextView dateRange;
    private TextView status;
    private Button next;
    private Button back;
    private LinearLayout reportArea;
    private Consumer<ReportEngine.Report> todayLoaded;
    private int requestId;
    private boolean hasReport;

    WeeklyScreen(MainActivity activity) {
        super(activity);
    }

    void render(Consumer<ReportEngine.Report> todayLoaded) {
        this.todayLoaded = todayLoaded;
        title(s("weekly_title"));
        dateRange = text(content, "", 16);
        dateRange.setTag("weekly_range");
        LinearLayout controls = new LinearLayout(activity);
        content.addView(controls);
        Button prev = button(controls, s("previous_week"), () -> changeWeek(-7));
        prev.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        next = button(controls, s("next_week"), () -> changeWeek(7));
        next.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        back = button(content, s("back_current"), () -> load(currentWeek()));
        reportArea = new LinearLayout(activity);
        reportArea.setOrientation(LinearLayout.VERTICAL);
        reportArea.setTag("weekly_report");
        content.addView(reportArea);
        status = text(reportArea, s("loading"), 16);
        load(activity.selectedWeek);
    }

    private long currentWeek() {
        return ReportEngine.weekStart(System.currentTimeMillis(), TimeZone.getDefault());
    }

    private void changeWeek(int days) {
        load(ReportEngine.shiftDays(activity.selectedWeek, days, TimeZone.getDefault()));
    }

    private String range(long week) {
        DateFormat format = DateFormat.getDateInstance(DateFormat.MEDIUM, activity.locale());
        return format.format(new Date(week))
                + " — "
                + format.format(new Date(ReportEngine.shiftDays(week, 6, TimeZone.getDefault())));
    }

    private void load(long week) {
        long current = currentWeek();
        activity.selectedWeek =
                Math.min(ReportEngine.weekStart(week, TimeZone.getDefault()), current);
        next.setEnabled(activity.selectedWeek < current);
        next.setAlpha(next.isEnabled() ? 1f : .4f);
        back.setVisibility(activity.selectedWeek < current ? View.VISIBLE : View.GONE);
        if (!hasReport) {
            dateRange.setText(range(activity.selectedWeek));
            status.setText(s("loading"));
        }
        final int request = ++requestId;
        activity.loadReport(
                activity.selectedWeek,
                report -> {
                    // A later tap can supersede this request without changing the page.
                    if (request != requestId) return;
                    LinearLayout replacement = new LinearLayout(activity);
                    replacement.setOrientation(LinearLayout.VERTICAL);
                    loaded(report, replacement);
                    // Keep the current cards on screen until the replacement is complete.
                    // Neither the ScrollView nor today's animated rings is recreated.
                    reportArea.removeAllViews();
                    reportArea.addView(replacement);
                    dateRange.setText(range(report.current.start));
                    if (!hasReport) todayLoaded.accept(report);
                    hasReport = true;
                },
                () -> {
                    if (request != requestId) return;
                    if (hasReport) toast(s("read_error"));
                    else status.setText(s("read_error"));
                });
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

    private void loaded(ReportEngine.Report report, LinearLayout content) {
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
                s("duration"),
                num(r.duration / 60000.0),
                s("minutes_unit") + "\n" + difference(r.duration / 60000.0, p.duration / 60000.0),
                0xff6edddd);
        LinearLayout parts = section(content, "part_chart");
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
        List<ReportEngine.Point> daily = new ArrayList<>();
        String[] dailyLabels = new String[7];
        for (int i = 0; i < 7; i++) {
            long time = ReportEngine.shiftDays(r.start, i, TimeZone.getDefault());
            daily.add(new ReportEngine.Point(time, r.daily[i]));
            dailyLabels[i] = new SimpleDateFormat("EEE", activity.locale()).format(new Date(time));
        }
        chartInto(section(content, "daily_chart"), daily, dailyLabels, false, s("completed_sets"));
        LinearLayout trend = section(content, "eight_weeks");
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
                        index,
                        false);
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
        LinearLayout exercises = section(content, "exercise_trend");
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
    }

    private AdapterView.OnItemSelectedListener listener(Runnable action) {
        return new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                action.run();
            }

            public void onNothingSelected(AdapterView<?> p) {}
        };
    }

    private LinearLayout section(LinearLayout parent, String key) {
        LinearLayout card = card(parent);
        card.setTag("report_card_" + key);
        LinearLayout header = new LinearLayout(activity);
        header.setTag("report_toggle_" + key);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(activity.dp(48));
        header.setFocusable(true);
        card.addView(header, new LinearLayout.LayoutParams(-1, -2));
        TextView label = text(header, s(key), 20);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        ImageButton arrow = new ImageButton(activity);
        arrow.setTag("report_arrow_" + key);
        arrow.setImageResource(R.drawable.ic_report_expand);
        arrow.setPadding(activity.dp(12), activity.dp(12), activity.dp(12), activity.dp(12));
        arrow.setFocusable(false);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        TypedValue ripple = new TypedValue();
        activity.getTheme()
                .resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        arrow.setBackgroundResource(ripple.resourceId);
        header.addView(arrow, new LinearLayout.LayoutParams(activity.dp(48), activity.dp(48)));
        LinearLayout body = new LinearLayout(activity);
        body.setTag("report_body_" + key);
        body.setOrientation(LinearLayout.VERTICAL);
        card.addView(body, new LinearLayout.LayoutParams(-1, -2));
        Runnable update =
                () -> {
                    boolean expanded = activity.expandedReportSections.contains(key);
                    body.setVisibility(expanded ? View.VISIBLE : View.GONE);
                    arrow.setRotation(expanded ? 180 : 0);
                    String action = s(expanded ? "collapse_section" : "expand_section", s(key));
                    header.setContentDescription(action);
                    arrow.setContentDescription(action);
                    ViewCompat.setStateDescription(
                            header, s(expanded ? "section_expanded" : "section_collapsed"));
                };
        header.setAccessibilityDelegate(
                new View.AccessibilityDelegate() {
                    @Override
                    public void onInitializeAccessibilityNodeInfo(
                            View host, AccessibilityNodeInfo info) {
                        super.onInitializeAccessibilityNodeInfo(host, info);
                        info.setClassName(Button.class.getName());
                    }
                });
        header.setOnClickListener(v -> {
            if (!activity.expandedReportSections.remove(key))
                activity.expandedReportSections.add(key);
            // Only toggle the existing body; preserve chart selection and the surrounding page.
            update.run();
        });
        arrow.setOnClickListener(v -> header.performClick());
        update.run();
        return body;
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
        c.addView(view, new LinearLayout.LayoutParams(-1, activity.dp(190)));
        c.addView(selection);
        StringBuilder data = new StringBuilder();
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) data.append("\n");
            data.append(s("chart_point", labels[i], num(points.get(i).value)))
                    .append(" ")
                    .append(unit);
        }
        view.setContentDescription(unit + " · " + s("chart_help") + "\n" + data);
    }
}
