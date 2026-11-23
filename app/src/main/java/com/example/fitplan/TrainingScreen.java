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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

final class TrainingScreen extends Screen {
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
        LinearLayout labels = illustratedHeader(card(results), id);
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
        Runnable refreshBudget = planBudget(plan);
        refreshBudget.run();
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
                                        plan.optJSONArray("rows").put(row);
                                        store.save("plan", plan);
                                        activity.openPlan(plan);
                                    })
                            .show();
                });
        JSONArray rows = plan.optJSONArray("rows"), history = store.list("history");
        ProgressScreen progress = new ProgressScreen(activity);
        for (int i = 0; i < rows.length(); i++) {
            final int index = i;
            JSONObject row = rows.optJSONObject(i);
            LinearLayout c = card(content);
            LinearLayout header = pane(c);
            text(
                    illustratedHeader(header, row.optString("exerciseId")),
                    activity.exerciseName(row),
                    18);
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
            prescriptionSlider(sliders, plan, row, "weight", "weight_label", 0, 100, 10, refresh);
            prescriptionSlider(sliders, plan, row, "sets", "sets_label", 1, 10, 1, refresh);
            prescriptionSlider(sliders, plan, row, "reps", "reps_label", 1, 20, 5, refresh);
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
            int tickInterval,
            Runnable refresh) {
        String label = s(labelKey);
        text(card, label, 14);
        LinearLayout line = pane(card);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        boolean weight = key.equals("weight");
        int scale = weight ? 2 : 1;
        SeekBar slider = new PrescriptionSeekBar(activity, min, max, tickInterval);
        slider.setMax((max - min) * scale);
        slider.setContentDescription(label);
        Button numberButton = new Button(activity);
        numberButton.setTextColor(MainActivity.ACCENT);
        numberButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff2b3427));
        numberButton.setMinHeight(activity.dp(48));
        line.addView(numberButton);
        line.addView(slider, new LinearLayout.LayoutParams(0, activity.dp(64), 1));
        Runnable sync =
                () -> {
                    double value = row.optDouble(key);
                    String number =
                            java.math.BigDecimal.valueOf(value)
                                    .stripTrailingZeros()
                                    .toPlainString();
                    numberButton.setText(number);
                    numberButton.setContentDescription(label + " " + number);
                    slider.setProgress((int) Math.round((value - min) * scale));
                };
        sync.run();
        numberButton.setOnClickListener(v -> {
            LinearLayout form = column();
            EditText field = input(form, label, numberButton.getText().toString(), true);
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
        slider.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    boolean dragging;

                    private void commit(int progress) {
                        updatePrescription(plan, row, key, min + (double) progress / scale);
                        sync.run();
                        refresh.run();
                    }

                    public void onStartTrackingTouch(SeekBar bar) {
                        dragging = true;
                    }

                    public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                        if (!user) return;
                        numberButton.setText(num(min + (double) progress / scale));
                        if (!dragging) commit(progress);
                    }

                    public void onStopTrackingTouch(SeekBar bar) {
                        dragging = false;
                        commit(bar.getProgress());
                    }
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
        LinearLayout rest = card(content);
        activity.timerLabel = text(rest, s("rest_timer"), 20);
        button(
                rest,
                s("rest60"),
                () -> {
                    put(session, "restUntil", System.currentTimeMillis() + 60000);
                    store.save("active", session);
                });
        button(
                rest,
                s("rest90"),
                () -> {
                    put(session, "restUntil", System.currentTimeMillis() + 90000);
                    store.save("active", session);
                });
        button(
                rest,
                s("stop_rest"),
                () -> {
                    put(session, "restUntil", 0);
                    store.save("active", session);
                });
        text(
                content,
                s("started", activity.planName(session), date(session.optLong("started"))),
                16);
        text(content, s("logging_help"), 14);
        LinearLayout exercise = pane(content);
        JSONArray rows = session.optJSONArray("rows");
        int current = session.optInt("displayRow");
        LinearLayout nav = pane(content);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        for (int direction : new int[] {-1, 1}) {
            int target = current + direction;
            Button arrow =
                    button(
                            nav,
                            s(direction < 0 ? "previous_exercise" : "next_exercise"),
                            () -> {
                                put(session, "displayRow", target);
                                store.save("active", session);
                                refreshAtCurrentPosition("workout");
                            });
            arrow.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            arrow.setEnabled(target >= 0 && target < rows.length());
        }
        LinearLayout adjustment = card(content);
        TextView summary = text(adjustment, "", 18);
        text(adjustment, s("remaining_estimate_note"), 14);
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
        renderLogs(session, true, exercise, refreshSummary);
        adjustmentHistory(session);
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
                                        store.saveSessionEdit("active", session, archived, true);
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
