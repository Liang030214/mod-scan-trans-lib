package com.modscantrans.core.cache;

import com.modscantrans.core.TargetLanguage;
import com.modscantrans.core.TransLibLogger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * AI 翻译缓存读写器(加载器无关,纯 Java)。
 *
 * <p><b>缓存条目绑定目标语言,同原文不同语种分开存储</b>(硬性规则)。
 * 内存结构:{@code (key, language) → CacheEntry},查询按 (key, language) 精确命中。
 *
 * <p><b>持久化</b>:启动时从缓存文件读入内存;{@link #flush} 时把内存全量写回文件
 * (TSV 格式,见 {@link CacheEntry#toCacheLine()})。写入时合并去重:同 (key, language)
 * 的条目以最新 createdAt 为准(后写覆盖先写),保证缓存新鲜度。
 *
 * <p><b>线程安全</b>:读写均 synchronized 保护同一把锁。
 *
 * <p><b>离线可用</b>:缓存文件在本地,断网仍可读;{@link #flush} 失败仅记警告,
 * 不影响内存中的缓存查询。
 */
public final class TranslationCache {
    /** 缓存文件第一行:格式版本号(用于兼容检测)。 */
    private static final String VERSION_LINE_PREFIX = "#version=";

    private final Path cacheFile;
    private final TransLibLogger logger;
    private final Object lock = new Object();
    /** (key, language) → CacheEntry。 */
    private final Map<CacheKey, CacheEntry> entries = new LinkedHashMap<>();
    private boolean dirty = false;

    public TranslationCache(Path cacheFile, TransLibLogger logger) {
        this.cacheFile = Objects.requireNonNull(cacheFile, "cacheFile 不能为空");
        this.logger = Objects.requireNonNull(logger, "logger 不能为空");
        loadFromDisk();
    }

    /**
     * 查询某语言键在指定目标语言下的缓存译文。
     *
     * @param key      语言键
     * @param language 目标语言
     * @return 缓存条目;未命中返回 empty
     */
    public Optional<CacheEntry> get(String key, TargetLanguage language) {
        if (key == null || language == null) {
            return Optional.empty();
        }
        synchronized (lock) {
            return Optional.ofNullable(entries.get(new CacheKey(key, language)));
        }
    }

    /**
     * 写入一条缓存(实时 AI 翻译成功后调用)。
     * <p>同 (key, language) 已有条目时,新条目 createdAt 更大才覆盖(保证新鲜度)。
     *
     * @param entry 缓存条目
     */
    public void put(CacheEntry entry) {
        Objects.requireNonNull(entry, "entry 不能为空");
        synchronized (lock) {
            CacheKey ck = new CacheKey(entry.key(), entry.language());
            entries.merge(ck, entry, (oldE, newE) ->
                    newE.createdAt() > oldE.createdAt() ? newE : oldE);
            dirty = true;
        }
    }

    /** 批量写入。 */
    public void putAll(Collection<CacheEntry> c) {
        if (c == null) {
            return;
        }
        for (CacheEntry e : c) {
            put(e);
        }
    }

    /**
     * 导出指定目标语言下的全部缓存({@code 键 → 译文})。
     *
     * @param language 目标语言
     * @return 不可变映射
     */
    public Map<String, String> export(TargetLanguage language) {
        synchronized (lock) {
            Map<String, String> out = new LinkedHashMap<>();
            for (var e : entries.entrySet()) {
                if (e.getKey().language.equals(language)) {
                    out.put(e.getValue().key(), e.getValue().translated());
                }
            }
            return Map.copyOf(out);
        }
    }

    /** @return 缓存条目总数 */
    public int size() {
        synchronized (lock) {
            return entries.size();
        }
    }

    /** @return 指定语言的缓存条目数 */
    public int size(TargetLanguage language) {
        synchronized (lock) {
            int n = 0;
            for (CacheKey k : entries.keySet()) {
                if (k.language.equals(language)) {
                    n++;
                }
            }
            return n;
        }
    }

    /** @return 是否有未持久化的变更 */
    public boolean isDirty() {
        synchronized (lock) {
            return dirty;
        }
    }

    /**
     * 清空内存缓存(规则:GUI 一键清理缓存)。
     * <p>注意:仅清内存,需后续 {@link #flush} 才会清空磁盘文件。
     */
    public void clear() {
        synchronized (lock) {
            entries.clear();
            dirty = true;
        }
    }

    /**
     * 清空指定目标语言的缓存(保留其它语种)。
     *
     * @param language 要清除的目标语言
     */
    public void clear(TargetLanguage language) {
        synchronized (lock) {
            entries.entrySet().removeIf(e -> e.getKey().language.equals(language));
            dirty = true;
        }
    }

    /**
     * 把内存缓存全量写回磁盘文件。
     *
     * <p>采用"写临时文件 → 原子重命名"避免写一半崩溃导致缓存损坏。
     *
     * @return true=写入成功
     */
    public boolean flush() {
        synchronized (lock) {
            if (!dirty) {
                return true;
            }
            try {
                Files.createDirectories(cacheFile.getParent());
                List<String> lines = new ArrayList<>(entries.size() + 1);
                lines.add(VERSION_LINE_PREFIX + CacheEntry.FORMAT_VERSION);
                for (CacheEntry e : entries.values()) {
                    lines.add(e.toCacheLine());
                }
                Path tmp = cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp");
                Files.write(tmp, lines, StandardCharsets.UTF_8);
                Files.move(tmp, cacheFile,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                dirty = false;
                logger.info("AI 翻译缓存已写入磁盘: " + cacheFile + " (" + entries.size() + " 条)");
                return true;
            } catch (IOException | RuntimeException e) {
                logger.warn("AI 翻译缓存写入磁盘失败(不影响运行): " + e.getMessage(), e);
                return false;
            }
        }
    }

    /** 启动时从磁盘读入内存。 */
    private void loadFromDisk() {
        try {
            if (!Files.isRegularFile(cacheFile)) {
                return;
            }
            List<String> lines = Files.readAllLines(cacheFile, StandardCharsets.UTF_8);
            int lineNo = 0;
            for (String line : lines) {
                lineNo++;
                if (line.isBlank() || line.startsWith(VERSION_LINE_PREFIX)) {
                    continue;
                }
                CacheEntry e = CacheEntry.fromCacheLine(line);
                if (e == null) {
                    logger.warn("缓存文件第 " + lineNo + " 行格式不符,跳过: " + truncate(line));
                    continue;
                }
                entries.put(new CacheKey(e.key(), e.language()), e);
            }
            logger.info("AI 翻译缓存读取自磁盘: " + entries.size() + " 条 (来源 " + cacheFile + ")");
        } catch (IOException | RuntimeException e) {
            logger.warn("AI 翻译缓存读取自磁盘失败(将启动空缓存): " + e.getMessage(), e);
        }
    }

    private static String truncate(String s) {
        return s.length() > 60 ? s.substring(0, 60) + "..." : s;
    }

    /** 内存中的缓存键:(key, language)。 */
    private record CacheKey(String key, TargetLanguage language) {
        CacheKey {
            Objects.requireNonNull(key);
            Objects.requireNonNull(language);
        }
    }
}
