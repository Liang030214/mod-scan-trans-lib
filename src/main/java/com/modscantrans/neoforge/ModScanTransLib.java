package com.modscantrans.neoforge;

import com.modscantrans.core.TransLibConfig;
import com.modscantrans.core.TransLibLogger;
import com.modscantrans.core.ai.AiService;
import com.modscantrans.core.ai.NoopAiTranslator;
import com.modscantrans.core.cache.TranslationCache;
import com.modscantrans.core.cfpa.CfpaService;
import com.modscantrans.core.cfpa.HttpCfpaClient;
import com.modscantrans.core.family.FamilyResolver;
import com.modscantrans.core.family.GlossaryStore;
import com.modscantrans.core.i18n.TranslationService;
import com.modscantrans.core.scanner.ModScanner;
import com.modscantrans.neoforge.client.ClientEventHandler;
import com.modscantrans.neoforge.config.ModScanTransConfig;
import com.modscantrans.neoforge.event.TranslationInjector;
import com.modscantrans.neoforge.gui.ModScanTransConfigScreen;
import java.nio.file.Path;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
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

    // —— core 层各服务(commonSetup 中初始化)——
    private ModScanner scanner;
    private CfpaService cfpaService;
    private GlossaryStore glossaryStore;
    private TranslationCache translationCache;
    private AiService aiService;
    private TranslationService translationService;

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

        // —— 客户端注册(仅客户端)——
        if (FMLEnvironment.dist == Dist.CLIENT) {
            // 快捷键 + 客户端 tick 监听
            ClientEventHandler.registerSelf(modEventBus);
            // 模组列表 "Config" 按钮集成:IConfigScreenFactory 是 NeoForge 1.21.1 的函数式接口,
            // 唯一方法 createScreen(Minecraft mc, Screen parent) -> Screen;
            // 直接传 lambda(官方文档用法:registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new))
            modContainer.registerExtensionPoint(
                    IConfigScreenFactory.class,
                    (mc, parent) -> new ModScanTransConfigScreen(parent));
            LOGGER.info("[{}] 客户端 GUI + 快捷键已注册", MODID);
        }
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

        // —— 初始化 core 各服务 ——
        Path configDir = FMLPaths.CONFIGDIR.get().resolve(MODID);
        Path cfpaCache = configDir.resolve("cfpa_index.cache");
        Path aiCache = configDir.resolve("ai_translations.cache");

        this.scanner = new ModScanner();
        this.cfpaService = new CfpaService(new HttpCfpaClient(logger), logger, cfpaCache);
        FamilyResolver familyResolver = FamilyResolver.withBuiltins();
        this.glossaryStore = new GlossaryStore(familyResolver, config);
        this.translationCache = new TranslationCache(aiCache, logger);

        // AI 翻译:暂用 NoopAiTranslator(实时 AI 由后续阶段接入,当前只用缓存)
        this.aiService = new AiService(NoopAiTranslator.INSTANCE, translationCache, config, logger);
        this.translationService = new TranslationService(config, cfpaService, glossaryStore, aiService, logger);

        // 启动时清理缓存(若配置开启)
        if (modConfig.clearCacheOnBoot()) {
            translationCache.clear();
            translationCache.flush();
            LOGGER.info("[{}] 启动时清理 AI 翻译缓存(clearCacheOnBoot=true)", MODID);
        }

        LOGGER.info("[{}] core 服务初始化完成: scanner/cfpa/family/ai/cache/i18n 就绪", MODID);

        // —— 注册事件层:翻译注入 ——
        TranslationInjector.register();
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

    /** @return 模组扫描器 */
    public ModScanner scanner() {
        return scanner;
    }

    /** @return 顶层翻译服务(事件层调用) */
    public TranslationService translationService() {
        return translationService;
    }

    /** @return AI 翻译缓存(GUI 一键清理用) */
    public TranslationCache translationCache() {
        return translationCache;
    }

    /** @return 当前模组实例(事件层 / GUI 层用) */
    public static ModScanTransLib instance() {
        return INSTANCE;
    }
}
