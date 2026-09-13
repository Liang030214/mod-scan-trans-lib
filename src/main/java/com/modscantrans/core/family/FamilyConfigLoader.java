package com.modscantrans.core.family;

import com.modscantrans.core.ModFamily;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 用户自定义家族配置加载器(加载器无关,纯 Java)。
 *
 * <p>配置格式为简单 JSON,支持以下字段:
 * <pre>{@code
 * [
 *   {
 *     "familyId": "myfamily",
 *     "displayName": "我的家族",
 *     "mainModId": "mymod",
 *     "memberModIds": ["addon1", "addon2"],
 *     "memberPrefixes": ["mymod"],
 *     "crossLinkedFamilyIds": ["otherfamily"]
 *   }
 * ]
 * }</pre>
 *
 * <p>字段均可选(除 familyId / mainModId),缺省为空集合。命名沿用 {@link ModFamily}。
 *
 * <p>采用自写极简解析(不引入 Gson),仅识别字符串键与字符串/数组值;
 * 格式错误时抛 {@link IllegalArgumentException} 并指明位置。
 */
public final class FamilyConfigLoader {
    private FamilyConfigLoader() {
    }

    /**
     * 从 JSON 文本加载自定义家族列表。
     *
     * @param json JSON 文本(顶层数组)
     * @return 家族列表
     */
    public static List<ModFamily> load(String json) {
        return new Parser(json).parse();
    }

    /**
     * 从文件加载(UTF-8)。
     *
     * @param path 配置文件路径
     * @return 家族列表;文件不存在返回空
     */
    public static List<ModFamily> loadFile(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        String json = Files.readString(path, StandardCharsets.UTF_8);
        return load(json);
    }

    /**
     * 从 Reader 加载。
     *
     * @param reader Reader
     * @return 家族列表
     */
    public static List<ModFamily> loadReader(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = reader.read(buf)) != -1) {
            sb.append(buf, 0, n);
        }
        return load(sb.toString());
    }

    /** 极简 JSON 解析器(仅支持字符串值与字符串数组)。 */
    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s == null ? "" : s;
        }

        List<ModFamily> parse() {
            skipWs();
            if (peek() == '[') {
                return parseArray();
            }
            // 兼容单对象(非数组)情况
            if (peek() == '{') {
                return List.of(parseObject());
            }
            throw error("期望 JSON 数组 '[' 或对象 '{'");
        }

        private List<ModFamily> parseArray() {
            expect('[');
            List<ModFamily> out = new ArrayList<>();
            skipWs();
            if (peek() == ']') {
                i++;
                return out;
            }
            while (true) {
                skipWs();
                out.add(parseObject());
                skipWs();
                char c = peek();
                if (c == ',') {
                    i++;
                    continue;
                }
                if (c == ']') {
                    i++;
                    break;
                }
                throw error("期望 ',' 或 ']'");
            }
            return out;
        }

        private ModFamily parseObject() {
            expect('{');
            String familyId = "";
            String displayName = "";
            String mainModId = "";
            Set<String> members = new LinkedHashSet<>();
            Set<String> prefixes = new LinkedHashSet<>();
            Set<String> cross = new LinkedHashSet<>();
            skipWs();
            if (peek() == '}') {
                i++;
                throw error("缺少 familyId / mainModId");
            }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                skipWs();
                switch (key) {
                    case "familyId" -> familyId = parseString();
                    case "displayName" -> displayName = parseString();
                    case "mainModId" -> mainModId = parseString();
                    case "memberModIds" -> members.addAll(parseStringArray());
                    case "memberPrefixes" -> prefixes.addAll(parseStringArray());
                    case "crossLinkedFamilyIds" -> cross.addAll(parseStringArray());
                    default -> skipUnknownValue();
                }
                skipWs();
                char c = peek();
                if (c == ',') {
                    i++;
                    continue;
                }
                if (c == '}') {
                    i++;
                    break;
                }
                throw error("期望 ',' 或 '}'");
            }
            if (familyId.isBlank() || mainModId.isBlank()) {
                throw error("familyId 与 mainModId 不能为空");
            }
            return new ModFamily(familyId,
                    displayName.isBlank() ? familyId : displayName,
                    mainModId, members, prefixes, cross);
        }

        private List<String> parseStringArray() {
            expect('[');
            List<String> out = new ArrayList<>();
            skipWs();
            if (peek() == ']') {
                i++;
                return out;
            }
            while (true) {
                skipWs();
                out.add(parseString());
                skipWs();
                char c = peek();
                if (c == ',') {
                    i++;
                    continue;
                }
                if (c == ']') {
                    i++;
                    break;
                }
                throw error("期望 ',' 或 ']'");
            }
            return out;
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (i >= s.length()) {
                        throw error("字符串转义不完整");
                    }
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (i + 4 > s.length()) {
                                throw error("\\u 转义不完整");
                            }
                            String hex = s.substring(i, i + 4);
                            i += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                        }
                        default -> throw error("未知转义: \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw error("字符串未闭合");
        }

        /** 跳过未知字段值(数字/布尔/null/对象/数组)。 */
        private void skipUnknownValue() {
            char c = peek();
            if (c == '"') {
                parseString();
            } else if (c == '{') {
                skipBracketed('{', '}');
            } else if (c == '[') {
                skipBracketed('[', ']');
            } else {
                while (i < s.length() && s.charAt(i) != ',' && s.charAt(i) != '}') {
                    i++;
                }
            }
        }

        private void skipBracketed(char open, char close) {
            int depth = 0;
            boolean inStr = false;
            boolean esc = false;
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (inStr) {
                    if (esc) {
                        esc = false;
                    } else if (c == '\\') {
                        esc = true;
                    } else if (c == '"') {
                        inStr = false;
                    }
                } else {
                    if (c == '"') {
                        inStr = true;
                    } else if (c == open) {
                        depth++;
                    } else if (c == close) {
                        depth--;
                        if (depth == 0) {
                            return;
                        }
                    }
                }
            }
        }

        private void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private char peek() {
            if (i >= s.length()) {
                throw error("意外的文本结束");
            }
            return s.charAt(i);
        }

        private void expect(char c) {
            if (i >= s.length() || s.charAt(i) != c) {
                throw error("期望 '" + c + "'");
            }
            i++;
        }

        private IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("家族配置解析失败 @[" + i + "]: " + msg);
        }
    }
}
