package com.knowledge.robot.inspection;

public interface SmartInspectionLogger {
    void log(String message);

    default void historyChanged(java.nio.file.Path historyDir) {
        // 可选实现：在处理完成后刷新历史记录
    }
}
