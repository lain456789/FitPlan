package com.example.fitplan;

import static com.example.fitplan.Store.copy;
import static com.example.fitplan.Store.id;
import static com.example.fitplan.Store.object;
import static com.example.fitplan.Store.put;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.database.sqlite.SQLiteException;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

final class TrainingScreen extends Screen {
    static final String REST_SECONDS_PREFERENCE = "workoutRestSeconds";
    private static final int DEFAULT_REST_SECONDS = 60;
    private static final int LONG_REST_SECONDS = 90;
    private static final int MAX_REST_SECONDS = 3600;
    private static final int PLAN_THUMBNAIL_HEIGHT_DP = 160;
    private Runnable refreshMuscleLibrary;
    private Runnable refreshNameLibrary;

    TrainingScreen(MainActivity activity) {
        super(activity);
    }

    void exercises() {
        activity.trainingTabs();
        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(heading);
        TextView title = text(heading, s("library"), 27);
        title.setTypeface(null, Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        Button add =
                addButton(
                        heading,
                        s("add_custom"),
                        false,
                        () ->
                                new CustomExerciseDialog()
                                        .show(
                                                activity.getSupportFragmentManager(),
                                                "customExercise"));
        RadioGroup modes = new RadioGroup(activity);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setBackground(UiKit.box(activity, UiKit.CARD, 0, 14));
        modes.setPadding(activity.dp(3), activity.dp(3), activity.dp(3), activity.dp(3));
        LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(-1, -2);
        modeLp.setMargins(0, activity.dp(6), 0, activity.dp(8));
        content.addView(modes, modeLp);
        RadioButton names = mode(modes, s("browse_name"));
        RadioButton muscles = mode(modes, s("browse_muscle"));
        muscles.setChecked(true);
        styleBrowseMode(muscles, names);
        LinearLayout namePane = pane(content);
        LinearLayout musclePane = pane(content);
        namePane.setVisibility(View.GONE);
        muscleLibrary(musclePane);
        modes.setOnCheckedChangeListener((group, checked) -> {
            boolean byMuscle = checked == muscles.getId();
            styleBrowseMode(muscles, names);
            if (byMuscle && musclePane.getChildCount() == 0) muscleLibrary(musclePane);
            if (!byMuscle && namePane.getChildCount() == 0) nameLibrary(namePane);
            namePane.setVisibility(byMuscle ? View.GONE : View.VISIBLE);
            musclePane.setVisibility(byMuscle ? View.VISIBLE : View.GONE);
        });
    }

    private RadioButton mode(RadioGroup parent, String label) {
        RadioButton option = new RadioButton(activity);
        option.setId(View.generateViewId());
        option.setButtonDrawable(null);
        option.setText(label);
        option.setTextSize(13);
        option.setGravity(Gravity.CENTER);
        option.setMinHeight(activity.dp(36));
        option.setPadding(activity.dp(10), activity.dp(7), activity.dp(10), activity.dp(7));
        parent.addView(option, new RadioGroup.LayoutParams(0, -2, 1));
        return option;
    }

    private void styleBrowseMode(RadioButton... buttons) {
        for (RadioButton b : buttons) {
            boolean active = b.isChecked();
            b.setBackground(active ? UiKit.box(activity, UiKit.SURFACE_HIGHLIGHT, 0, 11) : null);
            b.setTextColor(active ? MainActivity.FG : MainActivity.MUTED);
            b.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private LinearLayout pane(LinearLayout parent) {
        LinearLayout pane = new LinearLayout(activity);
        pane.setOrientation(LinearLayout.VERTICAL);
        parent.addView(pane);
        return pane;
    }

    private void muscleLibrary(LinearLayout pane) {
        musclePicker(pane, null);
    }

    private void musclePicker(LinearLayout pane, java.util.function.Consumer<JSONObject> onSelect) {
        MuscleCatalog[] groups = MuscleCatalog.values();
        String[] options = new String[groups.length + 2];
        options[0] = s("map_all");
        for (int i = 0; i < groups.length; i++) options[i + 1] = s("muscle_" + groups[i].key);
        options[groups.length + 1] = s("custom");
        int customIndex = groups.length + 1;
        LinearLayout diagram = card(pane);
        LinearLayout controls = pane(pane);
        Spinner picker = select(controls, s("map_pick"), options, 0);
        MuscleMapView map =
                new MuscleMapView(
                        activity,
                        key -> {
                            for (int i = 0; i < groups.length; i++) {
                                if (groups[i].key.equals(key)) {
                                    picker.setSelection(
                                            picker.getSelectedItemPosition() == i + 1 ? 0 : i + 1);
                                    break;
                                }
                            }
                        });
        diagram.addView(map, new LinearLayout.LayoutParams(-1, activity.dp(224)));
        text(diagram, s("map_faces"), 14).setGravity(Gravity.CENTER);
        View clear = button(controls, s("map_clear"), () -> picker.setSelection(0));
        LinearLayout results = pane(pane);
        Runnable render =
                () -> {
                    int pos = picker.getSelectedItemPosition();
                    JSONObject[] builtIns = new JSONObject[Catalog.EXERCISES.length];
                    List<JSONObject> customs = new ArrayList<>();
                    JSONArray entries = store.list("exercise");
                    for (int i = 0; i < entries.length(); i++) {
                        JSONObject ex = entries.optJSONObject(i);
                        int index = Catalog.builtIn(ex.optString("id"));
                        if (index >= 0) builtIns[index] = ex;
                        else customs.add(ex);
                    }
                    sortExercises(customs);
                    results.removeAllViews();
                    clear.setVisibility(pos == 0 ? View.GONE : View.VISIBLE);
                    map.highlight(pos == 0 || pos == customIndex ? "" : groups[pos - 1].key);
                    if (pos == 0) {
                        for (JSONObject ex : builtIns)
                            if (ex != null) exerciseCard(results, ex, onSelect);
                        if (!customs.isEmpty()) text(results, s("custom"), 16);
                        for (JSONObject ex : customs) exerciseCard(results, ex, onSelect);
                        return;
                    }
                    if (pos == customIndex) {
                        text(results, s("custom"), 16);
                        if (customs.isEmpty()) text(results, s("no_matches"), 16);
                        else for (JSONObject ex : customs) exerciseCard(results, ex, onSelect);
                        return;
                    }
                    MuscleCatalog group = groups[pos - 1];
                    int primaryCount = group.primary.length;
                    for (JSONObject ex : customs)
                        if (group.key.equals(ex.optString("muscle"))) primaryCount++;
                    text(
                            results,
                            primaryCount == 0 ? s("map_empty") : s("map_primary", primaryCount),
                            16);
                    for (int index : group.primary) {
                        if (builtIns[index] == null) continue;
                        if (index == 14 || index == 15)
                            text(results, s(index == 14 ? "map_crunch" : "map_stability"), 14);
                        exerciseCard(results, builtIns[index], onSelect);
                    }
                    boolean relatedHeading = false;
                    for (JSONObject ex : customs) {
                        if (group.key.equals(ex.optString("muscle")))
                            exerciseCard(results, ex, onSelect);
                    }
                    for (JSONObject ex : customs) {
                        if (!ex.optString("muscle").isEmpty()
                                || !group.part.equals(Catalog.partCode(ex.optString("part"))))
                            continue;
                        if (!relatedHeading)
                            text(results, s("related_custom", activity.partName(group.part)), 16);
                        relatedHeading = true;
                        exerciseCard(results, ex, onSelect);
                    }
                    if (group.assisting.length > 0) {
                        LinearLayout extra = new LinearLayout(activity);
                        extra.setOrientation(LinearLayout.VERTICAL);
                        extra.setVisibility(View.GONE);
                        button(
                                results,
                                s("map_secondary", group.assisting.length),
                                () -> {
                                    if (extra.getChildCount() == 0) {
                                        for (int index : group.assisting)
                                            if (builtIns[index] != null)
                                                exerciseCard(extra, builtIns[index], onSelect);
                                    }
                                    extra.setVisibility(
                                            extra.getVisibility() == View.VISIBLE
                                                    ? View.GONE
                                                    : View.VISIBLE);
                                });
                        results.addView(extra);
                    }
                };
        if (onSelect == null) refreshMuscleLibrary = render;
        picker.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(
                            AdapterView<?> parent, View view, int position, long id) {
                        render.run();
                    }

                    public void onNothingSelected(AdapterView<?> parent) {}
                });
    }

    private void nameLibrary(LinearLayout pane) {
        text(pane, s("library_intro"), 14);
        EditText search = input(pane, s("search"), "", false);
        Spinner filter =
                select(
                        pane,
                        s("part_filter"),
                        new String[] {
                            s("all"),
                            s("chest"),
                            s("shoulders"),
                            s("back"),
                            s("legs"),
                            s("arms"),
                            s("core")
                        },
                        0);
        LinearLayout results = column();
        pane.addView(results);
        Runnable render =
                () -> {
                    results.removeAllViews();
                    JSONArray all = store.list("exercise");
                    List<JSONObject> exercises = new ArrayList<>();
                    for (int i = 0; i < all.length(); i++) exercises.add(all.optJSONObject(i));
                    sortExercises(exercises);
                    int count = 0;
                    for (JSONObject ex : exercises) {
                        if (!activity.exerciseName(ex)
                                .toLowerCase(activity.locale())
                                .contains(
                                        search.getText()
                                                .toString()
                                                .trim()
                                                .toLowerCase(activity.locale()))) continue;
                        if (filter.getSelectedItemPosition() != 0
                                && !PARTS[filter.getSelectedItemPosition() - 1].equals(
                                        Catalog.partCode(ex.optString("part")))) continue;
                        count++;
                        exerciseCard(results, ex);
                    }
                    if (count == 0) text(results, s("no_matches"), 16);
                };
        search.addTextChangedListener(
                new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int a, int c, int f) {}

                    public void onTextChanged(CharSequence s, int a, int b, int c) {
                        render.run();
                    }

                    public void afterTextChanged(Editable e) {}
                });
        filter.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(
                            android.widget.AdapterView<?> p, View v, int pos, long id) {
                        render.run();
                    }

