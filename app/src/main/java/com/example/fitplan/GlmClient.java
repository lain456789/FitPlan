package com.example.fitplan;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Zero-dependency Java HTTP client for Zhipu BigModel (GLM) with proactive offline detection. */
final class GlmClient {
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final String SKILL_ASSET = "coach_skill.md";

    private GlmClient() {}

    static boolean isNetworkAvailable(Context context) {
        if (context == null) {
            return true;
        }
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return true;
        }
        Network network = manager.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities caps = manager.getNetworkCapabilities(network);
        if (caps == null) {
            return false;
        }
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
    }

    static String loadSkillMarkdown(Context context) {
        try (InputStream is = context.getAssets().open(SKILL_ASSET)) {
            return readAll(is);
        } catch (IOException e) {
            return "You are FitPlan AI Fitness Coach. Answer strictly using the user's provided "
                    + "training records and evidence-based training principles. Never fabricate "
                    + "data. Advise medical care if pain or discomfort is reported.";
        }
    }

    static GlmProtocol.GlmResult chat(
            Context context,
            String apiKey,
            String endpoint,
            String model,
            String skillMarkdown,
            String trainingContext,
            List<CoachModels.ChatMessage> history,
            String userMessage) {
        if (!isNetworkAvailable(context)) {
            return GlmProtocol.GlmResult.error("offline", "No active internet connection");
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return GlmProtocol.GlmResult.error("missing_key", "GLM_API_KEY is empty");
        }
        String targetUrl =
                endpoint == null || endpoint.trim().isEmpty()
                        ? "https://open.bigmodel.cn/api/paas/v4/chat/completions"
                        : endpoint.trim();
        String requestBody =
                GlmProtocol.buildRequestBody(
                        model, skillMarkdown, trainingContext, history, userMessage);

        HttpURLConnection conn = null;
        try {
            URL url = new URL(targetUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim());

            byte[] payload = requestBody.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(payload.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(payload);
            }

            int status = conn.getResponseCode();
            InputStream stream =
                    status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
            String body = stream == null ? "" : readAll(stream);
            return GlmProtocol.parseResponse(status, body);
        } catch (UnknownHostException | ConnectException | NoRouteToHostException e) {
            return GlmProtocol.GlmResult.error(
                    "offline",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } catch (IOException e) {
            String code = isNetworkAvailable(context) ? "network_error" : "offline";
            return GlmProtocol.GlmResult.error(
                    code, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readAll(InputStream input) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
        }
        return sb.toString();
    }
}
