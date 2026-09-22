package com.modscantrans.neoforge.client;

import com.modscantrans.neoforge.gui.ModScanTransConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端事件处理(仅客户端加载)。
 *
 * <p><b>职责</b>:
 * <ul>
 *   <li>注册快捷键(默认 {@code ]} 右方括号)用于打开模组设置界面;</li>
 *   <li>在客户端 tick 事件中检测快捷键按下,打开 {@link ModScanTransConfigScreen}。</li>
 * </ul>
 *
 * <p><b>客户端安全</b>:本类只在 {@code FMLEnvironment.dist == CLIENT} 时注册,
 * 不会在专用服务端加载。
 */
public final class ClientEventHandler {
    /** 打开设置界面的快捷键(默认 ] 右方括号)。 */
    public static final KeyMapping OPEN_CONFIG_KEY = new KeyMapping(
            "key.mod_scan_trans_lib.open_config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            "key.categories.mod_scan_trans_lib"
    );

    private ClientEventHandler() {
    }

    /**
     * 注册客户端事件(仅在客户端调用)。
     *
     * @param modEventBus 模组事件总线(注册快捷键)
     */
    public static void registerSelf(IEventBus modEventBus) {
        // 快捷键注册(mod 事件总线)
        modEventBus.addListener(ClientEventHandler::registerKeyMappings);

        // 客户端 tick 监听(NeoForge 事件总线)
        NeoForge.EVENT_BUS.addListener(ClientEventHandler::onClientTick);
    }

    /**
     * 快捷键注册回调。
     */
    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG_KEY);
    }

    /**
     * 客户端 tick 回调:检测快捷键按下,打开设置界面。
     * 仅在游戏内(无其他界面打开时)响应。
     */
    private static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            while (OPEN_CONFIG_KEY.consumeClick()) {
                mc.setScreen(new ModScanTransConfigScreen(null));
            }
        }
    }
}
