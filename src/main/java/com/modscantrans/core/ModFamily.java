package com.modscantrans.core;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 模组家族定义。
 *
 * <p><b>模组识别依靠 modID 匹配家族表,不依靠模组显示名称</b>。一个 modId 命中
 * 主 modid、显式成员列表或前缀规则即归入此家族。
 *
 * <p>匹配优先级(由家族解析器实现):<b>精确命中</b>(主 modid 或显式成员)
 * 优先于<b>前缀命中</b>。这样跨家族互联模组(如 Create Crafts &amp; Additions,
 * 拥有独立家族定义且为 Create 家族前缀 {@code create} 命中目标)会被其自身家族
 * 精确命中,而不会被 Create 家族的前缀规则抢走。
 *
 * <p>{@link #crossLinkedFamilyIds()} 声明跨家族互联:词条同时存入两个关联家族术语库。
 * 例如 Create Crafts &amp; Additions 与 Create 互相声明,词条双向存入双方术语库。
 *
 * @param familyId              家族唯一标识,如 {@code create}
 * @param displayName           显示名,如 {@code 机械动力家族}
 * @param mainModId             主模组 modid,如 {@code create};自动纳入成员集合
 * @param memberModIds          显式成员 modid 集合(不含主模组,会自动并入)
 * @param memberPrefixes        前缀规则(modid 以这些前缀开头即归入),小写
 * @param crossLinkedFamilyIds  跨家族互联的 familyId 集合,词条双向存入双方术语库
 */
public record ModFamily(
        String familyId,
        String displayName,
        String mainModId,
        Set<String> memberModIds,
        Set<String> memberPrefixes,
        Set<String> crossLinkedFamilyIds) {

    public ModFamily {
        Objects.requireNonNull(familyId, "familyId 不能为空");
        if (familyId.isBlank()) {
            throw new IllegalArgumentException("familyId 不能为空白");
        }
        Objects.requireNonNull(mainModId, "mainModId 不能为空");
        if (mainModId.isBlank()) {
            throw new IllegalArgumentException("mainModId 不能为空白");
        }
        familyId = familyId.toLowerCase(Locale.ROOT);

        var members = lowerCaseSet(memberModIds);
        var prefixes = lowerCaseSet(memberPrefixes);
        var cross = lowerCaseSet(crossLinkedFamilyIds);

        // 主 modid 自动纳入成员集合,便于统一精确匹配
        String mainLower = mainModId.toLowerCase(Locale.ROOT);
        if (!members.contains(mainLower)) {
            var tmp = new LinkedHashSet<>(members);
            tmp.add(mainLower);
            members = Set.copyOf(tmp);
        }
        memberModIds = members;
        memberPrefixes = prefixes;
        crossLinkedFamilyIds = cross;
    }

    private static Set<String> lowerCaseSet(Set<String> src) {
        if (src == null || src.isEmpty()) {
            return Set.of();
        }
        var tmp = new LinkedHashSet<String>();
        for (var s : src) {
            Objects.requireNonNull(s, "成员不能为 null");
            if (s.isBlank()) {
                throw new IllegalArgumentException("成员不能为空白");
            }
            tmp.add(s.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(tmp);
    }

    /**
     * 精确命中:modId 为主模组或显式成员。
     * <p>家族解析器优先使用本方法,精确命中者优先于前缀命中者。
     *
     * @param modId 待识别的 modid
     * @return 是否精确命中
     */
    public boolean isExactMember(String modId) {
        if (modId == null || modId.isBlank()) {
            return false;
        }
        return memberModIds.contains(modId.toLowerCase(Locale.ROOT));
    }

    /**
     * 前缀命中:modId 以任一前缀开头。
     * <p>仅当所有家族均未精确命中时,解析器才使用本方法。
     *
     * @param modId 待识别的 modid
     * @return 是否前缀命中
     */
    public boolean matchesByPrefix(String modId) {
        if (modId == null || modId.isBlank() || memberPrefixes.isEmpty()) {
            return false;
        }
        String lower = modId.toLowerCase(Locale.ROOT);
        for (var p : memberPrefixes) {
            if (lower.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 综合命中(精确或前缀),供简单场景使用。
     *
     * @param modId 待识别的 modid
     * @return 是否命中
     */
    public boolean matches(String modId) {
        return isExactMember(modId) || matchesByPrefix(modId);
    }
}
