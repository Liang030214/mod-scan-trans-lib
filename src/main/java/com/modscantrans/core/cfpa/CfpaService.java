package com.modscantrans.core.cfpa;

import com.modscantrans.core.TransLibConfig;
import com.modscantrans.core.TransLibLogger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * CFPA 模块对外门面(加载器无关)。
 *
 * <p>封装 CFPA 索引的<b>本地缓存 + 联网刷新</b>、{@link TransLibConfig#isCfpaEnabled() CFPA 联网总开关}、
 * 跨版本回退开关,并保证<b>离线 / 联网关闭时尽量从本地缓存返回,实在无缓存则返回空、不阻塞流程</b>。
 *
 * <p><b>缓存策略</b>(避免每次启动都打 GitHub API,未认证限流严重):
 * <ol>
 *   <li>构造时若提供 {@code cachePath},先同步读取本地缓存作为初始索引(快,不联网);</li>
 *   <li>首次 {@link #fetch} 时若缓存为空,触发一次联网 {@link CfpaClient#refreshIndex()};</li>
 *   <li>联网刷新成功后写回本地缓存;</li>
 *   <li>联网失败(离线)且无缓存:索引记为失败哨兵,后续不再重试网络,直接返回 empty,
 *       直到调用 {@link #refresh()} 重试。</li>
 * </ol>
 *
 * <p>线程安全:索引采用 {@code volatile} + 同步刷新;{@link #fetch} 为只读查询。
 */
public final class CfpaService {
    /** 标记"已尝试联网加载但失败"的空哨兵,与"尚未加载"区分。 */
    private static final CfpaIndex LOAD_FAILED = CfpaIndex.empty();

    private final CfpaClient client;
    private final TransLibLogger logger;
    private final Path cachePath;
    private volatile CfpaIndex index = null; // null=未加载,LOAD_FAILED=联网失败,其它=已加载

    /** 不带本地缓存(纯联网模式)。 */
    public CfpaService(CfpaClient client, TransLibLogger logger) {
        this(client, logger, null);
    }

    /**
     * @param client    CFPA 拉取客户端
     * @param logger    日志器
     * @param cachePath 可选本地缓存文件路径(索引持久化);为 null 时不缓存
     */
    public CfpaService(CfpaClient client, TransLibLogger logger, Path cachePath) {
        this.client = Objects.requireNonNull(client, "client 不能为空");
        this.logger = Objects.requireNonNull(logger, "logger 不能为空");
        this.cachePath = cachePath;
        // 启动时先尝试读本地缓存(快,不联网)
        CfpaIndex cached = loadFromCache();
        this.index = cached;
    }

    /**
     * 按 modID + MC 版本拉取 CFPA 人工汉化。
     *
     * <p>遵循 {@link TransLibConfig}:
     * <ul>
     *   <li>{@code cfpaEnabled=false} 时直接返回 empty(联网总开关关闭);</li>
     *   <li>有本地缓存则用缓存查询(离线可用);</li>
     *   <li>无缓存且联网失败时返回 empty,不阻塞;</li>
     *   <li>{@code enableFallback} 由调用方传入(跨版本回退)。</li>
     * </ul>
     *
     * @param config          运行配置(读 CFPA 开关)
     * @param modId           模组 modid
     * @param mcVersion       用户 MC 版本
     * @param enableFallback  当前版本族未命中时是否回退到更老版本
     * @return 命中的 CFPA 资源;未命中 / 离线 / 开关关闭返回 empty
     */
    public Optional<CfpaResource> fetch(TransLibConfig config, String modId,
                                        String mcVersion, boolean enableFallback) {
        Objects.requireNonNull(config, "config 不能为空");
        if (!config.isCfpaEnabled()) {
            return Optional.empty();
        }
        ensureIndex();
        CfpaIndex idx = index;
        if (idx == null || idx == LOAD_FAILED || idx.isEmpty()) {
            return Optional.empty();
        }
        return client.fetch(modId, mcVersion, enableFallback);
    }

    /** 确保索引已加载:有缓存就用缓存;无缓存则联网拉一次,失败记为 LOAD_FAILED。 */
    private synchronized void ensureIndex() {
        if (index != null) {
            return; // 已加载(缓存或联网结果)或已失败
        }
        try {
            CfpaIndex fresh = client.refreshIndex();
            if (fresh.isEmpty()) {
                index = LOAD_FAILED;
                logger.warn("CFPA 索引联网加载为空,标记为离线模式(有本地缓存仍可用,否则返回空直到 refresh)");
            } else {
                index = fresh;
                saveToCache(fresh);
            }
        } catch (RuntimeException e) {
            index = LOAD_FAILED;
            logger.warn("CFPA 索引联网加载异常,标记为离线模式: " + e.getMessage(), e);
        }
    }

    /**
     * 强制刷新索引(网络恢复后调用以重新尝试)。成功后写回本地缓存。
     *
     * @return 刷新后的索引
     */
    public synchronized CfpaIndex refresh() {
        try {
            CfpaIndex fresh = client.refreshIndex();
            if (!fresh.isEmpty()) {
                index = fresh;
                saveToCache(fresh);
            }
            return fresh;
        } catch (RuntimeException e) {
            logger.warn("CFPA 索引刷新异常: " + e.getMessage(), e);
            return CfpaIndex.empty();
        }
    }

    /** @return 当前索引(可能为 null=未加载 / 空哨兵 / 已加载) */
    public CfpaIndex currentIndex() {
        return index;
    }

    /** @return 是否处于离线模式(联网加载失败) */
    public boolean isOffline() {
        return index == LOAD_FAILED;
    }

    private CfpaIndex loadFromCache() {
        if (cachePath == null) {
            return null;
        }
        try {
            if (!Files.isRegularFile(cachePath)) {
                return null;
            }
            List<String> lines = Files.readAllLines(cachePath, StandardCharsets.UTF_8);
            CfpaIndex cached = CfpaIndex.fromCacheLines(lines);
            if (!cached.isEmpty()) {
                logger.info("CFPA 索引读取本地缓存成功:modCount=" + cached.modCount()
                        + " totalPaths=" + cached.totalPaths());
                return cached;
            }
        } catch (IOException | RuntimeException e) {
            logger.warn("CFPA 索引本地缓存读取失败,将尝试联网: " + e.getMessage(), e);
        }
        return null;
    }

    private void saveToCache(CfpaIndex idx) {
        if (cachePath == null || idx == null || idx.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(cachePath.getParent());
            List<String> lines = idx.toCacheLines();
            Files.write(cachePath, lines, StandardCharsets.UTF_8);
            logger.info("CFPA 索引已写入本地缓存: " + cachePath + " (" + lines.size() + " 行)");
        } catch (IOException | RuntimeException e) {
            logger.warn("CFPA 索引本地缓存写入失败(不影响运行): " + e.getMessage(), e);
        }
    }
}
