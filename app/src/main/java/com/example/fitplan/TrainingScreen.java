package com.example.fitplan;

import static com.example.fitplan.Store.copy;
import static com.example.fitplan.Store.id;
import static com.example.fitplan.Store.object;
import static com.example.fitplan.Store.put;

import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

final class TrainingScreen extends Screen {
    private static final int[] EXERCISE_IMAGES = {
        R.drawable.builtin_0,
        R.drawable.builtin_1,
        R.drawable.builtin_2,
        R.drawable.builtin_3,
        R.drawable.builtin_4,
        R.drawable.builtin_5,
        R.drawable.builtin_6,
        R.drawable.builtin_7,
        R.drawable.builtin_8,
        R.drawable.builtin_9,
        R.drawable.builtin_10,
        R.drawable.builtin_11,
        R.drawable.builtin_12,
        R.drawable.builtin_13,
        R.drawable.builtin_14,
        R.drawable.builtin_15,
        R.drawable.builtin_16,
        R.drawable.builtin_17,
        R.drawable.builtin_18,
        R.drawable.builtin_19,
        R.drawable.builtin_20,
        R.drawable.builtin_21,
        R.drawable.builtin_22,
        R.drawable.builtin_23,
        R.drawable.builtin_24,
        R.drawable.builtin_25,
        R.drawable.builtin_26,
        R.drawable.builtin_27,
        R.drawable.builtin_28,
        R.drawable.builtin_29
    };

    TrainingScreen(MainActivity activity) {
        super(activity);
    }

    void exercises() {
        activity.trainingTabs();
        title(s("library"));
        RadioGroup modes = new RadioGroup(activity);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(modes);
        RadioButton names = mode(modes, s("browse_name"));
        RadioButton muscles = mode(modes, s("browse_muscle"));
        names.setChecked(true);
        LinearLayout namePane = pane(content);
        LinearLayout musclePane = pane(content);
        musclePane.setVisibility(View.GONE);
        nameLibrary(namePane);
        modes.setOnCheckedChangeListener((group, checked) -> {
            boolean byMuscle = checked == muscles.getId();
            if (byMuscle && musclePane.getChildCount() == 0) muscleLibrary(musclePane);
            namePane.setVisibility(byMuscle ? View.GONE : View.VISIBLE);
            musclePane.setVisibility(byMuscle ? View.VISIBLE : View.GONE);
        });
    }

    private RadioButton mode(RadioGroup parent, String label) {
        RadioButton option = new RadioButton(activity);
        option.setId(View.generateViewId());
        option.setText(label);
        option.setMinHeight(activity.dp(48));
        parent.addView(option, new RadioGroup.LayoutParams(0, -2, 1));
        return option;
    }

    private LinearLayout pane(LinearLayout parent) {
        LinearLayout pane = new LinearLayout(activity);
        pane.setOrientation(LinearLayout.VERTICAL);
        parent.addView(pane);
        return pane;
    }

