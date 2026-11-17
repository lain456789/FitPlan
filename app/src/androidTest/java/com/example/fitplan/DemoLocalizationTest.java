package com.example.fitplan;

import static com.example.fitplan.Store.object;

import static org.junit.Assert.assertEquals;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DemoLocalizationTest {
    @Test
    public void demoTranslationsAreDisplayOnlyAndCustomContentIsPreserved() {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                String original = a.language();
                String before = a.store.list("history").toString();
                String[] chinese = {"胸肩训练", "背部与手臂", "腿部与核心", "上肢综合", "轻量恢复"};
                String[] english = {
                    "Chest & shoulders",
                    "Back & arms",
                    "Legs & core",
                    "Upper body",
                    "Light recovery"
                };
                try {
                    a.setLanguage("en");
                    for (int i = 0; i < chinese.length; i++) {
                        JSONObject record =
                                object(
                                        "demoDataset",
                                        "eightweeks-v1",
                                        "name",
                                        "[测试数据] " + chinese[i]);
                        assertEquals("[Demo] " + english[i], a.planName(record));
                    }
                    JSONObject demo = object("demoDataset", "eightweeks-v1", "name", "[测试数据] 胸肩训练");
                    assertEquals(
                            "Demo data for previewing reports; not an actual workout record.",
                            a.sessionNotes(demo, object("notes", DemoLabels.NOTE)));
                    assertEquals("用户自定义备注", a.sessionNotes(demo, object("notes", "用户自定义备注")));
                    assertEquals("[测试数据] 胸肩训练", a.planName(object("name", "[测试数据] 胸肩训练")));
                    assertEquals(
                            "我的新标题",
                            a.planName(object("demoDataset", "eightweeks-v1", "name", "我的新标题")));
                    a.setLanguage("zh");
                    assertEquals("[测试数据] 胸肩训练", a.planName(demo));
                    assertEquals(before, a.store.list("history").toString());
                } finally {
                    String preview =
                            androidx.test.platform.app.InstrumentationRegistry.getArguments()
                                    .getString("previewLanguage");
                    a.setLanguage(preview == null ? original : preview);
                }
            });
        }
    }
}
