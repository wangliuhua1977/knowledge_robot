package com.knowledge.robot.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.robot.model.ChatRequest;
import com.knowledge.robot.model.ChatResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class ChatClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ChatClient(ObjectMapper objectMapper) {
        this(objectMapper, false);
    }

    public ChatClient(ObjectMapper objectMapper, boolean trustAllCertificates) {
        this.httpClient = buildHttpClient(trustAllCertificates);
        this.objectMapper = objectMapper;
    }

    private HttpClient buildHttpClient(boolean trustAllCertificates) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20));
        if (trustAllCertificates) {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }}, new SecureRandom());
                builder = builder.sslContext(sslContext);
                SSLParameters sslParameters = new SSLParameters();
                sslParameters.setEndpointIdentificationAlgorithm(null);
                builder = builder.sslParameters(sslParameters);
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new IllegalStateException("无法初始化SSL上下文", e);
            }
        }
        return builder.build();
    }

    public ChatResponse sendChat(String endpoint,
                                 String token,
                                 ChatRequest request,
                                 Consumer<String> onEvent) throws IOException, InterruptedException {
        String payload = objectMapper.writeValueAsString(request);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<java.io.InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        int statusCode = response.statusCode();
        List<String> events = new ArrayList<>();
        StringBuilder rawBuilder = new StringBuilder();
        StringBuilder contentBuilder = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                events.add(line);
                rawBuilder.append(line).append('\n');
                if (onEvent != null) {
                    onEvent.accept(line);
                }
                parseLine(line, contentBuilder);
            }
        }

        if (events.isEmpty() && rawBuilder.length() == 0) {
            rawBuilder.append("状态码: ").append(statusCode);
        }

        return ChatResponse.builder()
                .statusCode(statusCode)
                .events(events)
                .assistantMessage(contentBuilder.length() == 0 ? null : contentBuilder.toString())
                .rawBody(rawBuilder.toString())
                .build();
    }

    private void parseLine(String line, StringBuilder contentBuilder) {
        String payload = line;
        if (line.startsWith("data:")) {
            payload = line.substring(5).trim();
        }
        if ("[DONE]".equalsIgnoreCase(payload)) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            JsonNode choices = node.get("choices");
            if (choices != null && choices.isArray()) {
                for (JsonNode choice : choices) {
                    JsonNode delta = choice.get("delta");
                    if (delta != null) {
                        JsonNode content = delta.get("content");
                        if (content != null && !content.isNull()) {
                            contentBuilder.append(content.asText());
                        }
                    }
                    JsonNode message = choice.get("message");
                    if (message != null) {
                        JsonNode content = message.get("content");
                        if (content != null && !content.isNull()) {
                            contentBuilder.append(content.asText());
                        }
                    }
                }
            }
        } catch (JsonProcessingException ignored) {
            // 非JSON内容，忽略解析
        }
    }

    public static Map<String, String> parseAgentLink(ObjectMapper objectMapper, String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            return objectMapper.convertValue(node, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
