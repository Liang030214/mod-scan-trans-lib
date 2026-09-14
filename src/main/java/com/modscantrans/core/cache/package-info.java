/**
 * AI 翻译缓存模块(加载器无关)。
 *
 * <p><b>缓存条目绑定目标语言,同原文不同语种分开存储</b>(硬性规则):
 * 同一 key 的中文译文、泰语译文是独立条目,互不覆盖。
 *
 * <p><b>离线可用</b>:缓存文件在本地,断网仍可读;实时 AI 翻译成功后写入,
 * 下次直接复用,不重复调 AI(降低延迟与成本)。
 *
 * <p>模块组成:
 * <ul>
 *   <li>{@link com.modscantrans.core.cache.CacheEntry} —— 缓存条目
 *       (含 toCacheLine/fromCacheLine 序列化);</li>
 *   <li>{@link com.modscantrans.core.cache.TranslationCache} —— 缓存读写器
 *       (内存 + 磁盘持久化,原子写,线程安全)。</li>
 * </ul>
 */
package com.modscantrans.core.cache;
