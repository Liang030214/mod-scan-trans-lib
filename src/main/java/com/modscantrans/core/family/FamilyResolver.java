package com.modscantrans.core.family;

import com.modscantrans.core.ModFamily;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 模组家族解析器:加载内置家族表 + 用户自定义家族配置,并按 <b>modID</b> 识别家族。
 *
 * <p><b>硬性规则</b>:模组识别依靠 modID 匹配家族表,不依靠模组显示名称。
 *
 * <p><b>匹配优先级</b>:<b>精确命中</b>(主 modid 或显式成员)优先于<b>前缀命中</b>。
 * 这样跨家族互联模组(如 Create Crafts &amp; Additions,拥有独立家族定义且 modid 以
 * {@code create} 前缀命中 Create 家族)会被自身家族精确命中,而不会被 Create 家族的前缀
 * 规则抢走。命中结果中精确者排前,前缀者排后,均纳入 {@link FamilyMatch#families()}。
 *
 * <p>无法匹配任何家族的模组为<b>旁系模组</b>({@link FamilyMatch.Kind#SIDELINE}),
 * 永久启用旁系参考库,不受家族术语参考开关控制。
 *
 * <p>本类不可变(家族表在构造后冻结),线程安全(查询只读)。
 */
public final class FamilyResolver {
    /** 家族表,按 familyId 索引;LinkedHashMap 保持插入顺序便于稳定输出。 */
    private final Map<String, ModFamily> familiesById;
    /** 命中时返回的引用数组快照(避免每次查询重建)。 */
    private final List<ModFamily> familyList;

    private FamilyResolver(Map<String, ModFamily> familiesById) {
        this.familiesById = Map.copyOf(familiesById);
        this.familyList = List.copyOf(familiesById.values());
    }

    /**
     * 仅加载内置家族表构造。
     */
    public static FamilyResolver withBuiltins() {
        return withCustom(List.of());
    }

    /**
     * 加载内置家族表 + 用户自定义家族配置构造。
     *
     * <p><b>合并规则</b>:自定义家族按 familyId 去重;若 familyId 与内置家族冲突,
     * <b>自定义覆盖内置</b>(用户可修正内置家族的前缀 / 成员 / 互联声明)。
     *
     * @param customFamilies 用户自定义家族(可为空集合)
     * @return 解析器
     */
    public static FamilyResolver withCustom(Collection<ModFamily> customFamilies) {
        Map<String, ModFamily> map = new LinkedHashMap<>();
        // 先放内置
        for (ModFamily f : BuiltInFamilies.all()) {
            map.put(f.familyId(), f);
        }
        // 自定义覆盖内置
        if (customFamilies != null) {
            for (ModFamily f : customFamilies) {
                Objects.requireNonNull(f, "自定义家族不能为 null");
                map.put(f.familyId(), f);
            }
        }
        return new FamilyResolver(map);
    }

    /**
     * 按 modID 识别模组所属家族。
     *
     * <p>流程:先扫所有家族的<b>精确命中</b>(主 modid / 显式成员),命中则纳入;
     * 若无精确命中,再扫<b>前缀命中</b>;两者皆无则判为旁系。
     * 跨家族互联:把命中家族们互相声明的 crossLinkedFamilyIds 汇总返回。
     *
     * @param modId 模组 modid(将规范化为小写)
     * @return 识别结果(家族或旁系)
     */
    public FamilyMatch resolve(String modId) {
        if (modId == null || modId.isBlank()) {
            return FamilyMatch.sideline("");
        }
        String id = modId.toLowerCase(Locale.ROOT);

        // 1) 精确命中
        List<FamilyMatch.ModFamilyRef> exacts = new ArrayList<>();
        for (ModFamily f : familyList) {
            if (f.isExactMember(id)) {
                exacts.add(new FamilyMatch.ModFamilyRef(f, true));
            }
        }
        if (!exacts.isEmpty()) {
            return new FamilyMatch(FamilyMatch.Kind.FAMILY_EXACT, id, exacts, collectCrossLinks(exacts));
        }

        // 2) 前缀命中(仅在无精确命中时)
        List<FamilyMatch.ModFamilyRef> prefixes = new ArrayList<>();
        for (ModFamily f : familyList) {
            if (f.matchesByPrefix(id)) {
                prefixes.add(new FamilyMatch.ModFamilyRef(f, false));
            }
        }
        if (!prefixes.isEmpty()) {
            return new FamilyMatch(FamilyMatch.Kind.FAMILY_PREFIX, id, prefixes, collectCrossLinks(prefixes));
        }

        // 3) 旁系
        return FamilyMatch.sideline(id);
    }

    /** 汇总命中家族们互相声明的 crossLinkedFamilyIds。 */
    private List<String> collectCrossLinks(List<FamilyMatch.ModFamilyRef> refs) {
        List<String> out = new ArrayList<>();
        for (FamilyMatch.ModFamilyRef r : refs) {
            out.addAll(r.family().crossLinkedFamilyIds());
        }
        return out;
    }

    /** @return 所有家族(不可变) */
    public List<ModFamily> families() {
        return familyList;
    }

    /** @return 家族数量 */
    public int familyCount() {
        return familyList.size();
    }

    /** @return 所有内置 + 自定义 familyId */
    public List<String> familyIds() {
        return List.copyOf(familiesById.keySet());
    }
}
