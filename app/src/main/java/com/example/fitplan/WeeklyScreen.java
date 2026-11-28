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

        // Compact Apple Fitness section header with inline < / > week switcher
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(-1, -2);
        hLp.topMargin = activity.dp(12);
        content.addView(header, hLp);

        LinearLayout titleCol = new LinearLayout(activity);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        header.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1));

        TextView heading = new TextView(activity);
        heading.setText(s("weekly_title"));
        heading.setTextSize(22);
        heading.setTextColor(MainActivity.FG);
        heading.setTypeface(null, Typeface.BOLD);
        titleCol.addView(heading);

        dateRange = new TextView(activity);
        dateRange.setTextSize(12);
        dateRange.setTextColor(MainActivity.MUTED);
        dateRange.setTag("weekly_range");
        titleCol.addView(dateRange);

        LinearLayout controls = new LinearLayout(activity);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(controls, new LinearLayout.LayoutParams(-2, -2));

        Button prev =
                UiKit.compactPill(activity, "\u2039", UiKit.SURFACE, 0, MainActivity.FG, 0, true);
        prev.setTextSize(18);
        prev.setContentDescription(s("previous_week"));
        prev.setPadding(activity.dp(14), activity.dp(4), activity.dp(14), activity.dp(4));
        prev.setOnClickListener(v -> changeWeek(-7));
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(-2, -2);
        prevLp.setMarginEnd(activity.dp(6));
        controls.addView(prev, prevLp);

        next = UiKit.compactPill(activity, "\u203A", UiKit.SURFACE, 0, MainActivity.FG, 0, true);
        next.setTextSize(18);
        next.setContentDescription(s("next_week"));
        next.setPadding(activity.dp(14), activity.dp(4), activity.dp(14), activity.dp(4));
        next.setOnClickListener(v -> changeWeek(7));
        controls.addView(next, new LinearLayout.LayoutParams(-2, -2));

        back = backButton(content, s("back_current"), () -> load(currentWeek()));

        reportArea = new LinearLayout(activity);
        reportArea.setOrientation(LinearLayout.VERTICAL);
        reportArea.setTag("weekly_report");
        content.addView(reportArea);
        status = text(reportArea, s("loading"), 14);
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
                + " – "
                + format.format(new Date(ReportEngine.shiftDays(week, 6, TimeZone.getDefault())));
    }

    private void load(long week) {
        long current = currentWeek();
        activity.selectedWeek =
                Math.min(ReportEngine.weekStart(week, TimeZone.getDefault()), current);
        next.setEnabled(activity.selectedWeek < current);
        next.setAlpha(next.isEnabled() ? 1f : .35f);
        back.setVisibility(activity.selectedWeek < current ? View.VISIBLE : View.GONE);
        if (!hasReport) {
            dateRange.setText(range(activity.selectedWeek));
            status.setText(s("loading"));
        }
        final int request = ++requestId;
        activity.loadReport(
                activity.selectedWeek,
                report -> {
                    if (request != requestId) return;
                    LinearLayout replacement = new LinearLayout(activity);
                    replacement.setOrientation(LinearLayout.VERTICAL);
                    loaded(report, replacement);
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

    private String compactDelta(double current, double previous) {
        if (previous == 0 && current == 0) return "—";
        if (previous == 0) return "+" + num(current);
        double d = current - previous;
        if (Math.abs(d) < 1e-6) return "±0";
        return (d > 0 ? "+" : "") + num(d);
    }

    private void tile(
            LinearLayout parent,
            String label,
            String value,
            String delta,
            int color,
            boolean addEndGap) {
        LinearLayout c = card(parent);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        lp.setMargins(0, activity.dp(4), addEndGap ? activity.dp(8) : 0, activity.dp(4));
        c.setLayoutParams(lp);
        c.setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12));

        TextView cap = new TextView(activity);
        cap.setText(label);
        cap.setTextSize(12);
        cap.setTextColor(MainActivity.MUTED);
        c.addView(cap);

        TextView n = new TextView(activity);
        n.setText(value);
        n.setTextSize(28);
        n.setTextColor(color);
        n.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(-2, -2);
        nLp.topMargin = activity.dp(2);
        c.addView(n, nLp);

        TextView d = new TextView(activity);
        d.setText(delta);
        d.setTextSize(12);
        d.setTextColor(MainActivity.MUTED);
        c.addView(d);
    }

    private void loaded(ReportEngine.Report report, LinearLayout content) {
        ReportEngine.Summary r = report.current, p = report.previous;
        LinearLayout row = new LinearLayout(activity);
        LinearLayout.LayoutParams r1Lp = new LinearLayout.LayoutParams(-1, -2);
        r1Lp.topMargin = activity.dp(4);
        content.addView(row, r1Lp);
        tile(
                row,
                s("sessions"),
                num(r.sessions),
                compactDelta(r.sessions, p.sessions),
                UiKit.CHART_AMBER,
                true);
        tile(row, s("days"), num(r.days), compactDelta(r.days, p.days), UiKit.RING_EXERCISE, false);

        LinearLayout row2 = new LinearLayout(activity);
        content.addView(row2, new LinearLayout.LayoutParams(-1, -2));
        tile(
                row2,
                s("completed_sets"),
                num(r.sets),
                compactDelta(r.sets, p.sets),
                UiKit.RING_MOVE,
                true);
        tile(
                row2,
                s("duration"),
                num(r.duration / 60000.0) + " " + s("minutes_unit"),
                compactDelta(r.duration / 60000.0, p.duration / 60000.0),
                UiKit.RING_STAND,
                false);

        LinearLayout parts = section(content, "part_chart");
        if (r.parts.isEmpty()) text(parts, s("empty_week"), 14);
        for (Map.Entry<String, Integer> entry : r.parts.entrySet()) {
            text(parts, s("part_summary", activity.partName(entry.getKey()), entry.getValue()), 15);
            ProgressBar bar =
                    new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(Math.max(1, r.sets));
            bar.setProgress(entry.getValue());
            bar.setProgressTintList(
                    android.content.res.ColorStateList.valueOf(UiKit.RING_EXERCISE));
            parts.addView(bar, new LinearLayout.LayoutParams(-1, activity.dp(7)));
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
        LinearLayout chartArea = new LinearLayout(activity);
        chartArea.setOrientation(LinearLayout.VERTICAL);
        Runnable renderChart =
                () -> {
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
        UiKit.segmentedControl(
                activity,
                trend,
                new String[] {s(metrics[0]), s(metrics[1]), s(metrics[2])},
                index,
                selectedIdx -> {
                    activity.metric = metrics[selectedIdx];
                    renderChart.run();
                });
        trend.addView(chartArea, new LinearLayout.LayoutParams(-1, -2));
        renderChart.run();

        LinearLayout exercises = section(content, "exercise_trend");
        button(exercises, s("progress_title"), () -> show("progress"));
        if (report.exercises.isEmpty()) text(exercises, s("no_exercise"), 14);
        else {
            List<ReportEngine.ExerciseTrend> all = new ArrayList<>(report.exercises.values());
            all.sort((a, b) -> {
                int pa = Catalog.partIndex(Catalog.exercisePart(a.id, ""));
                int pb = Catalog.partIndex(Catalog.exercisePart(b.id, ""));
                if (pa != pb) return Integer.compare(pa, pb);
                return a.name.compareToIgnoreCase(b.name);
            });
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
            LinearLayout area = new LinearLayout(activity);
            area.setOrientation(LinearLayout.VERTICAL);
            exercises.addView(area, new LinearLayout.LayoutParams(-1, -2));
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
        header.setMinimumHeight(activity.dp(44));
        header.setFocusable(true);
        card.addView(header, new LinearLayout.LayoutParams(-1, -2));
        TextView label = text(header, s(key), 17);
        label.setTypeface(null, Typeface.BOLD);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        ImageButton arrow = new ImageButton(activity);
        arrow.setTag("report_arrow_" + key);
        arrow.setImageResource(R.drawable.ic_report_expand);
        arrow.setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10));
        arrow.setFocusable(false);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        TypedValue ripple = new TypedValue();
        activity.getTheme()
                .resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        arrow.setBackgroundResource(ripple.resourceId);
        header.addView(arrow, new LinearLayout.LayoutParams(activity.dp(42), activity.dp(42)));
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
        selection.setTextSize(13);
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
        c.addView(view, new LinearLayout.LayoutParams(-1, activity.dp(180)));
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
