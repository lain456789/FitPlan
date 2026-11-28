package com.example.fitplan;

import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * Apple Fitness / iOS Settings-inspired Profile screen: tri-ring colored bento overview, grouped
 * training profile with iOS hairline dividers, overflow-free AI Coach & Model card (tap avatar to
 * change photo + full-width API config button), and instant Globe/Flag language switcher.
 */
final class ProfileScreen extends Screen {
    ProfileScreen(MainActivity activity) {
        super(activity);
    }

    void render() {
        title(s("profile"));

        CoachModels.CoachSnapshot snapshot = CoachReader.loadSnapshot(store);
        ProfileSummary summary = ProfileSummary.fromSnapshot(snapshot);
        CoachModels.UserProfile profile = snapshot.profile;
        CoachApiConfig.EffectiveConfig apiConfig = CoachApiConfig.resolveCurrent(activity);

        renderOverviewCard(summary);
        renderTrainingProfileCard(profile);
        renderAiCoachSettingsCard(apiConfig);
        renderLanguageCard();
        renderDefinitionsCard();

        TextView ver = text(content, "FitPlan " + s("version"), 12);
        ver.setGravity(Gravity.CENTER);
        ver.setTextColor(MainActivity.MUTED);
    }

    private void renderOverviewCard(ProfileSummary summary) {
        LinearLayout c = card(content);

        // 3-column Apple Tri-Ring colored Bento metrics strip
        LinearLayout metricsRow = new LinearLayout(activity);
        metricsRow.setOrientation(LinearLayout.HORIZONTAL);
        c.addView(metricsRow, new LinearLayout.LayoutParams(-1, -2));

        addMetricTile(
                metricsRow,
                String.valueOf(summary.totalSessions),
                s("profile_stat_sessions"),
                UiKit.RING_MOVE,
                true);
        addMetricTile(
                metricsRow,
                String.valueOf(summary.totalPerformedSets),
                s("profile_stat_sets"),
                UiKit.RING_EXERCISE,
                true);
        addMetricTile(
                metricsRow,
                String.valueOf(summary.totalCheckins),
                s("profile_stat_checkins"),
                UiKit.RING_STAND,
                false);

        UiKit.divider(activity, c);

        // Compact single-line readiness tap row
        LinearLayout readinessRow = new LinearLayout(activity);
        readinessRow.setOrientation(LinearLayout.HORIZONTAL);
        readinessRow.setGravity(Gravity.CENTER_VERTICAL);
        readinessRow.setPadding(0, activity.dp(6), 0, activity.dp(2));
        readinessRow.setOnClickListener(v -> show("dailyPlan"));
        c.addView(readinessRow, new LinearLayout.LayoutParams(-1, -2));

        TextView readinessValue = new TextView(activity);
        if (summary.latestCheckin == null) {
            readinessValue.setText(s("profile_no_checkin"));
            readinessValue.setTextColor(MainActivity.MUTED);
        } else {
            CoachModels.CheckinRecord ck = summary.latestCheckin;
            readinessValue.setText(
                    s(
                            "profile_checkin_summary",
                            ck.day,
                            s(ProfileSummary.sleepLabelKey(ck.sleep)),
                            s(ProfileSummary.foodLabelKey(ck.food)),
                            s(ProfileSummary.sorenessLabelKey(ck.soreness))));
            readinessValue.setTextColor(MainActivity.FG);
        }
        readinessValue.setTextSize(13);
        readinessRow.addView(readinessValue, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView chevron = new ImageView(activity);
        chevron.setImageResource(R.drawable.ic_action_chevron);
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams chevLp =
                new LinearLayout.LayoutParams(activity.dp(16), activity.dp(16));
        chevLp.setMarginStart(activity.dp(6));
        readinessRow.addView(chevron, chevLp);
    }

    private void addMetricTile(
            LinearLayout parent, String value, String caption, int accentColor, boolean addEndGap) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(UiKit.box(activity, UiKit.SURFACE_DEEP, 0, 14));
        tile.setPadding(activity.dp(8), activity.dp(10), activity.dp(8), activity.dp(10));

        TextView valView = new TextView(activity);
        valView.setText(value);
        valView.setTextSize(22);
        valView.setTextColor(accentColor);
        valView.setTypeface(null, Typeface.BOLD);
        valView.setGravity(Gravity.CENTER);
        tile.addView(valView, new LinearLayout.LayoutParams(-2, -2));

        TextView capView = new TextView(activity);
        capView.setText(caption);
        capView.setTextSize(11);
        capView.setTextColor(MainActivity.MUTED);
        capView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(-2, -2);
        capLp.topMargin = activity.dp(2);
        tile.addView(capView, capLp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        if (addEndGap) {
            lp.setMarginEnd(activity.dp(8));
        }
        parent.addView(tile, lp);
    }

    private void renderTrainingProfileCard(CoachModels.UserProfile profile) {
        LinearLayout c = card(content);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        c.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView heading = new TextView(activity);
        heading.setText(s("coach_profile_header"));
        heading.setTextSize(17);
        heading.setTextColor(MainActivity.FG);
        heading.setTypeface(null, Typeface.BOLD);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));

