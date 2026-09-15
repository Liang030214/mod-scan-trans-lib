package com.modscantrans.core.i18n;

import com.modscantrans.core.EntrySource;
import com.modscantrans.core.TargetLanguage;
import com.modscantrans.core.TransLibConfig;
import com.modscantrans.core.TransLibLogger;
import com.modscantrans.core.TranslationEntry;
import com.modscantrans.core.ai.AiService;
import com.modscantrans.core.cfpa.CfpaService;
import com.modscantrans.core.family.GlossaryStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 顶层翻译服务:统筹 CFPA / 家族术语库 / AI 缓存 / 实时 AI,对外输出统一翻译。
 *
 * <p><b>全局优先级</b>(硬性规则 1):
 * <pre>CFPA 社区人工汉化(最高,禁止 AI 覆盖) &gt; AI 本地缓存翻译 &gt; 实时 AI 机翻(兜底)</pre>
 *
 * <p><b>查询链路</b>(每个 key 依次尝试,命中即纳入,最终合并):
 * <ol>
 *   <li><b>CFPA</b>(若开关开启且联网命中):最高优先级,该 key 禁止再调 AI;</li>
 *   <li><b>家族术语库 / 旁系参考库</b>:家族模组查家族术语库(受家族术语参考开关控制),
 *       旁系模组永久查旁系参考库;命中来源为 AI_CACHE;</li>
 *   <li><b>AI 缓存</b>({@link AiService#translate} 内部先查缓存):命中即用,不调实时 AI;</li>
 *   <li><b>实时 AI</b>(兜底,未命中缓存且 AI 开关开启且就绪):翻译成功写回缓存。</li>
 * </ol>
 *
 * <p><b>CFPA 命中保护</b>:若某 key 已被 CFPA 命中,则<b>跳过</b>家族术语库 / AI 查询,
 * 保证 CFPA 不被任何 AI 来源覆盖(硬性规则 1 的核心)。
 *
 * <p><b>词条冲突处理</b>:最终由 {@link EntryMerger#merge} 按全局优先级裁决,
 * 同一 key 保留最高优先级一条。
 *
 * <p><b>离线模式</b>(硬性规则 7):CFPA 联网失败→返回空(不阻塞);AI 开关关闭或未就绪
 * →不调实时 AI;家族术语库与旁系参考库的本地条目 + AI 缓存仍可查;旁系参考基础词条离线可用。
 *
 * <p><b>多语种</b>(硬性规则 6):目标语言完全由用户手动选择(配置对象传入),
 * 不读 IP、不按地理位置自动切换。所有来源按同一 {@link TargetLanguage} 查询,保证术语统一。
 *
 * <p>线程安全:依赖各子模块的线程安全保证;本服务无状态(配置 / 子服务为引用)。
 */
public final class TranslationService {
    private final TransLibConfig config;
    private final CfpaService cfpa;
    private final GlossaryStore glossary;
    private final AiService ai;
    private final TransLibLogger logger;

    public TranslationService(TransLibConfig config, CfpaService cfpa,
                              GlossaryStore glossary, AiService ai, TransLibLogger logger) {
        this.config = Objects.requireNonNull(config, "config 不能为空");
        this.cfpa = Objects.requireNonNull(cfpa, "cfpa 不能为空");
        this.glossary = Objects.requireNonNull(glossary, "glossary 不能为空");
        this.ai = Objects.requireNonNull(ai, "ai 不能为空");
        this.logger = Objects.requireNonNull(logger, "logger 不能为空");
    }

    /**
     * 翻译单个模组的一个语言键(按全局优先级链路查询)。
     *
     * @param modId      模组 modid
     * @param key        语言键
     * @param sourceText  原文
     * @param mcVersion   MC 版本(CFPA 匹配用)
     * @param target      目标语言(用户手动选择)
     * @return 最佳翻译条目(含来源);未命中返回 empty
     */
    public Optional<TranslationEntry> translate(String modId, String key, String sourceText,
                                                String mcVersion, TargetLanguage target) {
        if (key == null || key.isBlank() || target == null) {
            return Optional.empty();
        }

        // 1) CFPA(最高优先级,命中则直接返回,禁止 AI 覆盖)
        Optional<TranslationEntry> cfpaHit = lookupCfpa(modId, key, sourceText, mcVersion, target);
        if (cfpaHit.isPresent()) {
            return cfpaHit;
        }

        // 2) 家族术语库 / 旁系参考库
        Optional<TranslationEntry> famHit = glossary.lookupForMod(modId, key, target);
        if (famHit.isPresent()) {
            return famHit;
        }

        // 3) AI 缓存 + 实时 AI(由 AiService 内部决定,缓存优先)
        return ai.translate(key, sourceText, modId, target);
    }

    /**
     * 批量翻译一个模组的多个语言键,并合并为 {@code 键 → 译文} 映射。
     *
     * <p>各来源命中的条目经 {@link EntryMerger#merge} 按全局优先级裁决;
     * CFPA 命中的 key 禁止被 AI 覆盖(本方法内 CFPA 命中后跳过该 key 的 AI 查询)。
     *
     * @param modId        模组 modid
     * @param sourceEntries 原文词条({@code 键 → 原文})
     * @param mcVersion     MC 版本
     * @param target        目标语言
     * @return {@code 键 → 译文}(未命中的 key 不在结果中)
     */
    public Map<String, String> translateMod(String modId, Map<String, String> sourceEntries,
                                            String mcVersion, TargetLanguage target) {
        if (sourceEntries == null || sourceEntries.isEmpty()) {
            return Map.of();
        }
        // 1) CFPA 批量拉取(整个模组)
        Optional<List<TranslationEntry>> cfpaEntries = loadCfpaEntries(modId, mcVersion, target);
        java.util.Set<String> cfpaKeys = cfpaEntries.map(l -> {
            java.util.Set<String> s = new java.util.HashSet<>();
            for (TranslationEntry e : l) {
                s.add(e.key());
            }
            return s;
        }).orElse(java.util.Set.of());

        // 2) 逐 key 查家族术语库 / AI
        List<TranslationEntry> otherEntries = new ArrayList<>();
        for (var e : sourceEntries.entrySet()) {
            String key = e.getKey();
            String src = e.getValue();
            // CFPA 已命中的 key 跳过 AI(保护规则)
            if (cfpaKeys.contains(key)) {
                continue;
            }
            Optional<TranslationEntry> fam = glossary.lookupForMod(modId, key, target);
            if (fam.isPresent()) {
                otherEntries.add(fam.get());
                continue;
            }
            ai.translate(key, src, modId, target).ifPresent(otherEntries::add);
        }

        // 3) 合并:CFPA + 其它来源
        return EntryMerger.merge(List.of(
                cfpaEntries.orElse(List.of()),
                otherEntries));
    }

    /** 查 CFPA,命中转 TranslationEntry(标 CFPA 来源)。 */
    private Optional<TranslationEntry> lookupCfpa(String modId, String key, String sourceText,
                                                  String mcVersion, TargetLanguage target) {
        if (!TargetLanguage.ZH_CN.equals(target)) {
            // CFPA 目前只产出 zh_CN;其它语种不查 CFPA
            return Optional.empty();
        }
        return cfpa.fetch(config, modId, mcVersion, true).map(res -> {
            String text = res.entries().get(key);
            if (text == null) {
                return null;
            }
            return new TranslationEntry(key, sourceText, text, target,
                    EntrySource.CFPA, modId, 1.0f);
        });
    }

    /** 批量拉 CFPA 整个模组,转 TranslationEntry 列表(zh_CN only)。 */
    private Optional<List<TranslationEntry>> loadCfpaEntries(String modId, String mcVersion,
                                                            TargetLanguage target) {
        if (!TargetLanguage.ZH_CN.equals(target)) {
            return Optional.empty();
        }
        return cfpa.fetch(config, modId, mcVersion, true).map(res -> {
            List<TranslationEntry> out = new ArrayList<>(res.entries().size());
            for (var e : res.entries().entrySet()) {
                out.add(new TranslationEntry(e.getKey(), "", e.getValue(),
                        target, EntrySource.CFPA, modId, 1.0f));
            }
            return out;
        });
    }
}
