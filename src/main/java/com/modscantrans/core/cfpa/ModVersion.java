package com.modscantrans.core.cfpa;

import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Minecraft 版本号(语义化比较)。
 *
 * <p>用于对 CFPA 已知版本目录降序排序,以便生成 fallback 回退链
 * (新版本未命中时回退到更老的版本)。
 *
 * <p>比较规则:按 {@code .} 分段,每段按数字比较;缺位补 0。
 * 例如 {@code 1.21 > 1.20 > 1.18 > 1.16 > 1.12.2}。
 *
 * <p>本类是不可变值对象,缓存了分段数组以加速比较。
 */
public final class ModVersion implements Comparable<ModVersion> {
    private static final Pattern NUMERIC = Pattern.compile("\\d+(?:\\.\\d+)*");

    private final String raw;
    private final int[] segments;

    private ModVersion(String raw) {
        this.raw = Objects.requireNonNull(raw, "版本不能为空").trim().toLowerCase(Locale.ROOT);
        if (this.raw.isEmpty() || !NUMERIC.matcher(this.raw).matches()) {
            // 非数字版本(快照等)退化为单段 0,排序靠后
            this.segments = new int[]{0};
            return;
        }
        String[] parts = this.raw.split("\\.");
        int[] arr = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            arr[i] = Integer.parseInt(parts[i]);
        }
        this.segments = arr;
    }

    /** @return 规范化后的版本字符串 */
    public String value() {
        return raw;
    }

    /**
     * 判断本版本是否为另一个完整版本的"前缀版本"。
     * <p>例如 {@code 1.21} 是 {@code 1.21.1} 的前缀版本(CFPA 用主次版本 {@code 1.21}
     * 覆盖所有 {@code 1.21.x} 补丁版);{@code 1.12.2} 精确匹配自身。
     *
     * @param full 完整版本(如 {@code 1.21.1})
     * @return 本版本是 full 的前缀版本时 true
     */
    public boolean isPrefixOf(String full) {
        if (full == null) {
            return false;
        }
        String f = full.trim().toLowerCase(Locale.ROOT);
        if (raw.equals(f)) {
            return true;
        }
        // 1.21 是 1.21.1 的前缀版本 <=> 1.21.1 去掉末尾的 .1 等于 1.21 <=> 1.21.1 startWith "1.21."
        return f.startsWith(raw + ".");
    }

    @Override
    public int compareTo(ModVersion o) {
        int n = Math.max(segments.length, o.segments.length);
        for (int i = 0; i < n; i++) {
            int a = i < segments.length ? segments[i] : 0;
            int b = i < o.segments.length ? o.segments[i] : 0;
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ModVersion m && raw.equals(m.raw));
    }

    @Override
    public int hashCode() {
        return raw.hashCode();
    }

    @Override
    public String toString() {
        return raw;
    }

    /** 解析版本字符串。 */
    public static ModVersion of(String version) {
        return new ModVersion(version);
    }

    /** 降序比较器(新版本在前)。 */
    public static Comparator<ModVersion> descending() {
        return (a, b) -> b.compareTo(a);
    }
}
