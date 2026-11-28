package com.example.fitplan;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.Typeface;
import android.graphics.drawable.RotateDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.LongConsumer;

final class RecordsScreen extends Screen {
    RecordsScreen(MainActivity activity) {
        super(activity);
    }

    void history() {
        activity.recordTabs();
        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(heading);
        TextView title = text(heading, s("history_title"), 22);
        title.setTypeface(null, Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        Button dates =
                UiKit.secondaryButton(
                        activity,
                        heading,
                        s("history_date_filter"),
                        R.drawable.ic_calendar,
                        this::chooseDateFilter);
        dates.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        dates.setMinHeight(activity.dp(42));
        HistoryReader.DateFilter filter = activity.historyDateFilter;
        if (filter != HistoryReader.DateFilter.ALL) {
            String selected =
                    filter == HistoryReader.DateFilter.CUSTOM
                            ? s(
                                    "history_range_value",
                                    dayLabel(activity.historyRangeStart),
                                    dayLabel(activity.historyRangeEnd))
                            : s(filter.label);
            text(content, selected, 13);
        }
        Map<Long, List<JSONObject>> months =
                HistoryReader.byMonth(
                        store.list("history"),
                        filter,
                        activity.historyRangeStart,
                        activity.historyRangeEnd,
                        System.currentTimeMillis(),
                        TimeZone.getDefault());
        if (months.isEmpty()) {
            text(
                    content,
                    s(
                            filter == HistoryReader.DateFilter.ALL
                                    ? "empty_history"
                                    : "empty_history_range"),
                    16);
            return;
        }
        SimpleDateFormat monthLabel =
                new SimpleDateFormat(s("history_month_pattern"), activity.locale());
        boolean latest = true;
        for (Map.Entry<Long, List<JSONObject>> month : months.entrySet()) {
            renderMonth(
                    month.getKey(),
                    monthLabel.format(new Date(month.getKey())),
                    month.getValue(),
                    latest);
            latest = false;
        }
    }

    private void renderMonth(long month, String label, List<JSONObject> sessions, boolean latest) {
        String key = Long.toString(month);
        LinearLayout group = new LinearLayout(activity);
        group.setOrientation(LinearLayout.VERTICAL);
        content.addView(group, new LinearLayout.LayoutParams(-1, -2));
        RotateDrawable arrow = new RotateDrawable();
        arrow.setDrawable(activity.getDrawable(R.drawable.ic_report_expand));
        arrow.setFromDegrees(0);
        arrow.setToDegrees(180);
        String heading =
                s(
                        sessions.size() == 1
                                ? "history_month_heading_one"
                                : "history_month_heading",
                        label,
                        sessions.size());
        Button header = button(group, heading, () -> {});
        header.setBackground(null);
        header.setTextSize(16);
        header.setTypeface(null, Typeface.BOLD);
        header.setTextColor(MainActivity.FG);
        header.setPadding(activity.dp(4), activity.dp(8), activity.dp(4), activity.dp(6));
        header.setMinHeight(activity.dp(42));
        header.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        header.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, arrow, null);
        header.setCompoundDrawablePadding(activity.dp(8));
        LinearLayout records = new LinearLayout(activity);
        records.setOrientation(LinearLayout.VERTICAL);
        group.addView(records, new LinearLayout.LayoutParams(-1, -2));
        Runnable update =
                () -> {
                    boolean expanded = activity.historyMonthExpansion.getBoolean(key, latest);
                    records.setVisibility(expanded ? View.VISIBLE : View.GONE);
                    if (expanded) {
                        for (JSONObject session : sessions) historyCard(records, session);
                    } else {
                        records.removeAllViews();
                    }
                    arrow.setLevel(expanded ? 10000 : 0);
                    ViewCompat.setStateDescription(
                            header, s(expanded ? "section_expanded" : "section_collapsed"));
                };
        header.setOnClickListener(view -> {
            boolean expanded = activity.historyMonthExpansion.getBoolean(key, latest);
            activity.historyMonthExpansion.putBoolean(key, !expanded);
            update.run();
        });
        update.run();
    }

