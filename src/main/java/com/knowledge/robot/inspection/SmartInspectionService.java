package com.knowledge.robot.inspection;

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
        logger.log("开始处理图片：" + file.getName());
        long appId = generateAppId();
        String chatId = generateChatId();
        try {
            long refId = uploadFile(file, appId, chatId);
            logger.log("上传完成，返回ID=" + refId);
            callCompletion(refId, chatId);
            moveToHistory(file.toPath(), historyDir);
            logger.log("文件已移至历史目录。\n");
        } catch (Exception e) {
            logger.log("处理失败：" + e.getMessage());
        }
    }

    private long uploadFile(File file, long appId, String chatId) throws IOException {
        logger.log("调用上传接口，准备发送文件和鉴权信息");
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

        logger.log("已附带Authorization和随机appId/chatId，文件：" + file.getName());

        try (Response resp = httpClient.newCall(request).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            logger.log("上传响应状态：" + resp.code());
            if (!resp.isSuccessful()) {
                throw new IOException("上传失败，HTTP " + resp.code());
            }
            long refId = parseRefId(body);
            return refId;
        }
    }

    private long parseRefId(String body) throws IOException {
        var node = mapper.readTree(body);
        var images = node.path("resultObject").path("image");
        if (images.isArray() && images.size() > 0) {
            var idNode = images.get(0).get("id");
            if (idNode != null && !idNode.isNull()) {
                String idText = idNode.asText();
                try {
                    return Long.parseLong(idText);
                } catch (NumberFormatException ex) {
                    throw new IOException("上传响应ID格式错误: " + idText, ex);
                }
            }
        }
        throw new IOException("上传响应缺少图片ID");
    }

    private void callCompletion(long refId, String chatId) throws IOException {
        logger.log("调用处理接口，使用返回ID触发智能点检");
        var payloadNode = mapper.createObjectNode();
        payloadNode.put("chatId", chatId);
        payloadNode.put("stream", true);
        payloadNode.putArray("refs").add(refId);
        var messages = payloadNode.putArray("messages");
        messages.add(mapper.createObjectNode()
                .put("role", "user")
                .put("content", "智能点检"));
        String payload = payloadNode.toString();
        logger.log("处理请求已附带Authorization，参数已组装完成");

        RequestBody body = RequestBody.create(payload, MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(config.completionUrl())
                .addHeader("Authorization", config.token())
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response resp = httpClient.newCall(request).execute()) {
            logger.log("处理响应状态：" + resp.code());
            if (!resp.isSuccessful()) {
                throw new IOException("处理接口返回失败，HTTP " + resp.code());
            }
        }
    }

    private void moveToHistory(Path file, Path historyDir) throws IOException {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
        Path target = historyDir.resolve(timestamp + "_" + file.getFileName());
        Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
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
