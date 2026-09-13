/**
 * 通用核心层(纯 Java,加载器无关,可被 NeoForge / Forge / Fabric 复用)。
 *
 * <p>本层不依赖任何模组加载器 API,仅由标准 Java 实现,完成以下功能:
 * <ol>
 *   <li>{@link com.modscantrans.core.scanner} —— 异步后台扫描 mods 文件夹所有 Jar 包,
 *       提取语言 json 文本,不阻塞游戏主线程;</li>
 *   <li>{@link com.modscantrans.core.cfpa} —— 联网拉取 CFPA 社区人工翻译资源,
 *       按 modID + MC 版本匹配,命中则直接使用人工词条(最高优先级,禁止 AI 覆盖);</li>
 *   <li>{@link com.modscantrans.core.family} —— 模组家族识别(加载内置家族表 +
 *       用户自定义家族配置),区分家族模组 / 旁系模组,构建家族术语库、旁系参考库;</li>
 *   <li>{@link com.modscantrans.core.ai} —— AI 翻译接口模块;</li>
 *   <li>{@link com.modscantrans.core.cache} —— 本地翻译缓存读写,
 *       缓存条目绑定目标语言,同原文不同语种分开存储;</li>
 *   <li>{@link com.modscantrans.core.i18n} —— 词条优先级判断逻辑,
 *       自动处理词条冲突。</li>
 * </ol>
 *
 * <p>词条全局优先级:CFPA 社区人工汉化(最高) &gt; AI 本地缓存翻译 &gt; 实时 AI 机翻(兜底)。
 *
 * <p>本顶层包另承载跨模块共享的基础类型(加载器无关):
 * <ul>
 *   <li>{@link com.modscantrans.core.TargetLanguage} —— 目标翻译语言(用户手动选择,不读 IP);</li>
 *   <li>{@link com.modscantrans.core.EntrySource} —— 词条来源与全局优先级;</li>
 *   <li>{@link com.modscantrans.core.ModInfo} —— 扫描到的模组信息;</li>
 *   <li>{@link com.modscantrans.core.TranslationEntry} —— 翻译词条(绑定目标语言);</li>
 *   <li>{@link com.modscantrans.core.ModFamily} —— 模组家族定义(modID 匹配,含跨家族互联);</li>
 *   <li>{@link com.modscantrans.core.TransLibConfig} —— 运行配置(纯数据,不含 toml 读写)。</li>
 * </ul>
 */
package com.modscantrans.core;
