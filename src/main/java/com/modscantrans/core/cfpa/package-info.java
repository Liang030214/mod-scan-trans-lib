/**
 * CFPA 社区人工汉化模块(加载器无关)。
 *
 * <p>联网拉取 CFPA 人工翻译资源,匹配规则为 <b>modID + MC 版本</b>,命中则直接使用人工词条。
 * CFPA 词条<b>优先级最高,禁止 AI 翻译覆盖</b>。
 *
 * <p><b>为何需要索引</b>:CFPA 资源路径为
 * {@code projects/assets/<slug>/<版本>/<namespace>/lang/zh_cn.json},
 * 其中 {@code <namespace>} 才是真 modid,而 {@code <slug>} 是连字符目录名(不等于 modid),
 * 无法用 modid 直接拼 URL。故先拉 CFPA 全树路径建索引,再按 (namespace, 版本) 查路径。
 *
 * <p>版本匹配含<b>归并</b>(CFPA 用主次版本 {@code 1.21} 覆盖所有 {@code 1.21.x})与
 * <b>跨版本回退</b>(遵循 CFPA packer 的 {@code fallbackVersions}:1.21→1.20→1.19→1.18)。
 *
 * <p><b>离线 / 网络异常</b>:索引置空、词条返回 empty,<b>不抛异常、不阻塞流程</b>;
 * 后续 fetch 不重复打无效网络,直到调用 {@link com.modscantrans.core.cfpa.CfpaService#refresh()} 重试。
 *
 * <p>模块组成:
 * <ul>
 *   <li>{@link com.modscantrans.core.cfpa.CfpaResource} —— CFPA 资源(最高优先级,zh_CN);</li>
 *   <li>{@link com.modscantrans.core.cfpa.ModVersion} —— MC 版本语义比较 + 前缀版本判断;</li>
 *   <li>{@link com.modscantrans.core.cfpa.CfpaIndex} —— (modid,版本)→路径 索引 + 匹配;</li>
 *   <li>{@link com.modscantrans.core.cfpa.CfpaClient} —— 拉取 SPI;</li>
 *   <li>{@link com.modscantrans.core.cfpa.HttpCfpaClient} —— JDK HttpClient 默认实现
 *       (GitHub tree API 建索引 + jsDelivr CDN 拉词条);</li>
 *   <li>{@link com.modscantrans.core.cfpa.CfpaService} —— 对外门面(懒加载/缓存/离线降级/开关)。</li>
 * </ul>
 */
package com.modscantrans.core.cfpa;
