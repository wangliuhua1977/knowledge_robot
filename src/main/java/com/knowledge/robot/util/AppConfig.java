package com.knowledge.robot.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 应用配置与持久化（~/.knowledge_robot.properties）
 * - 接口 URL/Token/refs/agentlink
 * - 字体：标题/回复的字体族与字号，可读写保存
 */
public final class AppConfig {

    private static final Properties P = new Properties();
    private static final Path FILE = Paths.get(System.getProperty("user.home"), ".knowledge_robot.properties");

    static {
        // 默认值
        P.setProperty("url", "https://openai.sc.ctc.com:8898/whaleagent/knowledgeService/api/v1/chat/completions");
        P.setProperty("token", "Bearer WhaleDI-Agent-6ade2321ada01f69fa7a465135ce65a02262408d006e25236788c7c08b86be20");
        P.setProperty("stream", "true");
        P.setProperty("refs", "23,24,35");
        P.setProperty("agentlink", "{\"key1\":\"value1\",\"key2\":\"value2\"}");

        // 字体默认
        P.setProperty("title.font.family", "Microsoft YaHei");
        P.setProperty("title.font.size", "18");
        P.setProperty("reply.font.family", "Microsoft YaHei");
        P.setProperty("reply.font.size", "14");

        // 加载本地覆盖
        if (Files.exists(FILE)) {
            try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                Properties loaded = new Properties();
                loaded.load(r);
                for (String k : loaded.stringPropertyNames()) {
                    P.setProperty(k, loaded.getProperty(k));
                }
            } catch (IOException ignore) {}
        } else {
            save(); // 首次写入默认
        }
    }

    private AppConfig() {}

    private static synchronized void save() {
        try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            P.store(w, "knowledge_robot config");
        } catch (IOException ignore) {}
    }

    // ====== 基础配置 ======

    public static String url() { return P.getProperty("url"); }

    public static String token() { return P.getProperty("token"); }

    // 保留接口，但服务端强制使用流式
    public static boolean stream() {
        return Boolean.parseBoolean(P.getProperty("stream", "true"));
    }

    public static List<Integer> refs() {
        String s = P.getProperty("refs", "");
        if (s.isBlank()) return Collections.emptyList();
        return Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .map(x -> {
                    try { return Integer.parseInt(x); } catch (Exception e) { return null; }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static String agentLink() { return P.getProperty("agentlink", "{}"); }

    // ====== 字体配置（持久化） ======

    public static String titleFontFamily() { return P.getProperty("title.font.family", "Microsoft YaHei"); }

    public static int titleFontSize() {
        try { return Integer.parseInt(P.getProperty("title.font.size", "18")); }
        catch (Exception e) { return 18; }
    }

    public static String replyFontFamily() { return P.getProperty("reply.font.family", "Microsoft YaHei"); }

    public static int replyFontSize() {
        try { return Integer.parseInt(P.getProperty("reply.font.size", "14")); }
        catch (Exception e) { return 14; }
    }

    public static synchronized void setTitleFont(String family, int size) {
        if (family != null && !family.isBlank()) P.setProperty("title.font.family", family);
        P.setProperty("title.font.size", String.valueOf(Math.max(8, size)));
        save();
    }

    public static synchronized void setReplyFont(String family, int size) {
        if (family != null && !family.isBlank()) P.setProperty("reply.font.family", family);
        P.setProperty("reply.font.size", String.valueOf(Math.max(8, size)));
        save();
    }
}
