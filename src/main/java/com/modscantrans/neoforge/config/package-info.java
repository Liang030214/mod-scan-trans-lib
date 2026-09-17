/**
 * NeoForge 配置层:定义 {@code config/mod_scan_trans_lib.toml} 的配置项,
 * 并与 core 层 {@link com.modscantrans.core.TransLibConfig} 双向桥接。
 *
 * <p>toml 仅做持久化读写,不持有业务状态;运行配置存在 core 层的 TransLibConfig,
 * 启动时灌入,GUI 修改时同步两边。
 *
 * <p>配置项与独立设置 GUI 一一对应:CFPA 联网总开关、AI 翻译总开关、
 * 家族术语参考开关、异步扫描开关、目标语言、清理缓存。
 */
package com.modscantrans.neoforge.config;