        boolean expanded = activity.profileFormExpanded || profile.isEmpty();
        Button toggleBtn =
                UiKit.compactPill(
                        activity,
                        s(expanded ? "profile_collapse" : "profile_edit"),
                        UiKit.SURFACE,
                        0,
                        MainActivity.ACCENT,
                        0,
                        true);
        toggleBtn.setTextSize(13);
        toggleBtn.setPadding(activity.dp(12), activity.dp(6), activity.dp(12), activity.dp(6));
        header.addView(toggleBtn, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout summaryBox = new LinearLayout(activity);
        summaryBox.setOrientation(LinearLayout.VERTICAL);
        summaryBox.setVisibility(expanded ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(-1, -2);
        sumLp.topMargin = activity.dp(8);
        c.addView(summaryBox, sumLp);
        populateProfileSummaryRows(summaryBox, profile);

        LinearLayout formBox = new LinearLayout(activity);
        formBox.setOrientation(LinearLayout.VERTICAL);
        formBox.setVisibility(expanded ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams formLp = new LinearLayout.LayoutParams(-1, -2);
        formLp.topMargin = activity.dp(8);
        c.addView(formBox, formLp);
        populateProfileForm(formBox, profile);

        toggleBtn.setOnClickListener(v -> {
            boolean next = formBox.getVisibility() != View.VISIBLE;
            activity.profileFormExpanded = next;
            formBox.setVisibility(next ? View.VISIBLE : View.GONE);
            summaryBox.setVisibility(next ? View.GONE : View.VISIBLE);
            toggleBtn.setText(s(next ? "profile_collapse" : "profile_edit"));
        });
    }

    private void populateProfileSummaryRows(LinearLayout parent, CoachModels.UserProfile p) {
        parent.removeAllViews();
        addProfileRow(
                parent, s("coach_profile_goal"), s(ProfileSummary.goalLabelKey(p.goal)), true);
        addProfileRow(
                parent, s("coach_profile_exp"), s(ProfileSummary.expLabelKey(p.experience)), true);
        addProfileRow(
                parent,
                s("coach_profile_weight"),
                p.weightKg.isEmpty() ? s("coach_unset") : p.weightKg + " kg",
                true);
        addProfileRow(
                parent,
                s("coach_profile_days"),
                p.weeklyDays > 0 ? s("coach_days_fmt", p.weeklyDays) : s("coach_unset"),
                true);
        addProfileRow(
                parent,
                s("coach_profile_equip"),
                s(ProfileSummary.equipLabelKey(p.equipment)),
                true);
        addProfileRow(
                parent,
                s("coach_profile_injuries"),
                p.injuries.isEmpty() ? s("coach_unset") : p.injuries,
                false);
    }

    private void addProfileRow(
            LinearLayout parent, String label, String value, boolean withDivider) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, activity.dp(8), 0, activity.dp(8));

        TextView k = new TextView(activity);
        k.setText(label);
        k.setTextSize(14);
        k.setTextColor(MainActivity.FG);
        row.addView(k, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView v = new TextView(activity);
        v.setText(value);
        v.setTextSize(14);
        v.setTextColor(MainActivity.MUTED);
        v.setGravity(Gravity.END);
        LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(0, -2, 1.2f);
        vLp.setMarginStart(activity.dp(8));
        row.addView(v, vLp);

        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        if (withDivider) {
            UiKit.divider(activity, parent);
        }
    }

    private void populateProfileForm(LinearLayout form, CoachModels.UserProfile profile) {
        form.removeAllViews();
        String[] goalLabels = {
            s("coach_unset"),
            s("coach_goal_hypertrophy"),
            s("coach_goal_strength"),
            s("coach_goal_fat_loss"),
            s("coach_goal_health")
        };
        Spinner goalSpinner =
                select(
                        form,
                        s("coach_profile_goal"),
                        goalLabels,
                        ProfileSummary.indexOf(ProfileSummary.GOAL_CODES, profile.goal));

        final int[] expIdx = {ProfileSummary.indexOf(ProfileSummary.EXP_CODES, profile.experience)};
        fieldLabel(form, s("coach_profile_exp"));
        UiKit.segmentedControl(
                activity,
                form,
                new String[] {
                    s("coach_unset"),
                    s("coach_exp_beginner"),
                    s("coach_exp_intermediate"),
                    s("coach_exp_advanced")
                },
                expIdx[0],
                i -> expIdx[0] = i);

        EditText weightEdit =
                styledInput(form, s("coach_profile_weight"), profile.weightKg, "", true);

        String[] daysLabels = new String[8];
        daysLabels[0] = s("coach_unset");
        for (int i = 1; i <= 7; i++) {
            daysLabels[i] = s("coach_days_fmt", i);
        }
        int selectedDays =
                profile.weeklyDays >= 1 && profile.weeklyDays <= 7 ? profile.weeklyDays : 0;
        Spinner daysSpinner = select(form, s("coach_profile_days"), daysLabels, selectedDays);

        String[] equipLabels = {
            s("coach_unset"),
            s("coach_equip_gym"),
            s("coach_equip_dumbbell_band"),
            s("coach_equip_home_bw")
        };
        Spinner equipSpinner =
                select(
                        form,
                        s("coach_profile_equip"),
                        equipLabels,
                        ProfileSummary.indexOf(ProfileSummary.EQUIP_CODES, profile.equipment));

        EditText injuriesEdit =
                styledInput(
                        form,
                        s("coach_profile_injuries"),
                        profile.injuries,
                        s("coach_profile_injuries_hint"),
                        false);

        Button saveBtn =
                primaryButton(
                        form,
                        s("coach_profile_save"),
                        () -> {
                            String weightStr = weightEdit.getText().toString().trim();
                            if (!weightStr.isEmpty()) {
                                try {
                                    double w = Double.parseDouble(weightStr);
                                    if (!Double.isFinite(w) || w < 25 || w > 250) {
                                        toast(s("coach_profile_weight_invalid"));
                                        return;
                                    }
                                } catch (NumberFormatException e) {
                                    toast(s("coach_profile_weight_invalid"));
                                    return;
                                }
                            }
                            CoachModels.UserProfile updated =
                                    new CoachModels.UserProfile(
                                            ProfileSummary.GOAL_CODES[
                                                    goalSpinner.getSelectedItemPosition()],
                                            ProfileSummary.EXP_CODES[expIdx[0]],
                                            weightStr,
                                            daysSpinner.getSelectedItemPosition(),
                                            ProfileSummary.EQUIP_CODES[
                                                    equipSpinner.getSelectedItemPosition()],
                                            injuriesEdit.getText().toString().trim(),
                                            System.currentTimeMillis());
                            CoachReader.saveProfile(store, updated);
                            activity.profileFormExpanded = false;
                            toast(s("coach_profile_saved"));
                            show("profile");
                        });
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1, -2);
        saveLp.topMargin = activity.dp(10);
        saveBtn.setLayoutParams(saveLp);
    }

