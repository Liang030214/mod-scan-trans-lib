package com.modscantrans.neoforge.config;

import com.modscantrans.core.TargetLanguage;
import com.modscantrans.core.TransLibConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 模组扫描翻译支持库的 NeoForge 配置定义(对应 {@code config/mod_scan_trans_lib.toml})。
 *
 * <p>定义 7 个配置项(与独立设置 GUI 一一对应,6 开关 + 1 语言下拉):
 * <ul>
 *   <li>{@code modEnabled} —— 模组主开关(默认 true,关闭则翻译注入器跳过注入,等效于禁用本模组效果);</li>
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
 *
 * <p><b>modEnabled 与 clearCacheOnBoot 是 NeoForge 适配层独有的开关</b>(不在 core TransLibConfig 中),
 * 因为它们只控制注入行为 / 一次性触发,与 core 翻译服务无关,符合"不修改 core"硬性规则。
 */
public final class ModScanTransConfig {
    private final ModConfigSpec.Builder builder;
    private final ModConfigSpec.BooleanValue modEnabled;
    private final ModConfigSpec.BooleanValue cfpaEnabled;
    private final ModConfigSpec.BooleanValue aiEnabled;
    private final ModConfigSpec.BooleanValue familyGlossaryEnabled;
    private final ModConfigSpec.BooleanValue asyncScanEnabled;
    private final ModConfigSpec.BooleanValue clearCacheOnBoot;
    private final ModConfigSpec.ConfigValue<String> targetLanguage;
    private final ModConfigSpec spec;

    public ModScanTransConfig() {
        this.builder = new ModConfigSpec.Builder();

        modEnabled = builder.comment("模组主开关(默认 true;关闭后翻译注入器跳过注入,等同禁用本模组效果,但 core 各服务仍可正常运行)")
                .define("modEnabled", true);
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

    /** @return 模组主开关(关闭则翻译注入器跳过注入) */
    public boolean modEnabled() {
        return modEnabled.get();
    }

    /** 设置模组主开关(GUI 开关回写 toml 用)。 */
    public void setModEnabled(boolean value) {
        modEnabled.set(value);
    }

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

    /** 设置 clearCacheOnBoot(GUI 开关回写 toml 用)。 */
    public void setClearCacheOnBoot(boolean value) {
        clearCacheOnBoot.set(value);
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
