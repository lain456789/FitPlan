package com.example.fitplan;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import static com.example.fitplan.Store.*;

/** Proposals are copies. Nothing is persisted until Store.applyAdjustment confirms the base. */
final class WorkoutAdjustments {
    static final class Invalid extends IllegalArgumentException {
        final String key;Invalid(String key){super(key);this.key=key;}
    }
    static final class Preview {
        final String baseJson,type;final JSONObject after;final JSONArray changes;
        final int beforePending,afterPending,changed;final long estimatedSeconds;
        Preview(JSONObject before,JSONObject after,String type,JSONArray changes,int changed,long estimate){
            this.baseJson=before.toString();this.after=after;this.type=type;this.changes=changes;this.changed=changed;
            this.beforePending=pending(before);this.afterPending=pending(after);this.estimatedSeconds=estimate;
        }
    }
    private WorkoutAdjustments(){}
    static JSONArray arrayCopy(JSONArray value){try{return new JSONArray(value.toString());}catch(org.json.JSONException e){throw new Invalid("adjust_invalid");}}
    static boolean isPending(JSONObject log){return !log.optBoolean("done")&&!log.optBoolean("skipped")&&!log.optBoolean("resolved");}
    static int pendingRow(JSONObject row){
        int total=0;JSONArray logs=row.optJSONArray("logs");if(logs!=null)for(int i=0;i<logs.length();i++)if(logs.optJSONObject(i)!=null&&isPending(logs.optJSONObject(i)))total++;return total;
    }
    static int pending(JSONObject session){int total=0;JSONArray rows=session.optJSONArray("rows");if(rows!=null)for(int i=0;i<rows.length();i++)total+=pendingRow(rows.optJSONObject(i));return total;}
    static int completed(JSONObject session){int count=0;JSONArray rows=session.optJSONArray("rows");if(rows!=null)for(int i=0;i<rows.length();i++){JSONArray logs=rows.optJSONObject(i).optJSONArray("logs");if(logs!=null)for(int j=0;j<logs.length();j++)if(logs.optJSONObject(j).optBoolean("done"))count++;}return count;}
    static long restSeconds(JSONObject session,long now){return Math.max(0,(session.optLong("restUntil")-now+999)/1000);}
    static long estimateSeconds(JSONObject session,long now){int count=pending(session);return count==0?0:count*WorkoutRules.SECONDS_PER_SET+restSeconds(session,now);}
    static String rowId(JSONObject row,int index){return row.optString("rowId","legacy-"+index);}
    private static JSONObject prepare(JSONObject session){
        JSONArray rows=session.optJSONArray("rows");if(session.optString("id").isEmpty()||rows==null)throw new Invalid("adjust_invalid");
        JSONObject next=copy(session);JSONArray copied=next.optJSONArray("rows");
        for(int i=0;i<copied.length();i++){
            JSONObject row=copied.optJSONObject(i);if(row==null||row.optJSONArray("logs")==null)throw new Invalid("adjust_invalid");
            put(row,"rowId",rowId(row,i));JSONArray logs=row.optJSONArray("logs");
            for(int j=0;j<logs.length();j++)if(logs.optJSONObject(j)==null)throw new Invalid("adjust_invalid");
        }
        if(!next.has("originalRows"))put(next,"originalRows",arrayCopy(copied));
        if(next.optJSONArray("adjustments")==null)put(next,"adjustments",new JSONArray());
        return next;
    }
    private static void event(JSONObject next,JSONObject change,long now){
        int revision=next.optInt("adjustmentRevision")+1;put(next,"adjustmentRevision",revision);
        put(change,"revision",revision);put(change,"at",now);put(change,"id",id());next.optJSONArray("adjustments").put(change);
    }
    static Preview shorten(JSONObject session,int minutes,long now){
        JSONObject next=prepare(session);JSONArray rows=next.optJSONArray("rows"),changes=new JSONArray();
        int[] counts=new int[rows.length()];for(int i=0;i<counts.length;i++)counts[i]=pendingRow(rows.optJSONObject(i));
        int[] keep;
        try{keep=WorkoutRules.allocate(counts,minutes,restSeconds(session,now));}catch(IllegalArgumentException e){throw new Invalid("adjust_time_invalid");}
        int removed=0;
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);JSONArray logs=row.optJSONArray("logs");int kept=0;
            for(int j=0;j<logs.length();j++){
                JSONObject log=logs.optJSONObject(j);if(!isPending(log))continue;
                if(kept<keep[i])kept++;else{put(log,"skipped",true);put(log,"skipReason","time");removed++;}
            }
            if(counts[i]>keep[i])changes.put(object("exerciseId",row.optString("exerciseId"),"name",row.optString("name"),
                    "rowId",row.optString("rowId"),"before",counts[i],"after",keep[i]));
        }
        if(pending(next)==0)put(next,"restUntil",0);
        if(removed>0){
            put(next,"remainingTimeBudgetMinutes",minutes);
            event(next,object("type","time","minutes",minutes,"removed",removed,"changes",arrayCopy(changes),
                    "beforePending",pending(session),"afterPending",pending(next)),now);
        }
        return new Preview(session,next,"time",changes,removed,estimateSeconds(next,now));
    }
    static Set<String> blocked(JSONObject session,String newlyUnavailable){
        if(!Arrays.asList(Catalog.EQUIPMENT).contains(newlyUnavailable))throw new Invalid("replace_equipment_required");
        Set<String> blocked=new TreeSet<>();JSONArray previous=session.optJSONArray("unavailableEquipment");
        if(previous!=null)for(int i=0;i<previous.length();i++)blocked.add(previous.optString(i));
        blocked.add(newlyUnavailable);return blocked;
    }
    static List<JSONObject> alternatives(JSONObject session,int rowIndex,String unavailable,JSONArray catalogue){
        JSONArray rows=session.optJSONArray("rows");if(rows==null||rowIndex<0||rowIndex>=rows.length())throw new Invalid("adjust_invalid");
        JSONObject source=rows.optJSONObject(rowIndex);if(pendingRow(source)==0)throw new Invalid("adjust_no_pending");
        Set<String> blocked=blocked(session,unavailable);List<JSONObject> result=new ArrayList<>();
        for(int i=0;i<catalogue.length();i++){
            JSONObject target=catalogue.optJSONObject(i);
            if(Catalog.canReplace(source.optString("exerciseId"),target.optString("id"),blocked))result.add(target);
        }
        result.sort(Comparator.comparingInt(ex->Catalog.builtIn(ex.optString("id"))));return result;
    }
    static Preview replace(JSONObject session,int rowIndex,JSONObject target,String unavailable,double weight,int reps,long now){
        if(!Double.isFinite(weight)||weight<0||weight>1000||reps<1||reps>100)throw new Invalid("replace_values_invalid");
        JSONObject next=prepare(session);JSONArray rows=next.optJSONArray("rows");
        if(rowIndex<0||rowIndex>=rows.length())throw new Invalid("adjust_invalid");
        JSONObject source=rows.optJSONObject(rowIndex);int count=pendingRow(source);
        if(count==0)throw new Invalid("adjust_no_pending");
        Set<String> blocked=blocked(session,unavailable);String targetId=target.optString("id");
        if(!Catalog.canReplace(source.optString("exerciseId"),targetId,blocked))throw new Invalid("replace_no_candidates");
        JSONArray logs=source.optJSONArray("logs");
        for(int i=0;i<logs.length();i++)if(isPending(logs.optJSONObject(i))){put(logs.optJSONObject(i),"skipped",true);put(logs.optJSONObject(i),"skipReason","equipment");}
        put(source,"retired",true);
        JSONArray newLogs=new JSONArray();for(int i=0;i<count;i++)newLogs.put(object("weight",weight,"reps",reps,"done",false));
        int builtin=Catalog.builtIn(targetId);String newRowId=id();
        JSONObject replacement=object("rowId",newRowId,"exerciseId",targetId,"name",Catalog.EXERCISES[builtin][0],"part",Catalog.EXERCISES[builtin][1],
                "sets",count,"reps",reps,"weight",weight,"logs",newLogs,"userConfirmed",true,"replacementOf",source.optString("rowId"));
        JSONArray updated=new JSONArray();for(int i=0;i<rows.length();i++){updated.put(rows.optJSONObject(i));if(i==rowIndex)updated.put(replacement);}
        put(next,"rows",updated);JSONArray unavailableList=new JSONArray();for(String item:blocked)unavailableList.put(item);put(next,"unavailableEquipment",unavailableList);
        JSONArray changes=new JSONArray().put(object("exerciseId",source.optString("exerciseId"),"name",source.optString("name"),
                "targetId",targetId,"targetName",replacement.optString("name"),"sets",count,"weight",weight,"reps",reps,"equipment",unavailable));
        event(next,object("type","equipment","sourceRowId",source.optString("rowId"),"targetRowId",newRowId,"changes",arrayCopy(changes)),now);
        return new Preview(session,next,"equipment",changes,count,estimateSeconds(next,now));
    }
    static boolean completedUnchanged(JSONObject before,JSONObject after){
        try{
            if(completed(before)!=completed(after)||TrainingFeedback.recorded(before)!=TrainingFeedback.recorded(after))return false;
            JSONArray old=before.getJSONArray("rows"),updated=after.getJSONArray("rows");Map<String,JSONObject> byId=new HashMap<>();
            for(int i=0;i<updated.length();i++){
                JSONObject row=updated.getJSONObject(i);String id=row.getString("rowId");if(byId.put(id,row)!=null)return false;
            }
            for(int i=0;i<old.length();i++){
                JSONObject original=old.getJSONObject(i),row=byId.get(rowId(original,i));
                if(row==null||!original.optString("exerciseId").equals(row.optString("exerciseId")))return false;
                JSONArray logs=original.getJSONArray("logs"),newLogs=row.getJSONArray("logs");if(newLogs.length()<logs.length())return false;
                for(int j=0;j<logs.length();j++)if((logs.getJSONObject(j).optBoolean("done")||logs.getJSONObject(j).optBoolean("resolved"))&&!logs.getJSONObject(j).toString().equals(newLogs.getJSONObject(j).toString()))return false;
            }
            return true;
        }catch(Exception e){return false;}
    }
}
