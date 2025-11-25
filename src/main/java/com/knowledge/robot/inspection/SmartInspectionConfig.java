package com.knowledge.robot.inspection;

public record SmartInspectionConfig(
        String folder,
        long intervalSeconds,
        String token,
        String chatId,
        String uploadUrl,
        String completionUrl
) {
}