    private void muscleLibrary(LinearLayout pane) {
        MuscleCatalog[] groups = MuscleCatalog.values();
        String[] options = new String[groups.length + 1];
        options[0] = s("map_all");
        for (int i = 0; i < groups.length; i++) options[i + 1] = s("muscle_" + groups[i].key);
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
        JSONObject[] builtIns = new JSONObject[Catalog.EXERCISES.length];
        JSONArray entries = store.list("exercise");
        for (int i = 0; i < entries.length(); i++) {
            JSONObject ex = entries.optJSONObject(i);
            int index = Catalog.builtIn(ex.optString("id"));
            if (index >= 0) builtIns[index] = ex;
        }
        picker.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent, View view, int pos, long id) {
                        results.removeAllViews();
                        clear.setVisibility(pos == 0 ? View.GONE : View.VISIBLE);
                        map.highlight(pos == 0 ? "" : groups[pos - 1].key);
                        if (pos == 0) {
                            for (JSONObject ex : builtIns)
                                if (ex != null) exerciseCard(results, ex);
                            return;
                        }
                        MuscleCatalog group = groups[pos - 1];
                        text(
                                results,
                                group.primary.length == 0
                                        ? s("map_empty")
                                        : s("map_primary", group.primary.length),
                                16);
                        for (int index : group.primary) {
                            if (builtIns[index] == null) continue;
                            if (index == 14 || index == 15)
                                text(results, s(index == 14 ? "map_crunch" : "map_stability"), 14);
                            exerciseCard(results, builtIns[index]);
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
                                                    exerciseCard(extra, builtIns[index]);
                                        }
                                        extra.setVisibility(
                                                extra.getVisibility() == View.VISIBLE
                                                        ? View.GONE
                                                        : View.VISIBLE);
                                    });
                            results.addView(extra);
                        }
                    }

                    public void onNothingSelected(android.widget.AdapterView<?> parent) {}
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
        button(
                pane,
                s("add_custom"),
                () -> {
                    LinearLayout form = column();
                    EditText name = input(form, s("exercise_name"), "", false);
                    Spinner part = select(form, s("part"), activity.partLabels(), 0);
                    dialog(
                            s("custom"),
                            form,
                            () -> {
                                String n = name.getText().toString().trim();
                                if (n.isEmpty())
                                    throw new IllegalArgumentException(s("name_required"));
                                store.save(
                                        "exercise",
                                        object(
                                                "name",
                                                n,
                                                "part",
                                                PARTS[part.getSelectedItemPosition()]));
                                show("library");
                            });
                });
        LinearLayout results = column();
        pane.addView(results);
        Runnable render =
                () -> {
                    results.removeAllViews();
                    JSONArray all = store.list("exercise");
                    int count = 0;
                    for (int i = 0; i < all.length(); i++) {
                        JSONObject ex = all.optJSONObject(i);
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
        render.run();
    }

    private void exerciseCard(LinearLayout results, JSONObject exercise) {
        String id = exercise.optString("id");
        int index = Catalog.builtIn(id);
        LinearLayout item = card(results);
        LinearLayout labels = item;
        if (index >= 0 && index < EXERCISE_IMAGES.length) {
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            labels = new LinearLayout(activity);
            labels.setOrientation(LinearLayout.VERTICAL);
            item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

            ImageView image = new ImageView(activity);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setImageResource(EXERCISE_IMAGES[index]);
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            int size = activity.dp(96);
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(size, size);
            layout.setMarginStart(activity.dp(8));
            item.addView(image, layout);
        }
        text(labels, activity.exerciseName(exercise), 18);
        text(labels, activity.partName(exercise.optString("part")), 14);
        text(labels, equipmentText(id), 14);
    }

    void plans() {
        activity.trainingTabs();
        title(s("plans_title"));
        text(content, s("plans_intro"), 14);
        button(
                content,
                s("new_plan"),
                () -> {
                    LinearLayout form = column();
                    EditText name = input(form, s("plan_name"), s("my_plan"), false);
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
        JSONArray plans = store.list("plan");
        if (plans.length() == 0) text(content, s("empty_plans"), 16);
        for (int i = 0; i < plans.length(); i++) {
            JSONObject plan = plans.optJSONObject(i);
            LinearLayout c = card(content);
            text(c, activity.planName(plan), 20);
            text(c, s("exercise_count", plan.optJSONArray("rows").length()), 14);
            button(c, s("view_edit"), () -> activity.openPlan(plan));
            button(c, s("start_workout"), () -> start(plan));
            button(
                    c,
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
        title(activity.planName(plan));
        if (plan.has("note") || plan.has("noteCode")) text(content, s("plan_note"), 14);
        if (plan.has("checkin")) {
            JSONArray items = plan.optJSONArray("rows");
            int total = 0;
            Set<String> covered = new HashSet<>();
            for (int i = 0; i < items.length(); i++) {
                JSONObject row = items.optJSONObject(i);
                total += row.optInt("sets");
                covered.add(Catalog.partCode(row.optString("part")));
            }
            text(content, s("plan_budget", total, items.length() == 0 ? 0 : 5 + total * 3), 16);
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
            if (plan.optInt("minutes") > 0 && 5 + total * 3 > plan.optInt("minutes"))
                text(content, s("plan_over_budget"), 14);
        }
        button(content, s("back_plans"), () -> show("plans"));
        button(
                content,
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
        button(
                content,
                s("add_exercise"),
                () -> {
                    JSONArray catalogue = store.list("exercise");
                    String[] labels = new String[catalogue.length()];
                    for (int i = 0; i < labels.length; i++)
                        labels[i] = activity.exerciseName(catalogue.optJSONObject(i));
                    new AlertDialog.Builder(activity)
                            .setTitle(s("choose_exercise"))
                            .setItems(
                                    labels,
                                    (d, index) -> {
                                        JSONObject ex = catalogue.optJSONObject(index);
                                        JSONObject row =
                                                object(
                                                        "exerciseId",
                                                        ex.optString("id"),
                                                        "name",
                                                        activity.exerciseName(ex),
                                                        "part",
                                                        ex.optString("part"),
                                                        "weight",
                                                        0,
                                                        "sets",
                                                        3,
                                                        "reps",
                                                        10);
                                        editPrescription(
                                                row,
                                                () -> {
                                                    plan.optJSONArray("rows").put(row);
                                                    store.save("plan", plan);
                                                    activity.openPlan(plan);
                                                });
                                    })
                            .show();
                });
        JSONArray rows = plan.optJSONArray("rows"), history = store.list("history");
        ProgressScreen progress = new ProgressScreen(activity);
        for (int i = 0; i < rows.length(); i++) {
            final int index = i;
            JSONObject row = rows.optJSONObject(i);
            LinearLayout c = card(content);
            text(c, activity.exerciseName(row), 18);
            text(
                    c,
                    s(
                            "prescription",
                            num(row.optDouble("weight")),
                            row.optInt("sets"),
                            row.optInt("reps")),
                    16);
            if (row.has("reason") || row.has("reasonCode") || row.optBoolean("userConfirmed"))
                text(c, activity.reason(row), 14);
            progress.planAdvice(plan, i, c, history);
            if (PlanEdits.needsConfirmation(plan, row)) text(c, s("confirm_weight_hint"), 14);
            button(
                    c,
                    s("adjust"),
                    () ->
                            editPrescription(
                                    row,
                                    () -> {
                                        store.save("plan", plan);
                                        activity.openPlan(plan);
                                    }));
            button(
                    c,
                    s("remove_exercise"),
                    () -> {
                        rows.remove(index);
                        store.save("plan", plan);
                        activity.openPlan(plan);
                    });
        }
        button(content, s("start_plan"), () -> start(plan));
    }

    void editPrescription(JSONObject row, Runnable save) {
        LinearLayout form = column();
        EditText weight = input(form, s("weight_label"), num(row.optDouble("weight")), true);
        EditText sets = input(form, s("sets_label"), row.optString("sets", "3"), true),
                reps = input(form, s("reps_label"), row.optString("reps", "10"), true);
        dialog(
                activity.exerciseName(row),
                form,
                () -> {
                    double prescribedWeight = number(weight, 0, 1000);
                    int targetSets = integer(sets, 1, 20), targetReps = integer(reps, 1, 100);
                    PlanEdits.confirm(
                            row,
                            prescribedWeight,
                            targetSets,
                            targetReps,
                            System.currentTimeMillis());
                    save.run();
                });
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

    void workout() {
        title(s("workout_title"));
        JSONObject session = store.first("active");
        if (session == null) {
            text(content, s("no_active"), 16);
            button(content, s("choose_plan"), () -> show("plans"));
            return;
        }
        text(
                content,
                s("started", activity.planName(session), date(session.optLong("started"))),
                16);
        text(content, s("logging_help"), 14);
        LinearLayout adjustment = card(content);
        text(
                adjustment,
                s(
                        "remaining_summary",
                        performedSets(session),
                        WorkoutAdjustments.pending(session),
                        (WorkoutAdjustments.estimateSeconds(session, System.currentTimeMillis())
                                        + 59)
                                / 60),
                18);
        text(adjustment, s("remaining_estimate_note"), 14);
        if (WorkoutAdjustments.pending(session) > 0)
            button(adjustment, s("shorten_remaining"), () -> shortenRemaining(session));
        else text(adjustment, s("adjust_nothing_left"), 14);
        adjustmentHistory(session);
        activity.timerLabel = text(content, s("rest_timer"), 20);
        button(
                content,
                s("rest60"),
                () -> {
                    put(session, "restUntil", System.currentTimeMillis() + 60000);
                    store.save("active", session);
                });
        button(
                content,
                s("rest90"),
                () -> {
                    put(session, "restUntil", System.currentTimeMillis() + 90000);
                    store.save("active", session);
                });
        button(
                content,
                s("stop_rest"),
                () -> {
                    put(session, "restUntil", 0);
                    store.save("active", session);
                });
        renderLogs(session, true);
        button(
                content,
                s("finish"),
                () -> {
                    if (performedSets(session) == 0 && TrainingFeedback.recorded(session) == 0) {
                        toast(s("one_set"));
                        return;
                    }
                    finishWorkout(session);
                });
        button(
                content,
                s("discard"),
                () ->
                        confirm(
                                s("discard_confirm"),
                                () -> {
                                    store.delete(session.optString("id"));
                                    show("workout");
                                }));
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
        if (!Catalog.equipmentKnown(exercise)) return s("equipment_unspecified");
        StringJoiner labels = new StringJoiner(" / ");
        for (String item : Catalog.equipment(exercise)) labels.add(s("eq_" + item));
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

    void replaceRemaining(JSONObject session, int rowIndex) {
        JSONObject row = session.optJSONArray("rows").optJSONObject(rowIndex);
        String exercise = row.optString("exerciseId");
        if (Catalog.builtIn(exercise) < 0) {
            toast(s("replace_no_candidates"));
            return;
        }
        String[] required = Catalog.equipment(exercise);
        if (Catalog.equipmentKnown(exercise) && required.length == 0) {
            toast(s("replace_no_equipment"));
            return;
        }
        String[] equipment = required.length == 0 ? Catalog.EQUIPMENT : required;
        String[] labels = new String[equipment.length + 1];
        labels[0] = s("equipment_select");
        for (int i = 0; i < equipment.length; i++) labels[i + 1] = s("eq_" + equipment[i]);
        LinearLayout form = column();
        text(form, activity.exerciseName(row), 20);
        text(form, s("replacement_notice"), 14);
        Spinner unavailable =
                select(form, s("unavailable_equipment"), labels, required.length == 0 ? 0 : 1);
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
            else {
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
        } else text(form, s("replacement_weight_notice"), 14);
        describeChanges(form, preview.type, preview.changes);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);
        AlertDialog d =
                new AlertDialog.Builder(activity)
                        .setTitle(s("adjustment_preview"))
                        .setView(scroll)
                        .setNegativeButton(s("cancel"), null)
                        .setPositiveButton(s("apply_adjustment"), null)
                        .create();
        activity.activeDialog = d;
        d.setOnShowListener(v ->
                d.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(w -> {
                            try {
                                store.applyAdjustment(preview);
                                d.dismiss();
                                show("workout");
                            } catch (Store.StaleWorkout e) {
                                d.dismiss();
                                toast(s("adjust_stale"));
                                show("workout");
                            } catch (WorkoutAdjustments.Invalid e) {
                                toast(s(e.key));
                            } catch (android.database.sqlite.SQLiteException e) {
                                toast(s("adjust_save_error"));
                            }
                        }));
        d.show();
    }

    void adjustmentHistory(JSONObject session) {
        JSONArray changes = session.optJSONArray("adjustments");
        if (changes == null || changes.length() == 0) return;
        LinearLayout card = card(content);
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
                                        : s("equipment_audit", date(event.optLong("at"))),
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
