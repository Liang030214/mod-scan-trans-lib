package com.modscantrans.core.family;

import java.util.List;
import java.util.Locale;

/**
 * 模组家族识别结果。
 *
 * <p><b>模组识别依靠 modID 匹配家族表,不依靠模组显示名称</b>。一个 modid 经过家族解析器
 * 后得到本结果,分三类:
 * <ul>
 *   <li>{@link Kind#FAMILY_EXACT} —— 精确命中(主 modid 或显式成员),优先级最高;</li>
 *   <li>{@link Kind#FAMILY_PREFIX} —— 前缀命中(modid 以家族前缀开头),优先级次之;</li>
 *   <li>{@link Kind#SIDELINE} —— 旁系模组(无法匹配内置 / 自定义家族的独立模组),
 *       永久启用旁系参考库,不受家族术语参考开关控制。</li>
 * </ul>
 *
 * <p><b>跨家族互联</b>:当一个模组既是某家族的精确成员,又因前缀规则被另一家族前缀命中,
 * 互联模组测试用例要求词条同时存入两个关联家族术语库。此时
 * {@link #families()} 返回<b>所有</b>命中的家族(精确者优先排前,前缀者排后),
 * {@link #crossLinkedFamilies()} 返回这些家族之间互相声明的互联关系。
 *
 * @param kind              识别类别
 * @param modId             被识别的 modid(小写)
 * @param families          命中的所有家族(精确在前、前缀在后);旁系为空
 * @param crossLinkedFamilies  跨家族互联的 familyId 集合(来自命中的家族们)
 */
public record FamilyMatch(
        Kind kind,
        String modId,
        List<ModFamilyRef> families,
        List<String> crossLinkedFamilies) {

    /** 识别类别。 */
    public enum Kind {
        /** 精确命中(主 modid 或显式成员),优先级最高。 */
        FAMILY_EXACT,
        /** 前缀命中(modid 以家族前缀开头),优先级次之。 */
        FAMILY_PREFIX,
        /** 旁系模组(无法匹配任何家族),永久启用旁系参考库。 */
        SIDELINE
    }

    /**
     * 家族引用:家族定义 + 命中方式。
     *
     * @param family 命中的家族定义
     * @param exact  true=精确命中,false=前缀命中
     */
    public record ModFamilyRef(
            com.modscantrans.core.ModFamily family,
            boolean exact) {
    }

    public FamilyMatch {
        modId = (modId == null) ? "" : modId.toLowerCase(Locale.ROOT);
        families = families == null ? List.of() : List.copyOf(families);
        crossLinkedFamilies = crossLinkedFamilies == null
                ? List.of() : List.copyOf(crossLinkedFamilies.stream()
                        .map(s -> s == null ? "" : s.toLowerCase(Locale.ROOT))
                        .filter(s -> !s.isEmpty())
                        .distinct()
                        .toList());
    }

    /**
     * 构造旁系结果。
     *
     * @param modId 被识别的 modid
     * @return 旁系识别结果
     */
    public static FamilyMatch sideline(String modId) {
        return new FamilyMatch(Kind.SIDELINE, modId, List.of(), List.of());
    }

    /** @return 是否旁系模组(永久启用旁系参考库) */
    public boolean isSidelined() {
        return kind == Kind.SIDELINE;
    }

    /** @return 是否命中家族(精确或前缀) */
    public boolean isFamilyMember() {
        return kind == Kind.FAMILY_EXACT || kind == Kind.FAMILY_PREFIX;
    }

    /**
     * @return 主家族(命中的第一个家族);旁系返回空
     */
    public java.util.Optional<ModFamilyRef> primaryFamily() {
        return families.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(families.get(0));
    }
}
