package com.modscantrans.core;

import java.util.Objects;

/**
 * 模组扫描翻译支持库的运行配置(纯数据,加载器无关)。
 *
 * <p>本类只承载配置值,<b>不负责 toml 读写</b>;toml 读写由 NeoForge / Forge 适配层负责。
 * 适配层在启动时填充本对象,再交给 core 层各模块使用;GUI 修改参数时同步更新本对象,
 * 并由适配层写入 toml。
 *
 * <p>读写策略:字段以 {@code volatile} 声明,读无需加锁(异步扫描线程、GUI 线程均可读);
 * 写通过 {@code synchronized} 方法保证原子。目标语言变更属于"重配置",建议触发重新扫描/合并。
 *
 * <p><b>硬性规则映射</b>:
 * <ul>
 *   <li>{@link #cfpaEnabled} —— CFPA 联网总开关;</li>
 *   <li>{@link #aiEnabled} —— AI 翻译总开关(关闭则不调用实时 AI 机翻);</li>
 *   <li>{@link #familyGlossaryEnabled} —— 家族术语参考开关(默认开启,仅家族模组生效;
 *       旁系模组永久启用旁系参考库,不受此开关控制);</li>
 *   <li>{@link #asyncScanEnabled} —— 异步扫描开关;</li>
 *   <li>{@link #targetLanguage} —— 目标语言,完全由用户手动选择,程序不读 IP、不按地理位置切换。</li>
 * </ul>
 */
public final class TransLibConfig {
    private volatile boolean cfpaEnabled = true;
    private volatile boolean aiEnabled = true;
    private volatile boolean familyGlossaryEnabled = true;
    private volatile boolean asyncScanEnabled = true;
    private volatile TargetLanguage targetLanguage = TargetLanguage.DEFAULT;

    public boolean isCfpaEnabled() {
        return cfpaEnabled;
    }

    public synchronized void setCfpaEnabled(boolean cfpaEnabled) {
        this.cfpaEnabled = cfpaEnabled;
    }

    public boolean isAiEnabled() {
        return aiEnabled;
    }

    public synchronized void setAiEnabled(boolean aiEnabled) {
        this.aiEnabled = aiEnabled;
    }

    /**
     * 家族术语参考开关(默认开启)。
     * <p>仅家族模组生效:开启时缓存读取、AI 翻译加载对应主模组术语库;
     * 关闭则不加载家族术语。<b>旁系模组永久启用旁系参考库,不受此开关控制。</b>
     */
    public boolean isFamilyGlossaryEnabled() {
        return familyGlossaryEnabled;
    }

    public synchronized void setFamilyGlossaryEnabled(boolean familyGlossaryEnabled) {
        this.familyGlossaryEnabled = familyGlossaryEnabled;
    }

    public boolean isAsyncScanEnabled() {
        return asyncScanEnabled;
    }

    public synchronized void setAsyncScanEnabled(boolean asyncScanEnabled) {
        this.asyncScanEnabled = asyncScanEnabled;
    }

    /**
     * 目标翻译语言,完全由用户手动选择,程序不读取 IP、不按地理位置自动切换。
     * 默认 {@link TargetLanguage#ZH_CN}。
     */
    public TargetLanguage getTargetLanguage() {
        return targetLanguage;
    }

    public synchronized void setTargetLanguage(TargetLanguage targetLanguage) {
        this.targetLanguage = Objects.requireNonNull(targetLanguage, "目标语言不能为空");
    }

    /**
     * 以当前配置生成不可变快照,供异步任务安全使用(避免任务执行期间配置被改)。
     *
     * @return 当前配置的不可变快照
     */
    public synchronized ConfigSnapshot snapshot() {
        return new ConfigSnapshot(
                cfpaEnabled, aiEnabled, familyGlossaryEnabled,
                asyncScanEnabled, targetLanguage);
    }

    /**
     * 配置的不可变快照。
     */
    public record ConfigSnapshot(
            boolean cfpaEnabled,
            boolean aiEnabled,
            boolean familyGlossaryEnabled,
            boolean asyncScanEnabled,
            TargetLanguage targetLanguage) {
    }
}
