/**
 * AI 翻译模块(加载器无关)。
 *
 * <p><b>优先级</b>:AI 翻译是<b>兜底</b>(CFPA &gt; AI 缓存 &gt; 实时 AI)。
 * 命中 CFPA 的 key 禁止调 AI(由 i18n 合并器保证)。
 *
 * <p><b>离线 / 开关关闭</b>:{@link com.modscantrans.core.ai.NoopAiTranslator}
 * 永远返回 empty,避免每次请求打无效网络;缓存仍可查(离线可用本地译文)。
 *
 * <p>模块组成:
 * <ul>
 *   <li>{@link com.modscantrans.core.ai.AiTranslator} —— SPI 接口
 *       (单条 / 批量翻译,返回 Optional,异常不抛);</li>
 *   <li>{@link com.modscantrans.core.ai.NoopAiTranslator} —— 空实现
 *       (开关关闭 / 离线 / 未注入时使用);</li>
 *   <li>{@link com.modscantrans.core.ai.AiService} —— 对外门面
 *       (缓存优先,未命中调实时 AI 并写回缓存)。</li>
 * </ul>
 *
 * @see com.modscantrans.core.cache 翻译缓存
 */
package com.modscantrans.core.ai;
