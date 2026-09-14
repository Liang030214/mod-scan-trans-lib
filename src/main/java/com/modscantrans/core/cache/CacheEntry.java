package com.modscantrans.core.cache;

import com.modscantrans.core.EntrySource;
import com.modscantrans.core.TargetLanguage;
import java.util.Locale;
import java.util.Objects;

/**
 * AI 翻译缓存条目:把某语言键的原文翻译存到本地,下次命中直接复用,不重复调 AI。
 *
 * <p><b>缓存条目绑定目标语言,同原文不同语种分开存储</b>(硬性规则)。
 * 即同一 {@code key} 的中文译文、泰语译文是两条独立的缓存条目,互不覆盖。
 *
 * <p><b>来源</b>:缓存只来自 AI(实时翻译成功后写入),固定为
 * {@link EntrySource#AI_CACHE};CFPA 人工汉化不进 AI 缓存(它有自己的索引)。
 *
 * @param key          语言键,如 {@code block.create.cogwheel}
 * @param sourceText   原文(通常英文,用于 AI 翻译时参考)
 * @param translated   AI 译文(目标语言)
 * @param language     目标语言(缓存绑定语种)
 * @param modId        所属 modid(可空)
 * @param createdAt    创建时间戳(毫秒)
 */
public record CacheEntry(
        String key,
        String sourceText,
        String translated,
        TargetLanguage language,
        String modId,
        long createdAt) {

    /** 缓存文件格式版本(用于序列化兼容检测)。 */
    public static final String FORMAT_VERSION = "1";

    public CacheEntry {
        Objects.requireNonNull(key, "key 不能为空");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key 不能为空白");
        }
        translated = translated == null ? "" : translated;
        sourceText = sourceText == null ? "" : sourceText;
        Objects.requireNonNull(language, "language 不能为空");
        modId = modId == null ? "" : modId.toLowerCase(Locale.ROOT);
    }

    /**
     * 序列化为缓存行(TSV 制表符分隔,顺序固定):
     * {@code <key>\t<sourceText>\t<translated>\t<lang>\t<modId>\t<createdAt>}
     *
     * <p>制表符在 JSON 文本里几乎不会出现,选它作分隔符降低转义复杂度;
     * 若原文 / 译文含制表符,解析时按位置切分仍能正确还原前 6 列。
     *
     * @return 缓存行
     */
    public String toCacheLine() {
        // 制表符 / 换行在值中需转义,否则破坏 TSV 结构
        return esc(key) + "\t" + esc(sourceText) + "\t" + esc(translated)
                + "\t" + language.code() + "\t" + esc(modId) + "\t" + createdAt;
    }

    /**
     * 从缓存行反序列化。
     *
     * @param line 缓存行
     * @return 缓存条目;格式不符返回 null
     */
    public static CacheEntry fromCacheLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] p = line.split("\t", -1);
        if (p.length < 6) {
            return null;
        }
        try {
            String key = unesc(p[0]);
            String sourceText = unesc(p[1]);
            String translated = unesc(p[2]);
            TargetLanguage lang = TargetLanguage.of(p[3]);
            String modId = unesc(p[4]);
            long createdAt = Long.parseLong(p[5].trim());
            return new CacheEntry(key, sourceText, translated, lang, modId, createdAt);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String unesc(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '\\' && i < s.length()) {
                char n = s.charAt(i++);
                switch (n) {
                    case '\\' -> sb.append('\\');
                    case 't' -> sb.append('\t');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    default -> sb.append('\\').append(n);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
