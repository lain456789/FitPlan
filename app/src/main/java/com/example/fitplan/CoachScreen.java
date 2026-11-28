package com.example.fitplan;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * Apple Fitness-styled AI Fitness Coach screen: compact framed coach persona card, clickable
 * model/API pill, 1-line Profile link, avatar-backed bubbles, and quick prompts pinned directly
 * above the bottom composer. All buttons use adaptive WRAP_CONTENT heights to prevent EN clipping.
 */
final class CoachScreen extends Screen {
    private static final String KEY_PRIVACY_ACCEPTED = "coach_privacy_accepted";

    private CoachAvatarView headerAvatar;
    private LinearLayout offlineBanner;
    private LinearLayout chatContainer;
    private Button sendButton;

    CoachScreen(MainActivity activity) {
        super(activity);
    }

    void render() {
        CoachModels.CoachSnapshot snapshot = CoachReader.loadSnapshot(store);
        CoachApiConfig.EffectiveConfig apiConfig = CoachApiConfig.resolveCurrent(activity);
        String providerLabel = s(CoachApiConfig.providerLabelKey(apiConfig.provider));

        // 1. Clean Coach Persona Header Card
        LinearLayout personaCard = card(content);
        LinearLayout topRow = new LinearLayout(activity);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        personaCard.addView(topRow, new LinearLayout.LayoutParams(-1, -2));

        headerAvatar = new CoachAvatarView(activity, 50, true, MainActivity.CARD);
        headerAvatar.setContentDescription(s("coach_avatar_change"));
        headerAvatar.setOnClickListener(v -> activity.showCoachAvatarOptions());
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(-2, -2);
        avatarLp.setMarginEnd(activity.dp(12));
        topRow.addView(headerAvatar, avatarLp);

        LinearLayout infoCol = new LinearLayout(activity);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        topRow.addView(infoCol, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView heading = new TextView(activity);
        heading.setText(s("coach_title"));
        heading.setTextSize(19);
        heading.setTextColor(MainActivity.FG);
        heading.setTypeface(null, Typeface.BOLD);
        infoCol.addView(heading);

        // Compact clickable model pill right under title
        LinearLayout modelPill = new LinearLayout(activity);
        modelPill.setOrientation(LinearLayout.HORIZONTAL);
        modelPill.setGravity(Gravity.CENTER_VERTICAL);
        boolean keyReady = apiConfig.hasKey();
        modelPill.setBackground(
                UiKit.rippleBox(
                        activity,
                        keyReady ? UiKit.SURFACE : UiKit.OFFLINE_BG,
                        keyReady ? 0 : UiKit.OFFLINE_BORDER,
                        12,
                        0x26FFFFFF));
        modelPill.setPadding(activity.dp(8), activity.dp(4), activity.dp(8), activity.dp(4));
        modelPill.setOnClickListener(v -> openApiSettings());

        ImageView sliderIcon = new ImageView(activity);
        sliderIcon.setImageResource(R.drawable.ic_settings_sliders);
        sliderIcon.setColorFilter(
                keyReady ? MainActivity.ACCENT : MainActivity.FG, PorterDuff.Mode.SRC_IN);
        sliderIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconLp =
                new LinearLayout.LayoutParams(activity.dp(13), activity.dp(13));
        iconLp.setMarginEnd(activity.dp(5));
        modelPill.addView(sliderIcon, iconLp);

        String shortModel =
                keyReady
                        ? (apiConfig.model.isEmpty() ? providerLabel : apiConfig.model)
                        : s("coach_model_pill_missing", providerLabel);
        TextView modelText = new TextView(activity);
        modelText.setText(shortModel);
        modelText.setTextSize(12);
        modelText.setTextColor(keyReady ? MainActivity.ACCENT : MainActivity.FG);
        modelText.setSingleLine(true);
        modelPill.addView(modelText);

        LinearLayout.LayoutParams mpLp = new LinearLayout.LayoutParams(-2, -2);
        mpLp.topMargin = activity.dp(4);
        infoCol.addView(modelPill, mpLp);

        Button newChat =
                UiKit.compactPill(
                        activity,
                        s("coach_new_chat"),
                        UiKit.SURFACE,
                        0,
                        MainActivity.FG,
                        R.drawable.ic_add,
                        false);
        newChat.setTextSize(12);
        newChat.setPadding(activity.dp(10), activity.dp(6), activity.dp(12), activity.dp(6));
        newChat.setOnClickListener(v -> {
            activity.clearCoachSession();
            toast(s("coach_chat_cleared"));
            show("coach");
        });
        LinearLayout.LayoutParams newChatLp = new LinearLayout.LayoutParams(-2, -2);
        newChatLp.setMarginStart(activity.dp(8));
        topRow.addView(newChat, newChatLp);

        // 2. Single-line clickable Profile link strip
        LinearLayout profileStrip = new LinearLayout(activity);
        profileStrip.setOrientation(LinearLayout.HORIZONTAL);
        profileStrip.setGravity(Gravity.CENTER_VERTICAL);
        profileStrip.setBackground(
                UiKit.rippleBox(activity, UiKit.SURFACE_DEEP, UiKit.BORDER, 14, 0x22FFFFFF));
        profileStrip.setPadding(activity.dp(12), activity.dp(8), activity.dp(10), activity.dp(8));
        LinearLayout.LayoutParams stripLp = new LinearLayout.LayoutParams(-1, -2);
        stripLp.setMargins(0, activity.dp(2), 0, activity.dp(8));
        content.addView(profileStrip, stripLp);

        TextView profileStripText = new TextView(activity);
        profileStripText.setText(
                snapshot.profile.isEmpty()
                        ? s("coach_profile_link_empty")
                        : profileSummaryText(snapshot.profile));
        profileStripText.setTextSize(12);
        profileStripText.setTextColor(MainActivity.MUTED);
        profileStripText.setSingleLine(true);
        profileStripText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        profileStrip.addView(profileStripText, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView chev = new ImageView(activity);
        chev.setImageResource(R.drawable.ic_action_chevron);
        chev.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams chevLp =
                new LinearLayout.LayoutParams(activity.dp(16), activity.dp(16));
        chevLp.setMarginStart(activity.dp(6));
        profileStrip.addView(chev, chevLp);

        profileStrip.setOnClickListener(v -> {
            activity.profileFormExpanded = true;
            show("profile");
        });

        // 3. Proactive offline banner (exact required Chinese prompt preserved)
        offlineBanner = new LinearLayout(activity);
        offlineBanner.setOrientation(LinearLayout.VERTICAL);
        offlineBanner.setBackground(
                UiKit.box(activity, UiKit.OFFLINE_BG, UiKit.OFFLINE_BORDER, 14));
        offlineBanner.setPadding(
                activity.dp(14), activity.dp(10), activity.dp(14), activity.dp(10));
        TextView offlineText = new TextView(activity);
        offlineText.setText(s("coach_offline"));
        offlineText.setTextColor(MainActivity.FG);
        offlineText.setTextSize(14);
        offlineText.setTypeface(null, Typeface.BOLD);
        offlineBanner.addView(offlineText);
        LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(-1, -2);
        bannerLp.setMargins(0, 0, 0, activity.dp(8));
        content.addView(offlineBanner, bannerLp);
        refreshOfflineAndAvatarStatus();

        // 4. Chat messages container
        chatContainer = new LinearLayout(activity);
        chatContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(chatContainer, new LinearLayout.LayoutParams(-1, -2));
        renderMessages();

        // 5. Pinned bottom dock: Quick prompt chips (directly above composer) -> Input -> Privacy
        LinearLayout actions = activity.findViewById(R.id.page_actions);
        actions.removeAllViews();
        actions.setVisibility(View.VISIBLE);

        HorizontalScrollView chipScroll = new HorizontalScrollView(activity);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams chipScrollLp = new LinearLayout.LayoutParams(-1, -2);
        chipScrollLp.setMargins(0, activity.dp(4), 0, activity.dp(6));
        actions.addView(chipScroll, chipScrollLp);

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
            Button chip =
                    UiKit.compactPill(
                            activity,
                            s(item[0]),
                            UiKit.CARD,
                            UiKit.BORDER,
                            MainActivity.FG,
                            0,
                            false);
            chip.setTextSize(12);
            chip.setMinHeight(activity.dp(32));
            chip.setMinimumHeight(activity.dp(32));
            chip.setPadding(activity.dp(12), activity.dp(6), activity.dp(12), activity.dp(6));
            String prompt = s(item[1]);
            chip.setOnClickListener(v -> requestSend(prompt));
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(-2, -2);
            chipLp.setMarginEnd(activity.dp(6));
            chipRow.addView(chip, chipLp);
        }

        LinearLayout inputRow = new LinearLayout(activity);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        actions.addView(inputRow, new LinearLayout.LayoutParams(-1, -2));

        EditText inputEdit = new EditText(activity);
        inputEdit.setHint(s("coach_input_hint"));
        inputEdit.setHintTextColor(MainActivity.MUTED);
        inputEdit.setTextColor(MainActivity.FG);
        inputEdit.setTextSize(14);
        inputEdit.setMaxLines(4);
        inputEdit.setBackground(UiKit.box(activity, UiKit.CARD, UiKit.BORDER, 22));
        inputEdit.setPadding(activity.dp(16), activity.dp(10), activity.dp(16), activity.dp(10));
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(0, -2, 1f);
        editLp.setMarginEnd(activity.dp(8));
        inputRow.addView(inputEdit, editLp);

        sendButton =
                UiKit.compactPill(
                        activity,
                        s("coach_send"),
                        MainActivity.ACCENT,
                        0,
                        UiKit.ON_PRIMARY,
                        R.drawable.ic_send,
                        true);
        sendButton.setMinHeight(activity.dp(42));
        sendButton.setMinimumHeight(activity.dp(42));
        sendButton.setEnabled(!activity.coachSending);
        sendButton.setOnClickListener(v -> {
            String prompt = inputEdit.getText().toString().trim();
            if (!prompt.isEmpty()) {
                inputEdit.setText("");
                requestSend(prompt);
            }
        });
        inputRow.addView(sendButton, new LinearLayout.LayoutParams(-2, -2));

        TextView privacyFooter = new TextView(activity);
        privacyFooter.setText(s("coach_privacy_footer", providerLabel));
        privacyFooter.setTextColor(MainActivity.MUTED);
        privacyFooter.setTextSize(10.5f);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(-1, -2);
        footerLp.topMargin = activity.dp(4);
        actions.addView(privacyFooter, footerLp);
    }

    private void openApiSettings() {
        new CoachApiDialog(activity).show(() -> show("coach"));
    }

    private void refreshOfflineAndAvatarStatus() {
        boolean online = GlmClient.isNetworkAvailable(activity);
        if (offlineBanner != null) {
            offlineBanner.setVisibility(online ? View.GONE : View.VISIBLE);
        }
        if (headerAvatar != null) {
            int status =
                    !online
                            ? CoachAvatarView.STATUS_OFFLINE
                            : activity.coachSending
                                    ? CoachAvatarView.STATUS_THINKING
                                    : CoachAvatarView.STATUS_ONLINE;
            headerAvatar.setStatus(status);
        }
    }

    private String profileSummaryText(CoachModels.UserProfile p) {
        if (p.isEmpty()) {
            return s("coach_profile_empty");
        }
        List<String> parts = new ArrayList<>();
        if (!p.goal.isEmpty()) {
            parts.add(s(ProfileSummary.goalLabelKey(p.goal)));
        }
        if (!p.weightKg.isEmpty()) {
            parts.add(p.weightKg + " kg");
        }
        if (p.weeklyDays > 0) {
            parts.add(s("coach_days_fmt", p.weeklyDays));
        }
        if (!p.experience.isEmpty()) {
            parts.add(s(ProfileSummary.expLabelKey(p.experience)));
        }
        return String.join(" · ", parts);
    }

    private void requestSend(String prompt) {
        if (activity.coachSending || prompt == null || prompt.trim().isEmpty()) {
            return;
        }
        String trimmed = prompt.trim();
        refreshOfflineAndAvatarStatus();

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
            CoachApiConfig.EffectiveConfig apiConfig = CoachApiConfig.resolveCurrent(activity);
            String providerLabel = s(CoachApiConfig.providerLabelKey(apiConfig.provider));
            activity.activeDialog =
                    new AlertDialog.Builder(activity)
                            .setTitle(s("coach_privacy_title"))
                            .setMessage(s("coach_privacy_body", providerLabel))
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
        refreshOfflineAndAvatarStatus();
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
        refreshOfflineAndAvatarStatus();
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
            CoachApiConfig.EffectiveConfig apiConfig = CoachApiConfig.resolveCurrent(appContext);
            GlmProtocol.GlmResult result =
                    GlmClient.chat(
                            appContext,
                            apiConfig.apiKey,
                            apiConfig.endpoint,
                            apiConfig.model,
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
                refreshOfflineAndAvatarStatus();
                if (result.success) {
                    activity.coachLastErrorCode = "";
                    activity.coachMessages.add(
                            new CoachModels.ChatMessage(
                                    "assistant",
                                    result.content,
                                    false,
                                    System.currentTimeMillis()));
                } else {
                    activity.coachLastErrorCode = result.errorCode;
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
            case "invalid_endpoint":
                return s("coach_api_endpoint_invalid");
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
        addBubble(s("coach_welcome"), false, false, false);
        int lastIndex = activity.coachMessages.size() - 1;
        for (int i = 0; i < activity.coachMessages.size(); i++) {
            CoachModels.ChatMessage msg = activity.coachMessages.get(i);
            boolean offerConfig =
                    i == lastIndex
                            && msg.isError
                            && ("missing_key".equals(activity.coachLastErrorCode)
                                    || "auth_error".equals(activity.coachLastErrorCode)
                                    || "invalid_endpoint".equals(activity.coachLastErrorCode));
            addBubble(msg.content, "user".equals(msg.role), msg.isError, offerConfig);
        }
        if (activity.coachSending) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.bottomMargin = activity.dp(10);
            chatContainer.addView(row, rowLp);

            CoachAvatarView mini = new CoachAvatarView(activity, 30, false, MainActivity.BG);
            LinearLayout.LayoutParams miniLp = new LinearLayout.LayoutParams(-2, -2);
            miniLp.setMarginEnd(activity.dp(8));
            row.addView(mini, miniLp);

            LinearLayout thinking = new LinearLayout(activity);
            thinking.setOrientation(LinearLayout.VERTICAL);
            thinking.setBackground(UiKit.box(activity, UiKit.CARD, 0, 18));
            thinking.setPadding(activity.dp(14), activity.dp(10), activity.dp(14), activity.dp(10));
            TextView tv = new TextView(activity);
            tv.setText(s("coach_thinking"));
            tv.setTextColor(MainActivity.ACCENT);
            tv.setTextSize(14);
            thinking.addView(tv);
            row.addView(thinking, new LinearLayout.LayoutParams(-2, -2));
        }
    }

    private void addBubble(
            String textValue, boolean isUser, boolean isError, boolean offerConfigButton) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(isUser ? Gravity.END : Gravity.START);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.bottomMargin = activity.dp(10);
        chatContainer.addView(row, rowLp);

        if (!isUser) {
            CoachAvatarView mini = new CoachAvatarView(activity, 30, false, MainActivity.BG);
            LinearLayout.LayoutParams miniLp = new LinearLayout.LayoutParams(-2, -2);
            miniLp.setMarginEnd(activity.dp(8));
            miniLp.topMargin = activity.dp(2);
            row.addView(mini, miniLp);
        }

        LinearLayout bubble = new LinearLayout(activity);
        bubble.setOrientation(LinearLayout.VERTICAL);
        int bg = isError ? UiKit.DANGER_BG : isUser ? UiKit.USER_BUBBLE : UiKit.CARD;
        int stroke = isError ? UiKit.DANGER_BORDER : 0;
        bubble.setBackground(UiKit.box(activity, bg, stroke, 18));
        bubble.setPadding(activity.dp(14), activity.dp(10), activity.dp(14), activity.dp(10));

        TextView tv = new TextView(activity);
        tv.setTextColor(MainActivity.FG);
        tv.setTextSize(14.5f);
        tv.setLineSpacing(activity.dp(3), 1.0f);
        tv.setTextIsSelectable(!isUser);
        if (isUser) {
            tv.setText(textValue);
        } else {
            tv.setText(MarkdownFormatter.format(textValue));
        }
        bubble.addView(tv, new LinearLayout.LayoutParams(-2, -2));

        if (isError && (!activity.coachLastPrompt.isEmpty() || offerConfigButton)) {
            LinearLayout actionRow = new LinearLayout(activity);
            actionRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(-2, -2);
            actLp.topMargin = activity.dp(8);
            bubble.addView(actionRow, actLp);

            if (!activity.coachLastPrompt.isEmpty()) {
                Button retry =
                        UiKit.compactPill(
                                activity,
                                s("coach_retry"),
                                MainActivity.ACCENT,
                                0,
                                UiKit.ON_PRIMARY,
                                0,
                                true);
                retry.setOnClickListener(v -> executeSend(activity.coachLastPrompt, true));
                LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(-2, -2);
                retryLp.setMarginEnd(activity.dp(8));
                actionRow.addView(retry, retryLp);
            }
            if (offerConfigButton) {
                Button cfg =
                        UiKit.compactPill(
                                activity,
                                s("coach_api_open"),
                                UiKit.SURFACE,
                                0,
                                MainActivity.ACCENT,
                                R.drawable.ic_settings_sliders,
                                true);
                cfg.setOnClickListener(v -> openApiSettings());
                actionRow.addView(cfg, new LinearLayout.LayoutParams(-2, -2));
            }
        }

        LinearLayout.LayoutParams bubbleLp =
                isUser
                        ? new LinearLayout.LayoutParams(-2, -2)
                        : new LinearLayout.LayoutParams(0, -2, 1f);
        if (isUser) {
            bubbleLp.setMarginStart(activity.dp(44));
        } else {
            bubbleLp.setMarginEnd(activity.dp(6));
        }
        row.addView(bubble, bubbleLp);
    }

    private void scrollToBottom() {
        ScrollView scroller = activity.findViewById(R.id.scroller);
        if (scroller != null) {
            scroller.post(() -> scroller.fullScroll(View.FOCUS_DOWN));
        }
    }
}
