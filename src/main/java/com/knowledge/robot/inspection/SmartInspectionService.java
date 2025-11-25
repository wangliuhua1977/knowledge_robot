package com.knowledge.robot.inspection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SmartInspectionService {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SmartInspectionLogger logger;
    private final OkHttpClient httpClient = buildClient();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "smart-inspection");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean processing = new AtomicBoolean(false);
    private SmartInspectionConfig config;

    public SmartInspectionService(SmartInspectionLogger logger) {
        this.logger = Objects.requireNonNull(logger);
    }

    public void start(SmartInspectionConfig cfg) {
        this.config = cfg;
        if (!running.compareAndSet(false, true)) {
            logger.log("智能点检已在运行中");
            return;
        }
        logger.log("启动智能点检任务，间隔 " + cfg.intervalSeconds() + " 秒，目录：" + cfg.folder());
        scheduler.scheduleWithFixedDelay(this::scanAndProcess, 0, cfg.intervalSeconds(), TimeUnit.SECONDS);
    }

    public void stop() {
        running.set(false);
        scheduler.shutdownNow();
        logger.log("智能点检已停止");
    }

    public boolean isRunning() {
        return running.get();
    }

    private void scanAndProcess() {
        if (!running.get()) {
            return;
        }
        if (!processing.compareAndSet(false, true)) {
            logger.log("上一轮处理尚未完成，跳过本轮");
            return;
        }
        try {
            Path folder = Path.of(config.folder());
            if (!Files.exists(folder)) {
                logger.log("目录不存在，自动创建：" + folder);
                Files.createDirectories(folder);
            }
            Path historyDir = folder.resolve("his");
            if (!Files.exists(historyDir)) {
                Files.createDirectories(historyDir);
            }
            List<Path> images = listImages(folder);
            if (images.isEmpty()) {
                logger.log("本轮扫描未发现图片文件，等待下次轮询。");
                return;
            }
            logger.log("发现图片数量：" + images.size());
            for (Path img : images) {
                if (!running.get()) {
                    break;
                }
                handleOneImage(img.toFile(), historyDir);
            }
        } catch (Exception ex) {
            logger.log("扫描处理异常：" + ex.getMessage());
        } finally {
            processing.set(false);
        }
    }

    private List<Path> listImages(Path folder) throws IOException {
        try (Stream<Path> stream = Files.list(folder)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                                || name.endsWith(".bmp") || name.endsWith(".gif");
                    })
                    .sorted(Comparator.comparing(Path::toString))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    private void handleOneImage(File file, Path historyDir) {
        logger.log("开始处理：" + file.getName());
        long appId = generateAppId();
        String chatId = generateChatId();
        try {
            long refId = uploadFile(file, appId, chatId);
            logger.log("上传成功，refId=" + refId);
            callCompletion(refId, chatId);
            moveToHistory(file.toPath(), historyDir);
            logger.log("文件已移至历史目录。\n");
        } catch (Exception e) {
            logger.log("处理失败：" + e.getMessage());
        }
    }

    private long uploadFile(File file, long appId, String chatId) throws IOException {
        logger.log("调用上传接口 -> " + config.uploadUrl());
        logger.log("使用 appId=" + appId + ", chatId=" + chatId);
        MultipartBody.Builder bodyBuilder = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("files", file.getName(), RequestBody.create(file, MediaType.parse("application/octet-stream")))
                .addFormDataPart("appId", String.valueOf(appId))
                .addFormDataPart("chatId", chatId);

        Request request = new Request.Builder()
                .url(config.uploadUrl())
                .addHeader("Authorization", config.token())
                .addHeader("User-Agent", "KnowledgeRobot")
                .post(bodyBuilder.build())
                .build();

        try (Response resp = httpClient.newCall(request).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            logResponseDetail("上传", resp, body);
            if (!resp.isSuccessful()) {
                throw new IOException("上传失败，HTTP " + resp.code());
            }
            Long refId = parseFirstId(body);
            if (refId != null) {
                return refId;
            }
            logger.log("未能解析ID，回退使用 appId");
            return appId;
        }
    }

    private void callCompletion(long refId, String chatId) throws IOException {
        logger.log("调用处理接口 -> " + config.completionUrl());
        var payloadNode = mapper.createObjectNode();
        payloadNode.put("chatId", chatId);
        payloadNode.put("stream", true);
        payloadNode.putArray("refs").add(refId);
        var messages = payloadNode.putArray("messages");
        messages.add(mapper.createObjectNode()
                .put("role", "user")
                .put("content", "智能点检"));
        String payload = payloadNode.toString();
        logger.log("提交报文：" + payload);

        RequestBody body = RequestBody.create(payload, MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(config.completionUrl())
                .addHeader("Authorization", config.token())
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response resp = httpClient.newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "";
            logResponseDetail("处理", resp, respBody);
            if (!resp.isSuccessful()) {
                throw new IOException("处理接口返回失败，HTTP " + resp.code());
            }
        }
    }

    private void logResponseDetail(String stage, Response resp, String body) {
        logger.log(stage + "响应状态：" + resp.code() + " " + resp.message());
        logger.log(stage + "响应JSON：" + body);
    }

    private void moveToHistory(Path file, Path historyDir) throws IOException {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
        Path target = historyDir.resolve(timestamp + "_" + file.getFileName());
        Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private Long parseFirstId(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(body);
            Long id = findId(root);
            if (id != null) {
                return id;
            }
            // 查找数字字符串
            String digits = body.replaceAll("\\D+", " ").trim();
            if (!digits.isEmpty()) {
                String[] parts = digits.split(" ");
                for (String p : parts) {
                    try {
                        return Long.parseLong(p);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (Exception e) {
            logger.log("解析上传响应时异常：" + e.getMessage());
        }
        return null;
    }

    private Long findId(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isNumber()) {
            return node.longValue();
        }
        if (node.isTextual()) {
            try {
                return Long.parseLong(node.asText());
            } catch (NumberFormatException ignored) {
            }
        }
        if (node.isObject()) {
            if (node.has("id")) {
                Long val = findId(node.get("id"));
                if (val != null) return val;
            }
            if (node.has("appId")) {
                Long val = findId(node.get("appId"));
                if (val != null) return val;
            }
            for (JsonNode child : node) {
                Long val = findId(child);
                if (val != null) return val;
            }
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                Long val = findId(child);
                if (val != null) return val;
            }
        }
        return null;
    }

    private long generateAppId() {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 19) {
            long value = Math.abs((long) (Math.random() * 10));
            sb.append(value);
        }
        return Long.parseLong(sb.toString());
    }

    private String generateChatId() {
        final String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 12) {
            int idx = (int) (Math.random() * chars.length());
            sb.append(chars.charAt(idx));
        }
        return sb.toString();
    }

    private OkHttpClient buildClient() {
        return new OkHttpClient.Builder()
                .sslSocketFactory(TrustAllSslContext.socketFactory(), TrustAllSslContext.trustManager())
                .hostnameVerifier((hostname, session) -> true)
                .callTimeout(java.time.Duration.ofSeconds(180))
                .readTimeout(java.time.Duration.ofSeconds(180))
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .writeTimeout(java.time.Duration.ofSeconds(60))
                .build();
    }
}
