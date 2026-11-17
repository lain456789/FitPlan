package com.example.fitplan;

import static com.example.fitplan.Store.copy;
import static com.example.fitplan.Store.id;
import static com.example.fitplan.Store.object;
import static com.example.fitplan.Store.put;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StoreInstrumentedTest {
    @Test
    public void independentSnapshotSurvivesReopenAndFinish() {
        android.content.Context context =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        Store store = new Store(context);
        JSONObject plan =
                object(
                        "id",
                        id(),
                        "name",
                        "test plan",
                        "rows",
                        new JSONArray().put(object("weight", 40)));
        JSONObject session = copy(plan);
        put(session, "id", id());
        try {
            store.save("test-plan", plan);
            store.save("test-active", session);
            put(plan.optJSONArray("rows").optJSONObject(0), "weight", 80);
            store.save("test-plan", plan);
            store.close();
            store = new Store(context);
            assertEquals(
                    40,
                    store.first("test-active")
                            .optJSONArray("rows")
                            .optJSONObject(0)
                            .optDouble("weight"),
                    0);
            // Use unique test kinds above so existing user sessions are never overwritten.
            store.finish(session);
            boolean found = false;
            JSONArray history = store.list("history");
            for (int i = 0; i < history.length(); i++)
                if (history.optJSONObject(i).optString("id").equals(session.optString("id")))
                    found = true;
            assertTrue(found);
            assertNull(store.first("test-active"));
        } finally {
            store.delete(plan.optString("id"));
            store.delete(session.optString("id"));
            store.close();
        }
    }
}
