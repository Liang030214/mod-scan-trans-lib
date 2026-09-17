package com.modscantrans.neoforge;

import com.modscantrans.core.TransLibLogger;
import org.slf4j.Logger;

/**
 * NeoForge 日志桥接:把 core 层的 {@link TransLibLogger} 接到 NeoForge 的 slf4j {@link Logger}。
 *
 * <p>core 层不依赖 slf4j,本类在适配层把 core 的日志调用转发给 MC 的日志系统,
 * 让所有 core 日志统一出现在 MC 的日志输出里(而不是 JDK java.util.logging 的独立控制台)。
 */
public final class NeoForgeLoggerBridge implements TransLibLogger {
    private final Logger slf4j;

    public NeoForgeLoggerBridge(Logger slf4j) {
        this.slf4j = java.util.Objects.requireNonNull(slf4j, "slf4j Logger 不能为空");
    }

    @Override
    public void info(String message) {
        slf4j.info(message);
    }

    @Override
    public void warn(String message) {
        slf4j.warn(message);
    }

    @Override
    public void warn(String message, Throwable t) {
        slf4j.warn(message, t);
    }

    @Override
    public void error(String message, Throwable t) {
        slf4j.error(message, t);
    }
}
