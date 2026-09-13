package com.modscantrans.core;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * 扫描到的模组信息(加载器无关)。
 *
 * <p>由扫描模块({@code com.modscantrans.core.scanner})从 mods 文件夹的 Jar 中提取。
 * 模组识别依靠 <b>modID 匹配家族表</b>,不依靠模组显示名称,故 {@link #modId}
 * 规范化为小写(MC 中 modid 通常全小写)。
 *
 * @param modId           模组唯一标识,规范化为小写
 * @param modName         模组显示名(仅作展示,不参与家族识别)
 * @param modVersion      模组版本
 * @param mcVersion       模组声明的目标 MC 版本
 * @param jarPath         Jar 文件路径
 * @param sourceLanguage  模组自带原文语种(通常 {@code en_us}),规范化为小写
 */
public record ModInfo(
        String modId,
        String modName,
        String modVersion,
        String mcVersion,
        Path jarPath,
        String sourceLanguage) {

    public ModInfo {
        Objects.requireNonNull(modId, "modId 不能为空");
        if (modId.isBlank()) {
            throw new IllegalArgumentException("modId 不能为空白");
        }
        modId = modId.toLowerCase(Locale.ROOT);
        modName = modName == null ? "" : modName;
        modVersion = modVersion == null ? "" : modVersion;
        mcVersion = mcVersion == null ? "" : mcVersion;
        Objects.requireNonNull(jarPath, "jarPath 不能为空");
        sourceLanguage = (sourceLanguage == null || sourceLanguage.isBlank())
                ? "en_us" : sourceLanguage.toLowerCase(Locale.ROOT);
    }
}
