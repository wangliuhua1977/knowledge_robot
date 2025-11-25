package com.knowledge.robot.inspection;

public record SmartInspectionConfig(
        String folder,
        long intervalSeconds,
        String token,
        String uploadUrl,
        String completionUrl
) {
}
