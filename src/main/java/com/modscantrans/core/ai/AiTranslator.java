package com.modscantrans.core.ai;

import com.modscantrans.core.TargetLanguage;

/**
 * AI 翻译 SPI 接口(加载器无关)。
 *
 * <p>core 层只定义接口,具体实现可走不同 AI 后端(OpenAI / 本地模型 / 第三方 API)。
 * 默认提供 {@link NoopAiTranslator}(始终返回 empty,用于 AI 翻译总开关关闭 / 离线场景)。
 *
 * <p><b>优先级</b>:AI 翻译是<b>兜底</b>(CFPA &gt; AI 缓存 &gt; 实时 AI)。
 * 调用方应<b>先查缓存</b>,命中则不调实时 AI;未命中且 AI 开关开启时才调本接口。
 *
 * <p><b>硬性规则</b>:CFPA 命中的 key 禁止调用 AI(由 i18n 合并器保证,本接口不感知)。
 *
 * <p><b>离线 / 网络异常</b>:实现应返回 {@link java.util.Optional#empty()},不抛异常、不阻塞。
 */
public interface AiTranslator {
    /**
     * 翻译一段文本。
     *
     * @param sourceText 原文(通常英文)
     * @param target     目标语言(用户手动选择,不读 IP)
     * @return 翻译结果;未翻译 / 离线 / 失败时返回 empty
     */
    java.util.Optional<String> translate(String sourceText, TargetLanguage target);

    /**
     * 批量翻译(默认实现:逐条调用 {@link #translate};高性能后端可重写为单次请求)。
     *
     * @param sourceTexts 原文列表
     * @param target      目标语言
     * @return 翻译结果列表(与输入同长,未翻译的位置为 empty)
     */
    default java.util.List<java.util.Optional<String>> translateBatch(
            java.util.List<String> sourceTexts, TargetLanguage target) {
        java.util.List<java.util.Optional<String>> out = new java.util.ArrayList<>(sourceTexts.size());
        for (String s : sourceTexts) {
            out.add(translate(s, target));
        }
        return java.util.List.copyOf(out);
    }

    /**
     * @return 实现是否就绪(已配置 API key 等);未就绪时 translate 直接返回 empty
     */
    boolean isReady();
}
