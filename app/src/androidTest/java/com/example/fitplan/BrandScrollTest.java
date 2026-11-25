package com.example.fitplan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;

@RunWith(AndroidJUnit4.class)
public class BrandScrollTest {
    private void idle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void waitHome(ActivityScenario<MainActivity> scenario) {
        for (int i = 0; i < 100; i++) {
            boolean[] ready = {false};
            scenario.onActivity(a -> ready[0] = a.content.findViewWithTag("today_rings") != null);
            if (ready[0]) {
                idle();
                return;
            }
            android.os.SystemClock.sleep(50);
        }
        fail("Home did not finish loading");
    }

    private void screenshot(String name) throws Exception {
        idle();
        Bitmap bitmap =
                InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File directory =
                InstrumentationRegistry.getInstrumentation()
                        .getTargetContext()
                        .getExternalFilesDir(null);
        try (FileOutputStream out = new FileOutputStream(new File(directory, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            bitmap.recycle();
        }
    }

    @Test
    public void logoShrinksAndFadesContinuouslyWithoutMovingNavigationOrResizingContent()
            throws Exception {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            waitHome(scenario);
            int[] originalHeight = new int[1];
            Rect navigation = new Rect();
            scenario.onActivity(a -> {
                ScrollView scroll = a.findViewById(R.id.scroller);
                View brand = a.findViewById(R.id.app_brand);
                scroll.scrollTo(0, 0);
                assertEquals(1f, brand.getAlpha(), .001f);
                assertEquals(1f, brand.getScaleX(), .001f);
                originalHeight[0] = a.content.getHeight();
                assertTrue(a.findViewById(R.id.navigation).getGlobalVisibleRect(navigation));
            });
            screenshot("brand-top");
            scenario.onActivity(a -> {
                ScrollView scroll = a.findViewById(R.id.scroller);
                View brand = a.findViewById(R.id.app_brand);
                scroll.scrollTo(0, brand.getHeight() / 2);
                assertTrue(brand.getAlpha() > 0 && brand.getAlpha() < 1);
                assertTrue(brand.getScaleX() > 0 && brand.getScaleX() < 1);
                assertEquals(brand.getScaleX(), brand.getScaleY(), .001f);
            });
            screenshot("brand-half");
            scenario.onActivity(a -> {
                ScrollView scroll = a.findViewById(R.id.scroller);
                View brand = a.findViewById(R.id.app_brand);
                scroll.scrollTo(0, brand.getHeight() + a.dp(70));
                assertEquals(0f, brand.getAlpha(), .001f);
            });
            idle();
            scenario.onActivity(a -> {
                assertEquals(originalHeight[0], a.content.getHeight());
                Rect after = new Rect();
                assertTrue(a.findViewById(R.id.navigation).getGlobalVisibleRect(after));
                assertEquals(navigation, after);
                View main = a.findViewById(R.id.main);
                assertEquals(
                        "No reserved header gap above the scroll area",
                        main.getPaddingTop(),
                        a.findViewById(R.id.scroller).getTop());
            });
            screenshot("brand-hidden");
            scenario.onActivity(a -> {
                ScrollView scroll = a.findViewById(R.id.scroller);
                View brand = a.findViewById(R.id.app_brand);
                scroll.scrollTo(0, brand.getHeight() / 2);
                assertTrue(brand.getAlpha() > 0 && brand.getAlpha() < 1);
                scroll.scrollTo(0, 0);
                assertEquals(1f, brand.getAlpha(), .001f);
                assertEquals(1f, brand.getScaleX(), .001f);
                assertEquals(0f, brand.getTranslationY(), .001f);
            });
        }
    }

    @Test
    public void sharedHeaderWorksOnEveryRouteAndRestoresAtTheTop() {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            waitHome(scenario);
            for (String route :
                    new String[] {
                        "dailyPlan",
                        "plans",
                        "library",
                        "history",
                        "progress",
                        "coach",
                        "profile",
                        "manualHistory",
                        "workout",
                        "home"
                    }) {
                scenario.onActivity(a -> a.show(route));
                idle();
                if (route.equals("home")) waitHome(scenario);
                scenario.onActivity(a -> {
                    ScrollView scroll = a.findViewById(R.id.scroller);
                    View brand = a.findViewById(R.id.app_brand);
                    assertEquals(route, 0, scroll.getScrollY());
                    assertEquals(route, 1f, brand.getAlpha(), .001f);
                    assertEquals(route, 1f, brand.getScaleX(), .001f);
                    assertEquals(route, scroll.getChildAt(0), brand.getParent());
                    if (!route.equals("workout") && scroll.canScrollVertically(1)) {
                        scroll.scrollTo(0, brand.getHeight());
                        assertTrue(route, scroll.getScrollY() > 0);
                        assertTrue(route, brand.getAlpha() < 1);
                    }
                    ViewGroup nav = a.findViewById(R.id.navigation);
                    Rect visible = new Rect();
                    for (int i = 0; i < nav.getChildCount(); i++)
                        assertTrue(route, nav.getChildAt(i).getGlobalVisibleRect(visible));
                });
            }
        }
    }
}
