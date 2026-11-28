package com.example.fitplan;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Visual configuration dialog for selecting AI provider, model, endpoint, and user API key with
 * live connection testing and one-tap reset to built-in GLM defaults.
 */
final class CoachApiDialog {
    private static final int INPUT_BG = UiKit.SURFACE_DEEP;
    private static final int BORDER = UiKit.BORDER;
    private static final int ON_ACCENT = UiKit.ON_PRIMARY;
    private static final int ERROR_TEXT = UiKit.DANGER;

    private final MainActivity activity;

    CoachApiDialog(MainActivity activity) {
        this.activity = activity;
    }

    void show(Runnable onSaved) {
        CoachApiConfig.UserConfig current = CoachApiConfig.load(activity);

        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(activity.dp(20), activity.dp(12), activity.dp(20), activity.dp(8));

        TextView intro = label(form, s("coach_api_subtitle"), 13, MainActivity.MUTED);
        intro.setPadding(0, 0, 0, activity.dp(10));

        // 1. Provider selector
        label(form, s("coach_api_provider"), 13, MainActivity.MUTED);
        String[] providerLabels = new String[CoachApiConfig.PROVIDERS.length];
        for (int i = 0; i < CoachApiConfig.PROVIDERS.length; i++) {
            providerLabels[i] = s(CoachApiConfig.providerLabelKey(CoachApiConfig.PROVIDERS[i]));
        }
        Spinner providerSpinner = new Spinner(activity);
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, providerLabels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        providerSpinner.setAdapter(adapter);
        providerSpinner.setSelection(
                ProfileSummary.indexOf(CoachApiConfig.PROVIDERS, current.provider));
        LinearLayout.LayoutParams spinLp = new LinearLayout.LayoutParams(-1, activity.dp(48));
        spinLp.bottomMargin = activity.dp(8);
        form.addView(providerSpinner, spinLp);

        // 2. Model presets + input
        label(form, s("coach_api_model"), 13, MainActivity.MUTED);
        HorizontalScrollView presetScroll = new HorizontalScrollView(activity);
        presetScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout presetRow = new LinearLayout(activity);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        presetScroll.addView(presetRow, new ViewGroup.LayoutParams(-2, -2));
        LinearLayout.LayoutParams presetLp = new LinearLayout.LayoutParams(-1, -2);
        presetLp.bottomMargin = activity.dp(6);
        form.addView(presetScroll, presetLp);

        EditText modelEdit = styledEdit(current.model, s("coach_api_model_hint"), false);
        LinearLayout.LayoutParams modelLp = new LinearLayout.LayoutParams(-1, -2);
        modelLp.bottomMargin = activity.dp(10);
        form.addView(modelEdit, modelLp);

        // 3. Endpoint (read-only info for presets, editable for custom)
        TextView endpointLabel = label(form, s("coach_api_endpoint"), 13, MainActivity.MUTED);
        TextView endpointHint = label(form, "", 12, MainActivity.MUTED);
        EditText endpointEdit =
                styledEdit(current.customEndpoint, s("coach_api_endpoint_hint"), false);
        LinearLayout.LayoutParams epLp = new LinearLayout.LayoutParams(-1, -2);
        epLp.bottomMargin = activity.dp(10);
        form.addView(endpointEdit, epLp);

        // 4. API Key input + Show/Hide toggle
        label(form, s("coach_api_key"), 13, MainActivity.MUTED);
        LinearLayout keyRow = new LinearLayout(activity);
        keyRow.setOrientation(LinearLayout.HORIZONTAL);
        keyRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams keyRowLp = new LinearLayout.LayoutParams(-1, -2);
        keyRowLp.bottomMargin = activity.dp(4);
        form.addView(keyRow, keyRowLp);

        EditText keyEdit = styledEdit(current.apiKey, s("coach_api_key_hint"), true);
        LinearLayout.LayoutParams keyEditLp = new LinearLayout.LayoutParams(0, -2, 1f);
        keyEditLp.setMarginEnd(activity.dp(8));
        keyRow.addView(keyEdit, keyEditLp);

        final boolean[] keyVisible = {false};
        Button toggleBtn = compactButton(s("coach_api_show"), UiKit.SURFACE, MainActivity.ACCENT);
        toggleBtn.setOnClickListener(v -> {
            keyVisible[0] = !keyVisible[0];
            int cursor = keyEdit.getSelectionEnd();
            keyEdit.setInputType(
                    keyVisible[0]
                            ? (InputType.TYPE_CLASS_TEXT
                                    | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
                            : (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD));
            keyEdit.setTypeface(Typeface.DEFAULT);
            keyEdit.setSelection(Math.max(0, Math.min(cursor, keyEdit.getText().length())));
            toggleBtn.setText(s(keyVisible[0] ? "coach_api_hide" : "coach_api_show"));
        });
        keyRow.addView(toggleBtn, new LinearLayout.LayoutParams(-2, -2));

        TextView keyNote = label(form, "", 12, MainActivity.MUTED);
        keyNote.setPadding(0, 0, 0, activity.dp(10));

        // 5. Test connection & Reset row + inline status feedback
        LinearLayout utilRow = new LinearLayout(activity);
        utilRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams utilLp = new LinearLayout.LayoutParams(-1, -2);
        utilLp.topMargin = activity.dp(2);
        form.addView(utilRow, utilLp);

        Button testBtn = compactButton(s("coach_api_test"), UiKit.SURFACE, MainActivity.ACCENT);
        LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(0, -2, 1f);
        testLp.setMarginEnd(activity.dp(8));
        utilRow.addView(testBtn, testLp);

        Button resetBtn = compactButton(s("coach_api_reset"), UiKit.SURFACE, MainActivity.MUTED);
        utilRow.addView(resetBtn, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView testStatus = label(form, "", 12, MainActivity.MUTED);
        testStatus.setVisibility(View.GONE);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(-1, -2);
        stLp.topMargin = activity.dp(6);
        testStatus.setLayoutParams(stLp);

        final String[] activeProvider = {current.provider};
        final boolean[] firstBind = {true};

        Runnable refreshProviderUi =
                () -> {
                    String provider = activeProvider[0];
                    boolean isCustom = CoachApiConfig.PROVIDER_CUSTOM.equals(provider);
                    boolean isGlm = CoachApiConfig.PROVIDER_GLM.equals(provider);

                    // Rebuild model preset chips
                    presetRow.removeAllViews();
                    String[] presets = CoachApiConfig.presetModels(provider);
                    presetScroll.setVisibility(presets.length > 0 ? View.VISIBLE : View.GONE);
                    for (String modelName : presets) {
                        Button chip =
                                compactButton(modelName, MainActivity.CARD, MainActivity.ACCENT);
                        chip.setBackground(rounded(MainActivity.CARD, 14, BORDER));
                        chip.setTextSize(12);
                        chip.setOnClickListener(v -> modelEdit.setText(modelName));
                        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(-2, -2);
                        chipLp.setMarginEnd(activity.dp(6));
                        presetRow.addView(chip, chipLp);
                    }

                    if (!firstBind[0]) {
                        String defaultModel =
                                isGlm && !BuildConfig.GLM_MODEL.trim().isEmpty()
                                        ? BuildConfig.GLM_MODEL.trim()
                                        : CoachApiConfig.defaultModel(provider);
                        modelEdit.setText(defaultModel);
                    } else if (modelEdit.getText().toString().trim().isEmpty()) {
                        String defaultModel =
                                isGlm && !BuildConfig.GLM_MODEL.trim().isEmpty()
                                        ? BuildConfig.GLM_MODEL.trim()
                                        : CoachApiConfig.defaultModel(provider);
                        modelEdit.setText(defaultModel);
                    }

                    endpointEdit.setVisibility(isCustom ? View.VISIBLE : View.GONE);
                    endpointLabel.setText(s("coach_api_endpoint"));
                    if (isCustom) {
                        endpointHint.setText(s("coach_api_endpoint_custom_help"));
                    } else {
                        String presetUrl =
                                isGlm && !BuildConfig.GLM_ENDPOINT.trim().isEmpty()
                                        ? BuildConfig.GLM_ENDPOINT.trim()
                                        : CoachApiConfig.presetEndpoint(provider);
                        endpointHint.setText(presetUrl);
                    }

                    boolean hasBuiltIn = !BuildConfig.GLM_API_KEY.trim().isEmpty();
                    if (isGlm && hasBuiltIn) {
                        keyNote.setText(s("coach_api_key_note_glm_builtin"));
                    } else if (CoachApiConfig.PROVIDER_OPENAI.equals(provider)) {
                        keyNote.setText(s("coach_api_key_note_openai"));
                    } else {
                        keyNote.setText(s("coach_api_key_note_required"));
                    }
                    firstBind[0] = false;
                };

        providerSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent, View view, int position, long id) {
                        activeProvider[0] = CoachApiConfig.PROVIDERS[position];
                        refreshProviderUi.run();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });
        refreshProviderUi.run();

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);

