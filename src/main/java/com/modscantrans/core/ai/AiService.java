package com.modscantrans.core.ai;

import com.modscantrans.core.EntrySource;
import com.modscantrans.core.TargetLanguage;
import com.modscantrans.core.TransLibConfig;
import com.modscantrans.core.TransLibLogger;
import com.modscantrans.core.TranslationEntry;
import com.modscantrans.core.cache.TranslationCache;
import java.util.Objects;
import java.util.Optional;

/**
 * AI 翻译对外门面(加载器无关)。
 *
 * <p>封装<b>优先级链路</b>:AI 缓存 &gt; 实时 AI。调用方先调
 * {@link #translate} 获取译文,内部自动:
 * <ol>
 *   <li>先查 {@link TranslationCache AI 缓存}(命中则返回,标 {@link EntrySource#AI_CACHE});</li>
 *   <li>未命中且 {@link TransLibConfig#isAiEnabled() AI 开关}开启且
 *       {@link AiTranslator#isReady()} 就绪时,调实时 AI;成功后写回缓存,标
 *       {@link EntrySource#AI_LIVE};失败 / 离线返回 empty。</li>
 * </ol>
 *
 * <p><b>硬性规则</b>:AI 翻译是<b>兜底</b>优先级(CFPA &gt; AI 缓存 &gt; 实时 AI)。
 * CFPA 命中的 key 由 i18n 合并器保证不调 AI,本接口不感知 CFPA。
 *
 * <p><b>离线 / AI 开关关闭</b>:{@link NoopAiTranslator} 实现永远返回 empty,
 * 避免每次请求都打无效网络;缓存仍可查(离线可用本地缓存译文)。
 *
 * <p>线程安全:缓存读写由 {@link TranslationCache} 保护;AI 调用本身无状态。
 */
public final class AiService {
    private final AiTranslator translator;
    private final TranslationCache cache;
    private final TransLibConfig config;
    private final TransLibLogger logger;

    /**
     * @param translator AI 翻译实现(可注入 {@link NoopAiTranslator#INSTANCE} 表示不启用)
     * @param cache       AI 翻译缓存
     * @param config      运行配置(读 AI 开关)
     * @param logger      日志器
     */
    public AiService(AiTranslator translator, TranslationCache cache,
                     TransLibConfig config, TransLibLogger logger) {
        this.translator = Objects.requireNonNull(translator, "translator 不能为空");
        this.cache = Objects.requireNonNull(cache, "cache 不能为空");
        this.config = Objects.requireNonNull(config, "config 不能为空");
        this.logger = Objects.requireNonNull(logger, "logger 不能为空");
    }

    /**
     * 翻译单个语言键(优先查缓存,未命中才调实时 AI)。
     *
     * @param key          语言键
     * @param sourceText   原文
     * @param modId        所属 modid(可空,写入缓存时带上)
     * @param target       目标语言
     * @return 翻译条目(含来源 AI_CACHE 或 AI_LIVE);未翻译返回 empty
     */
    public Optional<TranslationEntry> translate(String key, String sourceText,
                                                String modId, TargetLanguage target) {
        if (key == null || key.isBlank() || sourceText == null || sourceText.isBlank() || target == null) {
            return Optional.empty();
        }

        // 1) 先查缓存(命中即返回,不调 AI)
        Optional<TranslationEntry> cached = lookupCache(key, sourceText, modId, target);
        if (cached.isPresent()) {
            return cached;
        }

        // 2) 未命中:AI 开关关闭 / 未就绪 → 返回 empty(离线不重复打网络)
        if (!config.isAiEnabled() || !translator.isReady()) {
            return Optional.empty();
        }

        // 3) 调实时 AI
        Optional<String> translated = translator.translate(sourceText, target);
        if (translated.isEmpty()) {
            return Optional.empty();
        }
        String text = translated.get();
        long now = System.currentTimeMillis();

        // 4) 写回缓存(下次命中直接复用)
        com.modscantrans.core.cache.CacheEntry ce = new com.modscantrans.core.cache.CacheEntry(
                key, sourceText, text, target, modId, now);
        cache.put(ce);

        // 5) 返回 AI_LIVE 条目
        return Optional.of(new TranslationEntry(
                key, sourceText, text, target, EntrySource.AI_LIVE, modId, 0.6f));
    }

    /** 查缓存并构造 AI_CACHE 条目。 */
    private Optional<TranslationEntry> lookupCache(String key, String sourceText,
                                                  String modId, TargetLanguage target) {
        return cache.get(key, target).map(ce -> new TranslationEntry(
                ce.key(), ce.sourceText(), ce.translated(), target,
                EntrySource.AI_CACHE, ce.modId(), 0.7f));
    }

    /** @return AI 翻译实现是否就绪 */
    public boolean isReady() {
        return config.isAiEnabled() && translator.isReady();
    }

    /** @return 底层缓存(供 GUI 一键清理缓存调用) */
    public TranslationCache cache() {
        return cache;
    }
}
