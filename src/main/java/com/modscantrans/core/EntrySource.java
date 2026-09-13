package com.modscantrans.core;

/**
 * 翻译词条的来源,同时决定<b>全局优先级</b>。
 *
 * <p>词条全局优先级(不可修改的硬性规则):
 * <pre>
 *   CFPA 社区人工汉化(最高,禁止 AI 覆盖)
 *        &gt; AI 本地缓存翻译
 *        &gt; 实时 AI 机翻(兜底)
 * </pre>
 *
 * <p>枚举序号({@link #ordinal()})即优先级序,序号越小优先级越高。
 * 合并器据此取舍:{@link #CFPA} 永远胜出,{@link #AI_CACHE} 胜出 {@link #AI_LIVE}。
 * CFPA 词条禁止被 AI 翻译覆盖(即 CFPA 命中后,该 key 不再调用 AI)。
 */
public enum EntrySource {
    /** CFPA 社区人工汉化词条,优先级最高,禁止 AI 覆盖。 */
    CFPA,

    /** AI 本地缓存翻译,优先级中。 */
    AI_CACHE,

    /** 实时 AI 机翻,优先级最低(兜底)。 */
    AI_LIVE;

    /**
     * 比较两个来源的优先级。
     *
     * @param other 另一个来源
     * @return 负数表示本来源优先级更高,正数表示更低,0 表示同级
     */
    public int comparePriority(EntrySource other) {
        return Integer.compare(this.ordinal(), other.ordinal());
    }

    /** @return 本来源是否为 CFPA(最高优先级,不可覆盖) */
    public boolean isCfpa() {
        return this == CFPA;
    }

    /** @return 本来源是否为实时 AI 机翻(兜底,优先级最低) */
    public boolean isAiLive() {
        return this == AI_LIVE;
    }
}
