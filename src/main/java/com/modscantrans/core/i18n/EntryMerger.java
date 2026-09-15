package com.modscantrans.core.i18n;

import com.modscantrans.core.EntrySource;
import com.modscantrans.core.TargetLanguage;
import com.modscantrans.core.TranslationEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 词条优先级合并器:把来自不同来源(CFPA / 家族术语库 / AI 缓存 / 实时 AI)的翻译条目,
 * 按<b>全局优先级</b>合并为一个统一映射,自动处理词条冲突。
 *
 * <p><b>全局硬性规则 1</b>:词条优先级为
 * <b>CFPA 社区人工汉化(最高,禁止 AI 覆盖) &gt; AI 本地缓存翻译 &gt; 实时 AI 机翻(兜底)</b>。
 *
 * <p><b>冲突处理</b>:同一 {@code (key, 目标语言)} 出现多个来源时,保留<b>最高优先级</b>的一条,
 * 低优先级条目<b>被丢弃</b>(不覆盖)。优先级靠 {@link EntrySource#comparePriority(EntrySource)}
 * 比较:CFPA(0) &lt; AI_CACHE(1) &lt; AI_LIVE(2),序号越小优先级越高。
 *
 * <p>合并策略:
 * <ol>
 *   <li>CFPA 条目无条件保留(最高优先级);同一 key 有 CFPA 时,其它来源全部丢弃;</li>
 *   <li>无 CFPA 的 key,从家族术语库 / 旁系参考库条目(AI_CACHE)与实时 AI(AI_LIVE)中
 *       取优先级最高者;同为 AI_CACHE 时取置信度更高者;</li>
 *   <li>同优先级同置信度:保留后加入者(后写覆盖,反映最新翻译)。</li>
 * </ol>
 *
 * <p>本类无状态、线程安全(仅局部变量)。
 */
public final class EntryMerger {
    private EntryMerger() {
    }

    /**
     * 合并多个来源的翻译条目列表,按全局优先级保留最佳条目。
     *
     * @param entryCollections 多个来源的条目列表(顺序无关,按优先级裁决)
     * @return 合并后的 {@code 键 → 译文} 映射(保留插入顺序)
     */
    public static Map<String, String> merge(List<Collection<TranslationEntry>> entryCollections) {
        // (key → 最佳条目)
        Map<String, TranslationEntry> best = new LinkedHashMap<>();
        for (Collection<TranslationEntry> col : entryCollections) {
            if (col == null) {
                continue;
            }
            for (TranslationEntry e : col) {
                if (e == null || e.key() == null || e.key().isBlank()) {
                    continue;
                }
                best.merge(e.key(), e, EntryMerger::pickBetter);
            }
        }
        // 扁平化为 键→译文
        Map<String, String> out = new LinkedHashMap<>(best.size());
        for (TranslationEntry e : best.values()) {
            out.put(e.key(), e.translatedText());
        }
        return out;
    }

    /**
     * 合并并保留来源信息,返回 {@code 键 → 最佳条目}(含来源 / 置信度)。
     *
     * @param entryCollections 多个来源的条目列表
     * @return {@code 键 → 最佳条目}
     */
    public static Map<String, TranslationEntry> mergeDetailed(List<Collection<TranslationEntry>> entryCollections) {
        Map<String, TranslationEntry> best = new LinkedHashMap<>();
        for (Collection<TranslationEntry> col : entryCollections) {
            if (col == null) {
                continue;
            }
            for (TranslationEntry e : col) {
                if (e == null || e.key() == null || e.key().isBlank()) {
                    continue;
                }
                best.merge(e.key(), e, EntryMerger::pickBetter);
            }
        }
        return best;
    }

    /**
     * 裁决两条同 key 条目,返回应保留者。
     *
     * <p>规则:
     * <ul>
     *   <li>优先比较 {@link EntrySource}:序号小者(更高优先级)胜;</li>
     *   <li>来源相同时,置信度高者胜;</li>
     *   <li>来源和置信度都相同:保留新条目(newE,后写覆盖)。</li>
     * </ul>
     *
     * @param oldE 已有条目
     * @param newE 新条目
     * @return 应保留的条目
     */
    static TranslationEntry pickBetter(TranslationEntry oldE, TranslationEntry newE) {
        int cmp = newE.source().comparePriority(oldE.source());
        if (cmp < 0) {
            return newE; // 新条目优先级更高
        }
        if (cmp > 0) {
            return oldE; // 旧条目优先级更高,保留旧
        }
        // 来源相同:比置信度(高者胜)
        if (newE.confidence() > oldE.confidence()) {
            return newE;
        }
        if (newE.confidence() < oldE.confidence()) {
            return oldE;
        }
        // 都相同:后写覆盖
        return newE;
    }

    /**
     * 过滤出指定来源的条目(用于分离各来源便于合并)。
     *
     * @param entries 条目集合
     * @param source  目标来源
     * @return 该来源的条目列表
     */
    public static List<TranslationEntry> filterBySource(Collection<TranslationEntry> entries, EntrySource source) {
        List<TranslationEntry> out = new ArrayList<>();
        if (entries == null) {
            return out;
        }
        for (TranslationEntry e : entries) {
            if (e != null && e.source() == source) {
                out.add(e);
            }
        }
        return out;
    }
}
