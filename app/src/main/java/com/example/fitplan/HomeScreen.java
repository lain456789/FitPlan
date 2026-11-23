package com.example.fitplan;

import android.widget.LinearLayout;
import android.widget.TextView;

/** Today and weekly statistics share a single background report snapshot. */
final class HomeScreen extends Screen {
    HomeScreen(MainActivity activity) {
        super(activity);
    }

    void render() {
        title(s("dashboard_title"));
        LinearLayout today = card(content);
        text(today, s("today_summary") + " · " + activity.displayDay(), 18);
        LinearLayout values = column();
        values.setPadding(0, 0, 0, 0);
        today.addView(values);
        new WeeklyScreen(activity)
                .render(report -> {
                    values.removeAllViews();
                    ReportEngine.Summary data = report.today;
                    LinearLayout top = new LinearLayout(activity);
                    LinearLayout bottom = new LinearLayout(activity);
                    values.addView(top);
                    values.addView(bottom);
                    stat(top, "sessions", num(data.sessions), "today_sessions");
                    stat(top, "completed_sets", num(data.sets), "today_sets");
                    stat(
                            bottom,
                            "total_volume",
                            num(data.volume) + " " + s("volume_unit"),
                            "today_volume");
                    stat(
                            bottom,
                            "known_duration",
                            num(data.duration / 60000.0) + " " + s("minutes_unit"),
                            "today_duration");
                    text(values, s("known_duration_help"), 12);
                    if (data.sessions == 0) {
                        text(values, s("empty_today"), 14);
                        button(values, s("go_training"), () -> show("dailyPlan"));
                    }
                });
    }

    private void stat(LinearLayout parent, String label, String value, String tag) {
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(0, activity.dp(4), activity.dp(8), activity.dp(4));
        parent.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
        TextView name = text(cell, s(label), 13);
        name.setPadding(0, 0, 0, activity.dp(2));
        TextView number = text(cell, value, 21);
        number.setPadding(0, 0, 0, 0);
        number.setTag(tag);
        number.setTextColor(MainActivity.ACCENT);
    }
}
