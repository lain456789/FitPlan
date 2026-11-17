package com.example.fitplan;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

/** Small local document store. Plans, sessions and check-ins have independent identities. */
final class Store extends SQLiteOpenHelper {
    Store(Context context) {
        super(context, "fitplan.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE documents (id TEXT PRIMARY KEY, kind TEXT NOT NULL, body TEXT NOT NULL, updated INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX document_kind ON documents(kind, updated)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    static JSONObject object(Object... pairs) {
        JSONObject result = new JSONObject();
        for (int i = 0; i < pairs.length; i += 2) put(result, pairs[i].toString(), pairs[i + 1]);
        return result;
    }

    static void put(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (org.json.JSONException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static JSONObject copy(JSONObject value) {
        try {
            return new JSONObject(value.toString());
        } catch (org.json.JSONException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static String id() {
        return UUID.randomUUID().toString();
    }

    void save(String kind, JSONObject value) {
        if (!value.has("id")) put(value, "id", id());
        ContentValues row = new ContentValues();
        row.put("id", value.optString("id"));
        row.put("kind", kind);
        row.put("body", value.toString());
        row.put("updated", System.currentTimeMillis());
        if (getWritableDatabase()
                        .insertWithOnConflict(
                                "documents", null, row, SQLiteDatabase.CONFLICT_REPLACE)
                == -1) throw new SQLiteException("Could not save local record");
    }

    JSONArray list(String kind) {
        JSONArray result = new JSONArray();
        try (Cursor cursor =
                getReadableDatabase()
                        .query(
                                "documents",
                                new String[] {"body"},
                                "kind=?",
                                new String[] {kind},
                                null,
                                null,
                                "updated DESC")) {
            while (cursor.moveToNext()) {
                try {
                    result.put(new JSONObject(cursor.getString(0)));
                } catch (org.json.JSONException e) {
                    throw new IllegalStateException("Invalid local record", e);
                }
            }
        }
        return result;
    }

    JSONObject first(String kind) {
        return list(kind).optJSONObject(0);
    }

    void delete(String id) {
        getWritableDatabase().delete("documents", "id=?", new String[] {id});
    }

    void finish(JSONObject session) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            save("history", session);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    static final class StaleWorkout extends IllegalStateException {}

    void saveSessionEdit(String kind, JSONObject expected, JSONObject updated, boolean finish) {
        if (!kind.equals("active") && !kind.equals("history"))
            throw new IllegalArgumentException("Invalid session kind");
        if (finish && !kind.equals("active"))
            throw new IllegalArgumentException("Only an active session can finish");
        saveCheckedEdit(kind, expected, updated, finish);
    }

    void savePlanEdit(JSONObject expected, JSONObject updated) {
        saveCheckedEdit("plan", expected, updated, false);
    }

    private void saveCheckedEdit(
            String kind, JSONObject expected, JSONObject updated, boolean finish) {
        if (!expected.optString("id").equals(updated.optString("id")))
            throw new IllegalArgumentException("Session ID changed");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            JSONObject current = null;
            JSONArray all = list(kind);
            for (int i = 0; i < all.length(); i++)
                if (expected.optString("id").equals(all.optJSONObject(i).optString("id")))
                    current = all.optJSONObject(i);
            if (current == null || !current.toString().equals(expected.toString()))
                throw new StaleWorkout();
            save(finish ? "history" : kind, updated);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void applyAdjustment(WorkoutAdjustments.Preview preview) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            JSONObject current = null;
            JSONArray all = list("active");
            String expectedId = preview.after.optString("id");
            for (int i = 0; i < all.length(); i++)
                if (expectedId.equals(all.optJSONObject(i).optString("id")))
                    current = all.optJSONObject(i);
            if (current == null || !current.toString().equals(preview.baseJson))
                throw new StaleWorkout();
            if (preview.changed < 1
                    || !WorkoutAdjustments.completedUnchanged(current, preview.after)
                    || !current.optString("planId").equals(preview.after.optString("planId"))
                    || current.optLong("started") != preview.after.optLong("started"))
                throw new WorkoutAdjustments.Invalid("adjust_invalid");
            JSONObject next = copy(preview.after);
            JSONArray events = next.optJSONArray("adjustments");
            put(events.optJSONObject(events.length() - 1), "at", System.currentTimeMillis());
            save("active", next);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static boolean dailyPlan(JSONObject plan, String day) {
        if (plan.has("dailyPlanDay")) return day.equals(plan.optString("dailyPlanDay"));
        // Generated v0.2/v0.2.1 plans have a check-in snapshot, including renamed plans.
        JSONObject check = plan.optJSONObject("checkin");
        return check != null && day.equals(check.optString("day"));
    }

    /** Replace only this day's generated template; active/history snapshots are independent. */
    void saveDailyPlan(JSONObject check, JSONObject plan) {
        String day = check.optString("day");
        if (day.trim().isEmpty() || (plan != null && plan.optJSONArray("rows") == null))
            throw new IllegalArgumentException("Invalid daily plan");
        JSONObject snapshot = plan == null ? null : plan.optJSONObject("checkin");
        if (snapshot != null && !day.equals(snapshot.optString("day")))
            throw new IllegalArgumentException("Plan and check-in dates differ");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            JSONArray checks = list("checkin"), plans = list("plan");
            JSONObject previousCheck = null, previousPlan = null;
            for (int i = 0; i < checks.length(); i++)
                if (day.equals(checks.optJSONObject(i).optString("day"))) {
                    previousCheck = checks.optJSONObject(i);
                    break;
                }
            for (int i = 0; i < plans.length(); i++)
                if (dailyPlan(plans.optJSONObject(i), day)) {
                    previousPlan = plans.optJSONObject(i);
                    break;
                }
            // Caller-supplied IDs must never overwrite a different day or a manual plan.
            put(check, "id", previousCheck == null ? id() : previousCheck.optString("id"));
            if (previousCheck != null && previousCheck.has("created"))
                put(check, "created", previousCheck.optLong("created"));
            save("checkin", check);
            if (plan != null) {
                put(plan, "id", previousPlan == null ? id() : previousPlan.optString("id"));
                put(plan, "dailyPlanDay", day);
                put(plan, "checkin", copy(check));
                if (previousPlan != null) {
                    if (previousPlan.has("created"))
                        put(plan, "created", previousPlan.optLong("created"));
                    if (previousPlan.has("autoName") && !previousPlan.optBoolean("autoName")) {
                        put(plan, "name", previousPlan.optString("name"));
                        put(plan, "autoName", false);
                    }
                }
                save("plan", plan);
            }
            // Deduplicate only the requested day. No active/history records are touched.
            for (int i = 0; i < checks.length(); i++) {
                JSONObject old = checks.optJSONObject(i);
                if (day.equals(old.optString("day"))
                        && !check.optString("id").equals(old.optString("id")))
                    delete(old.optString("id"));
            }
            for (int i = 0; i < plans.length(); i++) {
                JSONObject old = plans.optJSONObject(i);
                if (dailyPlan(old, day)
                        && (plan == null || !plan.optString("id").equals(old.optString("id"))))
                    delete(old.optString("id"));
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void seed() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            java.util.Set<String> existing = new java.util.HashSet<>();
            JSONArray all = list("exercise");
            for (int i = 0; i < all.length(); i++)
                existing.add(all.optJSONObject(i).optString("id"));
            for (int i = 0; i < Catalog.EXERCISES.length; i++)
                if (!existing.contains("builtin-" + i)) {
                    String[] item = Catalog.EXERCISES[i];
                    save(
                            "exercise",
                            object(
                                    "id",
                                    "builtin-" + i,
                                    "name",
                                    item[0],
                                    "part",
                                    item[1],
                                    "replacementOnly",
                                    i >= 16));
                }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
