package com.modscantrans.core.family;

import com.modscantrans.core.ModFamily;
import com.modscantrans.core.TargetLanguage;
import com.modscantrans.core.TransLibConfig;
import com.modscantrans.core.TranslationEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 术语库仓库:统管所有家族术语库 + 旁系参考库,并处理<b>跨家族互联写入</b>。
 *
 * <p><b>家族术语参考开关</b>(规则3,默认开启):
 * <ul>
 *   <li>开启时:家族模组的缓存读取、AI 翻译加载对应主模组术语库;</li>
 *   <li>关闭时:不加载家族术语(家族术语库查询直接返回空)。</li>
 *   <li><b>旁系模组永久启用旁系参考库,不受此开关控制</b>。</li>
 * </ul>
 *
 * <p><b>跨家族互联写入</b>(规则5):翻译词条同时存入两个关联家族术语库。
 * 调用 {@link #storeForMod} 时,若该 modid 命中多个家族(精确 + 前缀),或命中家族
 * 声明了 crossLinkedFamilyIds,词条会写入<b>所有</b>相关家族术语库。
 *
 * <p><b>读取顺序</b>(规则1,全局优先级):CFPA &gt; AI 缓存 &gt; 实时 AI。
 * 本仓库不直接做优先级裁决(由 i18n 合并器负责),只提供按家族 / 旁系查条目。
 *
 * <p>线程安全:内部用 ConcurrentHashMap 风格的 synchronized 块保护。
 */
public final class GlossaryStore {
    private final FamilyResolver resolver;
    private final TransLibConfig config;
    private final Map<String, FamilyGlossary> familyGlossaries = new LinkedHashMap<>();
    private final SidelineReference sidelineReference;
    private final Object lock = new Object();

    public GlossaryStore(FamilyResolver resolver, TransLibConfig config) {
        this.resolver = Objects.requireNonNull(resolver, "resolver 不能为空");
        this.config = Objects.requireNonNull(config, "config 不能为空");
        this.sidelineReference = new SidelineReference();
    }

    /**
     * 按 modid 存储一条翻译词条到对应家族术语库 / 旁系参考库。
     *
     * <p>家族术语参考开关关闭时:<b>家族模组的词条不写入家族术语库</b>(直接跳过);
     * 旁系模组不受开关控制,正常写入旁系参考库。
     *
     * <p>跨家族互联:命中多个家族时,词条写入所有命中家族的术语库;
     * 同时,命中家族声明的 crossLinkedFamilyIds 对应的家族术语库也会写入(双向)。
     *
     * @param entry 翻译条目(modId 字段必填)
     */
    public void storeForMod(TranslationEntry entry) {
        Objects.requireNonNull(entry, "entry 不能为空");
        if (entry.modId() == null || entry.modId().isBlank()) {
            return; // 无 modid 不处理
        }
        FamilyMatch match = resolver.resolve(entry.modId());
        if (match.isSidelined()) {
            // 旁系:永久启用旁系参考库,不受开关控制
            sidelineReference.put(entry);
            return;
        }
        // 家族模组:受家族术语参考开关控制
        if (!config.isFamilyGlossaryEnabled()) {
            return; // 开关关闭,不加载家族术语
        }
        // 写入所有命中家族
        List<String> targets = collectTargetFamilyIds(match);
        for (String fid : targets) {
            FamilyGlossary g = getOrCreate(fid);
            g.put(entry);
        }
    }

    /** 批量存储。 */
    public void storeAllForMod(Collection<TranslationEntry> entries) {
        if (entries == null) {
            return;
        }
        for (TranslationEntry e : entries) {
            storeForMod(e);
        }
    }

    /**
     * 按 modid + 语言键查询术语词条。
     *
     * <p>家族术语参考开关关闭时:家族模组查询返回空(不加载家族术语);
     * 旁系模组不受开关控制,正常查旁系参考库。
     *
     * @param modId    模组 modid
     * @param key      语言键
     * @param language 目标语言
     * @return 命中的术语条目;无则 empty
     */
    public Optional<TranslationEntry> lookupForMod(String modId, String key, TargetLanguage language) {
        if (modId == null || modId.isBlank() || key == null || key.isBlank()) {
            return Optional.empty();
        }
        FamilyMatch match = resolver.resolve(modId);
        if (match.isSidelined()) {
            return sidelineReference.get(key, language);
        }
        // 开关关闭:家族术语不加载
        if (!config.isFamilyGlossaryEnabled()) {
            return Optional.empty();
        }
        // 查所有命中家族(精确优先)
        for (FamilyMatch.ModFamilyRef ref : match.families()) {
            FamilyGlossary g = getOrCreate(ref.family().familyId());
            Optional<TranslationEntry> e = g.get(key, language);
            if (e.isPresent()) {
                return e;
            }
        }
        // 查跨家族互联的家族术语库
        for (String fid : match.crossLinkedFamilies()) {
            FamilyGlossary g = getOrCreate(fid);
            Optional<TranslationEntry> e = g.get(key, language);
            if (e.isPresent()) {
                return e;
            }
        }
        return Optional.empty();
    }

    /** @return 指定家族术语库(不存在则创建空库) */
    public FamilyGlossary getOrCreate(String familyId) {
        String fid = familyId.toLowerCase(Locale.ROOT);
        synchronized (lock) {
            return familyGlossaries.computeIfAbsent(fid, FamilyGlossary::new);
        }
    }

    /** @return 旁系参考库 */
    public SidelineReference sideline() {
        return sidelineReference;
    }

    /** @return 所有已创建的家族术语库(不可变快照) */
    public List<FamilyGlossary> familyGlossaries() {
        synchronized (lock) {
            return List.copyOf(familyGlossaries.values());
        }
    }

    /** 清空所有家族术语库(保留旁系参考库内置词条)。 */
    public void clearFamilyGlossaries() {
        synchronized (lock) {
            familyGlossaries.clear();
        }
    }

    /**
     * 汇总词条应写入的目标 familyId 列表(命中家族 + 跨家族互联家族,去重)。
     */
    private List<String> collectTargetFamilyIds(FamilyMatch match) {
        List<String> out = new ArrayList<>();
        for (FamilyMatch.ModFamilyRef ref : match.families()) {
            String fid = ref.family().familyId();
            if (!out.contains(fid)) {
                out.add(fid);
            }
        }
        for (String fid : match.crossLinkedFamilies()) {
            if (!out.contains(fid)) {
                out.add(fid);
            }
        }
        return out;
    }
}