    private void historyCard(LinearLayout parent, JSONObject session) {
        LinearLayout c = card(parent);
        c.setOnClickListener(v -> activity.openHistory(session));

        TextView dateEyebrow = new TextView(activity);
        dateEyebrow.setText(date(session.optLong("finished")));
        dateEyebrow.setTextSize(12);
        dateEyebrow.setTextColor(MainActivity.MUTED);
        c.addView(dateEyebrow);

        TextView nameView = text(c, activity.planName(session), 18);
        nameView.setTypeface(null, Typeface.BOLD);
        nameView.setPadding(0, activity.dp(2), 0, activity.dp(4));

        LinearLayout badges = new LinearLayout(activity);
        badges.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(-1, -2);
        bLp.bottomMargin = activity.dp(8);
        c.addView(badges, bLp);

        UiKit.badge(
                activity,
                badges,
                performedSets(session) + " " + s("ring_sets_unit"),
                UiKit.RING_MOVE);
        UiKit.badge(
                activity,
                badges,
                num(volume(session)) + " " + s("volume_unit"),
                UiKit.RING_EXERCISE);
        if ("manual".equals(session.optString("recordSource"))) {
            UiKit.badge(activity, badges, s("manual_source"), UiKit.RING_STAND);
        }
        if (session.optBoolean("abandoned")) {
            UiKit.badge(activity, badges, s("abandoned_workout"), UiKit.CHART_AMBER);
        }

        // Keep correct_record directly inside card for clean tap + test compatibility; circular
        // trash beside it
        Button edit = editButton(c, s("correct_record"), () -> activity.openHistory(session));
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(-1, -2);
        editLp.topMargin = activity.dp(2);
        edit.setLayoutParams(editLp);

        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.END);
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(-1, -2);
        fLp.topMargin = activity.dp(6);
        c.addView(footer, fLp);
        UiKit.iconDeleteButton(
                activity,
                footer,
                s("delete_record"),
                () ->
                        confirm(
                                s("delete_record_confirm"),
                                () -> {
                                    store.delete(session.optString("id"));
                                    refreshAtCurrentPosition("history");
                                }));
    }

    private void chooseDateFilter() {
        HistoryReader.DateFilter[] filters = HistoryReader.DateFilter.values();
        String[] options = new String[filters.length];
        for (int i = 0; i < filters.length; i++) options[i] = s(filters[i].label);
        activity.activeDialog =
                new AlertDialog.Builder(activity)
                        .setTitle(s("history_date_filter"))
                        .setSingleChoiceItems(
                                options,
                                activity.historyDateFilter.ordinal(),
                                (dialog, index) -> {
                                    dialog.dismiss();
                                    HistoryReader.DateFilter selected = filters[index];
                                    if (selected == HistoryReader.DateFilter.CUSTOM)
                                        chooseDateRange();
                                    else applyDateFilter(selected);
                                })
                        .setNegativeButton(s("cancel"), null)
                        .show();
    }

    private void chooseDateRange() {
        long today = ReportEngine.dayStart(System.currentTimeMillis(), TimeZone.getDefault());
        long[] range = {
            activity.historyRangeStart == 0 ? today : activity.historyRangeStart,
            activity.historyRangeEnd == 0 ? today : activity.historyRangeEnd
        };
        LinearLayout form = column();
        addRangeDateButton(form, range, 0, "history_start_date");
        addRangeDateButton(form, range, 1, "history_end_date");
        dialog(
                s("history_custom_range"),
                form,
                s("history_apply_dates"),
                () -> {
                    if (range[0] > range[1])
                        throw new IllegalArgumentException(s("history_invalid_range"));
                    activity.historyRangeStart = range[0];
                    activity.historyRangeEnd = range[1];
                    applyDateFilter(HistoryReader.DateFilter.CUSTOM);
                });
    }

    private void addRangeDateButton(LinearLayout form, long[] range, int index, String label) {
        Button date = button(form, s(label, dayLabel(range[index])), () -> {});
        date.setOnClickListener(view ->
                pickDate(
                        range[index],
                        picked -> {
                            range[index] = picked;
                            date.setText(s(label, dayLabel(picked)));
                        }));
    }

    private void pickDate(long initial, LongConsumer onSelected) {
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(initial);
        AlertDialog parent = activity.activeDialog;
        DatePickerDialog picker =
                new DatePickerDialog(
                        activity,
                        (view, year, month, day) -> {
                            date.clear();
                            date.set(year, month, day);
                            onSelected.accept(date.getTimeInMillis());
                        },
                        date.get(Calendar.YEAR),
                        date.get(Calendar.MONTH),
                        date.get(Calendar.DAY_OF_MONTH));
        picker.getDatePicker().setMaxDate(System.currentTimeMillis());
        picker.setOnDismissListener(dialog -> {
            if (activity.activeDialog == picker) activity.activeDialog = parent;
        });
        activity.activeDialog = picker;
        picker.show();
    }

    private void applyDateFilter(HistoryReader.DateFilter filter) {
        activity.historyDateFilter = filter;
        activity.historyMonthExpansion.clear();
        show("history");
    }

    private String dayLabel(long date) {
        return DateFormat.getDateInstance(DateFormat.MEDIUM, activity.locale())
                .format(new Date(date));
    }

    void historyDetail(JSONObject session) {
        content.removeAllViews();
        backButton(content, s("back_history"), () -> show("history"));
        title(activity.planName(session));
        if ("manual".equals(session.optString("recordSource"))) {
            text(content, s("manual_source"), 14);
            if (!session.optBoolean("durationKnown"))
                text(content, s("manual_duration_unknown"), 14);
        }
        if (session.optBoolean("abandoned")) text(content, s("abandoned_workout"), 14);
        text(
                content,
                "manual".equals(session.optString("recordSource"))
                                && !session.optBoolean("durationKnown")
                        ? s("manual_time", date(session.optLong("finished")))
                        : s(
                                "session_time",
                                date(session.optLong("started")),
                                date(session.optLong("finished"))),
                14);
        button(
                content,
                s("progress_title"),
                () -> {
                    JSONArray rows = session.optJSONArray("rows");
                    if (rows != null && rows.length() > 0)
                        activity.exerciseId = rows.optJSONObject(0).optString("exerciseId");
                    show("progress");
                });
        JSONObject check = session.optJSONObject("checkin");
        if (check != null)
            text(
                    content,
                    s(
                            "snapshot",
                            check.optString("day"),
                            check.optString("minutes"),
                            new String[] {s("good"), s("fair"), s("poor")}[check.optInt("sleep")],
                            new String[] {s("adequate"), s("insufficient"), s("poor")}
                                    [check.optInt("food")],
                            activity.sessionNotes(session, check)),
                    14);
        new TrainingScreen(activity).adjustmentHistory(session, content);
        renderLogs(session, false, content, null);
    }
}