        AlertDialog dialog =
                new AlertDialog.Builder(activity)
                        .setTitle(s("coach_api_title"))
                        .setView(scroll)
                        .setNegativeButton(s("cancel"), null)
                        .setPositiveButton(s("save"), null)
                        .create();
        activity.activeDialog = dialog;

        final boolean[] testing = {false};
        testBtn.setOnClickListener(v -> {
            if (testing[0]) {
                return;
            }
            CoachApiConfig.UserConfig draft =
                    validateAndBuild(
                            activeProvider[0],
                            keyEdit.getText().toString(),
                            modelEdit.getText().toString(),
                            endpointEdit.getText().toString());
            if (draft == null) {
                return;
            }
            CoachApiConfig.EffectiveConfig effective =
                    CoachApiConfig.resolve(
                            draft,
                            BuildConfig.GLM_API_KEY,
                            BuildConfig.GLM_ENDPOINT,
                            BuildConfig.GLM_MODEL);
            if (!effective.hasKey()) {
                toast(s("coach_api_key_required"));
                return;
            }
            testing[0] = true;
            testBtn.setEnabled(false);
            testStatus.setVisibility(View.VISIBLE);
            testStatus.setTextColor(MainActivity.MUTED);
            testStatus.setText(s("coach_api_testing"));
            long started = System.currentTimeMillis();
            Context appContext = activity.getApplicationContext();
            activity.runCoachTask(() -> {
                GlmProtocol.GlmResult res =
                        GlmClient.testConnection(
                                appContext, effective.apiKey, effective.endpoint, effective.model);
                long elapsed = Math.max(1L, System.currentTimeMillis() - started);
                activity.runOnUiThread(() -> {
                    testing[0] = false;
                    if (activity.isFinishing() || activity.isDestroyed() || !dialog.isShowing()) {
                        return;
                    }
                    testBtn.setEnabled(true);
                    if (res.success) {
                        testStatus.setTextColor(MainActivity.ACCENT);
                        testStatus.setText(s("coach_api_test_ok", elapsed));
                    } else {
                        testStatus.setTextColor(ERROR_TEXT);
                        testStatus.setText(s("coach_api_test_fail", formatTestError(res)));
                    }
                });
            });
        });

