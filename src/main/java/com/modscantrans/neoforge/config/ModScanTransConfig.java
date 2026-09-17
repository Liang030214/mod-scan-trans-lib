package com.modscantrans.neoforge.config;

import com.modscantrans.core.TargetLanguage;
import com.modscantrans.core.TransLibConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 模组扫描翻译支持库的 NeoForge 配置定义(对应 {@code config/mod_scan_trans_lib.toml})。
 *
 * <p>定义 6 个配置项(与独立设置 GUI 一一对应):
 * <ul>
 *   <li>{@code cfpaEnabled} —— CFPA 联网总开关(默认 true);</li>
 *   <li>{@code aiEnabled} —— AI 翻译总开关(默认 true);</li>
 *   <li>{@code familyGlossaryEnabled} —— 家族术语参考开关(默认 true);</li>
 *   <li>{@code asyncScanEnabled} —— 异步扫描开关(默认 true);</li>
 *   <li>{@code clearCacheOnBoot} —— 启动时清理缓存(默认 false,仅作一次性触发用,GUI 的"一键清理缓存"按钮通过运行时 API 操作);</li>
 *   <li>{@code targetLanguage} —— 目标语言代码(默认 zh_CN,用户手动选择,不读 IP)。</li>
 * </ul>
 *
 * <p>本类只负责 toml 读写(ModConfigSpec),<b>不持有业务状态</b>;
 * 真正的运行配置存在 {@link TransLibConfig}(core 层)。启动时由适配层把 toml 值灌入 TransLibConfig,
 * GUI 修改时同步更新两边(TransLibConfig + toml 文件)。
 */
public final class ModScanTransConfig {
    private final ModConfigSpec.Builder builder;
    private final ModConfigSpec.BooleanValue cfpaEnabled;
    private final ModConfigSpec.BooleanValue aiEnabled;
    private final ModConfigSpec.BooleanValue familyGlossaryEnabled;
    private final ModConfigSpec.BooleanValue asyncScanEnabled;
    private final ModConfigSpec.BooleanValue clearCacheOnBoot;
    private final ModConfigSpec.ConfigValue<String> targetLanguage;
    private final ModConfigSpec spec;

    public ModScanTransConfig() {
        this.builder = new ModConfigSpec.Builder();

        cfpaEnabled = builder.comment("CFPA 社区人工汉化联网总开关(最高优先级,关闭则不联网拉取 CFPA 词条)")
                .define("cfpaEnabled", true);
        aiEnabled = builder.comment("AI 翻译总开关(关闭则不调用实时 AI 机翻,但仍可使用本地缓存)")
                .define("aiEnabled", true);
        familyGlossaryEnabled = builder.comment("家族术语参考开关(默认开启,仅家族模组生效;旁系模组永久启用旁系参考库,不受此开关控制)")
                .define("familyGlossaryEnabled", true);
        asyncScanEnabled = builder.comment("异步扫描开关(开启则后台扫描 mods 文件夹,不阻塞主线程)")
                .define("asyncScanEnabled", true);
        clearCacheOnBoot = builder.comment("启动时清理 AI 翻译缓存(默认 false;设为 true 启动后清理一次,通常由 GUI 一键清理按钮触发)")
                .define("clearCacheOnBoot", false);
        targetLanguage = builder.comment("目标翻译语言代码(用户手动选择,不读 IP 不按地理位置切换;可选: zh_CN/en_US/pt_PT/th_TH)")
                .define("targetLanguage", TargetLanguage.DEFAULT.code());

        this.spec = builder.build();
    }

    /** @return NeoForge 配置 Spec(用于注册到 ModContainer) */
    public ModConfigSpec spec() {
        return spec;
    }

    // —— toml 读取方法(GUI / 启动时灌入 TransLibConfig 用)——

    public boolean cfpaEnabled() {
        return cfpaEnabled.get();
    }

    public boolean aiEnabled() {
        return aiEnabled.get();
    }

    public boolean familyGlossaryEnabled() {
        return familyGlossaryEnabled.get();
    }

    public boolean asyncScanEnabled() {
        return asyncScanEnabled.get();
    }

    public boolean clearCacheOnBoot() {
        return clearCacheOnBoot.get();
    }

    public String targetLanguage() {
        return targetLanguage.get();
    }

    // —— toml 写入方法(GUI 修改参数后写回 toml)——

    /** 把 TransLibConfig 的当前值写回 toml(GUI 修改参数后调用)。 */
    public void saveFrom(TransLibConfig cfg) {
        cfpaEnabled.set(cfg.isCfpaEnabled());
        aiEnabled.set(cfg.isAiEnabled());
        familyGlossaryEnabled.set(cfg.isFamilyGlossaryEnabled());
        asyncScanEnabled.set(cfg.isAsyncScanEnabled());
        targetLanguage.set(cfg.getTargetLanguage().code());
        // clearCacheOnBoot 不回写(它是一次性触发标志,不反映运行状态)
    }

    /** 把 toml 值灌入 TransLibConfig(启动时调用)。 */
    public void loadInto(TransLibConfig cfg) {
        cfg.setCfpaEnabled(cfpaEnabled.get());
        cfg.setAiEnabled(aiEnabled.get());
        cfg.setFamilyGlossaryEnabled(familyGlossaryEnabled.get());
        cfg.setAsyncScanEnabled(asyncScanEnabled.get());
        cfg.setTargetLanguage(TargetLanguage.of(targetLanguage.get()));
    }
}
