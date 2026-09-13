package com.modscantrans.core.cfpa;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * CFPA 资源索引:从 CFPA 仓库全树路径构建 {@code (modid, CFPA版本) → 资源路径} 映射,
 * 并提供按 <b>modID + MC 版本</b> 匹配的查询能力。
 *
 * <p><b>为何需要索引</b>:CFPA 资源路径为
 * {@code projects/assets/<slug>/<cfpa版本>/<namespace>/lang/zh_cn.json},
 * 其中 {@code <namespace>} 才是真 modid,而 {@code <slug>} 是连字符目录名(不等于 modid),
 * 无法用 modid 直接拼 URL。故先拉全树路径建索引,再按 (namespace, 版本) 查路径。
 *
 * <p><b>版本匹配</b>({@link #resolve}):
 * <ol>
 *   <li>归并匹配:在 modid 的版本中找 {@code v.isPrefixOf(mcVersion)} 者(CFPA 用主次版本
 *       {@code 1.21} 覆盖所有 {@code 1.21.x}),取最长(最新)的命中;</li>
 *   <li>跨版本回退(可选,遵循 CFPA packer 的 {@code fallbackVersions} 行为):
 *       当前版本族未命中时,按已知 CFPA 版本降序回退到更老版本。</li>
 * </ol>
 *
 * <p>本类不可变,线程安全。
 */
public final class CfpaIndex {
    /** CFPA 资源路径固定为 {@code projects/assets/<slug>/<ver>/<ns>/lang/zh_cn.json}(7 段)。 */
    private static final int PATH_LEN = 7;
    private static final String LANG_FILE = "zh_cn.json";

    private final Map<String, List<VerPath>> entries;
    private final List<ModVersion> knownVersionsDesc;
    private final int totalPaths;

    private CfpaIndex(Map<String, List<VerPath>> entries,
                      List<ModVersion> knownVersionsDesc, int totalPaths) {
        this.entries = entries;
        this.knownVersionsDesc = knownVersionsDesc;
        this.totalPaths = totalPaths;
    }

    /** 单条 (版本 → 路径)。 */
    record VerPath(ModVersion version, String path) {
    }

    /** 解析命中的结果。 */
    public record Resolved(String modId, String cfpaVersion, String path) {
    }

    /**
     * 从 CFPA 仓库全树路径集合构建索引。
     *
     * @param paths 所有形如 {@code projects/assets/<slug>/<ver>/<ns>/lang/zh_cn.json} 的路径
     * @return 不可变索引
     */
    public static CfpaIndex fromPaths(Collection<String> paths) {
        Map<String, List<VerPath>> map = new HashMap<>();
        Set<ModVersion> known = new TreeSet<>();
        int count = 0;
        if (paths != null) {
            for (String p : paths) {
                Resolved r = parsePath(p);
                if (r == null) {
                    continue;
                }
                String id = r.modId();
                map.computeIfAbsent(id, k -> new ArrayList<>())
                        .add(new VerPath(ModVersion.of(r.cfpaVersion()), r.path()));
                known.add(ModVersion.of(r.cfpaVersion()));
                count++;
            }
        }
        // 冻结内部列表
        Map<String, List<VerPath>> frozen = new HashMap<>();
        for (var e : map.entrySet()) {
            frozen.put(e.getKey(), List.copyOf(e.getValue()));
        }
        List<ModVersion> desc = new ArrayList<>(known);
        desc.sort(ModVersion.descending());
        return new CfpaIndex(Map.copyOf(frozen), List.copyOf(desc), count);
    }

    /** 空索引(离线 / 拉取失败时使用)。 */
    public static CfpaIndex empty() {
        return new CfpaIndex(Map.of(), List.of(), 0);
    }

    /**
     * 解析单条 CFPA 资源路径。
     *
     * @param path 仓库内相对路径
     * @return 解析出的 (modid=namespace, cfpa版本, path);不符合结构返回 null
     */
    static Resolved parsePath(String path) {
        if (path == null) {
            return null;
        }
        String[] parts = path.split("/");
        if (parts.length != PATH_LEN) {
            return null;
        }
        if (!"projects".equals(parts[0]) || !"assets".equals(parts[1])
                || !"lang".equals(parts[5]) || !LANG_FILE.equals(parts[6])) {
            return null;
        }
        String modid = parts[4];
        String version = parts[3];
        if (modid.isBlank() || version.isBlank()) {
            return null;
        }
        return new Resolved(modid.toLowerCase(Locale.ROOT), version, path);
    }

    /**
     * 按 modID + MC 版本匹配 CFPA 资源路径。
     *
     * @param modId           模组 modid(namespace)
     * @param mcVersion       用户 MC 版本(如 {@code 1.21.1})
     * @param enableFallback  当前版本族未命中时是否回退到更老版本
     * @return 命中结果(含实际 CFPA 版本与路径);未命中返回 null
     */
    public Resolved resolve(String modId, String mcVersion, boolean enableFallback) {
        if (modId == null || modId.isBlank()) {
            return null;
        }
        String id = modId.toLowerCase(Locale.ROOT);
        List<VerPath> list = entries.get(id);
        if (list == null || list.isEmpty()) {
            return null;
        }

        // 1) 归并匹配:找 v.isPrefixOf(mcVersion),取最新(最长)
        ModVersion matched = null;
        String matchedPath = null;
        for (VerPath vp : list) {
            if (vp.version().isPrefixOf(mcVersion)) {
                if (matched == null || vp.version().compareTo(matched) > 0) {
                    matched = vp.version();
                    matchedPath = vp.path();
                }
            }
        }
        if (matched != null) {
            return new Resolved(id, matched.value(), matchedPath);
        }
        if (!enableFallback) {
            return null;
        }

        // 2) 跨版本回退:取比当前版本族更老的 CFPA 版本,降序试
        ModVersion family = familyOf(mcVersion);
        for (ModVersion fv : knownVersionsDesc) {
            if (family != null && fv.compareTo(family) >= 0) {
                continue; // 跳过同族及更新版本
            }
            for (VerPath vp : list) {
                if (vp.version().equals(fv)) {
                    return new Resolved(id, fv.value(), vp.path());
                }
            }
        }
        return null;
    }

    /** @return 索引内不同 modid 数量 */
    public int modCount() {
        return entries.size();
    }

    /** @return 索引内 CFPA 资源路径总数 */
    public int totalPaths() {
        return totalPaths;
    }

    /** @return 已知 CFPA 版本(降序) */
    public List<ModVersion> knownVersions() {
        return knownVersionsDesc;
    }

    /** @return 索引是否为空(离线 / 拉取失败) */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** 找出 mcVersion 所属的 CFPA 版本族(全局已知版本中 isPrefixOf 的最新者)。 */
    private ModVersion familyOf(String mcVersion) {
        ModVersion best = null;
        for (ModVersion v : knownVersionsDesc) {
            if (v.isPrefixOf(mcVersion) && (best == null || v.compareTo(best) > 0)) {
                best = v;
            }
        }
        return best;
    }

    /**
     * 序列化为缓存行(每行 {@code <version>\t<modid>\t<path>})。
     *
     * <p>供本地缓存持久化用:首次联网拉取索引后写本地,后续启动 / 离线时读取,
     * 避免每次启动都打 GitHub API(未认证限流严重)。
     *
     * @return 缓存行列表
     */
    public java.util.List<String> toCacheLines() {
        java.util.List<String> lines = new java.util.ArrayList<>(totalPaths);
        for (var e : entries.entrySet()) {
            String modid = e.getKey();
            for (VerPath vp : e.getValue()) {
                lines.add(vp.version().value() + "\t" + modid + "\t" + vp.path());
            }
        }
        return lines;
    }

    /**
     * 从缓存行反序列化(每行 {@code <version>\t<modid>\t<path>})。
     *
     * @param lines 缓存行
     * @return 索引;格式不符的行被跳过
     */
    public static CfpaIndex fromCacheLines(java.util.Collection<String> lines) {
        java.util.List<String> paths = new java.util.ArrayList<>();
        if (lines != null) {
            for (String line : lines) {
                if (line == null) {
                    continue;
                }
                String[] p = line.split("\t", 3);
                if (p.length != 3 || p[0].isBlank() || p[1].isBlank() || p[2].isBlank()) {
                    continue;
                }
                // 重建为标准 CFPA 路径形式,再走 parsePath 校验
                paths.add(p[2]);
            }
        }
        return fromPaths(paths);
    }
}
