package com.knowledge.robot.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

public class StatsStore {
    private static final AtomicLong COUNT = new AtomicLong(load());
    private static final Path DIR = Path.of(System.getProperty("user.home"), ".knowledge_robot");
    private static final Path FILE = DIR.resolve("stats.txt");

    private static long load() {
        try {
            Path dir = Path.of(System.getProperty("user.home"), ".knowledge_robot");
            Path file = dir.resolve("stats.txt");
            if (Files.exists(file)) {
                String s = Files.readString(file, StandardCharsets.UTF_8).trim();
                return Long.parseLong(s);
            }
        } catch (Exception ignore) {}
        return 0;
    }

    public static long read() {
        return COUNT.get();
    }

    public static synchronized void increment() {
        long v = COUNT.incrementAndGet();
        try {
            if (!Files.exists(DIR)) Files.createDirectories(DIR);
            Files.writeString(FILE, Long.toString(v), StandardCharsets.UTF_8);
        } catch (IOException ignore) {}
    }
}
