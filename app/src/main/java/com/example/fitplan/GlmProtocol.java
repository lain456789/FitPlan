package com.example.fitplan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure-Java Zhipu GLM OpenAI-compatible Chat Completions request builder and response parser. */
public final class GlmProtocol {
    public static final int MAX_HISTORY_TURNS = 10;
    public static final int MAX_HISTORY_MESSAGES = MAX_HISTORY_TURNS * 2;
    public static final double DEFAULT_TEMPERATURE = 0.4;
    public static final int DEFAULT_MAX_TOKENS = 2048;

    public static final class GlmResult {
        public final boolean success;
        public final String content;
        public final String errorCode;
        public final String errorDetail;
        public final int totalTokens;

        private GlmResult(
                boolean success,
                String content,
                String errorCode,
                String errorDetail,
                int totalTokens) {
            this.success = success;
            this.content = content;
            this.errorCode = errorCode;
            this.errorDetail = errorDetail;
            this.totalTokens = totalTokens;
        }

        public static GlmResult ok(String content, int totalTokens) {
            return new GlmResult(true, content, "", "", totalTokens);
        }

        public static GlmResult error(String errorCode, String errorDetail) {
            return new GlmResult(false, "", errorCode, errorDetail, 0);
        }
    }

    private GlmProtocol() {}

    public static String buildRequestBody(
            String model,
            String skillMarkdown,
            String trainingContext,
            List<CoachModels.ChatMessage> history,
            String userMessage) {
        String safeModel = model == null || model.trim().isEmpty() ? "glm-4-flash" : model.trim();
        String systemContent =
                (skillMarkdown == null ? "" : skillMarkdown.trim())
                        + (trainingContext == null ? "" : trainingContext);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", systemContent));

        List<CoachModels.ChatMessage> cleanHistory = new ArrayList<>();
        if (history != null) {
            for (CoachModels.ChatMessage msg : history) {
                if (msg == null || msg.isError || msg.content.trim().isEmpty()) {
                    continue;
                }
                if ("user".equals(msg.role) || "assistant".equals(msg.role)) {
                    cleanHistory.add(msg);
                }
            }
        }
        int start = Math.max(0, cleanHistory.size() - MAX_HISTORY_MESSAGES);
        for (int i = start; i < cleanHistory.size(); i++) {
            CoachModels.ChatMessage msg = cleanHistory.get(i);
            messages.add(message(msg.role, msg.content));
        }

        messages.add(message("user", userMessage == null ? "" : userMessage.trim()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", safeModel);
        body.put("messages", messages);
        body.put("stream", false);
        body.put("temperature", DEFAULT_TEMPERATURE);
        body.put("max_tokens", DEFAULT_MAX_TOKENS);
        return SimpleJson.stringify(body);
    }

    public static String buildPingRequestBody(String model) {
        String safeModel = model == null || model.trim().isEmpty() ? "glm-4-flash" : model.trim();
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("user", "Reply OK."));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", safeModel);
        body.put("messages", messages);
        body.put("stream", false);
        body.put("max_tokens", 16);
        return SimpleJson.stringify(body);
    }

    public static GlmResult parseResponse(int httpCode, String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return GlmResult.error("empty_response", "HTTP " + httpCode);
        }

        Map<String, Object> root;
        try {
            root = SimpleJson.asObject(SimpleJson.parse(responseBody));
        } catch (IllegalArgumentException e) {
            if (httpCode >= 400) {
                return GlmResult.error(classifyHttpError(httpCode), "HTTP " + httpCode);
            }
            return GlmResult.error("malformed_json", e.getMessage());
        }
        if (root == null) {
            return GlmResult.error("malformed_json", "Root JSON is not an object");
        }

        Map<String, Object> errorObj = SimpleJson.asObject(root.get("error"));
        if (errorObj != null) {
            String apiCode = SimpleJson.asString(errorObj.get("code"));
            String apiMsg = SimpleJson.asString(errorObj.get("message"));
            String detail =
                    (apiCode != null ? "[" + apiCode + "] " : "")
                            + (apiMsg != null ? apiMsg : "HTTP " + httpCode);
            return GlmResult.error(classifyHttpError(httpCode), detail.trim());
        }

        if (httpCode < 200 || httpCode >= 300) {
            return GlmResult.error(classifyHttpError(httpCode), "HTTP " + httpCode);
        }

        List<Object> choices = SimpleJson.asList(root.get("choices"));
        if (choices == null || choices.isEmpty()) {
            return GlmResult.error("empty_choices", "No choices returned");
        }
        Map<String, Object> firstChoice = SimpleJson.asObject(choices.get(0));
        if (firstChoice == null) {
            return GlmResult.error("empty_choices", "Invalid choice object");
        }
        Map<String, Object> message = SimpleJson.asObject(firstChoice.get("message"));
        if (message == null) {
            return GlmResult.error("empty_choices", "Missing message in choice");
        }

        String content = SimpleJson.asString(message.get("content"));
        if (content == null || content.trim().isEmpty()) {
            content = SimpleJson.asString(message.get("reasoning_content"));
        }
        if (content == null || content.trim().isEmpty()) {
            return GlmResult.error("empty_content", "Assistant reply was empty");
        }

        int totalTokens = 0;
        Map<String, Object> usage = SimpleJson.asObject(root.get("usage"));
        if (usage != null) {
            totalTokens = SimpleJson.asInt(usage.get("total_tokens"), 0);
        }
        return GlmResult.ok(content.trim(), totalTokens);
    }

    private static String classifyHttpError(int httpCode) {
        if (httpCode == 401 || httpCode == 403) {
            return "auth_error";
        }
        if (httpCode == 429) {
            return "rate_limit";
        }
        if (httpCode >= 500) {
            return "server_error";
        }
        return "api_error";
    }

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", role);
        map.put("content", content);
        return map;
    }
}
