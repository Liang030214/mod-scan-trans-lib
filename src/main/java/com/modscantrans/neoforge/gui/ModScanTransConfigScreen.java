package com.modscantrans.neoforge.gui;

import com.modscantrans.core.TargetLanguage;
import com.modscantrans.core.TransLibConfig;
import com.modscantrans.neoforge.ModScanTransLib;
import com.modscantrans.neoforge.config.ModScanTransConfig;
import com.modscantrans.neoforge.event.TranslationInjector;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 模组扫描翻译支持库 - 独立设置界面。
 *
 * <p><b>界面内容</b>(7 个配置项,与 toml 一一对应:6 开关 + 1 语言下拉):
 * <ol>
 *   <li>模组主开关(开关 — 关闭则跳过翻译注入,等效禁用本模组效果)</li>
 *   <li>CFPA 联网汉化(开关)</li>
 *   <li>AI 翻译(开关)</li>
 *   <li>家族术语参考(开关)</li>
 *   <li>异步扫描(开关)</li>
 *   <li>启动时清理缓存(开关)</li>
 *   <li>目标语言(下拉选择框 — 用户手动选择,不读 IP、不按地理位置切换)</li>
 * </ol>
 *
 * <p><b>操作按钮</b>:
 * <ul>
 *   <li><b>保存并重载</b> — 同步更新 core {@link TransLibConfig} + 写入 toml + 触发翻译重载;</li>
 *   <li><b>清理 AI 缓存</b> — 立即清空本地 AI 翻译缓存(不影响当前注入的词条);</li>
 *   <li><b>取消</b> — 丢弃改动,返回上级界面。</li>
 * </ul>
 *
 * <p><b>配置同步流程</b>(硬性约束):
 * <ol>
 *   <li>用户修改开关/下拉后点击"保存并重载";</li>
 *   <li>先更新 core 层 {@link TransLibConfig}(volatile 字段,立即生效);</li>
 *   <li>再调用 {@link ModScanTransConfig#saveFrom} 写入 toml(持久化);</li>
 *   <li>{@code modEnabled} 与 {@code clearCacheOnBoot} 通过各自的 setter 单独写入 toml
 *       (这两个是 NeoForge 适配层独有开关,不在 core TransLibConfig 中);</li>
 *   <li>最后在后台线程重新扫描 + 合并翻译,在主线程注入(刷新游戏内词条)。</li>
 * </ol>
 *
 * <p><b>语言选择约束</b>(硬性规则 6):下拉列表只提供 {@link TargetLanguage#BUILTINS},
 * 全程不读取 IP 地址、不根据地理位置自动切换语种。
 */
public class ModScanTransConfigScreen extends Screen {
    private static final int TOGGLE_WIDTH = 260;
    private static final int TOGGLE_HEIGHT = 20;
    private static final int ROW_SPACING = 22;
    private static final int START_Y = 35;
    private static final int BTN_WIDTH = 100;
    private static final int BTN_HEIGHT = 20;
    private static final int BTN_GAP = 10;

    // 本地状态(用户在界面上调整的值,点击"保存并重载"后才写入 core / toml)
    private boolean modEnabled;
    private boolean cfpaEnabled;
    private boolean aiEnabled;
    private boolean familyGlossaryEnabled;
    private boolean asyncScanEnabled;
    private boolean clearCacheOnBoot;
    private TargetLanguage targetLanguage;

    private final Screen parentScreen;

    public ModScanTransConfigScreen(Screen parent) {
        super(Component.translatable("mod_scan_trans_lib.config.title"));
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        // 加载当前配置值
        ModScanTransLib lib = ModScanTransLib.instance();
        TransLibConfig config = lib.config();
        ModScanTransConfig modConfig = lib.modConfig();

        modEnabled = modConfig.modEnabled();
        cfpaEnabled = config.isCfpaEnabled();
        aiEnabled = config.isAiEnabled();
        familyGlossaryEnabled = config.isFamilyGlossaryEnabled();
        asyncScanEnabled = config.isAsyncScanEnabled();
        clearCacheOnBoot = modConfig.clearCacheOnBoot();
        targetLanguage = config.getTargetLanguage();

        int centerX = this.width / 2;
        int toggleX = centerX - TOGGLE_WIDTH / 2;
        int y = START_Y;

        // —— 6 个开关(模组主开关放在最顶,作为总闸)——
        addRenderableWidget(CycleButton.onOffBuilder(modEnabled)
                .create(toggleX, y, TOGGLE_WIDTH, TOGGLE_HEIGHT,
                        Component.translatable("mod_scan_trans_lib.config.enabled"),
                        (btn, val) -> modEnabled = val));
        y += ROW_SPACING;

        addRenderableWidget(CycleButton.onOffBuilder(cfpaEnabled)
                .create(toggleX, y, TOGGLE_WIDTH, TOGGLE_HEIGHT,
                        Component.translatable("mod_scan_trans_lib.config.cfpa"),
                        (btn, val) -> cfpaEnabled = val));
        y += ROW_SPACING;

        addRenderableWidget(CycleButton.onOffBuilder(aiEnabled)
                .create(toggleX, y, TOGGLE_WIDTH, TOGGLE_HEIGHT,
                        Component.translatable("mod_scan_trans_lib.config.ai"),
                        (btn, val) -> aiEnabled = val));
        y += ROW_SPACING;

        addRenderableWidget(CycleButton.onOffBuilder(familyGlossaryEnabled)
                .create(toggleX, y, TOGGLE_WIDTH, TOGGLE_HEIGHT,
                        Component.translatable("mod_scan_trans_lib.config.family"),
                        (btn, val) -> familyGlossaryEnabled = val));
        y += ROW_SPACING;

        addRenderableWidget(CycleButton.onOffBuilder(asyncScanEnabled)
                .create(toggleX, y, TOGGLE_WIDTH, TOGGLE_HEIGHT,
                        Component.translatable("mod_scan_trans_lib.config.async"),
                        (btn, val) -> asyncScanEnabled = val));
        y += ROW_SPACING;

        addRenderableWidget(CycleButton.onOffBuilder(clearCacheOnBoot)
                .create(toggleX, y, TOGGLE_WIDTH, TOGGLE_HEIGHT,
                        Component.translatable("mod_scan_trans_lib.config.clearcache"),
                        (btn, val) -> clearCacheOnBoot = val));
        y += ROW_SPACING;

        // —— 目标语言下拉选择框(只提供手动选择,不读 IP)——
        // CycleButton.builder 的值显示函数需返回 Component(NeoForge 1.21.1 API)
        addRenderableWidget(CycleButton.<TargetLanguage>builder(tl -> Component.literal(tl.displayName()))
                .withValues(TargetLanguage.BUILTINS)
                .withInitialValue(targetLanguage)
                .create(toggleX, y, TOGGLE_WIDTH, TOGGLE_HEIGHT,
                        Component.translatable("mod_scan_trans_lib.config.language"),
                        (btn, val) -> targetLanguage = val));
        y += ROW_SPACING + 5;

        // —— 操作按钮行 ——
        int totalBtnWidth = BTN_WIDTH * 3 + BTN_GAP * 2;
        int btnX = centerX - totalBtnWidth / 2;

        addRenderableWidget(Button.builder(
                Component.translatable("mod_scan_trans_lib.config.save_reload"),
                b -> onSaveAndReload())
                .bounds(btnX, y, BTN_WIDTH, BTN_HEIGHT).build());
        btnX += BTN_WIDTH + BTN_GAP;

        addRenderableWidget(Button.builder(
                Component.translatable("mod_scan_trans_lib.config.clear_ai_cache"),
                b -> onClearAiCache())
                .bounds(btnX, y, BTN_WIDTH, BTN_HEIGHT).build());
        btnX += BTN_WIDTH + BTN_GAP;

        addRenderableWidget(Button.builder(
                Component.translatable("mod_scan_trans_lib.config.cancel"),
                b -> onCancel())
                .bounds(btnX, y, BTN_WIDTH, BTN_HEIGHT).build());
    }

    /**
     * 保存并重载:同步 core 配置 → 写 toml → 触发翻译重载。
     *
     * <p><b>约束</b>:
     * <ul>
     *   <li>core 配置(cfpaEnabled/aiEnabled/familyGlossaryEnabled/asyncScanEnabled/targetLanguage)
     *       同步写入 core {@link TransLibConfig} + toml;</li>
     *   <li>NeoForge 独有开关(modEnabled/clearCacheOnBoot)只写 toml,不写 core(它们不在 core 中);</li>
     *   <li>modEnabled=false 时,TranslationInjector 会自动跳过注入(见 TranslationInjector.collectAndMergeTranslations)。</li>
     * </ul>
     */
    private void onSaveAndReload() {
        ModScanTransLib lib = ModScanTransLib.instance();
        TransLibConfig config = lib.config();

        // 1) 更新 core 层 TransLibConfig(volatile,立即生效)
        config.setCfpaEnabled(cfpaEnabled);
        config.setAiEnabled(aiEnabled);
        config.setFamilyGlossaryEnabled(familyGlossaryEnabled);
        config.setAsyncScanEnabled(asyncScanEnabled);
        config.setTargetLanguage(targetLanguage);

        // 2) 写入 toml 持久化(core 配置 + NeoForge 独有开关)
        lib.modConfig().saveFrom(config);
        lib.modConfig().setModEnabled(modEnabled);
        lib.modConfig().setClearCacheOnBoot(clearCacheOnBoot);

        // 3) 关闭设置界面
        Minecraft.getInstance().setScreen(parentScreen);

        // 4) 触发翻译重载:后台扫描+合并 → 主线程注入
        //    (modEnabled=false 时 collectAndMergeTranslations 内部会返回空 Map,不注入)
        CompletableFuture.supplyAsync(TranslationInjector::collectAndMergeTranslations)
                .thenAccept(translations -> Minecraft.getInstance().execute(
                        () -> TranslationInjector.injectTranslations(translations)));
    }

    /**
     * 立即清理 AI 翻译缓存(不影响当前已注入的词条,下次重载时重新生成)。
     */
    private void onClearAiCache() {
        ModScanTransLib lib = ModScanTransLib.instance();
        if (lib.translationCache() != null) {
            lib.translationCache().clear();
            lib.translationCache().flush();
        }
    }

    /**
     * 取消:丢弃改动,返回上级界面。
     */
    private void onCancel() {
        Minecraft.getInstance().setScreen(parentScreen);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
