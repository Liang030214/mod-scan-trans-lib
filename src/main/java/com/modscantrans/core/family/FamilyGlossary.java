package com.modscantrans.core.family;

import com.modscantrans.core.TargetLanguage;
import com.modscantrans.core.TranslationEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 家族术语库:存储某一模组家族在<b>目标语言</b>下的术语词条。
 *
 * <p><b>语种同步</b>:家族术语库按 {@link TargetLanguage} 适配,同原文不同语种分开存储,
 * 保证术语统一。每个 (语言键, 目标语言) 组合保留<b>最高优先级</b>的一条
 * (CFPA &gt; AI缓存 &gt; 实时AI),低优先级不可覆盖高优先级。
 *
 * <p><b>跨家族互联</b>:同一翻译词条可同时存入两个关联家族术语库(如机械动力 ↔ 通用机械)。
 * 本类只负责存储,跨家族写入由 {@link GlossaryStore} 协调。
 *
 * <p>线程安全:写入与查询均 synchronized 保护同一把锁。
 */
public final class FamilyGlossary {
    private final String familyId;
    private final Object lock = new Object();
    /** (key → (语言 → 条目));同一 key 不同目标语言分开存储。 */
    private final Map<String, Map<TargetLanguage, TranslationEntry>> entries = new LinkedHashMap<>();

    public FamilyGlossary(String familyId) {
        this.familyId = Objects.requireNonNull(familyId, "familyId 不能为空").toLowerCase(Locale.ROOT);
    }

    /** @return 家族 ID */
    public String familyId() {
        return familyId;
    }

    /**
     * 写入一条术语词条(若已有更高优先级条目则跳过,不覆盖)。
     *
     * @param entry 翻译条目
     */
    public void put(TranslationEntry entry) {
        Objects.requireNonNull(entry, "entry 不能为空");
        synchronized (lock) {
            entries.computeIfAbsent(entry.key(), k -> new LinkedHashMap<>())
                    .merge(entry.language(), entry, (oldE, newE) -> {
                        // 新条目优先级更高才覆盖
                        return newE.source().comparePriority(oldE.source()) < 0 ? newE : oldE;
                    });
        }
    }

    /**
     * 批量写入术语词条。
     *
     * @param entryCollection 词条集合
     */
    public void putAll(Collection<TranslationEntry> entryCollection) {
        if (entryCollection == null) {
            return;
        }
        for (TranslationEntry e : entryCollection) {
            put(e);
        }
    }

    /**
     * 查询某语言键在指定目标语言下的最佳术语条目。
     *
     * @param key      语言键
     * @param language 目标语言
     * @return 最佳条目;无则 empty
     */
    public java.util.Optional<TranslationEntry> get(String key, TargetLanguage language) {
        synchronized (lock) {
            Map<TargetLanguage, TranslationEntry> m = entries.get(key);
            return m == null ? java.util.Optional.empty() : java.util.Optional.ofNullable(m.get(language));
        }
    }

    /**
     * 导出指定目标语言下的全部术语({@code 键 → 译文})。
     *
     * @param language 目标语言
     * @return 术语映射(不可变)
     */
    public Map<String, String> export(TargetLanguage language) {
        synchronized (lock) {
            Map<String, String> out = new LinkedHashMap<>();
            for (var e : entries.entrySet()) {
                TranslationEntry t = e.getValue().get(language);
                if (t != null) {
                    out.put(e.getKey(), t.translatedText());
                }
            }
            return Map.copyOf(out);
        }
    }

    /** @return 当前存储的不同 key 数量 */
    public int size() {
        synchronized (lock) {
            return entries.size();
        }
    }

    /** 清空。 */
    public void clear() {
        synchronized (lock) {
            entries.clear();
        }
    }

    /**
     * @return 本术语库中所有条目(扁平化,用于测试 / 调试)
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