    private void renderAiCoachSettingsCard(CoachApiConfig.EffectiveConfig apiConfig) {
        LinearLayout c = card(content);

        LinearLayout topRow = new LinearLayout(activity);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        c.addView(topRow, new LinearLayout.LayoutParams(-1, -2));

        // Tappable avatar with subtle bottom-right edit badge (no second cramped button needed!)
        FrameLayout avatarWrap = new FrameLayout(activity);
        avatarWrap.setContentDescription(s("coach_avatar_change"));
        avatarWrap.setOnClickListener(v -> activity.showCoachAvatarOptions());

        CoachAvatarView avatar = new CoachAvatarView(activity, 52, false, MainActivity.CARD);
        avatarWrap.addView(avatar, new FrameLayout.LayoutParams(-2, -2));

        ImageView editBadge = new ImageView(activity);
        editBadge.setImageDrawable(UiKit.icon(activity, R.drawable.ic_edit, 11, MainActivity.FG));
        editBadge.setScaleType(ImageView.ScaleType.CENTER);
        editBadge.setBackground(UiKit.circle(activity, UiKit.SURFACE_HIGHLIGHT, MainActivity.CARD));
        editBadge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams badgeLp =
                new FrameLayout.LayoutParams(activity.dp(20), activity.dp(20));
        badgeLp.gravity = Gravity.BOTTOM | Gravity.END;
        avatarWrap.addView(editBadge, badgeLp);

        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(-2, -2);
        avatarLp.setMarginEnd(activity.dp(14));
        topRow.addView(avatarWrap, avatarLp);

        LinearLayout infoCol = new LinearLayout(activity);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        topRow.addView(infoCol, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView heading = new TextView(activity);
        heading.setText(s("profile_ai_title"));
        heading.setTextSize(17);
        heading.setTextColor(MainActivity.FG);
        heading.setTypeface(null, Typeface.BOLD);
        infoCol.addView(heading);

        String providerName = s(CoachApiConfig.providerLabelKey(apiConfig.provider));
        TextView modelInfo = new TextView(activity);
        modelInfo.setText(
                s(
                        "coach_model_pill",
                        providerName,
                        apiConfig.model.isEmpty() ? "—" : apiConfig.model));
        modelInfo.setTextSize(13);
        modelInfo.setTextColor(MainActivity.ACCENT);
        LinearLayout.LayoutParams miLp = new LinearLayout.LayoutParams(-1, -2);
        miLp.topMargin = activity.dp(2);
        infoCol.addView(modelInfo, miLp);

        String keyStatus =
                !apiConfig.hasKey()
                        ? s("profile_ai_key_missing")
                        : apiConfig.usingBuiltInKey
                                ? s("profile_ai_key_builtin")
                                : s(
                                        "profile_ai_key_custom",
                                        CoachApiConfig.maskKey(apiConfig.apiKey));
        TextView keyView = new TextView(activity);
        keyView.setText(keyStatus);
        keyView.setTextSize(12);
        keyView.setTextColor(MainActivity.MUTED);
        infoCol.addView(keyView);

        // Single clean full-width adaptive-height button: zero text clipping in CN or EN!
        Button configBtn =
                UiKit.compactPill(
                        activity,
                        s("coach_api_open"),
                        UiKit.ADD_BG,
                        UiKit.ADD_BORDER,
                        MainActivity.ACCENT,
                        R.drawable.ic_settings_sliders,
                        true);
        configBtn.setMinHeight(activity.dp(42));
        configBtn.setMinimumHeight(activity.dp(42));
        configBtn.setOnClickListener(v -> new CoachApiDialog(activity).show(() -> show("profile")));
        LinearLayout.LayoutParams cfgLp = new LinearLayout.LayoutParams(-1, -2);
        cfgLp.topMargin = activity.dp(12);
        c.addView(configBtn, cfgLp);
    }

    private void renderLanguageCard() {
        LinearLayout c = card(content);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(-1, -2);
        hLp.bottomMargin = activity.dp(10);
        c.addView(header, hLp);

        ImageView globe = new ImageView(activity);
        globe.setImageResource(R.drawable.ic_globe);
        globe.setColorFilter(MainActivity.ACCENT, PorterDuff.Mode.SRC_IN);
        globe.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams globeLp =
                new LinearLayout.LayoutParams(activity.dp(20), activity.dp(20));
        globeLp.setMarginEnd(activity.dp(8));
        header.addView(globe, globeLp);

        TextView heading = new TextView(activity);
        heading.setText(s("language"));
        heading.setTextSize(17);
        heading.setTextColor(MainActivity.FG);
        heading.setTypeface(null, Typeface.BOLD);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));

