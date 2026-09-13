/**
 * 异步模组扫描模块(加载器无关)。
 *
 * <p>异步后台扫描 mods 文件夹所有 Jar 包,提取语言 json 文本,<b>不阻塞游戏主线程</b>。
 *
 * <p>本模块纯 Java,不依赖 Minecraft / NeoForge / Forge API,可被各加载器适配层复用:
 * <ul>
 *   <li>{@link com.modscantrans.core.scanner.ModScanner} —— 扫描器主类,提供
 *       {@link java.util.concurrent.CompletableFuture} 异步入口与同步单 jar 解析;</li>
 *   <li>{@link com.modscantrans.core.scanner.ScanResult} —— 单 jar 扫描结果(含
 *       {@link com.modscantrans.core.scanner.ScanResult.LangFile} 语言文件);</li>
 *   <li>{@link com.modscantrans.core.scanner.LangJson} —— 零依赖极简 JSON 解析
 *       (仅保留字符串键值对)。</li>
 * </ul>
 *
 * <p><b>异步模型</b>:{@code scanDirectory} 返回的 future 在后台守护线程池完成;
 * 调用方(NeoForge 适配层)在语言加载事件前等待 future 取结果即可,主线程不被阻塞。
 *
 * <p><b>语言文件提取</b>:在 jar 内查找 {@code assets/<namespace>/lang/<lang>.json},
 * 解析为 {@code 键 → 文本} 映射;原文优先取 {@code en_us}。
 *
 * <p><b>模组信息提取</b>(尽力而为):优先 {@code fabric.mod.json},其次
 * {@code META-INF/neoforge.mods.toml} / {@code mods.toml},再其次 jar Manifest,
 * 最后用命名空间 / 文件名兜底。<b>模组识别依靠 modID 匹配家族表,不依靠模组显示名称。</b>
 */
package com.modscantrans.core.scanner;
