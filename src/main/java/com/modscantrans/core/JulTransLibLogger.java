package com.modscantrans.core;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 基于 JDK {@code java.util.logging} 的默认日志器实现(零外部依赖)。
 *
 * <p>真正只依赖 JDK,无需 slf4j/Log4j,适合 core 层与测试环境。
 * 适配层可替换为桥接到 MC 日志的实现。
 */
final class JulTransLibLogger implements TransLibLogger {
    private final Logger jul;

    JulTransLibLogger(String name) {
        this.jul = Logger.getLogger("mod_scan_trans_lib." + name);
    }

    @Override
    public void info(String message) {
        jul.log(Level.INFO, message);
    }

    @Override
    public void warn(String message) {
        jul.log(Level.WARNING, message);
    }

    @Override
    public void warn(String message, Throwable t) {
        jul.log(Level.WARNING, message, t);
    }

    @Override
    public void error(String message, Throwable t) {
        jul.log(Level.SEVERE, message, t);
    }
}