        String currentLang = activity.language();
        LinearLayout optionsRow = new LinearLayout(activity);
        optionsRow.setOrientation(LinearLayout.HORIZONTAL);
        c.addView(optionsRow, new LinearLayout.LayoutParams(-1, -2));

        addLanguageOption(
                optionsRow,
                "zh",
                "简体中文",
                R.drawable.ic_flag_cn,
                false,
                "zh".equals(currentLang),
                true);
        addLanguageOption(
                optionsRow,
                "en",
                "English",
                R.drawable.ic_flag_en,
                false,
                "en".equals(currentLang),
                true);
        addLanguageOption(
                optionsRow,
                "system",
                s("follow_system"),
                R.drawable.ic_globe,
                true,
                "system".equals(currentLang),
                false);
    }

    private void addLanguageOption(
            LinearLayout parent,
            String code,
            String label,
            int iconRes,
            boolean tintIcon,
            boolean selected,
            boolean addEndGap) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(
                UiKit.box(
                        activity,
                        selected ? UiKit.ADD_BG : UiKit.SURFACE_DEEP,
                        selected ? MainActivity.ACCENT : 0,
                        16));
        tile.setPadding(activity.dp(8), activity.dp(11), activity.dp(8), activity.dp(11));
        tile.setSelected(selected);
        tile.setContentDescription(label);
        tile.setOnClickListener(v -> {
            if (!code.equals(activity.language())) {
                activity.setLanguage(code);
            }
        });

        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (tintIcon) {
            icon.setColorFilter(
                    selected ? MainActivity.ACCENT : MainActivity.MUTED, PorterDuff.Mode.SRC_IN);
        }
        int iconW = tintIcon ? activity.dp(22) : activity.dp(28);
        int iconH = activity.dp(20);
        tile.addView(icon, new LinearLayout.LayoutParams(iconW, iconH));

        TextView tv = new TextView(activity);
        tv.setText(label);
        tv.setTextSize(12);
        tv.setSingleLine(true);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(selected ? MainActivity.ACCENT : MainActivity.FG);
        tv.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(-2, -2);
        tvLp.topMargin = activity.dp(6);
        tile.addView(tv, tvLp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        if (addEndGap) {
            lp.setMarginEnd(activity.dp(8));
        }
        parent.addView(tile, lp);
    }

    private void renderDefinitionsCard() {
        LinearLayout info = card(content);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        info.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView heading = new TextView(activity);
        heading.setText(s("definitions"));
        heading.setTextSize(16);
        heading.setTextColor(MainActivity.FG);
        heading.setTypeface(null, Typeface.BOLD);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView expandIcon = new ImageView(activity);
        expandIcon.setImageResource(R.drawable.ic_report_expand);
        expandIcon.setRotation(activity.profileDefinitionsExpanded ? 180f : 0f);
        expandIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(expandIcon, new LinearLayout.LayoutParams(activity.dp(20), activity.dp(20)));

        TextView body = new TextView(activity);
        body.setText(s("definitions_text") + "\n\n" + s("privacy"));
        body.setTextSize(13);
        body.setTextColor(MainActivity.MUTED);
        body.setLineSpacing(activity.dp(3), 1.0f);
        body.setVisibility(activity.profileDefinitionsExpanded ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2);
        bodyLp.topMargin = activity.dp(8);
        info.addView(body, bodyLp);

        header.setOnClickListener(v -> {
            activity.profileDefinitionsExpanded = !activity.profileDefinitionsExpanded;
            body.setVisibility(activity.profileDefinitionsExpanded ? View.VISIBLE : View.GONE);
            expandIcon.setRotation(activity.profileDefinitionsExpanded ? 180f : 0f);
        });
    }

    private void fieldLabel(LinearLayout parent, String label) {
        TextView cap = new TextView(activity);
        cap.setText(label);
        cap.setTextSize(13);
        cap.setTextColor(MainActivity.MUTED);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = activity.dp(8);
        lp.bottomMargin = activity.dp(2);
        parent.addView(cap, lp);
    }

    private EditText styledInput(
            LinearLayout parent, String label, String initial, String hint, boolean number) {
        fieldLabel(parent, label);
        EditText edit = new EditText(activity);
        edit.setText(initial);
        edit.setHint(hint);
        edit.setHintTextColor(MainActivity.MUTED);
        edit.setTextColor(MainActivity.FG);
        edit.setTextSize(14);
        edit.setSingleLine(true);
        if (number) {
            edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        }
        edit.setBackground(UiKit.box(activity, UiKit.SURFACE_DEEP, UiKit.BORDER, 12));
        edit.setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10));
        parent.addView(edit, new LinearLayout.LayoutParams(-1, -2));
        return edit;
    }
}
