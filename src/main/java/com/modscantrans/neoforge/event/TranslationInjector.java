package com.modscantrans.neoforge.event;

import com.modscantrans.core.ModInfo;
import com.modscantrans.core.TargetLanguage;
import com.modscantrans.core.scanner.ScanResult;
import com.modscantrans.neoforge.ModScanTransLib;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.SharedConstants;
import net.minecraft.locale.Language;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 翻译注入器(NeoForge 事件层)。
 *
 * <p><b>职责</b>:订阅资源重载事件,在原版与所有模组语言文件加载完成后,
 * 将 core 层合并完成的翻译词条(CFPA &gt; AI 缓存 &gt; 实时 AI)注入游戏 I18n 系统。
 *
 * <p><b>注入时机保证</b>(硬性要求:晚于原版/其他模组语言加载,防止被覆盖):
 * <ol>
 *   <li>通过 {@link AddReloadListenerEvent} 注册一个 {@link PreparableReloadListener};</li>
 *   <li>在 {@code reload} 的异步准备阶段({@code backgroundExecutor})扫描模组 + 合并翻译;</li>
 *   <li>通过 {@link PreparationBarrier} 等待<b>所有</b>监听器(含 {@code LanguageManager})
 *       准备完成后,再在 {@code gameExecutor}(主线程)执行注入;</li>
 *   <li>此时 {@link Language#getInstance()} 已是加载完毕的 {@code ClientLanguage},
 *       我们的翻译最后写入,不会被原版/模组语言文件覆盖。</li>
 * </ol>
 *
 * <p><b>GUI 触发重载</b>:{@link #collectAndMergeTranslations()} 和
 * {@link #injectTranslations(Map)} 为公开静态方法,GUI "保存并重载" 按钮可直接调用,
 * 无需走完整资源重载流程(快速刷新翻译词条)。
 *
 * <p><b>注入方式</b>:调用 {@link Language#getInstance()} 取当前语言实例,
 * 取其 {@code getLanguageData()} 词条表。默认 {@link Language}(en_us)的词条表是
 * 可变 Map,可直接 {@code putAll};{@code ClientLanguage} 的词条表是不可变 Map,
 * 需通过反射替换 {@code storage} 字段(运行时改状态,不改 core / 不改原版代码)。
 *
 * <p><b>不修改 core</b>:本类只调用 core 层公开 API({@code TranslationService}),
 * 注入逻辑全部在 NeoForge 事件层实现。
 */
public final class TranslationInjector {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModScanTransLib.LOGGER.getName() + ".inject");

    /**
     * 获取当前用户的 MC 版本(CFPA 匹配用)。
     * 优先从 {@link SharedConstants} 读取,失败则回退到编译期常量。
     *
     * @return MC 版本号,如 {@code "1.21.1"}
     */
    private static String mcVersion() {
        try {
            return SharedConstants.getCurrentVersion().getId();
        } catch (Throwable t) {
            return "1.21.1";
        }
    }

    private TranslationInjector() {
    }

    /**
     * 注册事件监听器(在模组构造函数或 commonSetup 中调用一次)。
     *
     * <p>订阅 {@link AddReloadListenerEvent},向游戏资源重载链注入本模组的翻译监听器。
     */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(TranslationInjector::onAddReloadListener);
        LOGGER.info("[{}] 翻译注入器已注册(AddReloadListenerEvent)", ModScanTransLib.MODID);
    }

    /**
     * 资源重载监听器注册回调:向重载链追加翻译注入监听器。
     *
     * @param event 重载监听器注册事件
     */
    private static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new TranslationReloadListener());
        LOGGER.debug("[{}] 翻译重载监听器已加入资源重载链", ModScanTransLib.MODID);
    }

    /**
     * 扫描 mods 目录下所有 Jar,对每个模组调用 core 层 TranslationService
     * 按全局优先级合并翻译,返回合并后的 {@code 键 → 译文} 映射。
     *
     * <p>本方法可在任意线程调用(设计为后台线程执行,内部阻塞等待扫描完成)。
     * GUI "保存并重载" 按钮可直接调用本方法获取合并翻译。
     *
     * <p><b>modEnabled 短路</b>:若 NeoForge 层的模组主开关(modEnabled=false),
     * 本方法立即返回空 Map(不扫描、不合并、不注入),等效禁用本模组效果。
     * 此开关只在 NeoForge 适配层检查,不修改 core 任何代码。
     *
     * @return 合并后的翻译映射(可能为空,不会异常)
     */
    public static Map<String, String> collectAndMergeTranslations() {
        ModScanTransLib lib = ModScanTransLib.instance();
        if (lib == null || lib.translationService() == null || lib.scanner() == null) {
            LOGGER.warn("[{}] core 服务未就绪,跳过翻译注入", ModScanTransLib.MODID);
            return Map.of();
        }

        // 模组主开关检查(NeoForge 适配层独有,不在 core 中)
        if (!lib.modConfig().modEnabled()) {
            LOGGER.info("[{}] 模组主开关已关闭(modEnabled=false),跳过翻译注入", ModScanTransLib.MODID);
            return Map.of();
        }

        TargetLanguage target = lib.config().getTargetLanguage();
        Map<String, String> merged = new HashMap<>();

        try {
            List<ScanResult> results = lib.scanner()
                    .scanDirectory(FMLPaths.MODSDIR.get())
                    .join(); // 阻塞等待扫描完成(本方法已在后台线程)

            LOGGER.info("[{}] 扫描完成:{} 个模组,开始合并翻译(目标语言={})",
                    ModScanTransLib.MODID, results.size(), target.code());

            for (ScanResult r : results) {
                ModInfo info = r.modInfo();
                if (r.sourceEntries().isEmpty()) {
                    continue;
                }
                Map<String, String> modTrans = lib.translationService().translateMod(
                        info.modId(), r.sourceEntries(), mcVersion(), target);
                if (!modTrans.isEmpty()) {
                    merged.putAll(modTrans);
                }
            }
        } catch (RuntimeException e) {
            LOGGER.error("[{}] 扫描/合并翻译异常,跳过本次注入: " + e.getMessage(), e);
            return Map.of();
        }

        LOGGER.info("[{}] 翻译合并完成:共 {} 条词条待注入", ModScanTransLib.MODID, merged.size());
        return merged;
    }

    /**
     * 在主线程把合并翻译注入游戏当前语言表。
     *
     * <p>本方法必须<b>在主线程</b>调用(GUI 的重载回调通过
     * {@code Minecraft.getInstance().execute()} 切换到主线程后调用)。
     *
     * <p>默认 Language(en_us)的词条表可变,直接 putAll;ClientLanguage 的词条表不可变,
     * 用反射替换对应的 Map 字段(按类型查找,兼容不同 MC 版本的字段名)。
     *
     * @param translations 要注入的 {@code 键 → 译文} 映射(为空则不注入)
     */
    public static void injectTranslations(Map<String, String> translations) {
        if (translations == null || translations.isEmpty()) {
            return;
        }

        Language language = Language.getInstance();
        Map<String, String> languageData = language.getLanguageData();

        // 方案 A:词条表可变(默认 Language),直接 putAll
        try {
            languageData.putAll(translations);
            LOGGER.info("[{}] 翻译注入完成(直接 putAll):{} 条词条",
                    ModScanTransLib.MODID, translations.size());
            return;
        } catch (UnsupportedOperationException ignored) {
            // 不可变 Map(ClientLanguage),走反射替换
        }

        // 方案 B:用反射找到 getLanguageData() 对应的 Map 字段并替换为可变副本
        // (按类型 Map<String,String> + 引用匹配,兼容字段名变化)
        try {
            Map<String, String> replaced = new HashMap<>(languageData.size() + translations.size());
            replaced.putAll(languageData);
            replaced.putAll(translations);

            Field targetField = findStorageField(language, languageData);
            if (targetField == null) {
                LOGGER.error("[{}] 翻译注入失败:未找到语言词条表字段", ModScanTransLib.MODID);
                return;
            }
            targetField.setAccessible(true);
            targetField.set(language, replaced);

            LOGGER.info("[{}] 翻译注入完成(反射替换 {}#{}):{} 条词条",
                    ModScanTransLib.MODID, language.getClass().getSimpleName(),
                    targetField.getName(), translations.size());
        } catch (ReflectiveOperationException e) {
            LOGGER.error("[{}] 翻译注入失败:无法替换语言词条表字段", e);
        }
    }

    /**
     * 在语言实例中查找存储词条的 Map 字段。
     *
     * <p>遍历所有声明字段,找到类型为 {@code Map} 且与 {@code getLanguageData()}
     * 返回值同一引用的字段(即真正的词条表字段)。
     *
     * @param language     语言实例
     * @param languageData {@link Language#getLanguageData()} 的返回值
     * @return 匹配的字段,未找到返回 {@code null}
     */
    private static Field findStorageField(Language language, Map<String, String> languageData) {
        for (Field f : language.getClass().getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(f.getType())) {
                continue;
            }
            try {
                f.setAccessible(true);
                Object value = f.get(language);
                if (value == languageData) {
                    return f;
                }
            } catch (IllegalAccessException ignored) {
                // 跳过不可访问字段
            }
        }
        // 兜底:返回第一个 Map<String,String> 类型字段
        for (Field f : language.getClass().getDeclaredFields()) {
            if (Map.class.isAssignableFrom(f.getType())) {
                return f;
            }
        }
        return null;
    }

    /**
     * 翻译重载监听器:在资源重载的准备阶段扫描并合并翻译,
     * 在应用阶段(主线程)注入到游戏语言表。
     */
    private static final class TranslationReloadListener implements PreparableReloadListener {
        /** 异步扫描 + 翻译的结果(主线程注入时使用)。 */
        private volatile Map<String, String> pendingTranslations = Map.of();

        @Override
        public CompletableFuture<Void> reload(PreparationBarrier stage,
                                               ResourceManager resourceManager,
                                               ProfilerFiller preparationsProfiler,
                                               ProfilerFiller reloadProfiler,
                                               Executor backgroundExecutor,
                                               Executor gameExecutor) {
            // 1) 异步准备:扫描 mods 目录 + 调 TranslationService 合并翻译
            CompletableFuture<Void> prepare = CompletableFuture.runAsync(() -> {
                pendingTranslations = collectAndMergeTranslations();
            }, backgroundExecutor);

            // 2) 等待所有监听器准备完成(LanguageManager 也在内),再在主线程注入
            return prepare.thenCompose(v -> stage.wait(this))
                    .thenRunAsync(() -> injectTranslations(pendingTranslations), gameExecutor);
        }
    }
}