        resetBtn.setOnClickListener(v -> {
            CoachApiConfig.reset(activity);
            firstBind[0] = false;
            activeProvider[0] = CoachApiConfig.PROVIDER_GLM;
            providerSpinner.setSelection(0);
            keyEdit.setText("");
            endpointEdit.setText("");
            modelEdit.setText(
                    BuildConfig.GLM_MODEL.trim().isEmpty()
                            ? CoachApiConfig.defaultModel(CoachApiConfig.PROVIDER_GLM)
                            : BuildConfig.GLM_MODEL.trim());
            testStatus.setVisibility(View.GONE);
            refreshProviderUi.run();
            toast(s("coach_api_reset_done"));
            if (onSaved != null) {
                onSaved.run();
            }
        });

        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v -> {
                            CoachApiConfig.UserConfig draft =
                                    validateAndBuild(
                                            activeProvider[0],
                                            keyEdit.getText().toString(),
                                            modelEdit.getText().toString(),
                                            endpointEdit.getText().toString());
                            if (draft == null) {
                                return;
                            }
                            CoachApiConfig.EffectiveConfig effective =
                                    CoachApiConfig.resolve(
                                            draft,
                                            BuildConfig.GLM_API_KEY,
                                            BuildConfig.GLM_ENDPOINT,
                                            BuildConfig.GLM_MODEL);
                            if (!CoachApiConfig.PROVIDER_GLM.equals(draft.provider)
                                    && !effective.hasKey()) {
                                toast(s("coach_api_key_required"));
                                return;
                            }
                            CoachApiConfig.save(activity, draft);
                            toast(s("coach_api_saved"));
                            dialog.dismiss();
                            if (onSaved != null) {
                                onSaved.run();
                            }
                        }));
        dialog.show();
    }

    private CoachApiConfig.UserConfig validateAndBuild(
            String provider, String rawKey, String rawModel, String rawEndpoint) {
        String key = rawKey == null ? "" : rawKey.trim();
        String model = rawModel == null ? "" : rawModel.trim();
        String customEndpoint = "";
        if (CoachApiConfig.PROVIDER_CUSTOM.equals(provider)) {
            if (rawEndpoint == null || rawEndpoint.trim().isEmpty()) {
                toast(s("coach_api_endpoint_required"));
                return null;
            }
            try {
                customEndpoint = CoachApiConfig.normalizeEndpoint(rawEndpoint);
            } catch (IllegalArgumentException e) {
                toast(s("coach_api_endpoint_invalid"));
                return null;
            }
            if (model.isEmpty()) {
                toast(s("coach_api_model_required"));
                return null;
            }
        }
        return new CoachApiConfig.UserConfig(provider, key, model, customEndpoint);
    }

    private String formatTestError(GlmProtocol.GlmResult result) {
        switch (result.errorCode) {
            case "offline":
                return s("coach_offline");
            case "missing_key":
                return s("coach_api_key_required");
            case "auth_error":
                return s("coach_err_auth");
            case "rate_limit":
                return s("coach_err_rate");
            case "invalid_endpoint":
                return s("coach_api_endpoint_invalid");
            case "network_error":
                return s("coach_err_network");
            case "server_error":
                return s("coach_err_server");
            default:
                return result.errorDetail.isEmpty() ? result.errorCode : result.errorDetail;
        }
    }

    private String s(String key, Object... args) {
        return activity.s(key, args);
    }

    private void toast(String msg) {
        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
    }

    private TextView label(LinearLayout parent, String text, int sp, int color) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setPadding(0, activity.dp(4), 0, activity.dp(4));
        parent.addView(tv, new LinearLayout.LayoutParams(-1, -2));
        return tv;
    }

    private EditText styledEdit(String initial, String hint, boolean password) {
        EditText edit = new EditText(activity);
        edit.setText(initial);
        edit.setHint(hint);
        edit.setHintTextColor(MainActivity.MUTED);
        edit.setTextColor(MainActivity.FG);
        edit.setTextSize(14);
        edit.setSingleLine(true);
        if (password) {
            edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            edit.setTypeface(Typeface.DEFAULT);
        }
        edit.setBackground(rounded(INPUT_BG, 10, BORDER));
        edit.setPadding(activity.dp(10), activity.dp(9), activity.dp(10), activity.dp(9));
        return edit;
    }

    private Button compactButton(String text, int bg, int fg) {
        Button b = new Button(activity);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(fg);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(false);
        b.setMaxLines(2);
        b.setBackgroundTintList(null);
        b.setBackground(rounded(bg, 14, bg == MainActivity.ACCENT ? 0 : BORDER));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(activity.dp(38));
        b.setMinimumHeight(activity.dp(38));
        b.setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8));
        return b;
    }

    private GradientDrawable rounded(int fill, int radiusDp, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(activity.dp(radiusDp));
        if (stroke != 0) {
            drawable.setStroke(activity.dp(1), stroke);
        }
        return drawable;
    }
}
