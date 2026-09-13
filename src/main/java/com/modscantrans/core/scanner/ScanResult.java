package com.modscantrans.core.scanner;

import com.modscantrans.core.ModInfo;
import java.util.Map;
import java.util.Objects;

/**
 * 单个模组 Jar 的扫描结果。
 *
 * @param modInfo        扫描到的模组档案(modid 等,modid 统一小写)
 * @param sourceEntries  原文词条({@code 键 → 原文文本},通常取自 {@code en_us.json},
 *                       供后续翻译成目标语言用)
 * @param languages       该 jar 内提取到的所有语言文件;为不可变列表,可能为空
 */
public record ScanResult(
        ModInfo modInfo,
        Map<String, String> sourceEntries,
        java.util.List<LangFile> languages) {

    public ScanResult {
        Objects.requireNonNull(modInfo, "modInfo 不能为空");
        sourceEntries = sourceEntries == null ? Map.of() : Map.copyOf(sourceEntries);
        languages = languages == null ? java.util.List.of() : java.util.List.copyOf(languages);
    }

    /**
     * 一个语言文件的信息。
     *
     * @param namespace  资源命名空间,即 modid(小写),对应 {@code assets/<namespace>/lang/...}
     * @param langCode   语言代码(小写文件名去 {@code .json},如 {@code en_us})
     * @param entries     该文件的 {@code 键 → 文本} 映射
     */
    public record LangFile(
            String namespace,
            String langCode,
            Map<String, String> entries) {

        public LangFile {
            Objects.requireNonNull(namespace, "namespace 不能为空");
            if (namespace.isBlank()) {
                throw new IllegalArgumentException("namespace 不能为空白");
            }
            namespace = namespace.toLowerCase(java.util.Locale.ROOT);
            Objects.requireNonNull(langCode, "langCode 不能为空");
            if (langCode.isBlank()) {
                throw new IllegalArgumentException("langCode 不能为空白");
            }
            langCode = langCode.toLowerCase(java.util.Locale.ROOT);
            entries = entries == null ? Map.of() : Map.copyOf(entries);
        }
    }
}
