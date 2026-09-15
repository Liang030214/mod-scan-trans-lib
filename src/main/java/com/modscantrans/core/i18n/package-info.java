/**
 * 词条优先级合并与顶层翻译服务模块(加载器无关,core 收尾)。
 *
 * <p><b>全局硬性规则 1</b>:词条优先级为
 * <b>CFPA 社区人工汉化(最高,禁止 AI 覆盖) &gt; AI 本地缓存翻译 &gt; 实时 AI 机翻(兜底)</b>。
 *
 * <p>模块组成:
 * <ul>
 *   <li>{@link com.modscantrans.core.i18n.EntryMerger} —— 词条合并器
 *       (按全局优先级裁决冲突,保留最高优先级一条);</li>
 *   <li>{@link com.modscantrans.core.i18n.TranslationService} —— 顶层翻译服务
 *       (统筹 CFPA / 家族术语库 / AI 缓存 / 实时 AI,对外输出统一翻译)。</li>
 * </ul>
 *
 * <p><b>查询链路</b>(TranslationService.translate):
 * <ol>
 *   <li>CFPA(命中即返回,该 key 禁止再调 AI);</li>
 *   <li>家族术语库 / 旁系参考库(家族受开关控制,旁系永久启用);</li>
 *   <li>AI 缓存(命中即用,不调实时 AI);</li>
 *   <li>实时 AI(兜底,未命中缓存且 AI 开关开启且就绪)。</li>
 * </ol>
 *
 * <p><b>离线模式</b>:CFPA 联网失败返回空;AI 开关关闭不调实时 AI;
 * 家族术语库 / 旁系参考库本地条目 + AI 缓存仍可查;旁系参考基础词条离线可用。
 *
 * <p><b>多语种</b>:目标语言由用户手动选择(配置对象传入),不读 IP、不按地理位置切换。
 */
package com.modscantrans.core.i18n;
