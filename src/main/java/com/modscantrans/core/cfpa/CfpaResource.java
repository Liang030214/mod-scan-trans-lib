package com.modscantrans.core.cfpa;

import com.modscantrans.core.EntrySource;
import com.modscantrans.core.TargetLanguage;
import java.util.Map;
import java.util.Objects;

/**
 * CFPA 社区人工汉化资源(最高优先级,禁止 AI 覆盖)。
 *
 * <p>由 {@link CfpaClient} 按 <b>modID + MC 版本</b>匹配后拉取得到。
 * 词条来源固定为 {@link EntrySource#CFPA},目标语言固定为 {@link TargetLanguage#ZH_CN}
 * (CFPA 目前只产出简体中文人工汉化)。
 *
 * @param modId        命中的模组 modid(小写,即 CFPA 资源路径里的 namespace)
 * @param mcVersion   实际命中的 CFPA 版本目录(如 {@code 1.21},可能与请求版本不同——经归并/回退)
 * @param entries      人工汉化词条({@code 键 → 简体中文文本})
 * @param sourcePath   在 CFPA 仓库内的相对路径(如 {@code projects/assets/.../zh_cn.json})
 */
public record CfpaResource(
        String modId,
        String mcVersion,
        Map<String, String> entries,
        String sourcePath) {

    /** CFPA 产出的目标语言固定为简体中文。 */
    public static final TargetLanguage LANGUAGE = TargetLanguage.ZH_CN;
    /** CFPA 词条来源固定为最高优先级。 */
    public static final EntrySource SOURCE = EntrySource.CFPA;

    public CfpaResource {
        Objects.requireNonNull(modId, "modId 不能为空");
        if (modId.isBlank()) {
            throw new IllegalArgumentException("modId 不能为空白");
        }
        modId = modId.toLowerCase(java.util.Locale.ROOT);
        Objects.requireNonNull(mcVersion, "mcVersion 不能为空");
        entries = entries == null ? Map.of() : Map.copyOf(entries);
        sourcePath = sourcePath == null ? "" : sourcePath;
    }
}
