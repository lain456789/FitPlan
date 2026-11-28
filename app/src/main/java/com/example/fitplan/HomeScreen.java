package com.example.fitplan;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

/** Apple Fitness-style Summary dashboard: tri-color daily rings + compact weekly bento. */
final class HomeScreen extends Screen {
    HomeScreen(MainActivity activity) {
        super(activity);
    }

    void render() {
        TextView eyebrow = text(content, activity.displayDay(), 13);
        eyebrow.setTextColor(MainActivity.MUTED);
        eyebrow.setPadding(0, activity.dp(4), 0, 0);
        title(s("dashboard_title"));

        LinearLayout today = card(content);
        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        today.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        TextView cardTitle = text(heading, s("today_summary"), 18);
        cardTitle.setTypeface(null, Typeface.BOLD);
        cardTitle.setPadding(0, 0, 0, activity.dp(6));
        cardTitle.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout values = new LinearLayout(activity);
        values.setOrientation(LinearLayout.VERTICAL);
        today.addView(values, new LinearLayout.LayoutParams(-1, -2));
        new WeeklyScreen(activity).render(report -> showRings(values, report));
    }

    private void showRings(LinearLayout parent, ReportEngine.Report report) {
        ReportEngine.Summary data = report.today;
        ReportEngine.Targets goals = report.targets;
        parent.removeAllViews();

        String setsGoal = compactTarget(goals.sets, s("ring_sets_unit"));
        String exerciseGoal = compactTarget(goals.exercises, s("ring_exercises_unit"));
        String timeGoal = compactTarget(goals.minutes, s("minutes_unit"));

        String description =
                s("completed_sets")
                        + ": "
                        + num(data.sets)
                        + " "
                        + setsGoal
                        + "; "
                        + s("completed_exercises")
                        + ": "
                        + num(data.completedExercises)
                        + " "
                        + exerciseGoal
                        + "; "
                        + s("known_duration")
                        + ": "
                        + num(data.duration / 60000.0)
                        + " "
                        + timeGoal;

        LinearLayout chartRow = new LinearLayout(activity);
        boolean stacked =
                activity.getResources().getConfiguration().fontScale > 1.2f
                        || activity.getResources().getConfiguration().screenWidthDp < 320;
        chartRow.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        chartRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams chartLp = new LinearLayout.LayoutParams(-1, -2);
        chartLp.topMargin = activity.dp(4);
        chartLp.bottomMargin = activity.dp(8);
        parent.addView(chartRow, chartLp);

        ActivityRingsView rings = new ActivityRingsView(activity, data, goals, description);
        rings.setTag("today_rings");
        rings.setFocusable(true);
        rings.setOnClickListener(view ->
                new android.app.AlertDialog.Builder(activity)
                        .setTitle(s("definitions"))
                        .setMessage(
                                description
                                        + "\n\n"
                                        + s("ring_exercises_explanation")
                                        + "\n\n"
                                        + s("ring_explanation"))
                        .setPositiveButton(s("confirm"), null)
                        .show());

        int diameter =
                stacked
                        ? 156
                        : Math.max(
                                118,
                                Math.min(
                                        164,
                                        (int)
                                                ((activity.getResources()
                                                                        .getConfiguration()
                                                                        .screenWidthDp
                                                                - 84)
                                                        * .44f)));
        LinearLayout.LayoutParams ringParams =
                new LinearLayout.LayoutParams(activity.dp(diameter), activity.dp(diameter));
        ringParams.gravity = stacked ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL;
        if (!stacked) ringParams.setMarginEnd(activity.dp(16));
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
                "completed_exercises",
                num(data.completedExercises),
                exerciseGoal,
                "today_exercises",
                ActivityRingsView.EXERCISES);
        metric(
                legend,
                "known_duration",
                num(data.duration / 60000.0),
                timeGoal,
                "today_duration",
                ActivityRingsView.TIME);

        UiKit.divider(activity, parent);

        // Single clean iOS footer bar: sessions count on left + today's plan chevron action on
        // right
        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, activity.dp(6), 0, activity.dp(2));
        parent.addView(footer, new LinearLayout.LayoutParams(-1, -2));

        TextView sessionBadge = new TextView(activity);
        sessionBadge.setText(s("sessions") + " " + num(data.sessions));
        sessionBadge.setTextSize(13);
        sessionBadge.setTextColor(MainActivity.MUTED);
        sessionBadge.setTag("today_sessions");
        footer.addView(sessionBadge, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout planAction = new LinearLayout(activity);
        planAction.setOrientation(LinearLayout.HORIZONTAL);
        planAction.setGravity(Gravity.CENTER_VERTICAL);
        planAction.setTag("ring_plan_action");
        planAction.setFocusable(true);
        planAction.setBackground(
                UiKit.rippleBox(activity, UiKit.ADD_BG, UiKit.ADD_BORDER, 16, 0x33A6FF00));
        planAction.setPadding(activity.dp(12), activity.dp(7), activity.dp(8), activity.dp(7));
        planAction.setOnClickListener(view -> {
            JSONObject plan = activity.find("plan", goals.planId);
            if (plan != null) activity.openPlan(plan);
            else show("dailyPlan");
        });

        TextView source = new TextView(activity);
        source.setText(s(goals.hasPlan ? "ring_plan_source" : "ring_make_plan"));
        source.setTextSize(13);
        source.setTextColor(MainActivity.ACCENT);
        source.setTypeface(null, Typeface.BOLD);
        planAction.addView(source);

        ImageView chevron = new ImageView(activity);
        chevron.setImageDrawable(
                UiKit.icon(activity, R.drawable.ic_action_chevron, 16, MainActivity.ACCENT));
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams chevLp =
                new LinearLayout.LayoutParams(activity.dp(16), activity.dp(16));
        chevLp.setMarginStart(activity.dp(2));
        planAction.addView(chevron, chevLp);

        footer.addView(planAction, new LinearLayout.LayoutParams(-2, -2));
    }

    private String compactTarget(double target, String unit) {
        if (!Double.isFinite(target) || target <= 0) return unit;
        return "/ " + num(target) + " " + unit;
    }

    private void metric(
            LinearLayout parent, String label, String value, String target, String tag, int color) {
        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, activity.dp(4), 0, activity.dp(4));
        parent.addView(block, new LinearLayout.LayoutParams(-1, -2));

        TextView caption = new TextView(activity);
        caption.setText(s(label));
        caption.setTextSize(12);
        caption.setTextColor(MainActivity.MUTED);
        block.addView(caption);

        LinearLayout valRow = new LinearLayout(activity);
        valRow.setOrientation(LinearLayout.HORIZONTAL);
        valRow.setBaselineAligned(true);
        valRow.setGravity(Gravity.BOTTOM);
        block.addView(valRow, new LinearLayout.LayoutParams(-1, -2));

        TextView actual = new TextView(activity);
        actual.setText(value);
        actual.setTextSize(22);
        actual.setTypeface(null, Typeface.BOLD);
        actual.setTextColor(color);
        actual.setTag(tag);
        valRow.addView(actual);

        TextView comparison = new TextView(activity);
        comparison.setText(" " + target);
        comparison.setTextSize(12);
        comparison.setTextColor(MainActivity.MUTED);
        comparison.setTag(tag + "_target");
        valRow.addView(comparison);
    }
}
