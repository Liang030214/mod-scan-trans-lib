/**
 * 模组家族识别模块(加载器无关)。
 *
 * <p><b>硬性规则</b>:
 * <ul>
 *   <li>模组识别依靠 <b>modID 匹配家族表</b>,不依靠模组显示名称;</li>
 *   <li>家族术语参考开关(默认开启):仅家族模组生效;开启时缓存读取、AI 翻译加载
 *       对应主模组术语库;关闭则不加载家族术语。</li>
 *   <li><b>旁系模组永久启用旁系参考库,不受此开关控制</b>。</li>
 *   <li>跨家族互联模组:翻译词条同时存入两个关联家族术语库。</li>
 * </ul>
 *
 * <p><b>匹配优先级</b>:精确命中(主 modid / 显式成员)优先于前缀命中,
 * 以保证跨家族互联模组(如 Create Crafts &amp; Additions)被自身家族精确命中,
 * 不被 Create 家族前缀规则抢走。
 *
 * <p>模块组成:
 * <ul>
 *   <li>{@link com.modscantrans.core.family.BuiltInFamilies} —— 内置家族表
 *       (Create 机械动力 / Farmer's Delight 乐事 / Sophisticated 精妙 / Xaero 地图
 *       + Create Crafts &amp; Additions 通用机械,跨家族互联);</li>
 *   <li>{@link com.modscantrans.core.family.FamilyResolver} —— 家族解析器
 *       (内置 + 自定义,精确优先于前缀);</li>
 *   <li>{@link com.modscantrans.core.family.FamilyMatch} —— 识别结果
 *       (精确/前缀/旁系 + 命中家族 + 跨家族互联);</li>
 *   <li>{@link com.modscantrans.core.family.FamilyGlossary} —— 家族术语库
 *       (按目标语言分存,高优先级不被低优先级覆盖);</li>
 *   <li>{@link com.modscantrans.core.family.SidelineReference} —— 旁系参考库
 *       (永久启用,内置基础词条离线可用);</li>
 *   <li>{@link com.modscantrans.core.family.GlossaryStore} —— 术语库仓库
 *       (统管家族 + 旁系,处理跨家族互联写入,受家族术语参考开关控制);</li>
 *   <li>{@link com.modscantrans.core.family.FamilyConfigLoader} ——
 *       用户自定义家族配置加载器(JSON,纯 Java)。</li>
 * </ul>
 */
package com.modscantrans.core.family;
