package com.example.fitplan;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * Pure-Java configuration store and resolver for AI Coach providers, models, and user API keys.
 * Supports Zhipu GLM (default with BuildConfig fallback), DeepSeek, Qwen, OpenAI, and custom
 * OpenAI-compatible Chat Completions endpoints.
 */
public final class CoachApiConfig {
    public static final String PREFS_NAME = "coach_api_config";
    public static final String PROVIDER_GLM = "glm";
    public static final String PROVIDER_DEEPSEEK = "deepseek";
    public static final String PROVIDER_QWEN = "qwen";
    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_CUSTOM = "custom";

    public static final String[] PROVIDERS = {
        PROVIDER_GLM, PROVIDER_DEEPSEEK, PROVIDER_QWEN, PROVIDER_OPENAI, PROVIDER_CUSTOM
    };

    public static final String DEFAULT_GLM_ENDPOINT =
            "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    public static final String DEEPSEEK_ENDPOINT = "https://api.deepseek.com/chat/completions";
    public static final String QWEN_ENDPOINT =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    public static final String OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "model";
    private static final String KEY_CUSTOM_ENDPOINT = "custom_endpoint";

    private CoachApiConfig() {}

    public static final class UserConfig {
        public final String provider;
        public final String apiKey;
        public final String model;
        public final String customEndpoint;

        public UserConfig(String provider, String apiKey, String model, String customEndpoint) {
            this.provider = normalizeProvider(provider);
            this.apiKey = safeTrim(apiKey);
            this.model = safeTrim(model);
            this.customEndpoint = safeTrim(customEndpoint);
        }

        public static UserConfig defaults() {
            return new UserConfig(PROVIDER_GLM, "", "", "");
        }

        public boolean isDefault() {
            return PROVIDER_GLM.equals(provider)
                    && apiKey.isEmpty()
                    && model.isEmpty()
                    && customEndpoint.isEmpty();
        }
    }

    public static final class EffectiveConfig {
        public final String provider;
        public final String apiKey;
        public final String endpoint;
        public final String model;
        public final boolean usingBuiltInKey;

        public EffectiveConfig(
                String provider,
                String apiKey,
                String endpoint,
                String model,
                boolean usingBuiltInKey) {
            this.provider = provider;
            this.apiKey = apiKey;
            this.endpoint = endpoint;
            this.model = model;
            this.usingBuiltInKey = usingBuiltInKey;
        }

        public boolean hasKey() {
            return !apiKey.isEmpty();
        }
    }

    public static String normalizeProvider(String provider) {
        String p = safeTrim(provider).toLowerCase(Locale.US);
        for (String candidate : PROVIDERS) {
            if (candidate.equals(p)) {
                return candidate;
            }
        }
        return PROVIDER_GLM;
    }

    public static String presetEndpoint(String provider) {
        String p = normalizeProvider(provider);
        switch (p) {
            case PROVIDER_DEEPSEEK:
                return DEEPSEEK_ENDPOINT;
            case PROVIDER_QWEN:
                return QWEN_ENDPOINT;
            case PROVIDER_OPENAI:
                return OPENAI_ENDPOINT;
            case PROVIDER_CUSTOM:
                return "";
            default:
                return DEFAULT_GLM_ENDPOINT;
        }
    }

    public static String[] presetModels(String provider) {
        String p = normalizeProvider(provider);
        switch (p) {
            case PROVIDER_DEEPSEEK:
                return new String[] {"deepseek-chat", "deepseek-reasoner"};
            case PROVIDER_QWEN:
                return new String[] {"qwen-plus", "qwen-turbo", "qwen-max"};
            case PROVIDER_OPENAI:
                return new String[] {"gpt-4o-mini", "gpt-4o", "gpt-4.1-mini"};
            case PROVIDER_CUSTOM:
                return new String[] {};
            default:
                return new String[] {"glm-4-flash", "glm-4-air", "glm-4-plus"};
        }
    }

    public static String defaultModel(String provider) {
        String[] models = presetModels(provider);
        return models.length > 0 ? models[0] : "";
    }

    public static String providerLabelKey(String provider) {
        return "coach_provider_" + normalizeProvider(provider);
    }

