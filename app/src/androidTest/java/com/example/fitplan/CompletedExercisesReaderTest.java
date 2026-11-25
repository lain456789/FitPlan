package com.example.fitplan;

import static com.example.fitplan.Store.object;
import static com.example.fitplan.Store.put;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

@RunWith(AndroidJUnit4.class)
public class CompletedExercisesReaderTest {
    private final long now = System.currentTimeMillis();
    private final TimeZone zone = TimeZone.getDefault();

    private JSONObject row(String id, int planned, boolean... done) {
        JSONArray logs = new JSONArray();
        for (boolean value : done) logs.put(object("done", value, "weight", 0, "reps", 10));
        return object(
                "exerciseId",
                id,
                "name",
                "Same name",
                "part",
                "chest",
                "sets",
                planned,
                "logs",
                logs);
    }

    private ReportEngine.Summary read(JSONObject... rows) {
        JSONArray all = new JSONArray();
        for (JSONObject row : rows) all.put(row);
        JSONArray records = new JSONArray().put(object("finished", now, "rows", all));
        return ReportEngine.build(HistoryReader.read(records), now, now, zone).today;
    }

    @Test
    public void completedLegacyAndManualRowsCountButPartialRowsDoNot() {
        JSONObject legacy = row("legacy", 2, true, true);
        legacy.remove("sets");
        JSONObject manual = row("manual", 2, true, true);
        put(manual, "actualOnly", true);
        assertEquals(2, read(legacy, manual, row("partial", 2, true, false)).completedExercises);
        assertEquals(0, read(row("missing", 3, true, true)).completedExercises);
    }

    @Test
    public void skippedMalformedAndDuplicateRowsDoNotInflateCompletion() {
        JSONObject skipped = row("skip", 1, true);
        put(skipped.optJSONArray("logs").optJSONObject(0), "skipped", true);
        JSONObject malformed = row("bad", 2, true);
        malformed.optJSONArray("logs").put(JSONObject.NULL);
        assertEquals(0, read(skipped, malformed).completedExercises);
        assertEquals(0, read(row("same", 1, true), row("same", 1, false)).completedExercises);
        assertEquals(1, read(row("same", 1, true), row("same", 1, true)).completedExercises);
    }

    @Test
    public void targetUsesDistinctIdsAndDoesNotDependOnWeightConfirmation() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(zone);
        JSONObject a = row("a", 2, true, true),
                duplicate = row("a", 3, true),
                b = row("b", 2, true);
        for (JSONObject row : new JSONObject[] {a, duplicate, b}) {
            put(row, "reps", 10);
            put(row, "weight", 0);
            put(row, "needsWeightConfirmation", true);
        }
        JSONObject plan =
                object(
                        "id",
                        "target",
                        "dailyPlanDay",
                        format.format(new Date(now)),
                        "rows",
                        new JSONArray().put(a).put(duplicate).put(b));
        assertEquals(2, DailyTargetReader.read(new JSONArray().put(plan), now, zone).exercises);
        assertEquals(0, DailyTargetReader.read(new JSONArray(), now, zone).exercises);
    }
}
