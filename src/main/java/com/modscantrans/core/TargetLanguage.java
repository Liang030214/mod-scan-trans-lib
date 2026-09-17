package com.modscantrans.core;

import java.util.Objects;

/**
 * 目标翻译语言。
 *
 * <p><b>多语种硬性规则</b>:目标翻译语言完全由用户在模组独立设置界面手动选择;
 * 程序<b>不读取 IP 地址,不根据地理位置自动切换语种</b>。默认 zh_CN 简体中文;
 * 用户可手动切换 pt_PT 葡萄牙语、th_TH 泰语等。
 *
 * <p>选定语种后,所有随启动器加载进游戏的模组文本统一翻译成目标语种;
 * 家族术语库、旁系参考库同步适配目标语种,保证术语统一。
 *
 * <p>本类不使用 enum,而是值对象 + 内置常量,以便后续支持任意自定义语言代码
 * (例如社区新增语种时无需改动枚举)。{@link #code()} 返回 MC 语言代码
 * (如 {@code zh_CN});{@link #toLangFileName()} 返回用于资源文件名的小写形式
 * (MC 约定语言 json 文件名全小写,如 {@code zh_cn.json})。
 */
public final class TargetLanguage implements Comparable<TargetLanguage> {
    /** 简体中文(默认目标语言)。 */
    public static final TargetLanguage ZH_CN = new TargetLanguage("zh_CN", "简体中文");
    /** 美国英语(模组自带原文语种常用)。 */
    public static final TargetLanguage EN_US = new TargetLanguage("en_US", "English (US)");
    /** 葡萄牙语(葡萄牙)。 */
    public static final TargetLanguage PT_PT = new TargetLanguage("pt_PT", "Português");
    /** 泰语。 */
    public static final TargetLanguage TH_TH = new TargetLanguage("th_TH", "ภาษาไทย");

    /** 默认目标语言:简体中文。 */
    public static final TargetLanguage DEFAULT = ZH_CN;

    /** 所有内置可选语言(GUI 下拉框用,顺序固定:简中→英文→葡语→泰语)。 */
    public static java.util.List<TargetLanguage> BUILTINS = java.util.List.of(ZH_CN, EN_US, PT_PT, TH_TH);

    private final String code;
    private final String displayName;

    private TargetLanguage(String code, String displayName) {
        this.code = Objects.requireNonNull(code, "语言代码不能为空");
        if (code.isBlank()) {
            throw new IllegalArgumentException("语言代码不能为空白");
        }
        this.displayName = Objects.requireNonNull(displayName, "显示名不能为空");
    }

    /**
     * 按语言代码构造目标语言(支持内置常量之外的任意代码,便于扩展)。
     *
     * @param code MC 语言代码,如 {@code zh_CN}、{@code pt_PT}
     * @return 对应的 TargetLanguage
     */
    public static TargetLanguage of(String code) {
        Objects.requireNonNull(code, "语言代码不能为空");
        // 复用内置常量,保持引用一致与相等性
        return switch (code) {
            case "zh_CN" -> ZH_CN;
            case "en_US" -> EN_US;
            case "pt_PT" -> PT_PT;
            case "th_TH" -> TH_TH;
            default -> new TargetLanguage(code, code);
        };
    }

    /** @return MC 语言代码,如 {@code zh_CN} */
    public String code() {
        return code;
    }

    /** @return 用于设置界面下拉框的显示名 */
    public String displayName() {
        return displayName;
    }

    /**
     * @return 用于资源文件名的小写形式(MC 约定语言 json 文件名全小写,如 {@code zh_cn})
     */
    public String toLangFileName() {
        return code.toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public int compareTo(TargetLanguage o) {
        return code.compareTo(o.code);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof TargetLanguage t && code.equals(t.code));
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return code;
    }
}
