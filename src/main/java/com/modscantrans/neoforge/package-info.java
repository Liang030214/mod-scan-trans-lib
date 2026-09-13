/**
 * NeoForge 适配层。
 *
 * <p>本层负责将「加载器无关」的通用核心层({@link com.modscantrans.core})
 * 桥接进 NeoForge 1.21.1 的事件系统与配置 / 界面体系,包含:
 * <ul>
 *   <li>{@link com.modscantrans.neoforge.ModScanTransLib} —— {@code @Mod} 主入口;</li>
 *   <li>{@code config} —— 生成与读写 {@code mod_scan_trans_lib.toml} 配置;</li>
 *   <li>{@code gui} —— 模组独立设置界面(CFPA 联网总开关、AI 翻译总开关、
 *       家族术语参考开关、一键清理缓存、异步扫描开关、目标语言下拉选择);</li>
 *   <li>{@code event} —— 订阅 MC 语言加载事件,将合并完成的翻译词条注入游戏 I18n 系统。</li>
 * </ul>
 *
 * <p>注意:经典 Forge 移植入口见 {@link com.modscantrans.forge},
 * Fabric 仅预留空接口见 {@link com.modscantrans.fabric}。
 */
package com.modscantrans.neoforge;
