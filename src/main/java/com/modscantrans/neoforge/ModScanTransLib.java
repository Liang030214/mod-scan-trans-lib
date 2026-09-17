package com.modscantrans.neoforge;

import com.modscantrans.core.TransLibConfig;
import com.modscantrans.core.TransLibLogger;
import com.modscantrans.neoforge.config.ModScanTransConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模组扫描翻译支持库 - NeoForge 适配层主入口。
 *
 * <p>本类是 {@code @Mod} 注解主类,其 {@code MODID} 必须与
 * {@code META-INF/neoforge.mods.toml} 中的 {@code modId} 一致。
 *
 * <p>阶段3:注册配置文件 {@code mod_scan_trans_lib.toml},初始化 core 层各模块,
 * 把 NeoForge 配置桥接到 core 层 {@link TransLibConfig};后续由
 * {@code com.modscantrans.neoforge.event} 订阅语言加载事件注入翻译,
 * 由 {@code com.modscantrans.neoforge.gui} 提供独立设置界面。
 *
 * <p>设计要点(遵循项目硬性规则):
 * <ul>
 *   <li>词条优先级:CFPA 人工汉化 &gt; AI 本地缓存翻译 &gt; 实时 AI 机翻(兜底)。</li>
 *   <li>模组识别依靠 modID 匹配家族表,而非模组显示名称。</li>
 *   <li>目标语言完全由用户在独立设置界面手动选择,程序不读取 IP、不按地理位置自动切换语种。</li>
 * </ul>
 */
@Mod(ModScanTransLib.MODID)
public class ModScanTransLib {
    /** 模组唯一标识,必须小写英文,与 neoforge.mods.toml 中 modId 一致。 */
    public static final String MODID = "mod_scan_trans_lib";

    /** 模组专用 slf4j 日志器。 */
    public static final Logger LOGGER = LoggerFactory.getLogger("ModScanTransLib");

    /** core 层运行配置(单例,core 各模块共用)。 */
    private final TransLibConfig config;
    /** core 层日志器(桥接到 slf4j)。 */
    private final TransLibLogger logger;
    /** NeoForge 配置定义(toml 读写)。 */
    private final ModScanTransConfig modConfig;

    /** 单例(供事件层 / GUI 层取用 core 各服务)。 */
    private static volatile ModScanTransLib INSTANCE;

    public ModScanTransLib(IEventBus modEventBus, ModContainer modContainer) {
        INSTANCE = this;
        this.logger = new NeoForgeLoggerBridge(LOGGER);
        this.config = new TransLibConfig();
        this.modConfig = new ModScanTransConfig();

        // 注册配置文件(mod_scan_trans_lib.toml,生成在 config/ 目录)
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON,
                modConfig.spec(), "mod_scan_trans_lib.toml");

        modEventBus.addListener(this::commonSetup);
        LOGGER.info("[{}] 模组扫描翻译支持库 (NeoForge) 开始加载。", MODID);
    }

    /**
     * 模组通用初始化阶段:toml 已加载,把配置灌入 core 层 TransLibConfig,
     * 并初始化 core 各服务(scanner/cfpa/family/ai/cache/i18n)。
     *
     * @param event 通用初始化事件
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        // toml 值灌入 core 配置
        modConfig.loadInto(config);
        LOGGER.info("[{}] common setup: 配置加载完成 CFPA={} AI={} 家族术语={} 异步扫描={} 目标语言={}",
                MODID, config.isCfpaEnabled(), config.isAiEnabled(),
                config.isFamilyGlossaryEnabled(), config.isAsyncScanEnabled(),
                config.getTargetLanguage().code());

        // 阶段3 后续(事件层 + GUI 层)在此挂接 core 服务
        // 事件层:订阅语言加载事件,调用 TranslationService 注入合并翻译到 I18n
        // GUI 层:注册独立设置界面,修改参数自动写回 toml
    }

    /** @return core 运行配置(GUI / 事件层取用) */
    public TransLibConfig config() {
        return config;
    }

    /** @return core 日志器 */
    public TransLibLogger logger() {
        return logger;
    }

    /** @return NeoForge 配置定义(GUI 修改后写回 toml 用) */
    public ModScanTransConfig modConfig() {
        return modConfig;
    }

    /** @return 当前模组实例(事件层 / GUI 层用) */
    public static ModScanTransLib instance() {
        return INSTANCE;
    }
}
