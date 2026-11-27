package com.example.fitplan;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.example.fitplan.Store.object;

import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Base64;
import android.widget.EditText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.matcher.ViewMatchers.Visibility;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;

@RunWith(AndroidJUnit4.class)
public final class CustomExerciseDeletionTest {
    private enum Media {
        PHOTO,
        GIF,
        NONE
    }

    @Test
    public void deletesExerciseAndPhotoFromLibrary() throws Exception {
        verifyDeletion(Media.PHOTO);
    }

    @Test
    public void deletesExerciseAndGifFromLibrary() throws Exception {
        verifyDeletion(Media.GIF);
    }

    @Test
    public void deletesExerciseWithoutImages() throws Exception {
        verifyDeletion(Media.NONE);
    }

    private void verifyDeletion(Media media) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String exerciseId = Store.id();
        String label = "Custom media test " + exerciseId;
        JSONObject exercise = object("id", exerciseId, "name", label, "part", "chest");
        JSONObject plan =
                object(
                        "id",
                        Store.id(),
                        "name",
                        "Media test plan",
                        "rows",
                        new JSONArray()
                                .put(
                                        object(
                                                "exerciseId",
                                                exerciseId,
                                                "sets",
                                                1,
                                                "reps",
                                                8,
                                                "weight",
                                                0)));
        JSONObject history =
                object(
                        "id",
                        Store.id(),
                        "name",
                        "Media test history",
                        "finished",
                        System.currentTimeMillis(),
                        "rows",
                        Store.copy(plan).optJSONArray("rows"));
        boolean animated = media == Media.GIF;
        File source =
                media == Media.NONE
                        ? null
                        : File.createTempFile(
                                "custom-media-test",
                                animated ? ".gif" : ".png",
                                context.getCacheDir());
        CustomMedia files = new CustomMedia(context, exerciseId);
        try (Store store = new Store(context)) {
            try {
                if (source != null) {
                    writeSource(source, animated);
                    files.importFrom(context, Uri.fromFile(source));
                    assertTrue(files.thumb.isFile());
                    assertTrue((animated ? files.gif : files.photo).isFile());
                }
                store.save("exercise", exercise);
                store.save("plan", plan);
                store.save("history", history);
                String[] labels = new String[5];
                try (ActivityScenario<MainActivity> scenario =
                        ActivityScenario.launch(MainActivity.class)) {
                    scenario.onActivity(a -> {
                        a.show("library");
                        labels[0] = a.s("browse_name");
                        labels[1] = a.s("delete_custom_exercise");
                        labels[2] = a.s("cancel");
                        labels[3] = a.s("confirm");
                        labels[4] = a.s(animated ? "exercise_demo" : "exercise_photo", label);
                    });
                    onView(withText(labels[0])).perform(scrollTo(), click());
                    onView(
                                    allOf(
                                            isAssignableFrom(EditText.class),
                                            withEffectiveVisibility(Visibility.VISIBLE)))
                            .perform(replaceText(label));
                    openRemoveDialog(labels[1], label);
                    onView(withText(labels[2])).inRoot(isDialog()).perform(click());
                    scenario.onActivity(a ->
                            assertEquals(
                                    exercise.toString(),
                                    a.find("exercise", exerciseId).toString()));
                    if (source != null) assertTrue(files.thumb.isFile());
                    openRemoveDialog(labels[1], label);
                    onView(withText(labels[3])).inRoot(isDialog()).perform(click());
                    onView(withContentDescription(labels[4])).check(doesNotExist());
                    onView(allOf(withText(labels[1]), hasSibling(hasDescendant(withText(label)))))
                            .check(doesNotExist());
                    assertFalse(files.photo.exists());
                    assertFalse(files.gif.exists());
                    assertFalse(files.thumb.exists());
                    if (source != null) assertTrue(source.isFile());
                    scenario.onActivity(a -> {
                        assertNull(a.find("exercise", exerciseId));
                        assertEquals(
                                plan.toString(), a.find("plan", plan.optString("id")).toString());
                        assertEquals(
                                history.toString(),
                                a.find("history", history.optString("id")).toString());
                    });
                }
            } finally {
                store.delete(exerciseId);
                store.delete(plan.optString("id"));
                store.delete(history.optString("id"));
                files.discard();
                if (source != null) source.delete();
            }
        }
    }

    private void openRemoveDialog(String action, String name) {
        onView(
                        allOf(
                                withText(action),
                                hasSibling(hasDescendant(withText(name))),
                                withEffectiveVisibility(Visibility.VISIBLE)))
                .perform(scrollTo(), click());
    }

    private void writeSource(File source, boolean animated) throws Exception {
        try (FileOutputStream output = new FileOutputStream(source)) {
            if (animated) {
                output.write(
                        Base64.decode(
                                "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7",
                                Base64.DEFAULT));
            } else {
                Bitmap bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(0xFF436A8A);
                try {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
                } finally {
                    bitmap.recycle();
                }
            }
        }
    }
}
