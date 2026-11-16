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

final class TrainingScreen extends Screen {
    TrainingScreen(MainActivity a){super(a);}
    void exercises() {
        a.trainingTabs();
        title(s("library")); text(content,s("library_intro"),14);
        EditText search=input(content,s("search"),"",false);
        Spinner filter=select(content,s("part_filter"),new String[]{s("all"),s("chest"),s("shoulders"),s("back"),s("legs"),s("arms"),s("core")},0);
        button(content,s("add_custom"),()->{
            LinearLayout form=column(); EditText name=input(form,s("exercise_name"),"",false); Spinner part=select(form,s("part"),a.partLabels(),0);
            dialog(s("custom"),form,()->{ String n=name.getText().toString().trim(); if(n.isEmpty()) throw new IllegalArgumentException(s("name_required"));
                store.save("exercise",object("name",n,"part",PARTS[part.getSelectedItemPosition()])); show("library"); });
        });
        LinearLayout results=column(); content.addView(results);
        Runnable render=()->{
            results.removeAllViews(); JSONArray all=store.list("exercise"); int count=0;
            for(int i=0;i<all.length();i++) { JSONObject ex=all.optJSONObject(i);
                if(!a.exerciseName(ex).toLowerCase(a.locale()).contains(search.getText().toString().trim().toLowerCase(a.locale()))) continue;
                if(filter.getSelectedItemPosition()!=0 && !PARTS[filter.getSelectedItemPosition()-1].equals(Catalog.partCode(ex.optString("part")))) continue;
                count++; LinearLayout item=card(results); text(item,a.exerciseName(ex),18); text(item,a.partName(ex.optString("part")),14);
            } if(count==0) text(results,s("no_matches"),16);
        };
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){} public void onTextChanged(CharSequence s,int a,int b,int c){render.run();} public void afterTextChanged(Editable e){}});
        filter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){render.run();}public void onNothingSelected(android.widget.AdapterView<?> p){}}); render.run();
    }
    void plans() {
        a.trainingTabs();
        title(s("plans_title")); text(content,s("plans_intro"),14);
        button(content,s("new_plan"),()->{
            LinearLayout form=column(); EditText name=input(form,s("plan_name"),s("my_plan"),false);
            dialog(s("new_plan_title"),form,()->{ String n=name.getText().toString().trim(); if(n.isEmpty()) throw new IllegalArgumentException(s("enter_name"));
                store.save("plan",object("name",n,"created",System.currentTimeMillis(),"rows",new JSONArray())); show("plans"); });
        });
        JSONArray plans=store.list("plan"); if(plans.length()==0) text(content,s("empty_plans"),16);
        for(int i=0;i<plans.length();i++) {
            JSONObject plan=plans.optJSONObject(i); LinearLayout c=card(content); text(c,a.planName(plan),20);
            text(c,s("exercise_count",plan.optJSONArray("rows").length()),14);
            button(c,s("view_edit"),()->a.openPlan(plan)); button(c,s("start_workout"),()->start(plan));
            button(c,s("delete_plan"),()->confirm(s("delete_plan_confirm"),()->{store.delete(plan.optString("id"));show("plans");}));
        }
    }
    void editPlan(JSONObject plan) {
        content.removeAllViews(); title(a.planName(plan));
        if(plan.has("note") || plan.has("noteCode")) text(content,s("plan_note"),14);
        button(content,s("back_plans"),()->show("plans"));
        button(content,s("rename"),()->{ LinearLayout f=column(); EditText n=input(f,s("name"),a.planName(plan),false);
            dialog(s("rename_plan"),f,()->{if(n.getText().toString().trim().isEmpty()) throw new IllegalArgumentException(s("enter_name"));put(plan,"name",n.getText().toString().trim());put(plan,"autoName",false);store.save("plan",plan);a.openPlan(plan);}); });
        button(content,s("add_exercise"),()->{
            JSONArray catalogue=store.list("exercise"); String[] labels=new String[catalogue.length()];
            for(int i=0;i<labels.length;i++) labels[i]=a.exerciseName(catalogue.optJSONObject(i));
            new AlertDialog.Builder(a).setTitle(s("choose_exercise")).setItems(labels,(d,index)->{
                JSONObject ex=catalogue.optJSONObject(index);
                JSONObject row=object("exerciseId",ex.optString("id"),"name",a.exerciseName(ex),"part",ex.optString("part"),"weight",0,"sets",3,"reps",10);
                editPrescription(row,()->{plan.optJSONArray("rows").put(row);store.save("plan",plan);a.openPlan(plan);});
            }).show();
        });
        JSONArray rows=plan.optJSONArray("rows");
        for(int i=0;i<rows.length();i++) {
            final int index=i; JSONObject row=rows.optJSONObject(i); LinearLayout c=card(content);
            text(c,a.exerciseName(row),18); text(c,s("prescription",num(row.optDouble("weight")),row.optInt("sets"),row.optInt("reps")),16);
            if(row.has("reason") || row.has("reasonCode")) text(c,a.reason(row),14);
            button(c,s("adjust"),()->editPrescription(row,()->{store.save("plan",plan);a.openPlan(plan);}));
            button(c,s("remove_exercise"),()->{rows.remove(index);store.save("plan",plan);a.openPlan(plan);});
        }
        button(content,s("start_plan"),()->start(plan));
    }
    void editPrescription(JSONObject row,Runnable save) {
        LinearLayout form=column(); EditText weight=input(form,s("weight_label"),num(row.optDouble("weight")),true);
        EditText sets=input(form,s("sets_label"),row.optString("sets","3"),true), reps=input(form,s("reps_label"),row.optString("reps","10"),true);
        dialog(a.exerciseName(row),form,()->{
            double w=number(weight,0,1000); int s=integer(sets,1,20), r=integer(reps,1,100);
            put(row,"weight",w); put(row,"sets",s); put(row,"reps",r); save.run();
        });
    }
    void start(JSONObject plan) {
        if(store.first("active")!=null) { toast(s("active_exists")); show("workout"); return; }
        if(plan.optJSONArray("rows").length()==0) { toast(s("add_first")); return; }
        JSONObject session=copy(plan); put(session,"id",id()); put(session,"planId",plan.optString("id")); put(session,"started",System.currentTimeMillis());
        JSONArray rows=session.optJSONArray("rows");
        for(int i=0;i<rows.length();i++) { JSONObject row=rows.optJSONObject(i); JSONArray logs=new JSONArray();
            for(int s=0;s<row.optInt("sets");s++) logs.put(object("weight",row.optDouble("weight"),"reps",row.optInt("reps"),"done",false)); put(row,"logs",logs);
        }
        store.save("active",session); show("workout");
    }
    void workout() {
        title(s("workout_title")); JSONObject session=store.first("active");
        if(session==null) { text(content,s("no_active"),16); button(content,s("choose_plan"),()->show("plans")); return; }
        text(content,s("started",a.planName(session),date(session.optLong("started"))),16);
        text(content,s("logging_help"),14);
        a.timerLabel=text(content,s("rest_timer"),20);
        button(content,s("rest60"),()->{put(session,"restUntil",System.currentTimeMillis()+60000);store.save("active",session);});
        button(content,s("rest90"),()->{put(session,"restUntil",System.currentTimeMillis()+90000);store.save("active",session);});
        button(content,s("stop_rest"),()->{put(session,"restUntil",0);store.save("active",session);});
        renderLogs(session,true);
        button(content,s("finish"),()->{
            if(completed(session)==0) {toast(s("one_set"));return;}
            confirm(s("finish_confirm"),()->{put(session,"finished",System.currentTimeMillis());store.finish(session);show("history");});
        });
        button(content,s("discard"),()->confirm(s("discard_confirm"),()->{store.delete(session.optString("id"));show("workout");}));
    }
}
