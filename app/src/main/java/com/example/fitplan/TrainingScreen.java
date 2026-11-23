package com.example.fitplan;

import static com.example.fitplan.Store.copy;
import static com.example.fitplan.Store.id;
import static com.example.fitplan.Store.object;
import static com.example.fitplan.Store.put;

import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
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
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        Button back = button(actions, s("back_label"), () -> show("plans"));
        back.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        Button renameBtn =
                button(
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
        renameBtn.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        Button addBtn =
                button(
                        actions,
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
        addBtn.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
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
        int cardWidth =
                Math.round(activity.getResources().getDisplayMetrics().widthPixels * 0.90f);
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
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(MainActivity.CARD);
            cardBg.setCornerRadius(activity.dp(28));
            cardBg.setStroke(activity.dp(1), 0xFF2E343D);
            c.setBackground(cardBg);
            c.setElevation(activity.dp(2));
            LinearLayout.LayoutParams cardLayout = new LinearLayout.LayoutParams(cardWidth, -1);
            cardLayout.setMarginEnd(cardGap);
            track.addView(c, cardLayout);
            int imageRes = exerciseImage(row.optString("exerciseId"));
            if (imageRes != 0) {
                ImageView hero = new ImageView(activity);
                hero.setScaleType(ImageView.ScaleType.FIT_CENTER);
                hero.setImageResource(imageRes);
                hero.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                c.addView(hero, new LinearLayout.LayoutParams(-1, 0, 1));
            }
            LinearLayout header = pane(c);
            text(header, activity.exerciseName(row), 18);
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
            TextView remove = actionButton(rowActions, s("remove_short"));
            remove.setOnClickListener(
                    v -> {
                        rows.remove(index);
                        store.save("plan", plan);
                        activity.openPlan(plan);
                    });
            TextView next = actionButton(rowActions, s("move_right"));
            next.setEnabled(index < rows.length() - 1);
            next.setAlpha(index < rows.length() - 1 ? 1f : 0.35f);
            next.setOnClickListener(v -> moveRow(rows, plan, index, index + 1));
            header.setOnLongClickListener(
                    v -> {
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
            header.setOnTouchListener(
                    (v, event) -> {
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
                                            if (dragFrom[0] < target
                                                    && k > dragFrom[0]
                                                    && k <= target) shift = -step;
                                            else if (dragFrom[0] > target
                                                    && k >= target
                                                    && k < dragFrom[0]) shift = step;
                                            other.animate()
                                                    .translationX(shift)
                                                    .setDuration(180)
                                                    .start();
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
        button(content, s("start_plan"), () -> start(plan));
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
        number.setOnClickListener(
                v -> {
                    LinearLayout form = column();
                    EditText field = input(form, label, number.getText().toString(), true);
                    field.selectAll();
                    dialog(
                            label,
                            form,
                            () -> {
                                double value =
                                        weight ? number(field, min, max) : integer(field, min, max);
                                updatePrescription(plan, row, key, value);
                                sync.run();
                                refresh.run();
                            });
                });
        minus.setOnClickListener(
                v -> {
                    updatePrescription(
                            plan, row, key, Math.max(min, row.optDouble(key) - step));
                    sync.run();
                    refresh.run();
                });
        plus.setOnClickListener(
                v -> {
                    updatePrescription(
                            plan, row, key, Math.min(max, row.optDouble(key) + step));
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
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1C2128);
        bg.setCornerRadius(activity.dp(20));
        bg.setStroke(activity.dp(1), 0xFF2E343D);
        b.setBackground(bg);
        parent.addView(b, new LinearLayout.LayoutParams(activity.dp(40), activity.dp(40)));
        return b;
    }

    private TextView stepperNumber(LinearLayout parent) {
        TextView b = new TextView(activity);
        b.setTextSize(17);
        b.setTextColor(MainActivity.ACCENT);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1C2128);
        bg.setCornerRadius(activity.dp(20));
        bg.setStroke(activity.dp(1), 0xFF2E343D);
        b.setBackground(bg);
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
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1C2128);
        bg.setCornerRadius(activity.dp(18));
        bg.setStroke(activity.dp(1), 0xFF2E343D);
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, activity.dp(36), 1);
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

    private void workoutCards(JSONObject session, LinearLayout parent) {
        JSONArray rows = session.optJSONArray("rows");
        HorizontalScrollView strip = new HorizontalScrollView(activity);
        strip.setHorizontalScrollBarEnabled(false);
        strip.setClipChildren(false);
        strip.setClipToPadding(false);
        parent.addView(strip, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout track = new LinearLayout(activity);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setClipChildren(false);
        track.setClipToPadding(false);
        track.setPadding(0, activity.dp(4), 0, activity.dp(8));
        strip.addView(track, new LinearLayout.LayoutParams(-2, -1));
        int cardWidth =
                Math.round(activity.getResources().getDisplayMetrics().widthPixels * 0.86f);
        int visible = 0;
        for (int i = 0; i < rows.length(); i++) {
            final int rowIndex = i;
            JSONObject row = rows.optJSONObject(i);
            int remaining = WorkoutAdjustments.pendingRow(row);
            if (remaining == 0) continue;
            visible++;
            LinearLayout c = activity.column();
            c.setBackground(activity.shape(MainActivity.CARD, 24));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardWidth, -1);
            lp.setMarginEnd(activity.dp(12));
            track.addView(c, lp);
            LinearLayout header = pane(c);
            text(
                    illustratedHeader(header, row.optString("exerciseId")),
                    activity.exerciseName(row),
                    18);
            text(
                    c,
                    s(
                            "prescription",
                            num(row.optDouble("weight")),
                            row.optInt("sets"),
                            row.optInt("reps")),
                    14);
            button(c, s("complete_set", remaining), () -> completeNextSet(session, rowIndex));
        }
        if (visible == 0) text(parent, s("all_sets_done"), 16);
    }

    private void completeNextSet(JSONObject session, int rowIndex) {
        JSONObject row = session.optJSONArray("rows").optJSONObject(rowIndex);
        JSONArray logs = row.optJSONArray("logs");
        int logIndex = -1;
        for (int j = 0; j < logs.length(); j++) {
            if (WorkoutAdjustments.isPending(logs.optJSONObject(j))) {
                logIndex = j;
                break;
            }
        }
        if (logIndex < 0) {
            refreshAtCurrentPosition("workout");
            return;
        }
        try {
            JSONObject updated =
                    TrainingFeedback.record(
                            session,
                            rowIndex,
                            logIndex,
                            row.optDouble("weight"),
                            row.optInt("reps"),
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
        title(s("workout_title"));
        JSONObject session = store.first("active");
        if (session == null) {
            text(content, s("no_active"), 16);
            button(content, s("choose_plan"), () -> show("plans"));
            return;
        }
        LinearLayout cardsArea = new LinearLayout(activity);
        cardsArea.setOrientation(LinearLayout.VERTICAL);
        content.addView(cardsArea, new LinearLayout.LayoutParams(-1, 0, 1));
        workoutCards(session, cardsArea);
        LinearLayout timer = card(content);
        LinearLayout times = new LinearLayout(activity);
        times.setOrientation(LinearLayout.HORIZONTAL);
        timer.addView(times, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout elapsedBox = new LinearLayout(activity);
        elapsedBox.setOrientation(LinearLayout.VERTICAL);
        elapsedBox.setGravity(Gravity.CENTER);
        times.addView(elapsedBox, new LinearLayout.LayoutParams(0, -2, 1));
        text(elapsedBox, s("workout_elapsed_label"), 13);
        activity.elapsedLabel = text(elapsedBox, s("workout_elapsed", "0:00"), 26);
        LinearLayout restBox = new LinearLayout(activity);
        restBox.setOrientation(LinearLayout.VERTICAL);
        restBox.setGravity(Gravity.CENTER);
        times.addView(restBox, new LinearLayout.LayoutParams(0, -2, 1));
        text(restBox, s("rest_timer"), 13);
        activity.timerLabel = text(restBox, s("rest_done"), 26);
        LinearLayout restButtons = new LinearLayout(activity);
        restButtons.setOrientation(LinearLayout.HORIZONTAL);
        timer.addView(restButtons, new LinearLayout.LayoutParams(-1, -2));
        Button b60 =
                button(
                        restButtons,
                        s("rest60"),
                        () -> {
                            put(session, "restUntil", System.currentTimeMillis() + 60000);
                            store.save("active", session);
                        });
        b60.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        Button b90 =
                button(
                        restButtons,
                        s("rest90"),
                        () -> {
                            put(session, "restUntil", System.currentTimeMillis() + 90000);
                            store.save("active", session);
                        });
        b90.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        Button bStop =
                button(
                        restButtons,
                        s("stop_rest"),
                        () -> {
                            put(session, "restUntil", 0);
                            store.save("active", session);
                        });
        bStop.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout bottom = new LinearLayout(activity);
        bottom.setOrientation(LinearLayout.VERTICAL);
        content.addView(bottom, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout adjustment = card(bottom);
        TextView summary = text(adjustment, "", 16);
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
        adjustmentHistory(session, bottom);
        LinearLayout endButtons = new LinearLayout(activity);
        endButtons.setOrientation(LinearLayout.HORIZONTAL);
        bottom.addView(endButtons, new LinearLayout.LayoutParams(-1, -2));
        Button finish =
                button(
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
        finish.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        Button discard =
                button(
                        endButtons,
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
