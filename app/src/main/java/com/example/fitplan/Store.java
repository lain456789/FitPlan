package com.example.fitplan;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

/** Small local document store. Plans, sessions and check-ins have independent identities. */
final class Store extends SQLiteOpenHelper {
    Store(Context context) { super(context, "fitplan.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE documents (id TEXT PRIMARY KEY, kind TEXT NOT NULL, body TEXT NOT NULL, updated INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX document_kind ON documents(kind, updated)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
    static JSONObject object(Object... pairs) {
        JSONObject result = new JSONObject();
        for (int i = 0; i < pairs.length; i += 2) put(result, pairs[i].toString(), pairs[i + 1]);
        return result;
    }
    static void put(JSONObject target, String key, Object value) {
        try { target.put(key, value); } catch (org.json.JSONException e) { throw new IllegalArgumentException(e); }
    }
    static JSONObject copy(JSONObject value) {
        try { return new JSONObject(value.toString()); } catch (org.json.JSONException e) { throw new IllegalArgumentException(e); }
    }
    static String id() { return UUID.randomUUID().toString(); }
    void save(String kind, JSONObject value) {
        if (!value.has("id")) put(value, "id", id());
        ContentValues row = new ContentValues();
        row.put("id", value.optString("id")); row.put("kind", kind);
        row.put("body", value.toString()); row.put("updated", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("documents", null, row, SQLiteDatabase.CONFLICT_REPLACE);
    }
    JSONArray list(String kind) {
        JSONArray result = new JSONArray();
        try (Cursor cursor = getReadableDatabase().query("documents", new String[]{"body"}, "kind=?", new String[]{kind}, null, null, "updated DESC")) {
            while (cursor.moveToNext()) {
                try { result.put(new JSONObject(cursor.getString(0))); }
                catch (org.json.JSONException e) { throw new IllegalStateException("Invalid local record", e); }
            }
        }
        return result;
    }
    JSONObject first(String kind) { return list(kind).optJSONObject(0); }
    void delete(String id) { getWritableDatabase().delete("documents", "id=?", new String[]{id}); }
    void finish(JSONObject session) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try { save("history", session); db.setTransactionSuccessful(); } finally { db.endTransaction(); }
    }
    void seed() {
        if (first("exercise") != null) return;
        String[][] items = {{"杠铃卧推","胸"},{"哑铃上斜卧推","胸"},{"绳索夹胸","胸"},
            {"哑铃推举","肩"},{"哑铃侧平举","肩"},{"反向飞鸟","肩"},
            {"高位下拉","背"},{"坐姿划船","背"},{"单臂哑铃划船","背"},
            {"深蹲","腿"},{"腿举","腿"},{"腿弯举","腿"},
            {"哑铃弯举","手臂"},{"绳索下压","手臂"},{"卷腹","核心"},{"死虫式","核心"}};
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            for (int i=0; i<items.length; i++) save("exercise", object("id", "builtin-"+i, "name", items[i][0], "part", items[i][1]));
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
