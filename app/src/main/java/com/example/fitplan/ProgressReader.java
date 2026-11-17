package com.example.fitplan;

import org.json.*;
import java.util.*;
import static com.example.fitplan.Store.*;

final class ProgressReader {
    private ProgressReader(){}
    static List<ProgressEngine.Sample> samples(JSONArray history,String exerciseId){
        List<ProgressEngine.Sample> result=new ArrayList<>();
        for(int i=0;i<history.length();i++){
            JSONObject session=history.optJSONObject(i);JSONArray rows=session==null?null:session.optJSONArray("rows");if(rows==null)continue;
            int matches=0;for(int r=0;r<rows.length();r++)if(rows.optJSONObject(r)!=null&&exerciseId.equals(rows.optJSONObject(r).optString("exerciseId")))matches++;
            for(int r=0;r<rows.length();r++){
                JSONObject row=rows.optJSONObject(r);if(row==null||!exerciseId.equals(row.optString("exerciseId")))continue;
                JSONArray logs=row.optJSONArray("logs");if(logs==null)continue;boolean adjusted=row.optBoolean("retired")||row.has("replacementOf");
                List<ProgressEngine.SetResult> data=new ArrayList<>();
                for(int s=0;s<logs.length();s++){
                    JSONObject log=logs.optJSONObject(s);if(log==null){adjusted=true;continue;}
                    adjusted|=log.optBoolean("skipped");int effort=Arrays.asList(TrainingFeedback.EFFORTS).indexOf(log.optString("effort","unknown"));
                    data.add(new ProgressEngine.SetResult(log.optDouble("weight",-1),log.optBoolean("done")?log.optInt("reps"):0,log.optBoolean("done"),log.optBoolean("skipped"),Math.max(0,effort),TrainingFeedback.reason(log,session)));
                }
                result.add(new ProgressEngine.Sample(session.optString("id"),exerciseId,session.optLong("finished"),row.optInt("sets"),row.optInt("reps"),data,adjusted,matches>1));
            }
        }return result;
    }
    static ProgressEngine.Assessment assess(JSONArray history,String exerciseId){return ProgressEngine.assess(samples(history,exerciseId));}
    static JSONObject snapshot(ProgressEngine.Assessment result){
        JSONObject value=object("status",result.status,"action",result.action,"comparableCount",result.comparableCount,"canIncrease",result.canIncrease,"suggestedReps",result.suggestedReps);
        if(result.latest!=null)put(value,"latest",sample(result.latest));if(result.previous!=null)put(value,"previous",sample(result.previous));return value;
    }
    private static JSONObject sample(ProgressEngine.Sample sample){return object("sessionId",sample.id,"finished",sample.finished,"sets",sample.targetSets,"targetReps",sample.targetReps,"weight",sample.weight(),"performed",sample.performed(),"met",sample.met(),"totalReps",sample.totalReps(),"effort",sample.effort());}
    static boolean applies(ProgressEngine.Assessment result,JSONObject row){
        ProgressEngine.Sample latest=result.latest;
        return latest!=null&&latest.targetSets==row.optInt("sets")&&latest.targetReps==row.optInt("reps")&&latest.weight()>=0&&Math.abs(latest.weight()-row.optDouble("weight",-1))<0.000001;
    }
}
