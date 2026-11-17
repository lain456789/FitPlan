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

final class HomeScreen extends Screen {
    HomeScreen(MainActivity a){super(a);}
    JSONObject todayCheck() {
        JSONArray checks=store.list("checkin"); for(int i=0;i<checks.length();i++) { JSONObject c=checks.optJSONObject(i); if(day().equals(c.optString("day"))) return c; } return null;
    }
    void today() {
        title(s("today_title")); text(content,a.displayDay()+" · "+s("local_daily"),14);
        JSONObject check=a.homeDraft; if(check==null || !day().equals(check.optString("day"))) check=todayCheck(); if(check==null) check=object();
        a.loadHomeSummary(content);
        LinearLayout c=card(content); text(c,s("parts_question"),18);
        List<CheckBox> parts=new ArrayList<>(); JSONArray selected=check.optJSONArray("parts");
        for(String part:PARTS) { CheckBox box=new CheckBox(a); box.setText(a.partName(part));box.setTag(part); box.setChecked(selected!=null && a.containsPart(selected,part)); c.addView(box); parts.add(box); }
        EditText minutes=input(c,s("minutes_label"),check.optString("minutes","45"),true);
        Spinner volume=select(c,s("volume_label"),new String[]{s("light"),s("normal"),s("high")},check.optInt("volume",1));
        Spinner sleep=select(c,s("sleep_label"),new String[]{s("good"),s("fair"),s("poor")},check.optInt("sleep"));
        Spinner food=select(c,s("food_label"),new String[]{s("adequate"),s("insufficient"),s("poor")},check.optInt("food"));
        Spinner soreness=select(c,s("soreness_label"),new String[]{s("none_sore"),s("mild_sore"),s("severe_sore")},check.optInt("soreness"));
        EditText notes=input(c,s("notes_label"),check.optString("notes"),false);
        text(c,s("rules"),14);
        a.captureHome = () -> {
            JSONArray picked=new JSONArray();for(CheckBox box:parts)if(box.isChecked())picked.put(box.getTag().toString());
            a.homeDraft=object("day",day(),"parts",picked,"minutes",minutes.getText().toString(),"volume",volume.getSelectedItemPosition(),"sleep",sleep.getSelectedItemPosition(),"food",food.getSelectedItemPosition(),"soreness",soreness.getSelectedItemPosition(),"notes",notes.getText().toString());
        };
        button(c,s("generate"),()->{
            try {
                JSONArray targets=new JSONArray(); for(CheckBox box:parts) if(box.isChecked()) targets.put(box.getTag().toString());
                if(targets.length()==0) { toast(s("choose_part")); return; }
                int time=integer(minutes,10,180);
                JSONObject saved=object("day",day(),"created",System.currentTimeMillis(),"parts",targets,"minutes",time,
                    "volume",volume.getSelectedItemPosition(),"sleep",sleep.getSelectedItemPosition(),"food",food.getSelectedItemPosition(),"soreness",soreness.getSelectedItemPosition(),"notes",notes.getText().toString());
                JSONObject plan=submitCheckin(saved);
                if(plan==null) { toast(s("rest_only")); return; }
                show("plans");
            } catch(IllegalArgumentException e) { toast(e.getMessage()); }
            catch(android.database.sqlite.SQLiteException e) { toast(s("daily_save_error")); }
        });
        if(store.first("active")!=null) button(content,s("continue_workout"),()->show("workout"));
    }
    JSONObject submitCheckin(JSONObject check){
        JSONObject plan=check.optInt("soreness")==2?null:generate(check);
        store.saveDailyPlan(check,plan);
        return plan;
    }
    JSONObject generate(JSONObject check) {
        JSONArray rows=new JSONArray(), candidates=new JSONArray(), catalogue=store.list("exercise"), targets=check.optJSONArray("parts");
        boolean reduced=check.optInt("sleep")==2 || check.optInt("food")>0 || check.optInt("soreness")==1;
        int baseSets=check.optInt("volume")+2;
        // Round robin fixes a baseline order, shared by normal and reduced plans.
        for(int round=0; round<3; round++) {
            for(int p=0;p<targets.length();p++) {
                int found=0;
                for(int i=0;i<catalogue.length();i++) {
                    JSONObject ex=catalogue.optJSONObject(i);
                    if(ex.optBoolean("replacementOnly"))continue;
                    if(!targets.optString(p).equals(Catalog.partCode(ex.optString("part")))) continue;
                    if(found++!=round) continue;
                    candidates.put(ex);break;
                }
            }
        }
        Recommendation.Budget budget=Recommendation.budget(check.optInt("minutes"),baseSets,candidates.length(),reduced);
        for(int i=0;i<budget.exercises;i++){
            JSONObject ex=candidates.optJSONObject(i),reference=recentReference(ex.optString("id"),10,budget.sets);
            JSONObject row=object("exerciseId",ex.optString("id"),"name",a.exerciseName(ex),"part",Catalog.partCode(ex.optString("part")),
                    "weight",reference==null?0:reference.optDouble("weight"),"sets",budget.sets,"reps",10,
                    "reasonCode",reference==null?"first":"comparable","needsWeightConfirmation",reference==null,
                    "referencePolicyVersion",2,"reduced",budget.reduced,"timeLimited",budget.timeLimited,
                    "reference",reference==null?JSONObject.NULL:reference);
            ProgressEngine.Assessment analysis=ProgressReader.assess(store.list("history"),ex.optString("id"));
            if(analysis.latest!=null){JSONObject advice=ProgressReader.snapshot(analysis);put(advice,"stateAllowsProgression",!reduced);put(row,"progressAdvice",advice);}
            rows.put(row);
        }
        return object("name",s("daily_plan",a.displayDay()),"autoName",true,"created",System.currentTimeMillis(),
                "checkin",copy(check),"rows",rows,"minutes",check.optInt("minutes"),"noteCode","plan_note",
                "ruleVersion",2,"baselineTotalSets",budget.baselineTotalSets(),"timeLimited",budget.timeLimited);
    }
    JSONObject recentReference(String exerciseId,int targetReps,int targetSets) {
        ArrayList<JSONObject> sessions=new ArrayList<>(); JSONArray history=store.list("history");
        for(int i=0;i<history.length();i++) sessions.add(history.optJSONObject(i));
        sessions.sort((a,b)->Long.compare(b.optLong("finished"),a.optLong("finished")));
        for(JSONObject session:sessions) {
            if(session.optLong("finished")<=0)continue;
            JSONArray rows=session.optJSONArray("rows");if(rows==null)continue;
            double minimum=Double.POSITIVE_INFINITY;int completedSets=0;
            for(int r=0;r<rows.length();r++) {
                JSONObject row=rows.optJSONObject(r); if(!exerciseId.equals(row.optString("exerciseId"))) continue;
                JSONArray logs=row.optJSONArray("logs");if(logs==null)continue;
                List<Recommendation.ActualSet> actual=new ArrayList<>();
                for(int i=0;i<logs.length();i++){JSONObject log=logs.optJSONObject(i);if(log==null)continue;
                    actual.add(new Recommendation.ActualSet(log.optDouble("weight",-1),log.optInt("reps"),log.optBoolean("done")));}
                double weight=Recommendation.comparableWeight(actual,row.optInt("reps"),row.optInt("sets"),targetReps,targetSets);
                if(weight>=0){minimum=Math.min(minimum,weight);completedSets+=actual.size();}
            }
            if(Double.isFinite(minimum))return object("sessionId",session.optString("id"),"finished",session.optLong("finished"),
                    "weight",minimum,"reps",targetReps,"sets",completedSets);
        }
        return null;
    }
}
