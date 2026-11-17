package com.example.fitplan;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;
import static com.example.fitplan.Store.*;

final class RecordsScreen extends Screen {
    RecordsScreen(MainActivity a){super(a);}
    void history() {
        a.recordTabs();
        title(s("history_title")); JSONArray sessions=store.list("history");
        button(content,s("progress_title"),()->show("progress"));
        if(sessions.length()==0)text(content,s("empty_history"),16);
        for(int i=0;i<sessions.length();i++) {JSONObject session=sessions.optJSONObject(i);LinearLayout c=card(content);
            text(c,a.planName(session),20);text(c,s("history_line",date(session.optLong("finished")),completed(session),num(volume(session))),14);
            button(c,s("correct_record"),()->a.openHistory(session));
            button(c,s("delete_record"),()->confirm(s("delete_record_confirm"),()->{store.delete(session.optString("id"));show("history");}));
        }
    }
    void historyDetail(JSONObject session) {
        content.removeAllViews();title(a.planName(session));button(content,s("back_history"),()->show("history"));
        text(content,s("session_time",date(session.optLong("started")),date(session.optLong("finished"))),14);
        button(content,s("progress_title"),()->{JSONArray rows=session.optJSONArray("rows");if(rows!=null&&rows.length()>0)a.exerciseId=rows.optJSONObject(0).optString("exerciseId");show("progress");});
        JSONObject check=session.optJSONObject("checkin");
        if(check!=null)text(content,s("snapshot",check.optString("day"),check.optString("minutes"),new String[]{s("good"),s("fair"),s("poor")}[check.optInt("sleep")],new String[]{s("adequate"),s("insufficient"),s("poor")}[check.optInt("food")],a.sessionNotes(session,check)),14);
        new TrainingScreen(a).adjustmentHistory(session);
        renderLogs(session,false);
    }
}
