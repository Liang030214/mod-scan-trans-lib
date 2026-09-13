package com.modscantrans.neoforge;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

/**
 * 模组扫描翻译支持库 - NeoForge 适配层主入口。
 *
 * <p>本类是 {@code @Mod} 注解主类,其 {@code MODID} 必须与
 * {@code META-INF/neoforge.mods.toml} 中的 {@code modId} 一致。
 *
 * <p>阶段1仅搭建骨架:构造函数中挂载 {@link FMLCommonSetupEvent} 监听器占位,
 * 真正的扫描 / 翻译 / 词条注入逻辑将在后续阶段由通用核心层({@code com.modscantrans.core})
 * 提供实现,再由 NeoForge 适配层({@code com.modscantrans.neoforge.event})挂接进游戏事件系统。
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
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 模组构造函数,是模组加载时运行的第一段代码。
     * FML 会自动识别 {@link IEventBus}、{@link ModContainer} 等参数类型并注入。
     *
     * @param modEventBus   模组事件总线(用于监听模组加载阶段事件)
     * @param modContainer  本模组的容器(后续阶段用于注册 ModConfigSpec / 配置界面)
     */
    public ModScanTransLib(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        LOGGER.info("[{}] 模组扫描翻译支持库 (NeoForge) 开始加载。", MODID);
    }

    /**
     * 模组通用初始化阶段(占位)。
     *
     * <p>后续阶段在此接入:
     * <ol>
     *   <li>异步后台扫描 mods 文件夹 Jar,提取语言 json 文本(不阻塞主线程);</li>
     *   <li>联网拉取 CFPA 人工翻译资源(modID + MC 版本匹配);</li>
     *   <li>模组家族识别与家族术语库 / 旁系参考库构建;</li>
     *   <li>AI 翻译接口 + 本地缓存读写(缓存绑定目标语种);</li>
     *   <li>词条优先级判断与冲突处理。</li>
     * </ol>
     *
     * @param event 通用初始化事件
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[{}] common setup (骨架阶段,业务逻辑待后续阶段接入)。", MODID);
    }
}
