package com.modscantrans.core;

/**
 * 模组扫描翻译支持库的日志接口(加载器无关)。
 *
 * <p>core 层各模块统一使用本接口记录日志,<b>不直接依赖 slf4j/Log4j</b>,
 * 以保证 core 层纯 Java、加载器无关、可被 NeoForge / Forge / Fabric 复用。
 * 适配层可注入桥接到 MC 日志的实现(例如把 {@link #info} 接到 slf4j 的 Logger)。
 *
 * <p>未注入实现时,使用 {@link #getDefault(String)} 返回的 JDK {@code java.util.logging}
 * 默认实现{@link JulTransLibLogger}(JDK 自带,真正零外部依赖)。
 *
 * <p>调用方传入的 {@code message} 已是最终字符串(自行拼接参数),接口不做格式化,
 * 以避免引入格式化方言差异。
 */
public interface TransLibLogger {
    /** 记录信息级日志。 */
    void info(String message);

    /** 记录警告级日志。 */
    void warn(String message);

    /** 记录警告级日志并附带异常。 */
    void warn(String message, Throwable t);

    /** 记录错误级日志并附带异常。 */
    void error(String message, Throwable t);

    /**
     * 返回一个使用 JDK {@code java.util.logging} 的默认日志器(零外部依赖)。
     *
     * @param name 日志器名称,通常为模块名(如 {@code "ModScanner"})
     * @return JDK 日志器实现
     */
    static TransLibLogger getDefault(String name) {
        return new JulTransLibLogger(name);
    }
}
