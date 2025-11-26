package com.knowledge.robot.ui;

import java.awt.Color;

/**
 * 主题配色方案。
 */
public enum ThemePalette {
    DEFAULT("初始方案",
            new Color(245, 245, 245),
            Color.WHITE,
            new Color(60, 60, 60),
            new Color(45, 137, 239),
            Color.WHITE),
    CLASSIC("沉稳传统的配色方案",
            new Color(236, 229, 216),
            new Color(248, 244, 236),
            new Color(60, 52, 41),
            new Color(142, 116, 69),
            Color.WHITE),
    FUTURISTIC("最流行的极具AI科技感配色的方案",
            new Color(11, 17, 31),
            new Color(18, 25, 45),
            new Color(220, 235, 255),
            new Color(91, 229, 184),
            new Color(7, 15, 26)),
    TELECOM("现代通讯行业配色方案",
            new Color(230, 242, 255),
            new Color(244, 249, 255),
            new Color(33, 61, 101),
            new Color(0, 145, 234),
            Color.WHITE);

    private final String displayName;
    private final Color background;
    private final Color panel;
    private final Color text;
    private final Color accent;
    private final Color accentText;

    ThemePalette(String displayName, Color background, Color panel, Color text, Color accent, Color accentText) {
        this.displayName = displayName;
        this.background = background;
        this.panel = panel;
        this.text = text;
        this.accent = accent;
        this.accentText = accentText;
    }

    public String displayName() {
        return displayName;
    }

    public Color background() {
        return background;
    }

    public Color panel() {
        return panel;
    }

    public Color text() {
        return text;
    }

    public Color accent() {
        return accent;
    }

    public Color accentText() {
        return accentText;
    }

    public static ThemePalette fromDisplayName(String name) {
        for (ThemePalette palette : values()) {
            if (palette.displayName.equals(name)) {
                return palette;
            }
        }
        return DEFAULT;
    }
}
