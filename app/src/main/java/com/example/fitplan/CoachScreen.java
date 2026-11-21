package com.example.fitplan;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * Integrated AI Fitness Coach screen. Reads real local workout records, daily check-ins, exercise
 * catalog, and user profile from FitPlan's SQLite Store; keeps chat turns in memory only and clears
 * them when leaving this tab or closing the app.
 */
final class CoachScreen extends Screen {
    private static final int USER_BUBBLE = Color.rgb(34, 48, 30);
    private static final int ERROR_BUBBLE = Color.rgb(58, 28, 30);
    private static final int OFFLINE_BANNER = Color.rgb(66, 39, 24);
    private static final int INPUT_BG = Color.rgb(22, 25, 31);
    private static final int BORDER = Color.rgb(46, 51, 60);
    private static final int ON_ACCENT = Color.rgb(16, 22, 12);
    private static final String KEY_PRIVACY_ACCEPTED = "coach_privacy_accepted";

    private static final String[] GOAL_CODES = {
        "", "hypertrophy", "strength", "fat_loss", "health"
    };
    private static final String[] EXP_CODES = {"", "beginner", "intermediate", "advanced"};
    private static final String[] EQUIP_CODES = {
        "", "full_gym", "dumbbell_bench_band", "home_bodyweight"
    };

    private LinearLayout offlineBanner;
    private LinearLayout chatContainer;
    private Button sendButton;

    CoachScreen(MainActivity activity) {
        super(activity);
    }

