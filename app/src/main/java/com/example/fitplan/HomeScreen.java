package com.example.fitplan;

import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

/** Daily actuals and plan references loaded with the weekly report. */
final class HomeScreen extends Screen {
    HomeScreen(MainActivity activity) {
        super(activity);
    }

    void render() {
        title(s("dashboard_title"));
        LinearLayout today = card(content);
        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        today.addView(heading);
        TextView title = text(heading, s("today_summary"), 20);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        TextView date = text(heading, activity.displayDay(), 12);
        date.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        date.setGravity(Gravity.END);
        LinearLayout values = new LinearLayout(activity);
        values.setOrientation(LinearLayout.VERTICAL);
        today.addView(values);
        new WeeklyScreen(activity).render(report -> showRings(values, report));
    }

    private void showRings(LinearLayout parent, ReportEngine.Report report) {
        ReportEngine.Summary data = report.today;
        ReportEngine.Targets goals = report.targets;
        parent.removeAllViews();
        String setsGoal = goal(data.sets, goals.sets, s("ring_sets_unit"), false);
        String volumeGoal =
                !goals.hasPlan
                        ? s("ring_no_target")
                        : goals.volumePending
                                ? s("ring_weight_pending")
                                : goals.volume <= 0
                                        ? s("ring_bodyweight")
                                        : goal(data.volume, goals.volume, s("volume_unit"), false);
        String timeGoal = goal(data.duration / 60000.0, goals.minutes, s("minutes_unit"), true);
        String description =
                s("completed_sets")
                        + ": "
                        + num(data.sets)
                        + ", "
                        + setsGoal
                        + "; "
                        + s("total_volume")
                        + ": "
                        + num(data.volume)
                        + " "
                        + s("volume_unit")
                        + ", "
                        + volumeGoal
                        + "; "
                        + s("known_duration")
                        + ": "
                        + num(data.duration / 60000.0)
                        + " "
                        + s("minutes_unit")
                        + ", "
                        + timeGoal;
        LinearLayout chartRow = new LinearLayout(activity);
        boolean stacked =
                activity.getResources().getConfiguration().fontScale > 1.2f
                        || activity.getResources().getConfiguration().screenWidthDp < 320;
        chartRow.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        chartRow.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(chartRow);
        ActivityRingsView rings =
                new ActivityRingsView(activity, data, goals, s("ring_today"), description);
        rings.setTag("today_rings");
        rings.setFocusable(true);
        rings.setOnClickListener(view ->
                new android.app.AlertDialog.Builder(activity)
                        .setTitle(s("definitions"))
                        .setMessage(description + "\n\n" + s("ring_explanation"))
                        .setPositiveButton(s("confirm"), null)
                        .show());
        int diameter =
                stacked
                        ? 160
                        : Math.max(
                                112,
                                Math.min(
                                        166,
                                        (int)
                                                ((activity.getResources()
                                                                        .getConfiguration()
                                                                        .screenWidthDp
                                                                - 84)
                                                        * .45f)));
        LinearLayout.LayoutParams ringParams =
                new LinearLayout.LayoutParams(activity.dp(diameter), activity.dp(diameter));
        ringParams.gravity = stacked ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL;
        if (!stacked) ringParams.setMarginEnd(activity.dp(12));
        chartRow.addView(rings, ringParams);
        LinearLayout legend = new LinearLayout(activity);
        legend.setOrientation(LinearLayout.VERTICAL);
        chartRow.addView(
                legend,
                stacked
                        ? new LinearLayout.LayoutParams(-1, -2)
                        : new LinearLayout.LayoutParams(0, -2, 1));
        metric(
                legend,
                "completed_sets",
                num(data.sets),
                setsGoal,
                "today_sets",
                ActivityRingsView.SETS);
        metric(
                legend,
                "total_volume",
                num(data.volume) + " " + s("volume_unit"),
                volumeGoal,
                "today_volume",
                ActivityRingsView.VOLUME);
        metric(
                legend,
                "known_duration",
                num(data.duration / 60000.0) + " " + s("minutes_unit"),
                timeGoal,
                "today_duration",
                ActivityRingsView.TIME);
        LinearLayout sessions = new LinearLayout(activity);
        sessions.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(sessions);
        TextView label = text(sessions, s("sessions"), 14);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        TextView number = text(sessions, num(data.sessions), 18);
        number.setTag("today_sessions");
        text(parent, s("ring_short_help"), 12);
        TextView source =
                text(parent, s(goals.hasPlan ? "ring_plan_source" : "ring_make_plan"), 14);
        source.setTextColor(MainActivity.ACCENT);
        source.setTag("ring_plan_action");
        source.setMinHeight(activity.dp(48));
        source.setGravity(Gravity.CENTER_VERTICAL);
        source.setFocusable(true);
        source.setBackground(activity.shape(0xff293326, 12));
        source.setPadding(activity.dp(10), activity.dp(6), activity.dp(10), activity.dp(6));
        source.setOnClickListener(view -> {
            JSONObject plan = activity.find("plan", goals.planId);
            if (plan != null) activity.openPlan(plan);
            else show("dailyPlan");
        });
    }

    private String goal(double actual, double target, String unit, boolean budget) {
        if (!Double.isFinite(target) || target <= 0) return s("ring_no_target");
        return s(
                budget ? "ring_time_target" : "ring_goal",
                num(target),
                unit,
                num(Math.max(0, actual) / target * 100));
    }

    private void metric(
            LinearLayout parent, String label, String value, String target, String tag, int color) {
        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, activity.dp(4), 0, activity.dp(4));
        parent.addView(block);
        TextView caption = text(block, s(label), 12);
        caption.setTextColor(color);
        caption.setPadding(0, 0, 0, activity.dp(2));
        TextView actual = text(block, value, 20);
        actual.setTypeface(null, Typeface.BOLD);
        actual.setTextColor(color);
        actual.setTag(tag);
        actual.setPadding(0, 0, 0, 0);
        TextView comparison = text(block, target, 12);
        comparison.setTag(tag + "_target");
        comparison.setPadding(0, activity.dp(2), 0, 0);
    }
}