                    public void onNothingSelected(android.widget.AdapterView<?> p) {}
                });
        refreshNameLibrary = render;
        render.run();
    }

    private void exerciseCard(LinearLayout results, JSONObject exercise) {
        exerciseCard(results, exercise, null);
    }

    private void exerciseCard(
            LinearLayout results,
            JSONObject exercise,
            java.util.function.Consumer<JSONObject> select) {
        String id = exercise.optString("id");
        LinearLayout cardView = card(results);
        if (select != null) {
            cardView.setClickable(true);
            cardView.setOnClickListener(v -> select.accept(exercise));
        }
        String label = activity.exerciseName(exercise);
        View thumbnail = exerciseThumbnail(id, label);
        LinearLayout header = new LinearLayout(activity);
        cardView.addView(header, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout labels = headerWithImage(header, thumbnail);
        text(labels, label, 18);
        text(labels, activity.partName(exercise.optString("part")), 14);
        text(
                labels,
                Catalog.builtIn(id) >= 0 ? equipmentText(id) : customEquipmentText(exercise),
                14);
        if (select == null && Catalog.builtIn(id) < 0)
            deleteButton(
                    cardView,
                    s("delete_custom_exercise"),
                    true,
                    () ->
                            confirm(
                                    s("confirm_delete_custom_exercise", label),
                                    () -> deleteCustomExercise(id)));
    }

    private void deleteCustomExercise(String id) {
        try {
            store.deleteCustomExercise(id);
        } catch (SQLiteException e) {
            toast(s("delete_custom_exercise_failed"));
            return;
        }
        CustomMedia files = CustomMedia.savedFiles(activity, id);
        boolean cleared = files == null || files.discard();
        if (refreshMuscleLibrary != null) refreshMuscleLibrary.run();
        if (refreshNameLibrary != null) refreshNameLibrary.run();
        toast(s(cleared ? "custom_exercise_deleted" : "custom_exercise_media_cleanup_failed"));
    }

    void pickExercise(JSONObject plan) {
        LinearLayout form = column();
        musclePicker(form, ex -> addExercise(plan, ex));
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);
        activity.activeDialog =
                new AlertDialog.Builder(activity)
                        .setTitle(s("choose_exercise"))
                        .setView(scroll)
                        .setNegativeButton(s("cancel"), null)
                        .create();
        activity.activeDialog.show();
    }

    void addExercise(JSONObject plan, JSONObject exercise) {
        String exerciseId = exercise.optString("id");
        JSONArray rows = plan.optJSONArray("rows");
        for (int i = 0; i < rows.length(); i++) {
            JSONObject existing = rows.optJSONObject(i);
            if (existing != null && exerciseId.equals(existing.optString("exerciseId"))) {
                toast(s("plan_duplicate_exercise"));
                return;
            }
        }
        JSONObject row =
                object(
                        "exerciseId",
                        exerciseId,
                        "name",
                        activity.exerciseName(exercise),
                        "part",
                        exercise.optString("part"),
                        "weight",
                        0,
                        "sets",
                        3,
                        "reps",
                        10);
        rows.put(row);
        store.save("plan", plan);
        activity.activeDialog.dismiss();
        activity.openPlan(plan);
    }

    // 自动生成不重复的默认计划名：我的计划1、我的计划2……
    private String defaultPlanName() {
        String base = s("my_plan");
        Set<String> names = new HashSet<>();
        JSONArray plans = store.list("plan");
        for (int i = 0; i < plans.length(); i++)
            names.add(plans.optJSONObject(i).optString("name"));
        int n = 1;
        while (names.contains(base + n)) n++;
        return base + n;
    }

    // 从历史训练或模板中抽取处方行，复制为一个新计划。
    private JSONObject planFromRows(JSONArray sourceRows) {
        JSONArray rows = new JSONArray();
        if (sourceRows != null)
            for (int i = 0; i < sourceRows.length(); i++) {
                JSONObject r = sourceRows.optJSONObject(i);
                rows.put(
                        object(
                                "exerciseId",
                                r.optString("exerciseId", r.optString("id")),
                                "name",
                                activity.exerciseName(r),
                                "part",
                                r.optString("part"),
                                "weight",
                                r.optDouble("weight"),
                                "sets",
                                r.optInt("sets"),
                                "reps",
                                r.optInt("reps")));
            }
        return object(
                "name", defaultPlanName(), "created", System.currentTimeMillis(), "rows", rows);
    }

    void plans() {
        activity.trainingTabs();
        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        TextView headingTitle = text(heading, s("plans_title"), 22);
        headingTitle.setTypeface(null, Typeface.BOLD);
        headingTitle.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        addButton(
                heading,
                s("new_plan"),
                false,
                () -> {
                    LinearLayout form = column();
                    EditText name = input(form, s("plan_name"), defaultPlanName(), false);
                    dialog(
                            s("new_plan_title"),
                            form,
                            () -> {
                                String n = name.getText().toString().trim();
                                if (n.isEmpty())
                                    throw new IllegalArgumentException(s("enter_name"));
                                store.save(
                                        "plan",
                                        object(
                                                "name",
                                                n,
                                                "created",
                                                System.currentTimeMillis(),
                                                "rows",
                                                new JSONArray()));
                                show("plans");
                            });
                });
        LinearLayout copyRow = new LinearLayout(activity);
        copyRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams copyRowLp = new LinearLayout.LayoutParams(-1, -2);
        copyRowLp.setMargins(0, activity.dp(4), 0, activity.dp(6));
        content.addView(copyRow, copyRowLp);
        Button copyLast =
                button(
                        copyRow,
                        s("copy_from_last"),
                        () -> {
                            JSONObject last = store.first("history");
                            if (last == null) {
                                toast(s("no_history"));
                                return;
                            }
                            store.save("plan", planFromRows(last.optJSONArray("rows")));
                            show("plans");
                        });
        LinearLayout.LayoutParams copyLastLp = new LinearLayout.LayoutParams(0, -2, 1);
        copyLastLp.setMarginEnd(activity.dp(8));
        copyLast.setLayoutParams(copyLastLp);
        Button copyTemplate =
                button(
                        copyRow,
                        s("copy_from_template"),
                        () -> {
                            JSONArray templates = store.list("plan");
                            if (templates.length() == 0) {
                                toast(s("no_template"));
                                return;
                            }
                            String[] names = new String[templates.length()];
                            for (int i = 0; i < templates.length(); i++)
                                names[i] = activity.planName(templates.optJSONObject(i));
                            activity.activeDialog =
                                    new AlertDialog.Builder(activity)
                                            .setTitle(s("copy_from_template"))
                                            .setItems(
                                                    names,
                                                    (d, which) -> {
                                                        store.save(
                                                                "plan",
                                                                planFromRows(
                                                                        templates
                                                                                .optJSONObject(
                                                                                        which)
                                                                                .optJSONArray(
                                                                                        "rows")));
                                                        show("plans");
                                                    })
                                            .setNegativeButton(s("cancel"), null)
                                            .show();
                        });
        copyTemplate.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        JSONArray plans = store.list("plan");
        if (plans.length() == 0) text(content, s("empty_plans"), 15);
        for (int i = 0; i < plans.length(); i++) {
            JSONObject plan = plans.optJSONObject(i);
            LinearLayout c = card(content);

            // Tapping the card header opens Plan Detail / Edit (Apple Fitness native card pattern)
            LinearLayout headerRow = new LinearLayout(activity);
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setGravity(Gravity.CENTER_VERTICAL);
            headerRow.setOnClickListener(v -> activity.openPlan(plan));
            c.addView(headerRow, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout metaCol = new LinearLayout(activity);
            metaCol.setOrientation(LinearLayout.VERTICAL);
            headerRow.addView(metaCol, new LinearLayout.LayoutParams(0, -2, 1));

            TextView planTitle = new TextView(activity);
            planTitle.setText(activity.planName(plan));
            planTitle.setTextSize(18);
            planTitle.setTextColor(MainActivity.FG);
            planTitle.setTypeface(null, Typeface.BOLD);
            metaCol.addView(planTitle);

            TextView countView = new TextView(activity);
            countView.setText(s("exercise_count", plan.optJSONArray("rows").length()));
            countView.setTextSize(13);
            countView.setTextColor(MainActivity.MUTED);
            LinearLayout.LayoutParams cntLp = new LinearLayout.LayoutParams(-2, -2);
            cntLp.topMargin = activity.dp(2);
            metaCol.addView(countView, cntLp);

            android.widget.ImageView chevron = new android.widget.ImageView(activity);
            chevron.setImageResource(R.drawable.ic_action_chevron);
            chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            headerRow.addView(
                    chevron, new LinearLayout.LayoutParams(activity.dp(18), activity.dp(18)));

            LinearLayout cardActions = new LinearLayout(activity);
            cardActions.setOrientation(LinearLayout.HORIZONTAL);
            cardActions.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams caLp = new LinearLayout.LayoutParams(-1, -2);
            caLp.topMargin = activity.dp(12);
            c.addView(cardActions, caLp);

            Button startBtn = primaryButton(cardActions, s("start_workout"), () -> start(plan));
            LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(0, -2, 1f);
            startLp.setMarginEnd(activity.dp(10));
            startBtn.setLayoutParams(startLp);

            UiKit.iconDeleteButton(
                    activity,
                    cardActions,
                    s("delete_plan"),
                    () ->
                            confirm(
                                    s("delete_plan_confirm"),
                                    () -> {
                                        store.delete(plan.optString("id"));
                                        show("plans");
                                    }));
        }
    }

    void editPlan(JSONObject plan) {
        content.removeAllViews();
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        Button back = backButton(actions, s("back_label"), () -> show("plans"));
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(-2, -2);
        backLp.setMarginEnd(activity.dp(8));
        back.setLayoutParams(backLp);
        Button renameBtn =
                editButton(
                        actions,
                        s("rename"),
                        () -> {
                            LinearLayout f = column();
                            EditText n = input(f, s("name"), activity.planName(plan), false);
                            dialog(
                                    s("rename_plan"),
                                    f,
                                    () -> {
                                        if (n.getText().toString().trim().isEmpty())
                                            throw new IllegalArgumentException(s("enter_name"));
                                        put(plan, "name", n.getText().toString().trim());
                                        put(plan, "autoName", false);
                                        store.save("plan", plan);
                                        activity.openPlan(plan);
                                    });
                        });
        LinearLayout.LayoutParams renameLp = new LinearLayout.LayoutParams(0, -2, 1);
        renameLp.setMarginEnd(activity.dp(8));
        renameBtn.setLayoutParams(renameLp);
        Button addBtn = addButton(actions, s("add_exercise"), false, () -> pickExercise(plan));
        addBtn.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        title(activity.planName(plan));
        if (plan.has("note") || plan.has("noteCode")) text(content, s("plan_note"), 14);
        Runnable refreshBudget = planBudget(plan);
        refreshBudget.run();
        JSONArray rows = plan.optJSONArray("rows"), history = store.list("history");
        ProgressScreen progress = new ProgressScreen(activity);
        HorizontalScrollView strip = new HorizontalScrollView(activity);
        strip.setHorizontalScrollBarEnabled(false);
        strip.setClipChildren(false);
        strip.setClipToPadding(false);
        content.addView(strip, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout track = new LinearLayout(activity);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setClipChildren(false);
        track.setClipToPadding(false);
        track.setPadding(0, activity.dp(4), 0, activity.dp(8));
        strip.addView(track, new LinearLayout.LayoutParams(-2, -1));
        int cardWidth = Math.round(activity.getResources().getDisplayMetrics().widthPixels * 0.90f);
        final LinearLayout[] dragging = {null};
        final int[] dragFrom = {-1};
        final int[] dragTarget = {-1};
        final float[] lastX = {0};
        final float[] downX = {0};
        final int cardGap = activity.dp(12);
        for (int i = 0; i < rows.length(); i++) {
            final int index = i;
            JSONObject row = rows.optJSONObject(i);
            LinearLayout c = activity.column();
            c.setBackground(UiKit.cardSurface(activity));
            c.setElevation(activity.dp(2));
            LinearLayout.LayoutParams cardLayout = new LinearLayout.LayoutParams(cardWidth, -1);
            cardLayout.setMarginEnd(cardGap);
            track.addView(c, cardLayout);
            View thumbnail =
                    exerciseThumbnail(row.optString("exerciseId"), activity.exerciseName(row));
            if (thumbnail != null) {
                c.addView(
                        thumbnail,
                        new LinearLayout.LayoutParams(-1, activity.dp(PLAN_THUMBNAIL_HEIGHT_DP)));
            }
            LinearLayout header = pane(c);
            text(header, activity.exerciseName(row), 18);
            if (Catalog.builtIn(row.optString("exerciseId")) < 0)
                text(header, equipmentText(row.optString("exerciseId")), 14);
            LinearLayout sliders = pane(c);
            LinearLayout details = pane(c);
            LinearLayout confirmation = pane(c);
            Runnable refreshDetails =
                    () -> {
                        details.removeAllViews();
                        if (row.has("reason")
                                || row.has("reasonCode")
                                || row.optBoolean("userConfirmed"))
                            text(details, activity.reason(row), 14);
                        progress.planAdvice(plan, index, details, history);
                        confirmation.setVisibility(
                                PlanEdits.needsConfirmation(plan, row) ? View.VISIBLE : View.GONE);
                    };
            Runnable refresh =
                    () -> {
                        refreshBudget.run();
                        refreshDetails.run();
                    };
            prescriptionSlider(sliders, plan, row, "weight", "weight_label", 0, 100, refresh);
            prescriptionSlider(sliders, plan, row, "sets", "sets_label", 1, 10, refresh);
            prescriptionSlider(sliders, plan, row, "reps", "reps_label", 1, 20, refresh);
            if (PlanEdits.needsConfirmation(plan, row)) {
                text(confirmation, s("confirm_weight_hint"), 14);
                button(
                        confirmation,
                        s("confirm"),
                        () -> {
                            updatePrescription(plan, row, "weight", row.optDouble("weight"));
                            refresh.run();
                        });
            }
            refreshDetails.run();
            LinearLayout rowActions = new LinearLayout(activity);
            rowActions.setOrientation(LinearLayout.HORIZONTAL);
            rowActions.setGravity(Gravity.CENTER);
            c.addView(rowActions, new LinearLayout.LayoutParams(-1, -2));
            TextView prev = actionButton(rowActions, s("move_left"));
            prev.setEnabled(index > 0);
            prev.setAlpha(index > 0 ? 1f : 0.35f);
            prev.setOnClickListener(v -> moveRow(rows, plan, index, index - 1));
            TextView remove = dangerActionButton(rowActions, s("remove_short"));
            remove.setOnClickListener(v -> {
                rows.remove(index);
                store.save("plan", plan);
                activity.openPlan(plan);
            });
            TextView next = actionButton(rowActions, s("move_right"));
            next.setEnabled(index < rows.length() - 1);
            next.setAlpha(index < rows.length() - 1 ? 1f : 0.35f);
            next.setOnClickListener(v -> moveRow(rows, plan, index, index + 1));
            header.setOnLongClickListener(v -> {
                if (rows.length() <= 1) return false;
                dragging[0] = c;
                dragFrom[0] = index;
                dragTarget[0] = index;
                downX[0] = lastX[0];
                c.setElevation(activity.dp(18));
                c.setAlpha(0.96f);
                c.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start();
                header.requestDisallowInterceptTouchEvent(true);
                return true;
            });
            header.setOnTouchListener((v, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX[0] = event.getRawX();
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        if (dragging[0] == null) {
                            lastX[0] = event.getRawX();
                            return false;
                        }
                        {
                            float dx = event.getRawX() - downX[0];
                            c.setTranslationX(dx);
                            float step = cardWidth + cardGap;
                            int target = Math.round((dragFrom[0] * step + dx) / step);
                            target = Math.max(0, Math.min(rows.length() - 1, target));
                            if (target != dragTarget[0]) {
                                dragTarget[0] = target;
                                for (int k = 0; k < track.getChildCount(); k++) {
                                    View other = track.getChildAt(k);
                                    if (other == c) continue;
                                    float shift = 0;
                                    if (dragFrom[0] < target && k > dragFrom[0] && k <= target)
                                        shift = -step;
                                    else if (dragFrom[0] > target && k >= target && k < dragFrom[0])
                                        shift = step;
                                    other.animate().translationX(shift).setDuration(180).start();
                                }
                            }
                            lastX[0] = event.getRawX();
                            return true;
                        }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (dragging[0] == null) return false;
                        {
                            int target = dragTarget[0];
                            if (target != dragFrom[0]) {
                                JSONArray reordered = reorder(rows, dragFrom[0], target);
                                while (rows.length() > 0) rows.remove(0);
                                for (int k = 0; k < reordered.length(); k++)
                                    rows.put(reordered.opt(k));
                                store.save("plan", plan);
                            }
                            dragging[0] = null;
                            header.requestDisallowInterceptTouchEvent(false);
                            activity.openPlan(plan);
                            return true;
                        }
                }
                return false;
            });
        }
        primaryButton(content, s("start_plan"), () -> start(plan));
    }

    private static JSONArray reorder(JSONArray rows, int from, int to) {
        java.util.List<Object> items = new java.util.ArrayList<>();
        for (int i = 0; i < rows.length(); i++) items.add(rows.opt(i));
        Object moved = items.remove(from);
        items.add(to, moved);
        JSONArray out = new JSONArray();
        for (Object item : items) out.put(item);
        return out;
    }

    private Runnable planBudget(JSONObject plan) {
        if (plan.has("checkin")) {
            JSONArray items = plan.optJSONArray("rows");
            Set<String> covered = new HashSet<>();
            for (int i = 0; i < items.length(); i++) {
                JSONObject row = items.optJSONObject(i);
                covered.add(Catalog.partCode(row.optString("part")));
            }
            TextView budget = text(content, "", 16);
            if (plan.has("baselineTotalSets"))
                text(content, s("plan_baseline", plan.optInt("baselineTotalSets")), 14);
            if (plan.optBoolean("timeLimited")) text(content, s("plan_time_limit"), 14);
            JSONArray requested = plan.optJSONObject("checkin").optJSONArray("parts");
            StringJoiner missing = new StringJoiner(" / ");
            if (requested != null)
                for (int i = 0; i < requested.length(); i++)
                    if (!covered.contains(Catalog.partCode(requested.optString(i))))
                        missing.add(activity.partName(requested.optString(i)));
            if (missing.length() > 0)
                text(content, s("plan_missing_parts", missing.toString()), 14);
            TextView overBudget = text(content, s("plan_over_budget"), 14);
            return () -> {
                int total = 0;
                for (int i = 0; i < items.length(); i++)
                    total += items.optJSONObject(i).optInt("sets");
                int minutes = items.length() == 0 ? 0 : 5 + total * 3;
                budget.setText(s("plan_budget", total, minutes));
                overBudget.setVisibility(
                        plan.optInt("minutes") > 0 && minutes > plan.optInt("minutes")
                                ? View.VISIBLE
                                : View.GONE);
            };
        }
        return () -> {};
    }

    private void updatePrescription(JSONObject plan, JSONObject row, String key, double value) {
        PlanEdits.confirm(
                row,
                key.equals("weight") ? value : row.optDouble("weight"),
                key.equals("sets") ? (int) value : row.optInt("sets"),
                key.equals("reps") ? (int) value : row.optInt("reps"),
                System.currentTimeMillis());
        store.save("plan", plan);
    }

    private void prescriptionSlider(
            LinearLayout card,
            JSONObject plan,
            JSONObject row,
            String key,
            String labelKey,
            int min,
            int max,
            Runnable refresh) {
        String label = s(labelKey);
        boolean weight = key.equals("weight");
        double step = weight ? 0.5 : 1;
        LinearLayout line = pane(card);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView labelView = new TextView(activity);
        labelView.setText(label);
        labelView.setTextSize(14);
        labelView.setTextColor(MainActivity.MUTED);
        line.addView(labelView, new LinearLayout.LayoutParams(0, -2, 1));
        TextView minus = stepperButton(line, "\u2212");
        TextView number = stepperNumber(line);
        TextView plus = stepperButton(line, "+");
        Runnable sync =
                () -> {
                    double value = row.optDouble(key);
                    String text =
                            java.math.BigDecimal.valueOf(value)
                                    .stripTrailingZeros()
                                    .toPlainString();
                    number.setText(text);
                    number.setContentDescription(label + " " + text);
                };
        sync.run();
        number.setOnClickListener(v -> {
            LinearLayout form = column();
            EditText field = input(form, label, number.getText().toString(), true);
            field.selectAll();
            dialog(
                    label,
                    form,
                    () -> {
                        double value = weight ? number(field, min, max) : integer(field, min, max);
                        updatePrescription(plan, row, key, value);
                        sync.run();
                        refresh.run();
                    });
        });
        minus.setOnClickListener(v -> {
            updatePrescription(plan, row, key, Math.max(min, row.optDouble(key) - step));
            sync.run();
            refresh.run();
        });
        plus.setOnClickListener(v -> {
            updatePrescription(plan, row, key, Math.min(max, row.optDouble(key) + step));
            sync.run();
            refresh.run();
        });
    }

    private TextView stepperButton(LinearLayout parent, String glyph) {
        TextView b = new TextView(activity);
        b.setText(glyph);
        b.setTextSize(20);
        b.setTextColor(MainActivity.FG);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        b.setBackground(UiKit.box(activity, UiKit.SURFACE, UiKit.BORDER, 20));
        parent.addView(b, new LinearLayout.LayoutParams(activity.dp(40), activity.dp(40)));
        return b;
    }

    private TextView stepperNumber(LinearLayout parent) {
        TextView b = new TextView(activity);
        b.setTextSize(17);
        b.setTextColor(MainActivity.ACCENT);
        b.setTypeface(null, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        b.setBackground(UiKit.box(activity, UiKit.SURFACE, UiKit.BORDER, 20));
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(activity.dp(64), activity.dp(40));
        lp.setMarginStart(activity.dp(8));
        lp.setMarginEnd(activity.dp(8));
        parent.addView(b, lp);
        return b;
    }

    private TextView actionButton(LinearLayout parent, String text) {
        TextView b = new TextView(activity);
        b.setText(text);
        b.setTextSize(13);
        b.setTextColor(MainActivity.FG);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        b.setBackground(UiKit.box(activity, UiKit.SURFACE, UiKit.BORDER_STRONG, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMarginEnd(activity.dp(8));
        b.setMinHeight(activity.dp(36));
        b.setPadding(activity.dp(6), activity.dp(7), activity.dp(6), activity.dp(7));
        parent.addView(b, lp);
        return b;
    }

    private TextView dangerActionButton(LinearLayout parent, String text) {
        TextView b = new TextView(activity);
        b.setText(text);
        b.setTextSize(13);
        b.setTextColor(UiKit.DANGER);
        b.setTypeface(null, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        b.setBackground(UiKit.box(activity, UiKit.DANGER_BG, UiKit.DANGER_BORDER, 18));
        b.setMinHeight(activity.dp(36));
        b.setPadding(activity.dp(6), activity.dp(7), activity.dp(6), activity.dp(7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMarginEnd(activity.dp(8));
        parent.addView(b, lp);
        return b;
    }

    private void moveRow(JSONArray rows, JSONObject plan, int from, int to) {
        if (from < 0 || from >= rows.length() || to < 0 || to >= rows.length()) return;
        JSONArray reordered = reorder(rows, from, to);
        while (rows.length() > 0) rows.remove(0);
        for (int k = 0; k < reordered.length(); k++) rows.put(reordered.opt(k));
        store.save("plan", plan);
        activity.openPlan(plan);
    }

    void start(JSONObject plan) {
        if (store.first("active") != null) {
            toast(s("active_exists"));
            show("workout");
            return;
        }
        if (plan.optJSONArray("rows").length() == 0) {
            toast(s("add_first"));
            return;
        }
        JSONArray prescriptions = plan.optJSONArray("rows");
        for (int i = 0; i < prescriptions.length(); i++)
            if (PlanEdits.needsConfirmation(plan, prescriptions.optJSONObject(i))) {
                toast(s("confirm_weight_before_start"));
                activity.openPlan(plan);
                return;
            }
        JSONObject session = copy(plan);
        put(session, "id", id());
        put(session, "planId", plan.optString("id"));
        put(session, "started", System.currentTimeMillis());
        JSONArray rows = session.optJSONArray("rows");
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            JSONArray logs = new JSONArray();
            put(row, "rowId", id());
            for (int s = 0; s < row.optInt("sets"); s++)
                logs.put(
                        object(
                                "weight",
                                row.optDouble("weight"),
                                "reps",
                                row.optInt("reps"),
                                "done",
                                false));
            put(row, "logs", logs);
        }
        store.save("active", session);
        show("workout");
    }

    private void workoutCards(JSONObject session) {
        JSONArray rows = session.optJSONArray("rows");
        if (rows == null || rows.length() == 0) return;
        String id = session.optString("id");
        if (!id.equals(activity.workoutSessionId)) {
            activity.workoutSessionId = id;
            activity.workoutExerciseIndex = session.optInt("displayRow");
        }
        if (rows.length() == 1) {
            workoutCard(content, session, 0);
            return;
        }
        ExercisePager pager =
                new ExercisePager(
                        activity,
                        rows.length(),
                        (page, index) -> workoutCard(page, session, index));
        pager.attach(
                content,
                this,
                "workout_exercise",
                activity.workoutExerciseIndex,
                index -> activity.workoutExerciseIndex = index);
    }

    private void workoutCard(LinearLayout parent, JSONObject session, int rowIndex) {
        JSONObject row = session.optJSONArray("rows").optJSONObject(rowIndex);
        String exerciseId = row.optString("exerciseId");
        String name = activity.exerciseName(row);
        LinearLayout card = card(parent);
        LinearLayout header = new LinearLayout(activity);
        card.addView(header, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout details = headerWithImage(header, exerciseThumbnail(exerciseId, name));
        text(details, name, 18).setTypeface(null, Typeface.BOLD);
        if (Catalog.builtIn(exerciseId) < 0) text(details, equipmentText(exerciseId), 14);
        int targetSets = row.optInt("sets");
        int pending = WorkoutAdjustments.pendingRow(row);
        boolean adjusted = row.optBoolean("pendingEdited");
        JSONArray logs = row.optJSONArray("logs");
        if (adjusted && pending > 0) {
            JSONObject next = logs.optJSONObject(WorkoutAdjustments.firstPendingSet(row));
            text(
                    details,
                    s(
                            "pending_prescription",
                            num(next.optDouble("weight")),
                            pending,
                            TrainingFeedback.targetReps(row, next)),
                    14);
        } else if (!adjusted) {
            text(
                    details,
                    s("prescription", num(row.optDouble("weight")), targetSets, row.optInt("reps")),
                    14);
        }
        int recordedSets = 0;
        int skippedSets = 0;
        String skipReason = "unknown";
        if (logs != null) {
            for (int i = 0; i < logs.length(); i++) {
                JSONObject log = logs.optJSONObject(i);
                if (log == null) continue;
                if (log.optBoolean("done")) recordedSets++;
                else if (log.optBoolean("skipped") && "user".equals(log.optString("skipReason"))) {
                    skippedSets++;
                    skipReason = TrainingFeedback.reason(log, session);
                }
            }
        }
        text(details, s("workout_set_progress", recordedSets, targetSets), 14);
        if (pending > 0) {
            LinearLayout actions = new LinearLayout(activity);
            card.addView(actions);
            Button complete =
                    primaryButton(
                            actions, s("complete_set"), () -> completeNextSet(session, rowIndex));
            complete.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            Button adjust =
                    button(
                            actions,
                            s("adjust_exercise"),
                            () -> chooseExerciseAdjustment(session, rowIndex));
            LinearLayout.LayoutParams adjustParams = new LinearLayout.LayoutParams(0, -2, 1);
            adjustParams.setMarginStart(activity.dp(8));
            adjust.setLayoutParams(adjustParams);
        } else if (row.optBoolean("retired")) {
            text(details, s("replacement_retired"), 14);
        } else if (skippedSets > 0) {
            text(details, s("skipped_sets_status", skippedSets), 14);
            showSkipReason(details, skipReason);
        } else {
            boolean completed = targetSets > 0 && recordedSets >= targetSets;
            String status = completed ? "workout_exercise_done" : "no_pending_sets";
            TextView statusLabel = text(details, s(status), 14);
            if (completed) statusLabel.setTextColor(MainActivity.ACCENT);
        }
        if ((adjusted || row.optBoolean("retired") || skippedSets > 0) && logs != null) {
            for (int i = 0; i < logs.length(); i++) {
                JSONObject log = logs.optJSONObject(i);
                if (log != null && log.optBoolean("done")) {
                    text(
                            card,
                            s("log_line", i + 1, s("done"), actualWeight(log), log.optInt("reps")),
                            14);
                }
            }
        }
    }

    private void completeNextSet(JSONObject session, int rowIndex) {
        JSONObject row = session.optJSONArray("rows").optJSONObject(rowIndex);
        JSONArray logs = row.optJSONArray("logs");
        int logIndex = WorkoutAdjustments.firstPendingSet(row);
        if (logIndex < 0) {
            refreshAtCurrentPosition("workout");
            return;
        }
        JSONObject log = logs.optJSONObject(logIndex);
        try {
            JSONObject updated =
                    TrainingFeedback.record(
                            session,
                            rowIndex,
                            logIndex,
                            log.optDouble("weight", row.optDouble("weight")),
                            TrainingFeedback.targetReps(row, log),
                            true,
                            "appropriate",
                            "none",
                            System.currentTimeMillis());
            store.saveSessionEdit("active", session, updated, false);
        } catch (TrainingFeedback.Invalid e) {
            toast(s(e.key));
        } catch (Store.StaleWorkout e) {
            toast(s("feedback_stale"));
            show("workout");
            return;
        }
        refreshAtCurrentPosition("workout");
    }

    void workout() {
        TextView workoutTitle = text(content, s("workout_title"), 16);
        workoutTitle.setTypeface(null, Typeface.BOLD);
        workoutTitle.setGravity(Gravity.END);
        workoutTitle.setPadding(0, activity.dp(2), 0, activity.dp(4));
        JSONObject session = store.first("active");
        if (session == null) {
            text(content, s("no_active"), 16);
            button(content, s("choose_plan"), () -> show("plans"));
            return;
        }
        workoutCards(session);
        workoutTimers(session);
        LinearLayout adjustment = card(content);
        TextView summary = text(adjustment, "", 13);
        Button shorten =
                button(adjustment, s("shorten_remaining"), () -> shortenRemaining(session));
        TextView none = text(adjustment, s("adjust_nothing_left"), 14);
        Runnable refreshSummary =
                () -> {
                    int pending = WorkoutAdjustments.pending(session);
                    summary.setText(
                            s(
                                    "remaining_summary",
                                    performedSets(session),
                                    pending,
                                    (WorkoutAdjustments.estimateSeconds(
                                                            session, System.currentTimeMillis())
                                                    + 59)
                                            / 60));
                    shorten.setVisibility(pending > 0 ? View.VISIBLE : View.GONE);
                    none.setVisibility(pending == 0 ? View.VISIBLE : View.GONE);
                };
        refreshSummary.run();
        adjustmentHistory(session, content);
        LinearLayout endButtons = new LinearLayout(activity);
        endButtons.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(endButtons, new LinearLayout.LayoutParams(-1, -2));
        Button finish =
                primaryButton(
                        endButtons,
                        s("finish"),
                        () -> {
                            if (performedSets(session) == 0
                                    && TrainingFeedback.recorded(session) == 0) {
                                toast(s("one_set"));
                                return;
                            }
                            finishWorkout(session);
                        });
        LinearLayout.LayoutParams finishLp = new LinearLayout.LayoutParams(0, -2, 1);
        finishLp.setMarginEnd(activity.dp(8));
        finish.setLayoutParams(finishLp);
        Button discard =
                deleteButton(
                        endButtons,
                        s("discard"),
                        false,
                        () -> {
                            LinearLayout form = column();
                            CheckBox delete = new CheckBox(activity);
                            delete.setText(s("delete_with_workout"));
                            form.addView(delete);
                            dialog(
                                    s("discard_confirm"),
                                    form,
                                    s("confirm"),
                                    () -> {
                                        if (delete.isChecked()) {
                                            store.delete(session.optString("id"));
                                        } else {
                                            JSONObject archived = copy(session);
                                            put(archived, "finished", System.currentTimeMillis());
                                            put(archived, "abandoned", true);
                                            put(archived, "restUntil", 0);
                                            archived.remove("displayRow");
                                            try {
                                                store.saveSessionEdit(
                                                        "active", session, archived, true);
                                            } catch (Store.StaleWorkout e) {
                                                activity.activeDialog.dismiss();
                                                show("workout");
                                                toast(s("feedback_stale"));
                                                return;
                                            }
                                        }
                                        show(delete.isChecked() ? "workout" : "history");
                                    });
                        });
        discard.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void workoutTimers(JSONObject session) {
        LinearLayout timer = card(content);
        LinearLayout times = new LinearLayout(activity);
        times.setOrientation(LinearLayout.HORIZONTAL);
        timer.addView(times, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout elapsedBox = new LinearLayout(activity);
        elapsedBox.setOrientation(LinearLayout.VERTICAL);
        elapsedBox.setGravity(Gravity.CENTER);
        times.addView(elapsedBox, new LinearLayout.LayoutParams(0, -2, 1));
        text(elapsedBox, s("workout_elapsed_label"), 12).setPadding(0, 0, 0, 0);
        activity.elapsedLabel = text(elapsedBox, s("workout_elapsed", "0:00"), 18);
        LinearLayout restBox = new LinearLayout(activity);
        restBox.setOrientation(LinearLayout.VERTICAL);
        restBox.setGravity(Gravity.CENTER);
        times.addView(restBox, new LinearLayout.LayoutParams(0, -2, 1));
        text(restBox, s("rest_timer"), 12).setPadding(0, 0, 0, 0);
        activity.timerLabel = text(restBox, "", 18);
        LinearLayout idleActions = new LinearLayout(activity);
        timer.addView(idleActions, new LinearLayout.LayoutParams(-1, -2));
        Button start =
                primaryButton(
                        idleActions,
                        s("rest_start", restSeconds()),
                        () -> setRestTimer(session, restSeconds()));
        start.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        Button adjust = button(idleActions, s("rest_adjust"), () -> chooseRestDuration(start));
        LinearLayout.LayoutParams adjustParams = new LinearLayout.LayoutParams(-2, -2);
        adjustParams.setMarginStart(activity.dp(8));
        adjust.setLayoutParams(adjustParams);
        Button stop = deleteButton(timer, s("stop_rest"), true, () -> setRestTimer(session, 0));
        activity.updateRestControls =
                resting -> {
                    idleActions.setVisibility(resting ? View.GONE : View.VISIBLE);
                    stop.setVisibility(resting ? View.VISIBLE : View.GONE);
                };
        activity.updateWorkoutTimers(session);
    }

    private int restSeconds() {
        return activity.getSharedPreferences("preferences", Context.MODE_PRIVATE)
                .getInt(REST_SECONDS_PREFERENCE, DEFAULT_REST_SECONDS);
    }

    private void chooseRestDuration(Button start) {
        int seconds = restSeconds();
        String[] choices = {
            s("rest_option_seconds", DEFAULT_REST_SECONDS),
            s("rest_option_seconds", LONG_REST_SECONDS),
            s("rest_custom")
        };
        int selected = seconds == DEFAULT_REST_SECONDS ? 0 : seconds == LONG_REST_SECONDS ? 1 : 2;
        activity.activeDialog =
                new AlertDialog.Builder(activity)
                        .setTitle(s("rest_custom_seconds"))
                        .setSingleChoiceItems(
                                choices,
                                selected,
                                (dialog, which) -> {
                                    dialog.dismiss();
                                    if (which == 2) {
                                        LinearLayout form = column();
                                        EditText input =
                                                input(
                                                        form,
                                                        s("rest_custom_seconds"),
                                                        String.valueOf(seconds),
                                                        true);
                                        input.setInputType(InputType.TYPE_CLASS_NUMBER);
                                        dialog(
                                                s("rest_custom"),
                                                form,
                                                () ->
                                                        saveRestDuration(
                                                                start,
                                                                integer(
                                                                        input,
                                                                        1,
                                                                        MAX_REST_SECONDS)));
                                    } else {
                                        saveRestDuration(
                                                start,
                                                which == 0
                                                        ? DEFAULT_REST_SECONDS
                                                        : LONG_REST_SECONDS);
                                    }
                                })
                        .setNegativeButton(s("cancel"), null)
                        .show();
    }

    private void saveRestDuration(Button start, int seconds) {
        activity.getSharedPreferences("preferences", Context.MODE_PRIVATE)
                .edit()
                .putInt(REST_SECONDS_PREFERENCE, seconds)
                .apply();
        start.setText(s("rest_start", seconds));
    }

    private void setRestTimer(JSONObject session, int seconds) {
        long restUntil = seconds == 0 ? 0 : System.currentTimeMillis() + seconds * 1000L;
        JSONObject updated = copy(session);
        put(updated, "restUntil", restUntil);
        try {
            store.saveSessionEdit("active", session, updated, false);
            put(session, "restUntil", restUntil);
            activity.updateWorkoutTimers(session);
        } catch (Store.StaleWorkout e) {
            refreshAtCurrentPosition("workout");
            toast(s("feedback_stale"));
        } catch (SQLiteException e) {
            toast(s("feedback_save_error"));
        }
    }

    void finishWorkout(JSONObject session) {
        LinearLayout form = column();
        text(form, s("finish_confirm"), 16);
        int pending = WorkoutAdjustments.pending(session);
        String[] labels = new String[TrainingFeedback.REASONS.length];
        for (int i = 0; i < labels.length; i++)
            labels[i] = s("cause_" + TrainingFeedback.REASONS[i]);
        Spinner reason =
                pending > 0 ? select(form, s("end_reason_label", pending), labels, 0) : null;
        dialog(
                s("finish"),
                form,
                s("confirm"),
                () -> {
                    try {
                        JSONObject finished =
                                TrainingFeedback.finish(
                                        session,
                                        reason == null
                                                ? "completed"
                                                : TrainingFeedback.REASONS[
                                                        reason.getSelectedItemPosition()],
                                        System.currentTimeMillis());
                        store.saveSessionEdit("active", session, finished, true);
                        show("history");
                    } catch (TrainingFeedback.Invalid e) {
                        throw new IllegalArgumentException(s(e.key));
                    } catch (Store.StaleWorkout e) {
                        activity.activeDialog.dismiss();
                        toast(s("feedback_stale"));
                        show("workout");
                    } catch (android.database.sqlite.SQLiteException e) {
                        throw new IllegalArgumentException(s("feedback_save_error"));
                    }
                });
    }

    String equipmentText(String exercise) {
        if (Catalog.builtIn(exercise) < 0)
            return customEquipmentText(activity.find("exercise", exercise));
        if (!Catalog.equipmentKnown(exercise)) return s("equipment_unspecified");
        StringJoiner labels = new StringJoiner(" / ");
        for (String item : Catalog.equipment(exercise)) labels.add(s("eq_" + item));
        return labels.length() == 0 ? s("equipment_none") : labels.toString();
    }

    String customEquipmentText(JSONObject exercise) {
        JSONArray codes = exercise == null ? null : exercise.optJSONArray("equipment");
        if (codes == null) return s("equipment_unspecified");
        StringJoiner labels = new StringJoiner(" / ");
        for (String code : Catalog.EQUIPMENT)
            for (int i = 0; i < codes.length(); i++)
                if (code.equals(codes.optString(i))) {
                    labels.add(s("eq_" + code));
                    break;
                }
        String other = exercise.optString("otherEquipment").trim();
        if (!other.isEmpty()) labels.add(other);
        return labels.length() == 0 ? s("equipment_none") : labels.toString();
    }

    void shortenRemaining(JSONObject session) {
        LinearLayout form = column();
        text(form, s("remaining_estimate_note"), 14);
        EditText minutes =
                input(
                        form,
                        s("remaining_minutes"),
                        String.valueOf(
                                Math.min(
                                        180,
                                        (WorkoutAdjustments.estimateSeconds(
                                                                session, System.currentTimeMillis())
                                                        + 59)
                                                / 60)),
                        true);
        dialog(
                s("shorten_remaining"),
                form,
                s("preview_adjustment"),
                () -> {
                    int value = integer(minutes, 0, 180);
                    try {
                        WorkoutAdjustments.Preview preview =
                                WorkoutAdjustments.shorten(
                                        session, value, System.currentTimeMillis());
                        if (preview.changed == 0) toast(s("adjust_already_fits"));
                        else showAdjustmentPreview(preview);
                    } catch (WorkoutAdjustments.Invalid e) {
                        throw new IllegalArgumentException(s(e.key));
                    }
                });
    }

    private void chooseExerciseAdjustment(JSONObject session, int rowIndex) {
        LinearLayout options = column();
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(options);
        AlertDialog dialog =
                new AlertDialog.Builder(activity)
                        .setTitle(s("adjust_exercise"))
                        .setView(scroll)
                        .setNegativeButton(s("cancel"), null)
                        .create();
        adjustmentButton(
                options, dialog, "edit_pending_sets", () -> editPendingSets(session, rowIndex));
        adjustmentButton(
                options, dialog, "replace_remaining", () -> replaceRemaining(session, rowIndex));
        adjustmentButton(
                options, dialog, "skip_remaining_sets", () -> skipPendingSets(session, rowIndex));
        activity.activeDialog = dialog;
        dialog.show();
    }

    private void adjustmentButton(
            LinearLayout options, AlertDialog dialog, String key, Runnable action) {
        Button choice =
                button(
                        options,
                        s(key),
                        () -> {
                            dialog.dismiss();
                            action.run();
                        });
        choice.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        choice.setTextColor(MainActivity.FG);
        choice.setTextSize(16);
        choice.setMinHeight(activity.dp(54));
        choice.setPadding(activity.dp(14), activity.dp(8), activity.dp(14), activity.dp(8));
        choice.setCompoundDrawablesRelativeWithIntrinsicBounds(
                0, 0, R.drawable.ic_action_chevron, 0);
        choice.setCompoundDrawablePadding(activity.dp(10));
        GradientDrawable surface = UiKit.box(activity, UiKit.SURFACE, UiKit.BORDER_STRONG, 16);
        choice.setBackgroundTintList(null);
        choice.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33C6F432), surface, null));
        LinearLayout.LayoutParams layout = (LinearLayout.LayoutParams) choice.getLayoutParams();
        layout.bottomMargin = activity.dp(8);
    }

    private void skipPendingSets(JSONObject session, int rowIndex) {
        JSONObject row = session.optJSONArray("rows").optJSONObject(rowIndex);
        LinearLayout form = column();
        text(
                form,
                s(
                        "skip_sets_confirmation",
                        activity.exerciseName(row),
                        WorkoutAdjustments.pendingRow(row)),
                16);
        String[] labels = new String[TrainingFeedback.REASONS.length];
        for (int i = 0; i < labels.length; i++)
            labels[i] = s("cause_" + TrainingFeedback.REASONS[i]);
        Spinner reason = select(form, s("skip_reason_optional"), labels, 0);
        dialog(
                s("skip_remaining_sets"),
                form,
                s("confirm_skip_sets"),
                () -> {
                    WorkoutAdjustments.Preview preview;
                    try {
                        preview =
                                WorkoutAdjustments.skipPending(
                                        session,
                                        rowIndex,
                                        TrainingFeedback.REASONS[reason.getSelectedItemPosition()],
                                        System.currentTimeMillis());
                    } catch (WorkoutAdjustments.Invalid e) {
                        throw new IllegalArgumentException(s(e.key));
                    }
                    applyWorkoutAdjustment(preview);
                });
    }

    private void showSkipReason(LinearLayout parent, String reason) {
        if (!"unknown".equals(reason))
            text(parent, s("skip_reason_value", s("cause_" + reason)), 14);
    }

    private void editPendingSets(JSONObject session, int rowIndex) {
        JSONObject row = session.optJSONArray("rows").optJSONObject(rowIndex);
        int first = WorkoutAdjustments.firstPendingSet(row);
        if (first < 0) {
            toast(s("adjust_no_pending"));
            return;
        }
        JSONObject next = row.optJSONArray("logs").optJSONObject(first);
        LinearLayout form = column();
        EditText weight =
                input(
                        form,
                        s("weight_label"),
                        next.optString("weight", row.optString("weight")),
                        true);
        EditText reps =
                input(
                        form,
                        s("reps_label"),
                        String.valueOf(TrainingFeedback.targetReps(row, next)),
                        true);
        EditText sets =
                input(
                        form,
                        s("pending_sets_label"),
                        String.valueOf(WorkoutAdjustments.pendingRow(row)),
                        true);
        reps.setInputType(InputType.TYPE_CLASS_NUMBER);
        sets.setInputType(InputType.TYPE_CLASS_NUMBER);
        dialog(
                s("edit_pending_sets"),
                form,
                s("preview_adjustment"),
                () -> {
                    double selectedWeight = number(weight, 0, 1000);
                    int selectedReps = integer(reps, 1, 100);
                    int selectedSets = integer(sets, 1, WorkoutAdjustments.maxPendingSets(row));
                    try {
                        WorkoutAdjustments.Preview preview =
                                WorkoutAdjustments.editPending(
                                        session,
                                        rowIndex,
                                        selectedWeight,
                                        selectedReps,
                                        selectedSets,
                                        System.currentTimeMillis());
                        if (preview.changed == 0) toast(s("pending_sets_unchanged"));
                        else showAdjustmentPreview(preview);
                    } catch (WorkoutAdjustments.Invalid e) {
                        throw new IllegalArgumentException(s(e.key));
                    }
                });
    }

    void replaceRemaining(JSONObject session, int rowIndex) {
        JSONObject row = session.optJSONArray("rows").optJSONObject(rowIndex);
        JSONObject exercise = activity.find("exercise", row.optString("exerciseId"));
        String[] required = WorkoutAdjustments.equipmentFor(exercise == null ? row : exercise);
        if (required != null && required.length == 0) {
            toast(s("replace_no_equipment"));
            return;
        }
        String[] equipment = required == null ? Catalog.EQUIPMENT : required;
        String[] labels = new String[equipment.length + 1];
        labels[0] = s("equipment_select");
        for (int i = 0; i < equipment.length; i++) {
            String code = equipment[i];
            labels[i + 1] =
                    code.startsWith(WorkoutAdjustments.CUSTOM_EQUIPMENT_PREFIX)
                            ? code.substring(WorkoutAdjustments.CUSTOM_EQUIPMENT_PREFIX.length())
                            : s("eq_" + code);
        }
        LinearLayout form = column();
        text(form, activity.exerciseName(row), 20);
        text(form, s("replacement_notice"), 14);
        Spinner unavailable =
                select(form, s("unavailable_equipment"), labels, required == null ? 0 : 1);
        dialog(
                s("replace_remaining"),
                form,
                s("find_replacements"),
                () -> {
                    if (unavailable.getSelectedItemPosition() == 0)
                        throw new IllegalArgumentException(s("replace_equipment_required"));
                    String blocked = equipment[unavailable.getSelectedItemPosition() - 1];
                    try {
                        List<JSONObject> options =
                                WorkoutAdjustments.alternatives(
                                        session, rowIndex, blocked, store.list("exercise"));
                        if (options.isEmpty())
                            throw new IllegalArgumentException(s("replace_no_candidates"));
                        String[] names = new String[options.size()];
                        for (int i = 0; i < names.length; i++)
                            names[i] =
                                    activity.exerciseName(options.get(i))
                                            + "\n"
                                            + equipmentText(options.get(i).optString("id"));
                        activity.activeDialog =
                                new AlertDialog.Builder(activity)
                                        .setTitle(s("choose_replacement"))
                                        .setItems(
                                                names,
                                                (d, index) ->
                                                        replacementSettings(
                                                                session,
                                                                rowIndex,
                                                                options.get(index),
                                                                blocked))
                                        .setNegativeButton(s("cancel"), null)
                                        .show();
                    } catch (WorkoutAdjustments.Invalid e) {
                        throw new IllegalArgumentException(s(e.key));
                    }
                });
    }

    void replacementSettings(
            JSONObject session, int rowIndex, JSONObject target, String unavailable) {
        JSONObject source = session.optJSONArray("rows").optJSONObject(rowIndex);
        LinearLayout form = column();
        text(form, activity.exerciseName(source) + " → " + activity.exerciseName(target), 20);
        text(form, equipmentText(target.optString("id")), 14);
        text(form, s("replacement_weight_notice"), 14);
        text(form, s("replacement_set_count", WorkoutAdjustments.pendingRow(source)), 16);
        EditText weight = input(form, s("actual_weight"), "", true),
                reps = input(form, s("reps_label"), source.optString("reps", "10"), true);
        dialog(
                s("replacement_settings"),
                form,
                s("preview_adjustment"),
                () -> {
                    double value = number(weight, 0, 1000);
                    int repetitions = integer(reps, 1, 100);
                    try {
                        showAdjustmentPreview(
                                WorkoutAdjustments.replace(
                                        session,
                                        rowIndex,
                                        target,
                                        unavailable,
                                        value,
                                        repetitions,
                                        System.currentTimeMillis()));
                    } catch (WorkoutAdjustments.Invalid e) {
                        throw new IllegalArgumentException(s(e.key));
                    }
                });
    }

    private void describeChanges(LinearLayout parent, String type, JSONArray changes) {
        for (int i = 0; i < changes.length(); i++) {
            JSONObject change = changes.optJSONObject(i);
            if (type.equals("time"))
                text(
                        parent,
                        s(
                                "time_change_line",
                                activity.exerciseName(change),
                                change.optInt("before"),
                                change.optInt("after")),
                        16);
            else if (type.equals("sets")) {
                text(
                        parent,
                        s(
                                "set_adjustment_line",
                                activity.exerciseName(change),
                                change.optInt("before"),
                                change.optInt("after"),
                                num(change.optDouble("weight")),
                                change.optInt("reps")),
                        16);
            } else if (type.equals("skip")) {
                text(
                        parent,
                        s("skip_sets_change", activity.exerciseName(change), change.optInt("sets")),
                        16);
                showSkipReason(parent, change.optString("reason", "unknown"));
            } else {
                JSONObject target =
                        object(
                                "exerciseId",
                                change.optString("targetId"),
                                "name",
                                change.optString("targetName"));
                text(
                        parent,
                        s(
                                "replacement_change_line",
                                activity.exerciseName(change),
                                activity.exerciseName(target),
                                change.optInt("sets"),
                                num(change.optDouble("weight")),
                                change.optInt("reps")),
                        16);
            }
        }
    }

    void showAdjustmentPreview(WorkoutAdjustments.Preview preview) {
        LinearLayout form = column();
        text(form, s("completed_protected", WorkoutAdjustments.completed(preview.after)), 18);
        if (preview.type.equals("time")) {
            text(
                    form,
                    s(
                            "time_preview_summary",
                            preview.afterPending,
                            preview.changed,
                            (preview.estimatedSeconds + 59) / 60),
                    16);
            if (preview.afterPending == 0) text(form, s("adjust_nothing_left"), 14);
        } else if (preview.type.equals("equipment")) text(form, s("replacement_weight_notice"), 14);
        describeChanges(form, preview.type, preview.changes);
        dialog(
                s("adjustment_preview"),
                form,
                s("apply_adjustment"),
                () -> applyWorkoutAdjustment(preview));
    }

    private void applyWorkoutAdjustment(WorkoutAdjustments.Preview preview) {
        try {
            store.applyAdjustment(preview);
            if (preview.type.equals("equipment"))
                activity.workoutExerciseIndex = preview.after.optInt("displayRow");
            refreshAtCurrentPosition("workout");
        } catch (Store.StaleWorkout e) {
            toast(s("adjust_stale"));
            show("workout");
        } catch (WorkoutAdjustments.Invalid e) {
            throw new IllegalArgumentException(s(e.key));
        } catch (SQLiteException e) {
            throw new IllegalArgumentException(s("adjust_save_error"));
        }
    }

    void adjustmentHistory(JSONObject session, LinearLayout parent) {
        JSONArray changes = session.optJSONArray("adjustments");
        if (changes == null || changes.length() == 0) return;
        LinearLayout card = card(parent);
        text(card, s("adjustment_history"), 18);
        button(
                card,
                s("adjustment_history_count", changes.length()),
                () -> {
                    LinearLayout all = column();
                    for (int i = 0; i < changes.length(); i++) {
                        JSONObject event = changes.optJSONObject(i);
                        String type = event.optString("type");
                        text(
                                all,
                                type.equals("time")
                                        ? s(
                                                "time_audit",
                                                date(event.optLong("at")),
                                                event.optInt("minutes"),
                                                event.optInt("removed"))
                                        : s(
                                                type.equals("sets")
                                                        ? "set_adjustment_audit"
                                                        : type.equals("skip")
                                                                ? "skip_sets_audit"
                                                                : "equipment_audit",
                                                date(event.optLong("at"))),
                                16);
                        describeChanges(all, type, event.optJSONArray("changes"));
                    }
                    ScrollView scroll = new ScrollView(activity);
                    scroll.addView(all);
                    activity.activeDialog =
                            new AlertDialog.Builder(activity)
                                    .setTitle(s("adjustment_history"))
                                    .setView(scroll)
                                    .setPositiveButton(s("back_label"), null)
                                    .show();
                });
    }
}
