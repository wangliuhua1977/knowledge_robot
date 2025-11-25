package com.knowledge.robot.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Loads shared API settings from {@code app.properties} so that different modules
 * (auto learning, smart inspection, etc.) reuse the same endpoints and Authorization header.
 */
public final class AppSettings {
    private static final String RESOURCE_PATH = "/app.properties";
    private static final String DEFAULT_UPLOAD_URL = "https://openai.sc.ctc.com:8898/whaleagent/knowledgeService/core/chat/upload-files";
    private static final String DEFAULT_COMPLETION_URL = "https://openai.sc.ctc.com:8898/whaleagent/knowledgeService/api/v1/chat/completions";

    private static final AppSettings INSTANCE = new AppSettings();

    private final Properties props = new Properties();

    private AppSettings() {
        try (InputStream in = AppSettings.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in != null) {
                props.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // Fallback to defaults silently; caller can still read default values.
        }
    }

    public static AppSettings get() {
        return INSTANCE;
    }

    public String apiToken() {
        return props.getProperty("api.token", "").trim();
    }

    public String uploadUrl() {
        return props.getProperty("inspection.uploadUrl", DEFAULT_UPLOAD_URL).trim();
    }

    public String completionUrl() {
        String completion = props.getProperty("inspection.completionUrl");
        if (completion != null && !completion.isBlank()) {
            return completion.trim();
        }
        return props.getProperty("api.url", DEFAULT_COMPLETION_URL).trim();
    }
}
