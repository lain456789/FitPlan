package com.example.fitplan;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.text.InputType;
import android.widget.*;
import org.json.*;
import static com.example.fitplan.Store.*;

abstract class Screen {
    final MainActivity a;final Store store;final LinearLayout content;
    static final String[] PARTS=Catalog.PARTS;
    Screen(MainActivity activity){a=activity;store=a.store;content=a.content;}
    String s(String key,Object... args){return a.s(key,args);}
    void show(String page){a.show(page);}
    String num(double n){return a.num(n);}
    String date(long time){return a.date(time);}
    String day(){return a.day();}
    TextView text(LinearLayout parent,String value,int size){return a.text(parent,value,size);}
    void title(String value){text(content,value,27).setTypeface(null,Typeface.BOLD);}
    LinearLayout column(){return a.column();}
    LinearLayout card(LinearLayout parent){return a.card(parent);}
    Button button(LinearLayout parent,String label,Runnable action){return a.button(parent,label,action);}
    void toast(String message){Toast.makeText(a,message,Toast.LENGTH_LONG).show();}
    EditText input(LinearLayout parent,String label,String initial,boolean number){
        TextView caption=text(parent,label,14);EditText edit=new EditText(a);edit.setId(android.view.View.generateViewId());caption.setLabelFor(edit.getId());
        edit.setSingleLine(true);if(number)edit.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        edit.setText(initial);edit.setTextColor(MainActivity.FG);parent.addView(edit,new LinearLayout.LayoutParams(-1,-2));return edit;
    }
    Spinner select(LinearLayout parent,String label,String[] options,int selected){
        TextView caption=text(parent,label,14);Spinner spinner=new Spinner(a);spinner.setId(android.view.View.generateViewId());caption.setLabelFor(spinner.getId());
        ArrayAdapter<String> adapter=new ArrayAdapter<>(a,android.R.layout.simple_spinner_item,options);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);spinner.setSelection(Math.max(0,Math.min(selected,options.length-1)));parent.addView(spinner,new LinearLayout.LayoutParams(-1,a.dp(52)));return spinner;
    }
    double number(EditText edit,double min,double max){try{double n=Double.parseDouble(edit.getText().toString());if(Double.isFinite(n)&&n>=min&&n<=max)return n;}catch(NumberFormatException ignored){}throw new IllegalArgumentException(s("range_error",num(min),num(max)));}
    int integer(EditText edit,int min,int max){double n=number(edit,min,max);if(n!=Math.floor(n))throw new IllegalArgumentException(s("integer_error"));return (int)n;}
    void dialog(String title,LinearLayout form,Runnable save){
        dialog(title,form,s("save"),save);
    }
    void dialog(String title,LinearLayout form,String positive,Runnable save){
        ScrollView scroll=new ScrollView(a);scroll.addView(form);
        AlertDialog d=new AlertDialog.Builder(a).setTitle(title).setView(scroll).setNegativeButton(s("cancel"),null).setPositiveButton(positive,null).create();
        a.activeDialog=d;d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{try{save.run();d.dismiss();}catch(IllegalArgumentException e){toast(e.getMessage());}}));d.show();
    }
    void confirm(String message,Runnable action){a.activeDialog=new AlertDialog.Builder(a).setMessage(message).setNegativeButton(s("cancel"),null).setPositiveButton(s("confirm"),(d,w)->action.run()).show();}
    int completed(JSONObject session){int count=0;JSONArray rows=session.optJSONArray("rows");if(rows==null)return 0;for(int i=0;i<rows.length();i++){JSONArray logs=rows.optJSONObject(i).optJSONArray("logs");if(logs!=null)for(int j=0;j<logs.length();j++)if(logs.optJSONObject(j).optBoolean("done"))count++;}return count;}
    double volume(JSONObject session){double total=0;JSONArray rows=session.optJSONArray("rows");if(rows==null)return 0;for(int i=0;i<rows.length();i++){JSONArray logs=rows.optJSONObject(i).optJSONArray("logs");if(logs!=null)for(int j=0;j<logs.length();j++){JSONObject log=logs.optJSONObject(j);if(log.optBoolean("done"))total+=log.optDouble("weight")*log.optInt("reps");}}return total;}
        void renderLogs(JSONObject session,boolean active) {
        JSONArray rows=session.optJSONArray("rows");
        for(int i=0;i<rows.length();i++) {
            final int rowIndex=i;
            JSONObject row=rows.optJSONObject(i); LinearLayout c=card(content); text(c,a.exerciseName(row),20);
            text(c,s("feedback_target",row.optInt("reps")),14);
            if(row.optBoolean("retired"))text(c,s("replacement_retired"),14);
            if(row.has("replacementOf"))text(c,s("replacement_confirmed"),14);
            JSONArray logs=row.optJSONArray("logs");
            boolean skipped=false;
            for(int j=0;j<logs.length();j++) {
                final int index=j; JSONObject log=logs.optJSONObject(j);
                if(log.optBoolean("skipped")&&!log.optBoolean("done")){
                    text(c,s("equipment".equals(log.optString("skipReason"))?"set_replaced":"set_time_cancelled",j+1),14);skipped=true;continue;
                }
                text(c,s("log_line",j+1,log.optBoolean("done")?s("done"):s("not_done"),num(log.optDouble("weight")),log.optInt("reps")),16);
                if(log.optBoolean("done")||log.optBoolean("resolved")||!active)text(c,s("feedback_record_line",s("effort_"+validEffort(log.optString("effort","unknown"))),s("cause_"+TrainingFeedback.reason(log,session))),14);
                button(c,s(log.optBoolean("done")||log.optBoolean("resolved")?"edit_set":"record_set",j+1),()->{
                    LinearLayout form=column();text(form,s("feedback_help",row.optInt("reps")),14);
                    String initialReps=!active&&!log.optBoolean("done")&&!log.has("feedbackVersion")?"0":log.optString("reps");
                    EditText weight=input(form,s("actual_weight"),num(log.optDouble("weight")),true), reps=input(form,s("actual_reps"),initialReps,true);
                    CheckBox done=new CheckBox(a);done.setText(s("set_done"));done.setChecked(active&&!log.has("feedbackVersion")||log.optBoolean("done"));form.addView(done);
                    String[] efforts=new String[TrainingFeedback.EFFORTS.length],reasons=new String[TrainingFeedback.REASONS.length];
                    for(int e=0;e<efforts.length;e++)efforts[e]=s("effort_"+TrainingFeedback.EFFORTS[e]);
                    for(int r=0;r<reasons.length;r++)reasons[r]=s("cause_"+TrainingFeedback.REASONS[r]);
                    Spinner effort=select(form,s("effort_label"),efforts,java.util.Arrays.asList(TrainingFeedback.EFFORTS).indexOf(log.optString("effort","unknown")));
                    Spinner reason=select(form,s("incomplete_reason_label"),reasons,java.util.Arrays.asList(TrainingFeedback.REASONS).indexOf(TrainingFeedback.reason(log,session)));
                    dialog(s("set_title",a.exerciseName(row),index+1),form,()->{
                        double w=number(weight,0,1000);int r=integer(reps,0,100);
                        try{
                            JSONObject updated=TrainingFeedback.record(session,rowIndex,index,w,r,done.isChecked(),TrainingFeedback.EFFORTS[effort.getSelectedItemPosition()],TrainingFeedback.REASONS[reason.getSelectedItemPosition()],System.currentTimeMillis());
                            if(active&&done.isChecked())put(updated,"restUntil",System.currentTimeMillis()+60000);
                            store.saveSessionEdit(active?"active":"history",session,updated,false);
                            if(active)show("workout");else a.openHistory(updated);
                        }catch(TrainingFeedback.Invalid e){throw new IllegalArgumentException(s(e.key));}
                        catch(Store.StaleWorkout e){a.activeDialog.dismiss();toast(s("feedback_stale"));if(active)show("workout");else{JSONObject current=a.find("history",session.optString("id"));if(current==null)show("history");else a.openHistory(current);}}
                        catch(android.database.sqlite.SQLiteException e){throw new IllegalArgumentException(s("feedback_save_error"));}
                    });
                });
            }
            if(active&&WorkoutAdjustments.pendingRow(row)>0)button(c,s("replace_remaining"),()->new TrainingScreen(a).replaceRemaining(session,rowIndex));
            if(active&&!row.optBoolean("retired")&&(!skipped||WorkoutAdjustments.pendingRow(row)>0)) button(c,s("add_set"),()->{ if(logs.length()>=20){toast(s("max_sets"));return;} logs.put(object("weight",row.optDouble("weight"),"reps",row.optInt("reps"),"done",false));store.save("active",session);show("workout"); });
        }
    }
    private String validEffort(String value){return java.util.Arrays.asList(TrainingFeedback.EFFORTS).contains(value)?value:"unknown";}
}
