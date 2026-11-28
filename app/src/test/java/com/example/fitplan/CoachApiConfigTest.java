package com.example.fitplan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class CoachApiConfigTest {
    @Test
    public void defaultGlmFallsBackToBuiltInConfig() {
        CoachApiConfig.EffectiveConfig cfg =
                CoachApiConfig.resolve(
                        CoachApiConfig.UserConfig.defaults(),
                        "built-in-key-1234",
                        "https://open.bigmodel.cn/api/paas/v4/chat/completions",
                        "glm-4-flash");
        assertEquals(CoachApiConfig.PROVIDER_GLM, cfg.provider);
        assertEquals("built-in-key-1234", cfg.apiKey);
        assertEquals("https://open.bigmodel.cn/api/paas/v4/chat/completions", cfg.endpoint);
        assertEquals("glm-4-flash", cfg.model);
        assertTrue(cfg.usingBuiltInKey);
        assertTrue(cfg.hasKey());
    }

    @Test
    public void userKeyOverridesBuiltInOnGlmAndSelectsPresetProviders() {
        CoachApiConfig.EffectiveConfig glmOverride =
                CoachApiConfig.resolve(
                        new CoachApiConfig.UserConfig("glm", "user-glm-key", "glm-4-plus", ""),
                        "built-in-key",
                        CoachApiConfig.DEFAULT_GLM_ENDPOINT,
                        "glm-4-flash");
        assertEquals("user-glm-key", glmOverride.apiKey);
        assertEquals("glm-4-plus", glmOverride.model);
        assertFalse(glmOverride.usingBuiltInKey);

        CoachApiConfig.EffectiveConfig deepseek =
                CoachApiConfig.resolve(
                        new CoachApiConfig.UserConfig("deepseek", "sk-ds-9999", "", ""),
                        "built-in-key",
                        CoachApiConfig.DEFAULT_GLM_ENDPOINT,
                        "glm-4-flash");
        assertEquals(CoachApiConfig.PROVIDER_DEEPSEEK, deepseek.provider);
        assertEquals("sk-ds-9999", deepseek.apiKey);
        assertEquals(CoachApiConfig.DEEPSEEK_ENDPOINT, deepseek.endpoint);
        assertEquals("deepseek-chat", deepseek.model);
        assertFalse(deepseek.usingBuiltInKey);

        CoachApiConfig.EffectiveConfig qwenNoKey =
                CoachApiConfig.resolve(
                        new CoachApiConfig.UserConfig("qwen", "", "", ""),
                        "built-in-key",
                        CoachApiConfig.DEFAULT_GLM_ENDPOINT,
                        "glm-4-flash");
        assertEquals(CoachApiConfig.QWEN_ENDPOINT, qwenNoKey.endpoint);
        assertEquals("qwen-plus", qwenNoKey.model);
        assertFalse(qwenNoKey.hasKey());
        assertFalse(qwenNoKey.usingBuiltInKey);
    }

    @Test
    public void normalizesCustomEndpointsAndRejectsInsecureUrls() {
        assertEquals(
                "https://api.example.com/v1/chat/completions",
                CoachApiConfig.normalizeEndpoint("https://api.example.com/v1/"));
        assertEquals(
                "https://api.example.com/v1/chat/completions",
                CoachApiConfig.normalizeEndpoint("https://api.example.com/v1/chat/completions"));
        assertEquals(
                "http://10.0.2.2:11434/v1/chat/completions",
                CoachApiConfig.normalizeEndpoint("http://10.0.2.2:11434/v1"));

        try {
            CoachApiConfig.normalizeEndpoint("http://example.com/v1");
            fail("Expected non-https remote endpoint to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("https"));
        }
    }

    @Test
    public void masksApiKeysSafely() {
        assertEquals("", CoachApiConfig.maskKey(""));
        assertEquals("••••", CoachApiConfig.maskKey("12345"));
        assertEquals("sk••••89", CoachApiConfig.maskKey("sk12345689"));
        String masked = CoachApiConfig.maskKey("sk-live-secret-token-a9f2");
        assertEquals("sk-••••a9f2", masked);
        assertFalse(masked.contains("secret"));
    }
}
