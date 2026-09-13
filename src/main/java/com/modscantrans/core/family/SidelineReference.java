package com.modscantrans.core.family;

import com.modscantrans.core.TargetLanguage;
import com.modscantrans.core.TranslationEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 旁系参考库:为<b>旁系模组</b>(扫描后无法匹配内置 / 自定义家族的独立模组)提供
 * 翻译参考词条。
 *
 * <p><b>硬性规则</b>:旁系模组<b>永久启用旁系参考库,不受家族术语参考开关控制</b>。
 * 旁系参考库整合 MC 社区、模组 Wiki 公开多语言词条,内置基础词条支持离线使用。
 *
 * <p><b>语种同步</b>:同原文不同语种分开存储(按 {@link TargetLanguage} 适配),
 * 保证术语统一。每个 (key, 目标语言) 保留最高优先级一条。
 *
 * <p>线程安全:写入与查询均 synchronized 保护。
 */
public final class SidelineReference {
    private final Object lock = new Object();
    /** (key → (语言 → 条目))。 */
    private final Map<String, Map<TargetLanguage, TranslationEntry>> entries = new LinkedHashMap<>();

    /** 离线可用的内置基础词条(规则5:旁系参考基础词条离线可用)。 */
    private final Map<String, Map<TargetLanguage, String>> builtInTerms = new LinkedHashMap<>();

    public SidelineReference() {
        loadBuiltInTerms();
    }

    /**
     * 写入一条参考词条(不覆盖更高优先级条目)。
     *
     * @param entry 翻译条目
     */
    public void put(TranslationEntry entry) {
        Objects.requireNonNull(entry, "entry 不能为空");
        synchronized (lock) {
            entries.computeIfAbsent(entry.key(), k -> new LinkedHashMap<>())
                    .merge(entry.language(), entry, (oldE, newE) ->
                            newE.source().comparePriority(oldE.source()) < 0 ? newE : oldE);
        }
    }

    /** 批量写入。 */
    public void putAll(Collection<TranslationEntry> entryCollection) {
        if (entryCollection == null) {
            return;
        }
        for (TranslationEntry e : entryCollection) {
            put(e);
        }
    }

    /**
     * 查询某语言键在指定目标语言下的参考词条。
     *
     * <p>查询顺序:动态写入的词条 → 内置基础词条(离线兜底)。
     *
     * @param key      语言键
     * @param language 目标语言
     * @return 参考条目;无则 empty
     */
    public java.util.Optional<TranslationEntry> get(String key, TargetLanguage language) {
        synchronized (lock) {
            Map<TargetLanguage, TranslationEntry> m = entries.get(key);
            if (m != null) {
                TranslationEntry t = m.get(language);
                if (t != null) {
                    return java.util.Optional.of(t);
                }
            }
            // 离线兜底:内置基础词条
            Map<TargetLanguage, String> built = builtInTerms.get(key);
            if (built != null) {
                String text = built.get(language);
                if (text != null) {
                    // 内置词条视为低优先级 AI 缓存(离线可用,可被联网结果覆盖)
                    return java.util.Optional.of(new TranslationEntry(
                            key, "", text, language, com.modscantrans.core.EntrySource.AI_CACHE,
                            "", 0.5f));
                }
            }
            return java.util.Optional.empty();
        }
    }

    /** 导出指定目标语言的全部参考词条。 */
    public Map<String, String> export(TargetLanguage language) {
        synchronized (lock) {
            Map<String, String> out = new LinkedHashMap<>();
            // 先放内置(低优先级兜底)
            for (var e : builtInTerms.entrySet()) {
                String text = e.getValue().get(language);
                if (text != null) {
                    out.put(e.getKey(), text);
                }
            }
            // 再覆盖动态写入(高优先级)
            for (var e : entries.entrySet()) {
                TranslationEntry t = e.getValue().get(language);
                if (t != null) {
                    out.put(e.getKey(), t.translatedText());
                }
            }
            return Map.copyOf(out);
        }
    }

    /** @return 动态写入的不同 key 数量(不含内置) */
    public int size() {
        synchronized (lock) {
            return entries.size();
        }
    }

    /** @return 内置基础词条数量 */
    public int builtInCount() {
        synchronized (lock) {
            return builtInTerms.size();
        }
    }

    /** 清空动态写入部分(保留内置基础词条)。 */
    public void clearDynamic() {
        synchronized (lock) {
            entries.clear();
        }
    }

    /**
     * 加载离线可用的内置基础词条(MC 通用术语 + 常见模组 Wiki 词条)。
     * 仅放极少量示例,正式使用时由适配层 / 资源包补充。
     */
    private void loadBuiltInTerms() {
        // MC 通用基础术语示例(简体中文)
        putBuiltIn("itemGroup.minecraft", TargetLanguage.ZH_CN, "物品栏");
        putBuiltIn("item.minecraft.clock", TargetLanguage.ZH_CN, "钟");
        putBuiltIn("block.minecraft.furnace", TargetLanguage.ZH_CN, "熔炉");
        putBuiltIn("container.minecraft.inventory", TargetLanguage.ZH_CN, "物品栏");
        putBuiltIn("gui.minecraft.done", TargetLanguage.ZH_CN, "完成");
        // 葡萄牙语基础示例
        putBuiltIn("block.minecraft.furnace", TargetLanguage.PT_PT, "Fornalha");
        putBuiltIn("gui.minecraft.done", TargetLanguage.PT_PT, "Concluído");
        // 泰语基础示例
        putBuiltIn("block.minecraft.furnace", TargetLanguage.TH_TH, "เตาเผา");
        putBuiltIn("gui.minecraft.done", TargetLanguage.TH_TH, "เสร็จสิ้น");
    }

    private void putBuiltIn(String key, TargetLanguage lang, String text) {
        builtInTerms.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(lang, text);
    }

    /**
     * @return 动态写入的所有条目(扁平化,用于测试 / 调试)
     */
    public List<TranslationEntry> allEntries() {
        synchronized (lock) {
            List<TranslationEntry> out = new ArrayList<>();
            for (var m : entries.values()) {
                out.addAll(m.values());
            }
            return out;
        }
    }
}
