/**
 * NeoForge 配置层(阶段3开发)。
 *
 * <p>生成并读写 {@code config/mod_scan_trans_lib.toml},包含开关:
 * <ul>
 *   <li>CFPA 联网总开关;</li>
 *   <li>AI 翻译总开关;</li>
 *   <li>家族术语参考开关(默认开启,仅家族模组生效;旁系模组永久启用旁系参考库,不受此开关控制);</li>
 *   <li>一键清理缓存;</li>
 *   <li>异步扫描开关;</li>
 *   <li>目标语言下拉选择(默认 zh_CN;可切换 pt_PT / th_TH 等;仅跟随用户手动选择,不读 IP、不按地理位置切换)。</li>
 * </ul>
 *
 * <p>界面修改的参数自动写入 toml。
 */
package com.modscantrans.neoforge.config;
