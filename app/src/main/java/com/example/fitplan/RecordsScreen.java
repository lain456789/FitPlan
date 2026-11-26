package com.example.fitplan;

import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

final class RecordsScreen extends Screen {
    RecordsScreen(MainActivity activity) {
        super(activity);
    }

    void history() {
        activity.recordTabs();
        title(s("history_title"));
        List<JSONObject> sessions = HistoryReader.sorted(store.list("history"));
        button(content, s("progress_title"), () -> show("progress"));
        if (sessions.size() == 0) text(content, s("empty_history"), 16);
        for (int i = 0; i < sessions.size(); i++) {
            JSONObject session = sessions.get(i);
            LinearLayout c = card(content);
            text(c, activity.planName(session), 20);
            if ("manual".equals(session.optString("recordSource"))) text(c, s("manual_source"), 14);
            if (session.optBoolean("abandoned")) text(c, s("abandoned_workout"), 14);
            text(
                    c,
                    s(
                            "history_line",
                            date(session.optLong("finished")),
                            performedSets(session),
                            num(volume(session))),
                    14);
            button(c, s("correct_record"), () -> activity.openHistory(session));
            button(
                    c,
                    s("delete_record"),
                    () ->
                            confirm(
                                    s("delete_record_confirm"),
                                    () -> {
                                        store.delete(session.optString("id"));
                                        show("history");
                                    }));
        }
    }

    void historyDetail(JSONObject session) {
        content.removeAllViews();
        title(activity.planName(session));
        if ("manual".equals(session.optString("recordSource"))) {
            text(content, s("manual_source"), 14);
            if (!session.optBoolean("durationKnown"))
                text(content, s("manual_duration_unknown"), 14);
        }
        if (session.optBoolean("abandoned")) text(content, s("abandoned_workout"), 14);
        backButton(content, s("back_history"), () -> show("history"));
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
