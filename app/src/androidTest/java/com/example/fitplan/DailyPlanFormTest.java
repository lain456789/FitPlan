package com.example.fitplan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class DailyPlanFormTest {
    private Context context;
    private String language;

    @Before
    public void rememberLanguage() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        language = context.getSharedPreferences("preferences", 0).getString("language", "zh");
    }

    @After
    public void restoreLanguage() {
        context.getSharedPreferences("preferences", 0)
                .edit()
                .putString("language", language)
                .commit();
    }

    private EditText minutes(MainActivity a) {
        return a.content.findViewWithTag("daily_minutes");
    }

    private Button generate(MainActivity a) {
        return (Button) ((ViewGroup) a.findViewById(R.id.page_actions)).getChildAt(0);
    }

    private String stored(MainActivity a) {
        StringBuilder result = new StringBuilder();
        try (Cursor cursor =
                a.store
                        .getReadableDatabase()
                        .rawQuery("SELECT id,kind,body,updated FROM documents ORDER BY id", null)) {
            while (cursor.moveToNext()) {
                for (int i = 0; i < 4; i++) result.append(cursor.getString(i)).append('\n');
            }
        }
        return result.toString();
    }

    private <T extends View> List<T> collect(View view, Class<T> type) {
        List<T> result = new ArrayList<>();
        if (type.isInstance(view)) result.add(type.cast(view));
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                result.addAll(collect(group.getChildAt(i), type));
        }
        return result;
    }

    private void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        android.os.SystemClock.sleep(250);
        Bitmap bitmap =
                InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        try (FileOutputStream output =
                new FileOutputStream(new File(context.getExternalFilesDir(null), name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } finally {
            bitmap.recycle();
        }
    }

    @Test
    public void overLimitImmediatelyShowsErrorAndInvalidSubmissionDoesNotWriteData() {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                a.setLanguage("zh");
                a.show("dailyPlan");
                String before = stored(a);
                EditText minutes = minutes(a);
                minutes.setText("181");
                assertEquals(a.s("daily_minutes_max"), minutes.getError().toString());
                generate(a).performClick();
                assertEquals("dailyPlan", a.page);
                assertEquals(before, stored(a));
                minutes.setText("99999999999999999999999999999");
                assertEquals(a.s("daily_minutes_max"), minutes.getError().toString());
                generate(a).performClick();
                assertEquals(before, stored(a));
                for (String invalid : new String[] {"", "9", "20.5"}) {
                    minutes.setText(invalid);
                    generate(a).performClick();
                    assertEquals(a.s("daily_minutes_invalid"), minutes.getError().toString());
                    assertEquals("dailyPlan", a.page);
                    assertEquals(before, stored(a));
                }
                minutes.setText("180");
                assertNull(minutes.getError());
            });
        }
    }

    @Test
    public void boundaryTimesCreatePlansAndTransactionRollsBackAllTestWrites() {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {
                a.show("dailyPlan");
                String before = stored(a);
                SQLiteDatabase db = a.store.getWritableDatabase();
                db.beginTransaction();
                try {
                    String planId = null;
                    for (String time : new String[] {"10", "180"}) {
                        if (!"dailyPlan".equals(a.page)) a.show("dailyPlan");
                        for (CheckBox part : collect(a.content, CheckBox.class))
                            part.setChecked("chest".equals(part.getTag()));
                        for (Spinner option : collect(a.content, Spinner.class))
                            option.setSelection(0);
                        minutes(a).setText(time);
                        generate(a).performClick();
                        assertEquals("planDetail", a.page);
                        JSONObject plan = a.find("plan", a.detailId);
                        assertEquals(Integer.parseInt(time), plan.optInt("minutes"));
                        assertEquals(
                                Integer.parseInt(time),
                                new DailyPlanScreen(a).todayCheck().optInt("minutes"));
                        assertTrue(plan.optJSONArray("rows").length() > 0);
                        if (planId != null) assertEquals(planId, plan.optString("id"));
                        planId = plan.optString("id");
                    }
                } finally {
                    db.endTransaction();
                }
                assertEquals(before, stored(a));
            });
        }
    }

    @Test
    public void bilingualFieldsAlignAndInvalidDraftSurvivesLanguageAndPageChanges()
            throws Exception {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            for (String lang : new String[] {"zh", "en"}) {
                scenario.onActivity(a -> {
                    a.setLanguage(lang);
                    a.show("dailyPlan");
                    minutes(a).setText("45");
                    for (CheckBox part : collect(a.content, CheckBox.class)) part.setChecked(false);
                    for (EditText input : collect(a.content, EditText.class))
                        if (input != minutes(a)) input.setText("");
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(a -> {
                    ViewGroup form = a.content.findViewWithTag("daily_form");
                    assertNotNull(form);
                    List<CheckBox> chips = collect(form, CheckBox.class);
                    assertEquals(Screen.PARTS.length, chips.size());
                    for (CheckBox chip : chips) {
                        assertTrue(chip.getWidth() > 0);
                        assertTrue(chip.getHeight() > 0);
                    }
                    assertTrue(minutes(a).getWidth() > 0);
                    Rect visible = new Rect();
                    assertTrue(generate(a).getGlobalVisibleRect(visible));
                    assertEquals(generate(a).getHeight(), visible.height());
                });
                screenshot("daily-form-" + lang);
                scenario.onActivity(a ->
                        ((ScrollView) a.findViewById(R.id.scroller)).smoothScrollTo(0, a.dp(340)));
                android.os.SystemClock.sleep(350);
                screenshot("daily-form-fields-" + lang);
            }
            scenario.onActivity(a -> {
                minutes(a).setText("181");
                a.show("plans");
                a.show("dailyPlan");
                assertEquals("181", minutes(a).getText().toString());
                assertEquals(a.s("daily_minutes_max"), minutes(a).getError().toString());
                a.setLanguage("zh");
                assertEquals("181", minutes(a).getText().toString());
                assertEquals(a.s("daily_minutes_max"), minutes(a).getError().toString());
            });
            scenario.recreate();
            scenario.onActivity(a -> {
                assertEquals("181", minutes(a).getText().toString());
                assertEquals(a.s("daily_minutes_max"), minutes(a).getError().toString());
            });
        }
    }
}
