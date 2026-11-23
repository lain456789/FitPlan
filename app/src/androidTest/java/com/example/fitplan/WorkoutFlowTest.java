package com.example.fitplan;

import static com.example.fitplan.Store.object;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@androidx.test.filters.SdkSuppress(minSdkVersion = 29)
public class WorkoutFlowTest {
    // Public view callbacks avoid Espresso's obsolete hidden InputManager API on API 37.
    private View find(View root, String label, String parentLabel) {
        if (root.isClickable()
                && ((root instanceof TextView
                                && ((TextView) root).getText().toString().equals(label))
                        || label.equals(String.valueOf(root.getContentDescription())))
                && (parentLabel == null || contains((View) root.getParent(), parentLabel)))
            return root;
        if (root instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
                View match = find(((ViewGroup) root).getChildAt(i), label, parentLabel);
                if (match != null) return match;
            }
        return null;
    }

    private boolean contains(View root, String label) {
        if (root instanceof TextView && ((TextView) root).getText().toString().equals(label))
            return true;
        if (root instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++)
                if (contains(((ViewGroup) root).getChildAt(i), label)) return true;
        return false;
    }

    private void click(String label, String parentLabel) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> {
                    java.util.List<View> roots = WindowInspector.getGlobalWindowViews();
                    for (int i = roots.size() - 1; i >= 0; i--) {
                        View v = find(roots.get(i), label, parentLabel);
                        if (v != null) {
                            v.performClick();
                            return;
                        }
                    }
                    fail("Button not found: " + label);
                });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @Test
    public void startRecordFinishAndReadHistory() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Store store = new Store(context);
        assumeTrue(store.first("active") == null);
        String name = "自动验证训练-" + System.currentTimeMillis();
        JSONObject plan =
                object(
                        "name",
                        name,
                        "rows",
                        new JSONArray()
                                .put(
                                        object(
                                                "exerciseId",
                                                "builtin-0",
                                                "name",
                                                "杠铃卧推",
                                                "weight",
                                                20,
                                                "sets",
                                                1,
                                                "reps",
                                                10)));
        store.save("plan", plan);
        String language =
                context.getSharedPreferences("preferences", 0).getString("language", "zh");
        context.getSharedPreferences("preferences", 0).edit().putString("language", "zh").commit();
        String sessionId = null;
        try {
            Intent intent =
                    new Intent(context, MainActivity.class)
                            .addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
            click("训练", null);
            click("我的计划", null);
            click("开始训练", name);
            JSONObject active = store.first("active");
            assertNotNull(active);
            sessionId = active.optString("id");
            click("记录第 1 组", null);
            click("保存", null);
            assertTrue(
                    store.first("active")
                            .optJSONArray("rows")
                            .optJSONObject(0)
                            .optJSONArray("logs")
                            .optJSONObject(0)
                            .optBoolean("done"));
            click("结束并保存训练", null);
            click("确定", null);
            assertNull(store.first("active"));
            JSONObject history = store.first("history");
            assertEquals(sessionId, history.optString("id"));
            assertEquals(20, history.optJSONArray("rows").optJSONObject(0).optDouble("weight"), 0);
            assertFalse(store.first("plan").optJSONArray("rows").optJSONObject(0).has("logs"));
        } finally {
            context.getSharedPreferences("preferences", 0)
                    .edit()
                    .putString("language", language)
                    .commit();
            store.delete(plan.optString("id"));
            if (sessionId != null) store.delete(sessionId);
            store.close();
        }
    }
}
