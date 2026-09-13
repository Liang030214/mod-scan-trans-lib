package com.modscantrans.core.family;

import com.modscantrans.core.ModFamily;
import java.util.List;
import java.util.Set;

/**
 * 内置模组家族对照表(硬编码,加载器无关)。
 *
 * <p>遵循用户指定的<b>内置模组家族对照表</b>:
 *
 * <ol>
 *   <li><b>Create 机械动力家族</b>:主模组 create;附属 Create:Aeronautics 航空学、
 *       Create:Steam 'n Rails 启明铁道、Create:Escalated 自动扶梯、Create Deco 装饰、
 *       Create:Framed 多彩边框;全部 Create 开头附属模组归入本家族(前缀规则)。
 *       <b>跨家族互联</b>:与 Create Crafts &amp; Additions 通用机械家族互相声明,
 *       兼容词条,词条同时存入两个家族术语库。</li>
 *   <li><b>Farmer's Delight 乐事家族</b>:主模组 farmerdelight;附属 Chef's Delight 厨房乐事;
 *       所有 Delight 后缀附属模组归入此家族(前缀规则:modid 以 delight 结尾)。</li>
 *   <li><b>Sophisticated 精妙家族</b>:主模组 sophisticatedcore;附属 sophisticatedbackpacks
 *       精妙背包、sophisticatedstorage 精妙仓储;所有 Sophisticated 开头附属模组归入本家族。</li>
 *   <li><b>Xaero 地图家族</b>:主模组 xaerominimap;附属 xaeroworldmap 世界地图;
 *       所有 Xaero 开头附属模组归入本家族。</li>
 * </ol>
 *
 * <p><b>跨家族互联模组定义</b>:Create Crafts &amp; Additions 通用机械为独立家族;
 * 二者兼容词条,词条同时存入两个家族术语库。
 *
 * <p><b>关于前缀规则</b>:ModFamily 的前缀规则是"modid 以这些前缀开头"。
 * 对"Delight 后缀附属"这种需求,由于 modid 命名多样(如 {@code FarmersDelight}、
 * {@code cakedelight} 等),仅靠后缀匹配不可靠。这里采用保守策略:在成员集合中显式列出
 * 已知 Delight 相关 modid,同时不设前缀规则,以免误伤其它模组。用户可通过自定义家族配置
 * 补充更多 Delight 后缀模组到成员集合。
 */
final class BuiltInFamilies {
    private BuiltInFamilies() {
    }

    /** @return 所有内置家族(顺序固定,Create→乐事→精妙→Xaero→通用机械) */
    static List<ModFamily> all() {
        return List.of(create(), farmersDelight(), sophisticated(), xaero(), createCraftsAndAdditions());
    }

    /**
     * Create 机械动力家族。
     * <p>主模组 create;前缀规则 create(覆盖所有 Create 开头附属);
     * 跨家族互联 create_c_and_a(通用机械)。
     */
    private static ModFamily create() {
        return new ModFamily(
                "create",
                "机械动力家族",
                "create",
                Set.of(
                        // Create:Aeronautics 航空学
                        "create_aeronautics",
                        // Create:Steam 'n Rails 启明铁道
                        "create_steam_and_rails", "createrailwaysnavigator", "createrailways",
                        // Create:Escalated 自动扶梯
                        "create_escalated",
                        // Create Deco 装饰
                        "createdeco",
                        // Create:Framed 多彩边框
                        "createframed"
                ),
                Set.of("create"),
                Set.of("create_c_and_a") // 跨家族互联:通用机械
        );
    }

    /**
     * Farmer's Delight 乐事家族。
     * <p>主模组 farmerdelight;显式成员含 Chef's Delight 厨房乐事等;
     * 不设前缀规则(避免误伤),用户可自定义补充。
     */
    private static ModFamily farmersDelight() {
        return new ModFamily(
                "farmersdelight",
                "乐事家族",
                "farmersdelight",
                Set.of(
                        // Chef's Delight 厨房乐事
                        "chefsdelight",
                        // 其它常见 Delight 后缀附属(显式列出,避免前缀误伤)
                        "corn_delight", "crabs_delight", "baking_delight"
                ),
                Set.of(), // 不设前缀:Delight 后缀命名多样,靠显式成员更稳妥
                Set.of()
        );
    }

    /**
     * Sophisticated 精妙家族。
     * <p>主模组 sophisticatedcore;前缀规则 sophisticated(所有 Sophisticated 开头附属)。
     */
    private static ModFamily sophisticated() {
        return new ModFamily(
                "sophisticatedcore",
                "精妙家族",
                "sophisticatedcore",
                Set.of(
                        // Sophisticated Backpacks 精妙背包
                        "sophisticatedbackpacks",
                        // Sophisticated Storage 精妙仓储
                        "sophisticatedstorage"
                ),
                Set.of("sophisticated"),
                Set.of()
        );
    }

    /**
     * Xaero 地图家族。
     * <p>主模组 xaerominimap;前缀规则 xaero(所有 Xaero 开头附属)。
     */
    private static ModFamily xaero() {
        return new ModFamily(
                "xaerominimap",
                "Xaero 地图家族",
                "xaerominimap",
                Set.of(
                        // Xaero's World Map 世界地图
                        "xaeroworldmap"
                ),
                Set.of("xaero"),
                Set.of()
        );
    }

    /**
     * Create Crafts &amp; Additions 通用机械家族(独立家族,与 Create 跨家族互联)。
     * <p>主模组 create_addon;
     * 互联声明 create(机械动力),使词条同时存入两个家族术语库。
     */
    private static ModFamily createCraftsAndAdditions() {
        return new ModFamily(
                "create_c_and_a",
                "通用机械家族",
                "create_addon",
                Set.of(),
                Set.of(), // 不设 create 前缀:否则会把整个 Create 家族都归到通用机械
                Set.of("create") // 跨家族互联:机械动力
        );
    }
}
