package com.modscantrans.core.scanner;

import com.modscantrans.core.ModInfo;
import com.modscantrans.core.TransLibLogger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

/**
 * 模组扫描器:异步扫描 mods 文件夹所有 Jar 包,提取语言 json 文本,<b>不阻塞游戏主线程</b>。
 *
 * <p>纯 Java 实现,不依赖 Minecraft / NeoForge / Forge API,可被各加载器适配层复用。
 *
 * <p><b>异步模型</b>:调用方调用 {@link #scanDirectory(Path)} 后立即返回
 * {@link CompletableFuture},实际扫描在<b>后台守护线程</b>执行;调用方通过
 * {@code thenAccept / join} 在合适的时机取结果(例如 NeoForge 适配层在语言加载事件前等待)。
 * 单个 jar 解析失败不影响整体扫描,仅记录警告并跳过。
 *
 * <p><b>语言文件提取</b>:在 jar 内查找 {@code assets/<namespace>/lang/<lang>.json},
 * 解析为 {@code 键 → 文本} 映射。原文优先取 {@code en_us},其次 jar 内首个可用语言。
 *
 * <p><b>模组信息提取</b>(尽力而为):优先 {@code fabric.mod.json} 的
 * {@code id/name/version};其次 {@code META-INF/neoforge.mods.toml} /
 * {@code META-INF/mods.toml} 的 {@code modId/displayName/version};再其次 jar
 * {@code Manifest} 的 {@code Implementation-Version};最后用命名空间作 modid 兜底。
 */
public final class ModScanner implements AutoCloseable {
    /** 匹配 mods 目录下所有 .jar(含 .jar 结尾,大小写不敏感)。 */
    private static final PathMatcher JAR_MATCHER =
            FileSystems.getDefault().getPathMatcher("glob:**.{jar,JAR}");

    private static final Pattern FABRIC_ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern FABRIC_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern FABRIC_VERSION = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TOML_MODID = Pattern.compile("modId\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern TOML_DISPLAYNAME = Pattern.compile("displayName\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern TOML_VERSION = Pattern.compile("(?m)^version\\s*=\\s*\"([^\"]+)\"");

    private final ExecutorService executor;
    private final TransLibLogger logger;

    /**
     * 使用默认后台线程池(守护线程,并发度 = min(4, CPU 核数))。
     */
    public ModScanner() {
        this(defaultExecutor(), TransLibLogger.getDefault("ModScanner"));
    }

    /**
     * @param executor 后台执行器(扫描任务在此执行,不阻塞调用线程)
     * @param logger   日志器
     */
    public ModScanner(ExecutorService executor, TransLibLogger logger) {
        this.executor = Objects.requireNonNull(executor, "executor 不能为空");
        this.logger = Objects.requireNonNull(logger, "logger 不能为空");
    }

    /**
     * 异步扫描整个 mods 目录下的所有 Jar 包,提取语言文本与模组信息。
     *
     * <p>递归扫描子目录(兼容带版本子文件夹的 mods 目录)。单个 jar 失败仅记录警告,
     * 不会让整体 future 失败。
     *
     * @param modsDir mods 文件夹
     * @return future,完成后返回所有 jar 的扫描结果列表(可能为空,不会异常完成)
     */
    public CompletableFuture<List<ScanResult>> scanDirectory(Path modsDir) {
        Objects.requireNonNull(modsDir, "modsDir 不能为空");
        return CompletableFuture.supplyAsync(() -> {
            List<Path> jars;
            try {
                jars = listJars(modsDir);
            } catch (IOException e) {
                logger.warn("列出 mods 目录 jar 失败: " + modsDir, e);
                return List.of();
            }
            if (jars.isEmpty()) {
                logger.info("mods 目录无 jar,跳过扫描: " + modsDir);
                return List.of();
            }
            logger.info("mods 目录发现 " + jars.size() + " 个 jar,开始异步扫描: " + modsDir);
            return scanJarsParallel(jars);
        }, executor);
    }

    /**
     * 同步扫描单个 Jar(供测试或需要同步结果时使用)。
     *
     * @param jarPath jar 文件路径
     * @return 扫描结果;解析失败时返回 null
     */
    public ScanResult scanJar(Path jarPath) {
        Objects.requireNonNull(jarPath, "jarPath 不能为空");
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            List<ScanResult.LangFile> languages = extractLanguages(jar);
            Map<String, String> source = pickSourceEntries(languages);
            ModInfo info = extractModInfo(jarPath, jar, languages);
            return new ScanResult(info, source, languages);
        } catch (IOException e) {
            logger.warn("扫描 jar 失败: " + jarPath, e);
            return null;
        }
    }

    /** 并行扫描多个 jar,合并结果。 */
    private List<ScanResult> scanJarsParallel(List<Path> jars) {
        List<CompletableFuture<ScanResult>> futures = new ArrayList<>(jars.size());
        for (Path jar : jars) {
            futures.add(CompletableFuture.supplyAsync(() -> scanJar(jar), executor));
        }
        List<ScanResult> results = new ArrayList<>(jars.size());
        for (CompletableFuture<ScanResult> f : futures) {
            ScanResult r = f.join();
            if (r != null) {
                results.add(r);
            }
        }
        logger.info("异步扫描完成,成功 " + results.size() + "/" + jars.size());
        return List.copyOf(results);
    }

