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
        ScrollView scroll=new ScrollView(a);scroll.addView(form);
        AlertDialog d=new AlertDialog.Builder(a).setTitle(title).setView(scroll).setNegativeButton(s("cancel"),null).setPositiveButton(s("save"),null).create();
        a.activeDialog=d;d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{try{save.run();d.dismiss();}catch(IllegalArgumentException e){toast(e.getMessage());}}));d.show();
    }
    void confirm(String message,Runnable action){a.activeDialog=new AlertDialog.Builder(a).setMessage(message).setNegativeButton(s("cancel"),null).setPositiveButton(s("confirm"),(d,w)->action.run()).show();}
    int completed(JSONObject session){int count=0;JSONArray rows=session.optJSONArray("rows");if(rows==null)return 0;for(int i=0;i<rows.length();i++){JSONArray logs=rows.optJSONObject(i).optJSONArray("logs");if(logs!=null)for(int j=0;j<logs.length();j++)if(logs.optJSONObject(j).optBoolean("done"))count++;}return count;}
    double volume(JSONObject session){double total=0;JSONArray rows=session.optJSONArray("rows");if(rows==null)return 0;for(int i=0;i<rows.length();i++){JSONArray logs=rows.optJSONObject(i).optJSONArray("logs");if(logs!=null)for(int j=0;j<logs.length();j++){JSONObject log=logs.optJSONObject(j);if(log.optBoolean("done"))total+=log.optDouble("weight")*log.optInt("reps");}}return total;}
        void renderLogs(JSONObject session,boolean active) {
        JSONArray rows=session.optJSONArray("rows");
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.optJSONObject(i); LinearLayout c=card(content); text(c,a.exerciseName(row),20);
            JSONArray logs=row.optJSONArray("logs");
            for(int j=0;j<logs.length();j++) {
                final int index=j; JSONObject log=logs.optJSONObject(j);
                text(c,s("log_line",j+1,log.optBoolean("done")?s("done"):s("not_done"),num(log.optDouble("weight")),log.optInt("reps")),16);
                button(c,s(log.optBoolean("done")?"edit_set":"record_set",j+1),()->{
                    LinearLayout form=column(); EditText weight=input(form,s("actual_weight"),num(log.optDouble("weight")),true), reps=input(form,s("actual_reps"),log.optString("reps"),true);
                    CheckBox done=new CheckBox(a);done.setText(s("set_done"));done.setChecked(true);form.addView(done);
                    dialog(s("set_title",a.exerciseName(row),index+1),form,()->{
                        double w=number(weight,0,1000);int r=integer(reps,1,100);
                        put(log,"weight",w);put(log,"reps",r);put(log,"done",done.isChecked());
                        if(active && done.isChecked()) put(session,"restUntil",System.currentTimeMillis()+60000);
                        store.save(active?"active":"history",session); if(active)show("workout");else a.openHistory(session);
                    });
                });
            }
            if(active) button(c,s("add_set"),()->{ if(logs.length()>=20){toast(s("max_sets"));return;} logs.put(object("weight",row.optDouble("weight"),"reps",row.optInt("reps"),"done",false));store.save("active",session);show("workout"); });
        }
    }
}
