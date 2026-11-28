package com.example.fitplan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GlmProtocolTest {
    @Test
    public void buildsValidRequestWithSkillContextHistoryCapAndErrorFiltering() {
        List<CoachModels.ChatMessage> history = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            history.add(new CoachModels.ChatMessage("user", "Q" + i, false, i * 1000L));
            history.add(new CoachModels.ChatMessage("assistant", "A" + i, false, i * 1000L + 500));
        }
        history.add(new CoachModels.ChatMessage("assistant", "Offline error", true, 99_000L));

        String body =
                GlmProtocol.buildRequestBody(
                        "glm-4-flash",
                        "# Skill\nQuote \"test\" \\ slash",
                        "\n\n## CONTEXT\n{\"ok\":true}",
                        history,
                        "下周怎么练？");

        Map<String, Object> parsed = SimpleJson.asObject(SimpleJson.parse(body));
        assertNotNull(parsed);
        assertEquals("glm-4-flash", parsed.get("model"));
        assertEquals(Boolean.FALSE, parsed.get("stream"));

        List<Object> messages = SimpleJson.asList(parsed.get("messages"));
        assertEquals(22, messages.size());

        Map<String, Object> systemMsg = SimpleJson.asObject(messages.get(0));
        assertEquals("system", systemMsg.get("role"));
        String sysText = SimpleJson.asString(systemMsg.get("content"));
        assertTrue(sysText.contains("Quote \"test\" \\ slash"));
        assertTrue(sysText.contains("{\"ok\":true}"));

        Map<String, Object> firstKept = SimpleJson.asObject(messages.get(1));
        assertEquals("user", firstKept.get("role"));
        assertEquals("Q3", firstKept.get("content"));

        Map<String, Object> lastMsg = SimpleJson.asObject(messages.get(messages.size() - 1));
        assertEquals("user", lastMsg.get("role"));
        assertEquals("下周怎么练？", lastMsg.get("content"));
    }

    @Test
    public void parsesSuccessfulGlmCompletionResponse() {
        String json =
                "{\"id\":\"req_1\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\","
                        + "\"content\":\"### 建议\\n- 卧推先减载 **10%**\\n\"}}],"
                        + "\"usage\":{\"prompt_tokens\":500,\"completion_tokens\":80,\"total_tokens\":580}}";

        GlmProtocol.GlmResult result = GlmProtocol.parseResponse(200, json);
        assertTrue(result.success);
        assertEquals("### 建议\n- 卧推先减载 **10%**", result.content);
        assertEquals(580, result.totalTokens);
    }

    @Test
    public void classifiesApiAndHttpErrorsCleanly() {
        String authJson = "{\"error\":{\"code\":\"1002\",\"message\":\"Invalid API key\"}}";
        GlmProtocol.GlmResult auth = GlmProtocol.parseResponse(401, authJson);
        assertFalse(auth.success);
        assertEquals("auth_error", auth.errorCode);
        assertTrue(auth.errorDetail.contains("Invalid API key"));

        String rateJson = "{\"error\":{\"code\":\"1302\",\"message\":\"Rate limit exceeded\"}}";
        GlmProtocol.GlmResult rate = GlmProtocol.parseResponse(429, rateJson);
        assertFalse(rate.success);
        assertEquals("rate_limit", rate.errorCode);

        GlmProtocol.GlmResult bad = GlmProtocol.parseResponse(200, "not json at all");
        assertFalse(bad.success);
        assertEquals("malformed_json", bad.errorCode);

        GlmProtocol.GlmResult empty = GlmProtocol.parseResponse(503, "");
        assertFalse(empty.success);
        assertEquals("empty_response", empty.errorCode);
    }

    @Test
    public void buildsCompactPingRequestBody() {
        String body = GlmProtocol.buildPingRequestBody("deepseek-chat");
        Map<String, Object> parsed = SimpleJson.asObject(SimpleJson.parse(body));
        assertNotNull(parsed);
        assertEquals("deepseek-chat", parsed.get("model"));
        assertEquals(Boolean.FALSE, parsed.get("stream"));
        assertEquals(16, SimpleJson.asInt(parsed.get("max_tokens"), 0));
        List<Object> messages = SimpleJson.asList(parsed.get("messages"));
        assertEquals(1, messages.size());
    }
}
