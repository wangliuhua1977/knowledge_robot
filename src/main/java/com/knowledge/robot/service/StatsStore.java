package com.knowledge.robot.service;

import java.util.prefs.Preferences;

public final class StatsStore {
    private static final Preferences PREFS = Preferences.userRoot().node("com.knowledge.robot.service.StatsStore");
    private static final String KEY_RUNS = "auto_chat_runs";

    private StatsStore() {
    }

    public static synchronized void increment() {
        long now = PREFS.getLong(KEY_RUNS, 0L) + 1;
        PREFS.putLong(KEY_RUNS, now);
    }

    public static synchronized long read() {
        return PREFS.getLong(KEY_RUNS, 0L);
    }
}