    void render() {
        // 1. Header row: Title + New Chat button
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView heading = new TextView(activity);
        heading.setText(s("coach_title"));
        heading.setTextSize(26);
        heading.setTextColor(MainActivity.FG);
        heading.setTypeface(null, Typeface.BOLD);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));

        Button newChat = compactButton(s("coach_new_chat"), MainActivity.CARD, MainActivity.ACCENT);
        newChat.setOnClickListener(v -> {
            activity.clearCoachSession();
            toast(s("coach_chat_cleared"));
            show("coach");
        });
        header.addView(newChat, new LinearLayout.LayoutParams(-2, activity.dp(36)));

        // 2. Live local data status line
        CoachModels.CoachSnapshot snapshot = CoachReader.loadSnapshot(store);
        String statusLine =
                snapshot.workouts.isEmpty() && snapshot.checkins.isEmpty()
                        ? s("coach_data_empty")
                        : s(
                                "coach_data_status",
                                snapshot.workouts.size(),
                                snapshot.checkins.size(),
                                snapshot.exercises.size());
        TextView dataStatus = text(content, statusLine, 13);
        dataStatus.setTextColor(MainActivity.MUTED);

        // 3. Proactive offline banner (visible immediately when disconnected)
        offlineBanner = new LinearLayout(activity);
        offlineBanner.setOrientation(LinearLayout.VERTICAL);
        offlineBanner.setBackground(rounded(OFFLINE_BANNER, 14, Color.rgb(160, 92, 48)));
        offlineBanner.setPadding(
                activity.dp(14), activity.dp(10), activity.dp(14), activity.dp(10));
        TextView offlineText = new TextView(activity);
        offlineText.setText(s("coach_offline"));
        offlineText.setTextColor(MainActivity.FG);
        offlineText.setTextSize(14);
        offlineText.setTypeface(null, Typeface.BOLD);
        offlineBanner.addView(offlineText);
        LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(-1, -2);
        bannerLp.setMargins(0, activity.dp(4), 0, activity.dp(8));
        content.addView(offlineBanner, bannerLp);
        refreshOfflineBanner();

        // 4. Collapsible Training Profile card
        LinearLayout profileCard = card(content);
        TextView profileTitle = new TextView(activity);
        profileTitle.setText(
                s("coach_profile_header") + (activity.coachProfileExpanded ? "  ▲" : "  ▼"));
        profileTitle.setTextColor(MainActivity.FG);
        profileTitle.setTextSize(16);
        profileTitle.setTypeface(null, Typeface.BOLD);
        profileCard.addView(profileTitle);

        TextView profileSummary = new TextView(activity);
        profileSummary.setText(profileSummaryText(snapshot.profile));
        profileSummary.setTextColor(MainActivity.MUTED);
        profileSummary.setTextSize(13);
        LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(-1, -2);
        sumLp.topMargin = activity.dp(4);
        profileCard.addView(profileSummary, sumLp);

        LinearLayout profileForm = new LinearLayout(activity);
        profileForm.setOrientation(LinearLayout.VERTICAL);
        profileForm.setVisibility(activity.coachProfileExpanded ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams formLp = new LinearLayout.LayoutParams(-1, -2);
        formLp.topMargin = activity.dp(8);
        profileCard.addView(profileForm, formLp);
        populateProfileForm(profileForm, snapshot.profile);

        View.OnClickListener toggleProfile =
                v -> {
                    activity.coachProfileExpanded = !activity.coachProfileExpanded;
                    profileTitle.setText(
                            s("coach_profile_header")
                                    + (activity.coachProfileExpanded ? "  ▲" : "  ▼"));
                    profileForm.setVisibility(
                            activity.coachProfileExpanded ? View.VISIBLE : View.GONE);
                };
        profileTitle.setOnClickListener(toggleProfile);
        profileSummary.setOnClickListener(toggleProfile);

        // 5. Quick prompt chips
        HorizontalScrollView chipScroll = new HorizontalScrollView(activity);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams chipScrollLp = new LinearLayout.LayoutParams(-1, -2);
        chipScrollLp.setMargins(0, activity.dp(4), 0, activity.dp(10));
        content.addView(chipScroll, chipScrollLp);

        LinearLayout chipRow = new LinearLayout(activity);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipScroll.addView(chipRow, new ViewGroup.LayoutParams(-2, -2));

        String[][] quickItems = {
            {"coach_quick_1", "coach_quick_1_prompt"},
            {"coach_quick_2", "coach_quick_2_prompt"},
            {"coach_quick_3", "coach_quick_3_prompt"},
            {"coach_quick_4", "coach_quick_4_prompt"}
        };
        for (String[] item : quickItems) {
            Button chip = compactButton(s(item[0]), MainActivity.CARD, MainActivity.ACCENT);
            chip.setBackground(rounded(MainActivity.CARD, 18, BORDER));
            chip.setPadding(activity.dp(12), activity.dp(6), activity.dp(12), activity.dp(6));
            String prompt = s(item[1]);
            chip.setOnClickListener(v -> requestSend(prompt));
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(-2, activity.dp(36));
            chipLp.setMarginEnd(activity.dp(8));
            chipRow.addView(chip, chipLp);
        }

        // 6. Chat thread container
        chatContainer = new LinearLayout(activity);
        chatContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(chatContainer, new LinearLayout.LayoutParams(-1, -2));
        renderMessages();

        // 7. Pinned bottom composer + persistent privacy footer inside R.id.home_actions
        LinearLayout actions = activity.findViewById(R.id.home_actions);
        actions.removeAllViews();
        actions.setVisibility(View.VISIBLE);

        LinearLayout inputRow = new LinearLayout(activity);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = activity.dp(6);
        actions.addView(inputRow, rowLp);

        EditText inputEdit = new EditText(activity);
        inputEdit.setHint(s("coach_input_hint"));
        inputEdit.setHintTextColor(MainActivity.MUTED);
        inputEdit.setTextColor(MainActivity.FG);
        inputEdit.setTextSize(15);
        inputEdit.setMaxLines(4);
        inputEdit.setBackground(rounded(INPUT_BG, 14, BORDER));
        inputEdit.setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10));
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(0, -2, 1f);
        editLp.setMarginEnd(activity.dp(8));
        inputRow.addView(inputEdit, editLp);

        sendButton = compactButton(s("coach_send"), MainActivity.ACCENT, ON_ACCENT);
        sendButton.setEnabled(!activity.coachSending);
        sendButton.setOnClickListener(v -> {
            String prompt = inputEdit.getText().toString().trim();
            if (!prompt.isEmpty()) {
                inputEdit.setText("");
                requestSend(prompt);
            }
        });
        inputRow.addView(sendButton, new LinearLayout.LayoutParams(-2, activity.dp(44)));

        TextView privacyFooter = new TextView(activity);
        privacyFooter.setText(s("coach_privacy_footer"));
        privacyFooter.setTextColor(MainActivity.MUTED);
        privacyFooter.setTextSize(11);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(-1, -2);
        footerLp.topMargin = activity.dp(4);
        actions.addView(privacyFooter, footerLp);
    }

    private void refreshOfflineBanner() {
        if (offlineBanner != null) {
            boolean online = GlmClient.isNetworkAvailable(activity);
            offlineBanner.setVisibility(online ? View.GONE : View.VISIBLE);
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
                        indexOf(GOAL_CODES, profile.goal));

        String[] expLabels = {
            s("coach_unset"),
            s("coach_exp_beginner"),
            s("coach_exp_intermediate"),
            s("coach_exp_advanced")
        };
        Spinner expSpinner =
                select(
                        form,
                        s("coach_profile_exp"),
                        expLabels,
                        indexOf(EXP_CODES, profile.experience));

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
                        indexOf(EQUIP_CODES, profile.equipment));

        EditText injuriesEdit =
                styledInput(
                        form,
                        s("coach_profile_injuries"),
                        profile.injuries,
                        s("coach_profile_injuries_hint"),
                        false);

        Button saveBtn = compactButton(s("coach_profile_save"), MainActivity.ACCENT, ON_ACCENT);
        saveBtn.setOnClickListener(v -> {
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
                            GOAL_CODES[goalSpinner.getSelectedItemPosition()],
                            EXP_CODES[expSpinner.getSelectedItemPosition()],
                            weightStr,
                            daysSpinner.getSelectedItemPosition(),
                            EQUIP_CODES[equipSpinner.getSelectedItemPosition()],
                            injuriesEdit.getText().toString().trim(),
                            System.currentTimeMillis());
            CoachReader.saveProfile(store, updated);
            activity.coachProfileExpanded = false;
            toast(s("coach_profile_saved"));
            show("coach");
        });
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1, activity.dp(42));
        saveLp.topMargin = activity.dp(10);
        form.addView(saveBtn, saveLp);
    }

    private EditText styledInput(
            LinearLayout parent, String label, String initial, String hint, boolean number) {
        TextView cap = text(parent, label, 13);
        cap.setTextColor(MainActivity.MUTED);
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
        edit.setBackground(rounded(INPUT_BG, 10, BORDER));
        edit.setPadding(activity.dp(10), activity.dp(8), activity.dp(10), activity.dp(8));
        parent.addView(edit, new LinearLayout.LayoutParams(-1, -2));
        return edit;
    }

    private String profileSummaryText(CoachModels.UserProfile p) {
        if (p.isEmpty()) {
            return s("coach_profile_empty");
        }
        List<String> parts = new ArrayList<>();
        if (!p.goal.isEmpty()) {
            parts.add(labelForGoal(p.goal));
        }
        if (!p.experience.isEmpty()) {
            parts.add(labelForExp(p.experience));
        }
        if (!p.weightKg.isEmpty()) {
            parts.add(p.weightKg + " kg");
        }
        if (p.weeklyDays > 0) {
            parts.add(s("coach_days_fmt", p.weeklyDays));
        }
        if (!p.equipment.isEmpty()) {
            parts.add(labelForEquip(p.equipment));
        }
        if (!p.injuries.isEmpty()) {
            parts.add(p.injuries);
        }
        return String.join(" · ", parts);
    }

    private String labelForGoal(String code) {
        if ("hypertrophy".equals(code)) {
            return s("coach_goal_hypertrophy");
        }
        if ("strength".equals(code)) {
            return s("coach_goal_strength");
        }
        if ("fat_loss".equals(code)) {
            return s("coach_goal_fat_loss");
        }
        if ("health".equals(code)) {
            return s("coach_goal_health");
        }
        return code;
    }

    private String labelForExp(String code) {
        if ("beginner".equals(code)) {
            return s("coach_exp_beginner");
        }
        if ("intermediate".equals(code)) {
            return s("coach_exp_intermediate");
        }
        if ("advanced".equals(code)) {
            return s("coach_exp_advanced");
        }
        return code;
    }

    private String labelForEquip(String code) {
        if ("full_gym".equals(code)) {
            return s("coach_equip_gym");
        }
        if ("dumbbell_bench_band".equals(code)) {
            return s("coach_equip_dumbbell_band");
        }
        if ("home_bodyweight".equals(code)) {
            return s("coach_equip_home_bw");
        }
        return code;
    }

    private static int indexOf(String[] values, String target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(target)) {
                return i;
            }
        }
        return 0;
    }

    private void requestSend(String prompt) {
        if (activity.coachSending || prompt == null || prompt.trim().isEmpty()) {
            return;
        }
        String trimmed = prompt.trim();
        refreshOfflineBanner();

        // Check offline proactively so the user immediately sees the exact offline message.
        if (!GlmClient.isNetworkAvailable(activity)) {
            activity.coachLastPrompt = trimmed;
            activity.coachMessages.add(
                    new CoachModels.ChatMessage(
                            "user", trimmed, false, System.currentTimeMillis()));
            activity.coachMessages.add(
                    new CoachModels.ChatMessage(
                            "assistant", s("coach_offline"), true, System.currentTimeMillis()));
            renderMessages();
            scrollToBottom();
            return;
        }

        SharedPreferences prefs =
                activity.getSharedPreferences("preferences", Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_PRIVACY_ACCEPTED, false)) {
            activity.activeDialog =
                    new AlertDialog.Builder(activity)
                            .setTitle(s("coach_privacy_title"))
                            .setMessage(s("coach_privacy_body"))
                            .setNegativeButton(s("cancel"), null)
                            .setPositiveButton(
                                    s("coach_privacy_agree"),
                                    (d, w) -> {
                                        prefs.edit().putBoolean(KEY_PRIVACY_ACCEPTED, true).apply();
                                        executeSend(trimmed, false);
                                    })
                            .show();
            return;
        }
        executeSend(trimmed, false);
    }

    private void executeSend(String userPrompt, boolean isRetry) {
        if (activity.coachSending) {
            return;
        }
        refreshOfflineBanner();
        if (!GlmClient.isNetworkAvailable(activity)) {
            if (isRetry
                    && !activity.coachMessages.isEmpty()
                    && activity.coachMessages.get(activity.coachMessages.size() - 1).isError) {
                activity.coachMessages.remove(activity.coachMessages.size() - 1);
            } else {
                activity.coachMessages.add(
                        new CoachModels.ChatMessage(
                                "user", userPrompt, false, System.currentTimeMillis()));
            }
            activity.coachLastPrompt = userPrompt;
            activity.coachMessages.add(
                    new CoachModels.ChatMessage(
                            "assistant", s("coach_offline"), true, System.currentTimeMillis()));
            renderMessages();
            scrollToBottom();
            return;
        }

        activity.coachSending = true;
        activity.coachLastPrompt = userPrompt;
        if (sendButton != null) {
            sendButton.setEnabled(false);
        }

        if (isRetry
                && !activity.coachMessages.isEmpty()
                && activity.coachMessages.get(activity.coachMessages.size() - 1).isError) {
            activity.coachMessages.remove(activity.coachMessages.size() - 1);
        } else {
            activity.coachMessages.add(
                    new CoachModels.ChatMessage(
                            "user", userPrompt, false, System.currentTimeMillis()));
        }

        List<CoachModels.ChatMessage> priorHistory =
                new ArrayList<>(
                        activity.coachMessages.subList(
                                0, Math.max(0, activity.coachMessages.size() - 1)));
        int requestEpoch = activity.coachEpoch;
        String uiLang = activity.locale().getLanguage().equals("en") ? "en" : "zh";
        Context appContext = activity.getApplicationContext();

        renderMessages();
        scrollToBottom();

        activity.runCoachTask(() -> {
            CoachModels.CoachSnapshot snapshot;
            try (Store database = new Store(appContext)) {
                snapshot = CoachReader.loadSnapshot(database);
            }
            CoachContextBuilder.BuiltContext built =
                    CoachContextBuilder.build(
                            snapshot, System.currentTimeMillis(), TimeZone.getDefault(), uiLang);
            String skill = activity.coachSkillMarkdown();
            GlmProtocol.GlmResult result =
                    GlmClient.chat(
                            appContext,
                            BuildConfig.GLM_API_KEY,
                            BuildConfig.GLM_ENDPOINT,
                            BuildConfig.GLM_MODEL,
                            skill,
                            built.promptBlock,
                            priorHistory,
                            userPrompt);
            activity.runOnUiThread(() -> {
                if (activity.isFinishing()
                        || activity.isDestroyed()
                        || requestEpoch != activity.coachEpoch
                        || !activity.page.equals("coach")) {
                    return;
                }
                activity.coachSending = false;
                if (sendButton != null) {
                    sendButton.setEnabled(true);
                }
                refreshOfflineBanner();
                if (result.success) {
                    activity.coachMessages.add(
                            new CoachModels.ChatMessage(
                                    "assistant",
                                    result.content,
                                    false,
                                    System.currentTimeMillis()));
                } else {
                    activity.coachMessages.add(
                            new CoachModels.ChatMessage(
                                    "assistant",
                                    friendlyError(result),
                                    true,
                                    System.currentTimeMillis()));
                }
                renderMessages();
                scrollToBottom();
            });
        });
    }

    private String friendlyError(GlmProtocol.GlmResult result) {
        switch (result.errorCode) {
            case "offline":
                return s("coach_offline");
            case "missing_key":
                return s("coach_err_no_key");
            case "auth_error":
                return s("coach_err_auth");
            case "rate_limit":
                return s("coach_err_rate");
            case "network_error":
                return s("coach_err_network");
            case "server_error":
                return s("coach_err_server");
            default:
                return s(
                        "coach_err_generic",
                        result.errorDetail.isEmpty() ? result.errorCode : result.errorDetail);
        }
    }

    private void renderMessages() {
        if (chatContainer == null) {
            return;
        }
        chatContainer.removeAllViews();
        addBubble(s("coach_welcome"), false, false);
        for (CoachModels.ChatMessage msg : activity.coachMessages) {
            addBubble(msg.content, "user".equals(msg.role), msg.isError);
        }
        if (activity.coachSending) {
            LinearLayout thinking = new LinearLayout(activity);
            thinking.setOrientation(LinearLayout.VERTICAL);
            thinking.setBackground(rounded(MainActivity.CARD, 14, BORDER));
            thinking.setPadding(activity.dp(14), activity.dp(10), activity.dp(14), activity.dp(10));
            TextView tv = new TextView(activity);
            tv.setText(s("coach_thinking"));
            tv.setTextColor(MainActivity.ACCENT);
            tv.setTextSize(14);
            thinking.addView(tv);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.bottomMargin = activity.dp(10);
            chatContainer.addView(thinking, lp);
        }
    }

    private void addBubble(String textValue, boolean isUser, boolean isError) {
        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(isUser ? Gravity.END : Gravity.START);
        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(-1, -2);
        wrapLp.bottomMargin = activity.dp(10);
        chatContainer.addView(wrapper, wrapLp);

        LinearLayout bubble = new LinearLayout(activity);
        bubble.setOrientation(LinearLayout.VERTICAL);
        int bg = isError ? ERROR_BUBBLE : isUser ? USER_BUBBLE : MainActivity.CARD;
        bubble.setBackground(rounded(bg, 14, isUser ? 0 : BORDER));
        bubble.setPadding(activity.dp(14), activity.dp(10), activity.dp(14), activity.dp(10));

        TextView tv = new TextView(activity);
        tv.setTextColor(MainActivity.FG);
        tv.setTextSize(15);
        tv.setLineSpacing(activity.dp(3), 1.0f);
        tv.setTextIsSelectable(!isUser);
        if (isUser) {
            tv.setText(textValue);
        } else {
            tv.setText(MarkdownFormatter.format(textValue));
        }
        bubble.addView(tv, new LinearLayout.LayoutParams(-2, -2));

        if (isError && !activity.coachLastPrompt.isEmpty()) {
            Button retry = compactButton(s("coach_retry"), MainActivity.ACCENT, ON_ACCENT);
            retry.setOnClickListener(v -> executeSend(activity.coachLastPrompt, true));
            LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(-2, activity.dp(34));
            retryLp.topMargin = activity.dp(8);
            bubble.addView(retry, retryLp);
        }

        LinearLayout.LayoutParams bubbleLp = new LinearLayout.LayoutParams(-2, -2);
        if (isUser) {
            bubbleLp.setMarginStart(activity.dp(40));
        } else {
            bubbleLp.setMarginEnd(activity.dp(14));
        }
        wrapper.addView(bubble, bubbleLp);
    }

    private void scrollToBottom() {
        ScrollView scroller = activity.findViewById(R.id.scroller);
        if (scroller != null) {
            scroller.post(() -> scroller.fullScroll(View.FOCUS_DOWN));
        }
    }

    private Button compactButton(String label, int bgColor, int fgColor) {
        Button b = new Button(activity);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(fgColor);
        b.setBackgroundTintList(ColorStateList.valueOf(bgColor));
        b.setBackground(rounded(bgColor, 10, bgColor == MainActivity.ACCENT ? 0 : BORDER));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(activity.dp(12), activity.dp(4), activity.dp(12), activity.dp(4));
        return b;
    }

    private GradientDrawable rounded(int fillColor, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(activity.dp(radiusDp));
        if (strokeColor != 0) {
            drawable.setStroke(activity.dp(1), strokeColor);
        }
        return drawable;
    }
}
