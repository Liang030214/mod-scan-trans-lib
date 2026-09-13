package com.modscantrans.core.cfpa;

import com.modscantrans.core.LangJson;
import com.modscantrans.core.TransLibLogger;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CFPA 词条拉取的默认 HTTP 实现(加载器无关,基于 JDK {@code HttpClient})。
 *
 * <p><b>建索引</b>:{@link #refreshIndex()} 调 GitHub git tree API(recursive=1)拉取
 * CFPA 仓库全树路径,正则提取所有 {@code "path":"..."} 值,交给
 * {@link CfpaIndex#fromPaths} 构建 {@code (modid, 版本) → 路径} 索引。
 *
 * <p><b>拉词条</b>:{@link #fetch} 先 {@link CfpaIndex#resolve} 得路径,再用
 * <b>jsDelivr CDN</b>({@code https://cdn.jsdelivr.net/gh/CFPAOrg/...@main/<path>})
 * 拉取 {@code zh_cn.json} 并解析为词条映射。jsDelivr 有 CDN 缓存,比直连 raw 更稳。
 *
 * <p><b>容错</b>:任何网络 / 解析异常均返回空结果(索引 → {@link CfpaIndex#empty()},
 * 词条 → {@link Optional#empty()}),<b>不抛异常、不阻塞流程</b>。
 *
 * <p><b>代理</b>:{@code HttpClient} 使用 {@link java.net.ProxySelector#getDefault()},
 * 自动遵循 JVM 系统属性 {@code https.proxyHost/Port}(便于沙箱 / 企业网络)。
 */
public final class HttpCfpaClient implements CfpaClient {
    private static final String REPO = "CFPAOrg/Minecraft-Mod-Language-Package";
    private static final String DEFAULT_BRANCH = "main";
    private static final String TREE_API =
            "https://api.github.com/repos/" + REPO + "/git/trees/" + DEFAULT_BRANCH + "?recursive=1";
    private static final String CDN_BASE =
            "https://cdn.jsdelivr.net/gh/" + REPO + "@" + DEFAULT_BRANCH + "/";

    private static final Pattern PATH_FIELD = Pattern.compile("\"path\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TRUNCATED = Pattern.compile("\"truncated\"\\s*:\\s*(true|false)");

    private final HttpClient http;
    private final TransLibLogger logger;
    private final Duration requestTimeout;
    private final String githubToken;
    private volatile CfpaIndex index = CfpaIndex.empty();

    /** 使用默认超时(索引 30s / 词条 15s),无 token(受未认证限流)。 */
    public HttpCfpaClient(TransLibLogger logger) {
        this(logger, null, Duration.ofSeconds(30), Duration.ofSeconds(15));
    }

    /**
     * @param logger        日志器
     * @param githubToken   可选 GitHub token(认证后 5000/小时,避免未认证限流);可为 null
     * @param indexTimeout  索引拉取超时
     * @param entryTimeout  词条拉取超时
     */
    public HttpCfpaClient(TransLibLogger logger, String githubToken,
                          Duration indexTimeout, Duration entryTimeout) {
        this.logger = java.util.Objects.requireNonNull(logger, "logger 不能为空");
        this.requestTimeout = entryTimeout;
        this.githubToken = githubToken;
        this.http = HttpClient.newBuilder()
                .proxy(java.net.ProxySelector.getDefault())
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        // 索引超时单独用 indexTimeout(见 refreshIndex)
        this.indexTimeout = indexTimeout;
    }

    private final Duration indexTimeout;

    @Override
    public CfpaIndex refreshIndex() {
        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(TREE_API))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "mod-scan-trans-lib")
                    .timeout(indexTimeout)
                    .GET();
            if (githubToken != null && !githubToken.isBlank()) {
                // 认证后限流提升至 5000/小时,避免未认证 60/小时被限流
                rb.header("Authorization", "Bearer " + githubToken);
            }
            HttpRequest req = rb.build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                logger.warn("CFPA tree API 返回状态码 " + resp.statusCode() + ",索引置空");
                index = CfpaIndex.empty();
                return index;
            }
            String body = resp.body();
            if (TRUNCATED.matcher(body).find() && TRUNCATED.matcher(body).group(1).equals("true")) {
                logger.warn("CFPA tree API 返回被截断(truncated=true),索引可能不完整");
            }
            List<String> paths = new ArrayList<>();
            Matcher m = PATH_FIELD.matcher(body);
            while (m.find()) {
                paths.add(m.group(1));
            }
            index = CfpaIndex.fromPaths(paths);
            logger.info("CFPA 索引刷新完成:共 " + paths.size() + " 个路径,命中 "
                    + index.modCount() + " 个 modid,已知版本 " + index.knownVersions());
            return index;
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.warn("CFPA 索引刷新失败(离线?),索引置空: " + e.getMessage(), e);
            index = CfpaIndex.empty();
            return index;
        }
    }

    @Override
    public Optional<CfpaResource> fetch(String modId, String mcVersion, boolean enableFallback) {
        CfpaIndex idx = index;
        if (idx.isEmpty()) {
            // 索引为空(未刷新或离线):尝试刷新一次
            idx = refreshIndex();
            if (idx.isEmpty()) {
                return Optional.empty();
            }
        }
        CfpaIndex.Resolved r = idx.resolve(modId, mcVersion, enableFallback);
        if (r == null) {
            return Optional.empty();
        }
        try {
            String url = CDN_BASE + r.path();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "mod-scan-trans-lib")
                    .timeout(requestTimeout)
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                logger.warn("CFPA 词条拉取失败 [" + modId + "@" + r.cfpaVersion() + "] 状态码 " + resp.statusCode());
                return Optional.empty();
            }
            var entries = LangJson.parseObject(resp.body());
            if (entries.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new CfpaResource(r.modId(), r.cfpaVersion(), entries, r.path()));
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.warn("CFPA 词条拉取异常 [" + modId + "@" + mcVersion + "]: " + e.getMessage(), e);
            return Optional.empty();
        }
    }

    /** @return 当前内存中的索引(可能为空) */
    public CfpaIndex currentIndex() {
        return index;
    }
}
