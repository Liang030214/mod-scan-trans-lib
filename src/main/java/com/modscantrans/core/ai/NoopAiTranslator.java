package com.modscantrans.core.ai;

import com.modscantrans.core.TargetLanguage;
import java.util.Optional;

/**
 * 始终返回未命中的 AI 翻译器(空实现)。
 *
 * <p>用于:
 * <ul>
 *   <li>AI 翻译总开关关闭时({@link com.modscantrans.core.TransLibConfig#isAiEnabled()} = false);</li>
 *   <li>未注入任何 AI 实现(占位);</li>
 *   <li>离线场景(避免每次请求都打无效网络)。</li>
 * </ul>
 *
 * <p>{@link #isReady()} 永远返回 false,表明不会真正调用 AI。
 */
public final class NoopAiTranslator implements AiTranslator {
    /** 单例(无状态)。 */
    public static final NoopAiTranslator INSTANCE = new NoopAiTranslator();

    private NoopAiTranslator() {
    }

    @Override
    public Optional<String> translate(String sourceText, TargetLanguage target) {
        return Optional.empty();
    }

    @Override
    public boolean isReady() {
        return false;
    }
}
