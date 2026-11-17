package com.example.fitplan;

import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

final class RecordsScreen extends Screen {
    RecordsScreen(MainActivity activity) {
        super(activity);
    }

    void history() {
        activity.recordTabs();
        title(s("history_title"));
        JSONArray sessions = store.list("history");
        button(content, s("progress_title"), () -> show("progress"));
        if (sessions.length() == 0) text(content, s("empty_history"), 16);
        for (int i = 0; i < sessions.length(); i++) {
            JSONObject session = sessions.optJSONObject(i);
            LinearLayout c = card(content);
            text(c, activity.planName(session), 20);
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
        button(content, s("back_history"), () -> show("history"));
        text(
                content,
                s(
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
        new TrainingScreen(activity).adjustmentHistory(session);
        renderLogs(session, false);
    }
}