    /** 递归列出 mods 目录下所有 jar 文件。 */
    private List<Path> listJars(Path modsDir) throws IOException {
        List<Path> jars = new ArrayList<>();
        try (var stream = Files.walk(modsDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> JAR_MATCHER.matches(p.getFileName()))
                    .forEach(jars::add);
        }
        return jars;
    }

    /** 提取 jar 内所有 {@code assets/<ns>/lang/*.json} 语言文件。 */
    private List<ScanResult.LangFile> extractLanguages(JarFile jar) throws IOException {
        List<ScanResult.LangFile> files = new ArrayList<>();
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName();
            if (!name.startsWith("assets/") || !name.endsWith(".json") || !name.contains("/lang/")) {
                continue;
            }
            String[] parts = name.split("/");
            // assets/<namespace>/lang/<lang>.json => ["assets","<namespace>","lang","<lang>.json"]
            if (parts.length < 4 || !"lang".equals(parts[2])) {
                continue;
            }
            String namespace = parts[1];
            String fileName = parts[parts.length - 1];
            String langCode = fileName.substring(0, fileName.length() - ".json".length());
            Map<String, String> entries2 = readLangJson(jar, entry);
            if (!entries2.isEmpty()) {
                files.add(new ScanResult.LangFile(namespace, langCode, entries2));
            }
        }
        return files;
    }

    /** 读取并解析单个语言 json 条目。 */
    private Map<String, String> readLangJson(JarFile jar, ZipEntry entry) throws IOException {
        try (var in = jar.getInputStream(entry)) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return LangJson.parseObject(json);
        }
    }

    /** 选定原文词条:优先 en_us,其次任意第一个语言文件。 */
    private Map<String, String> pickSourceEntries(List<ScanResult.LangFile> languages) {
        for (ScanResult.LangFile f : languages) {
            if ("en_us".equals(f.langCode())) {
                return f.entries();
            }
        }
        return languages.isEmpty() ? Map.of() : languages.get(0).entries();
    }

    /** 尽力提取模组信息。 */
    private ModInfo extractModInfo(Path jarPath, JarFile jar, List<ScanResult.LangFile> languages) {
        String modId = "";
        String modName = "";
        String modVersion = "";
        String mcVersion = "";

        // 1) 优先 fabric.mod.json
        var fabricEntry = jar.getJarEntry("fabric.mod.json");
        if (fabricEntry != null) {
            try (var in = jar.getInputStream(fabricEntry)) {
                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                modId = find(modId, FABRIC_ID, content);
                modName = find(modName, FABRIC_NAME, content);
                modVersion = find(modVersion, FABRIC_VERSION, content);
            } catch (IOException e) {
                logger.warn("读取 fabric.mod.json 失败: " + jarPath, e);
            }
        }

        // 2) 其次 neoforge.mods.toml / mods.toml
        if (modId.isEmpty()) {
            for (String meta : new String[]{"META-INF/neoforge.mods.toml", "META-INF/mods.toml"}) {
                var tomlEntry = jar.getJarEntry(meta);
                if (tomlEntry == null) {
                    continue;
                }
                try (var in = jar.getInputStream(tomlEntry)) {
                    String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    modId = find(modId, TOML_MODID, content);
                    modName = find(modName, TOML_DISPLAYNAME, content);
                    modVersion = find(modVersion, TOML_VERSION, content);
                    break;
                } catch (IOException e) {
                    logger.warn("读取 " + meta + " 失败: " + jarPath, e);
                }
            }
        }

        // 3) modid 仍为空:用语言文件命名空间兜底
        if (modId.isEmpty() && !languages.isEmpty()) {
            modId = languages.get(0).namespace();
        }

        // 4) version 仍为空:用 jar Manifest 的 Implementation-Version
        if (modVersion.isEmpty()) {
            modVersion = readManifestVersion(jar);
        }

        // 5) 最终兜底:modid 用 jar 文件名
        if (modId.isEmpty()) {
            modId = jarPath.getFileName().toString().toLowerCase(Locale.ROOT);
        }

        // 原文语种:en_us 优先,否则用首个语言文件的 langCode
        String sourceLang = "en_us";
        if (!languages.isEmpty()) {
            sourceLang = languages.stream()
                    .filter(f -> "en_us".equals(f.langCode()))
                    .map(ScanResult.LangFile::langCode)
                    .findFirst()
                    .orElse(languages.get(0).langCode());
        }

        return new ModInfo(modId, modName, modVersion, mcVersion, jarPath, sourceLang);
    }

    private static String find(String fallback, Pattern p, String content) {
        if (!fallback.isEmpty()) {
            return fallback;
        }
        Matcher m = p.matcher(content);
        return m.find() ? m.group(1) : fallback;
    }

    private String readManifestVersion(JarFile jar) {
        try {
            Manifest mf = jar.getManifest();
            if (mf == null) {
                return "";
            }
            Attributes a = mf.getMainAttributes();
            String v = a.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
            if (v == null || v.isBlank()) {
                v = a.getValue("Specification-Version");
            }
            return v == null ? "" : v;
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static ExecutorService defaultExecutor() {
        int n = Math.min(4, Runtime.getRuntime().availableProcessors());
        if (n < 1) {
            n = 1;
        }
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "mod-scan-trans-" + counter.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        };
        return Executors.newFixedThreadPool(n, factory);
    }
}