    /**
     * Normalizes a user-entered OpenAI-compatible URL. Requires https:// except for local loopback
     * testing addresses, strips trailing slashes, and appends /chat/completions when omitted.
     */
    public static String normalizeEndpoint(String raw) {
        String trimmed = safeTrim(raw);
        if (trimmed.isEmpty()) {
            return "";
        }
        String lower = trimmed.toLowerCase(Locale.US);
        boolean isHttps = lower.startsWith("https://");
        boolean isLocalHttp =
                lower.startsWith("http://localhost")
                        || lower.startsWith("http://127.0.0.1")
                        || lower.startsWith("http://10.0.2.2");
        if (!isHttps && !isLocalHttp) {
            throw new IllegalArgumentException("Endpoint must use https://");
        }
        String withoutTrailing = trimmed;
        while (withoutTrailing.endsWith("/")) {
            withoutTrailing = withoutTrailing.substring(0, withoutTrailing.length() - 1);
        }
        int schemeEnd = withoutTrailing.indexOf("://") + 3;
        if (schemeEnd >= withoutTrailing.length()
                || withoutTrailing.substring(schemeEnd).contains(" ")) {
            throw new IllegalArgumentException("Invalid endpoint URL");
        }
        String lowerClean = withoutTrailing.toLowerCase(Locale.US);
        if (lowerClean.endsWith("/chat/completions") || lowerClean.endsWith("/completions")) {
            return withoutTrailing;
        }
        return withoutTrailing + "/chat/completions";
    }

    public static EffectiveConfig resolve(
            UserConfig user, String builtInKey, String builtInEndpoint, String builtInModel) {
        UserConfig safeUser = user == null ? UserConfig.defaults() : user;
        String provider = normalizeProvider(safeUser.provider);
        String safeBuiltInKey = safeTrim(builtInKey);
        String safeBuiltInEndpoint = safeTrim(builtInEndpoint);
        String safeBuiltInModel = safeTrim(builtInModel);

        String endpoint;
        if (PROVIDER_CUSTOM.equals(provider)) {
            endpoint =
                    safeUser.customEndpoint.isEmpty() ? "" : safeNormalize(safeUser.customEndpoint);
        } else if (PROVIDER_GLM.equals(provider)) {
            endpoint = safeBuiltInEndpoint.isEmpty() ? DEFAULT_GLM_ENDPOINT : safeBuiltInEndpoint;
        } else {
            endpoint = presetEndpoint(provider);
        }

        String model;
        if (!safeUser.model.isEmpty()) {
            model = safeUser.model;
        } else if (PROVIDER_GLM.equals(provider) && !safeBuiltInModel.isEmpty()) {
            model = safeBuiltInModel;
        } else {
            model = defaultModel(provider);
        }

        String key;
        boolean usingBuiltIn = false;
        if (!safeUser.apiKey.isEmpty()) {
            key = safeUser.apiKey;
        } else if (PROVIDER_GLM.equals(provider) && !safeBuiltInKey.isEmpty()) {
            key = safeBuiltInKey;
            usingBuiltIn = true;
        } else {
            key = "";
        }

        return new EffectiveConfig(provider, key, endpoint, model, usingBuiltIn);
    }

    public static String maskKey(String key) {
        String trimmed = safeTrim(key);
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.length() <= 6) {
            return "••••";
        }
        if (trimmed.length() <= 10) {
            return trimmed.substring(0, 2) + "••••" + trimmed.substring(trimmed.length() - 2);
        }
        return trimmed.substring(0, 3) + "••••" + trimmed.substring(trimmed.length() - 4);
    }

    public static UserConfig load(Context context) {
        if (context == null) {
            return UserConfig.defaults();
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return new UserConfig(
                prefs.getString(KEY_PROVIDER, PROVIDER_GLM),
                prefs.getString(KEY_API_KEY, ""),
                prefs.getString(KEY_MODEL, ""),
                prefs.getString(KEY_CUSTOM_ENDPOINT, ""));
    }

    public static EffectiveConfig resolveCurrent(Context context) {
        return resolve(
                load(context),
                BuildConfig.GLM_API_KEY,
                BuildConfig.GLM_ENDPOINT,
                BuildConfig.GLM_MODEL);
    }

    public static void save(Context context, UserConfig config) {
        if (context == null) {
            return;
        }
        UserConfig safe = config == null ? UserConfig.defaults() : config;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PROVIDER, safe.provider)
                .putString(KEY_API_KEY, safe.apiKey)
                .putString(KEY_MODEL, safe.model)
                .putString(KEY_CUSTOM_ENDPOINT, safe.customEndpoint)
                .apply();
    }

    public static void reset(Context context) {
        if (context == null) {
            return;
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static String safeNormalize(String raw) {
        try {
            return normalizeEndpoint(raw);
        } catch (IllegalArgumentException e) {
            return safeTrim(raw);
        }
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
