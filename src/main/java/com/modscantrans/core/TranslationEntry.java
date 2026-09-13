package com.modscantrans.core;

import java.util.Objects;

/**
 * 一条翻译词条:把某个语言键({@code key})的原文翻译成目标语言的译文。
 * 带 {@link EntrySource 来源}与置信度,供合并器({@code com.modscantrans.core.i18n})
 * 按<b>全局优先级</b>取舍。
 *
 * <p>缓存条目绑定目标语言,同原文不同语种分开存储:即同一 {@code key} 的译文
 * 会按 {@link #language} 区分存入不同缓存条目,互不覆盖。
 *
 * @param key             语言键,如 {@code block.create.cogwheel}
 * @param sourceText      原文(通常英文)
 * @param translatedText  目标语言译文
 * @param language        目标语言
 * @param source          来源(CFPA / AI_CACHE / AI_LIVE)
 * @param modId           所属模组 modid(可空,表示来源不明确)
 * @param confidence      置信度 0~1;CFPA 强制为 1.0
 */
public record TranslationEntry(
        String key,
        String sourceText,
        String translatedText,
        TargetLanguage language,
        EntrySource source,
        String modId,
        float confidence) {

    public TranslationEntry {
        Objects.requireNonNull(key, "key 不能为空");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key 不能为空白");
        }
        translatedText = translatedText == null ? "" : translatedText;
        sourceText = sourceText == null ? "" : sourceText;
        Objects.requireNonNull(language, "language 不能为空");
        Objects.requireNonNull(source, "source 不能为空");
        if (confidence < 0f || confidence > 1f) {
            throw new IllegalArgumentException("置信度需在 [0,1] 区间:" + confidence);
        }
        // CFPA 词条为人工汉化,置信度强制为 1.0
        if (source == EntrySource.CFPA) {
            confidence = 1.0f;
        }
        modId = modId == null ? "" : modId;
    }

    /**
     * @return 本条目是否为 CFPA 人工汉化(最高优先级,禁止 AI 覆盖)
     */
    public boolean isCfpa() {
        return source.isCfpa();
    }
}
