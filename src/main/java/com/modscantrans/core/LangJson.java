package com.modscantrans.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 极简 JSON 解析/序列化工具(零外部依赖)。
 *
 * <p>专为读取 Minecraft 语言文件({@code assets/<modid>/lang/<lang>.json})与
 * CFPA 人工汉化({@code zh_cn.json})等「对象 → 字符串键 → 字符串值」结构设计。
 * <b>只解析并保留字符串键值对</b>:非字符串值(数字 / 布尔 / null)与嵌套对象 / 数组会被
 * <b>跳过</b>而不保留——这对语言文件足够(其值均为字符串),也能安全放过
 * {@code fabric.mod.json} 中 {@code depends} 这类嵌套结构而不报错。
 *
 * <p>选择自写而不用 Gson,是为了让 core 层真正零外部依赖、加载器无关,
 * NeoForge / Forge / Fabric 均可直接复用。scanner 与 cfpa 等模块共享本工具。
 */
public final class LangJson {
    private LangJson() {
    }

    /**
     * 解析 JSON 对象文本为 {@code 键 → 字符串值} 映射。
     *
     * @param json JSON 文本
     * @return 保留下来的字符串键值对(保留插入顺序)
     * @throws IllegalArgumentException 文本不是合法对象结构时
     */
    public static Map<String, String> parseObject(String json) {
        Parser p = new Parser(json);
        return p.parseObject();
    }

    /**
     * 把 {@code 键 → 字符串值} 映射序列化为紧凑 JSON 文本。
     *
     * @param map 映射
     * @return JSON 文本
     */
    public static String toJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder(map.size() * 16 + 2);
        sb.append('{');
        boolean first = true;
        for (var e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            appendString(sb, e.getKey());
            sb.append(':');
            appendString(sb, e.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    private static void appendString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    /** 递归下降解析器,内部类持有解析状态。 */
    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        Map<String, String> parseObject() {
            skipWs();
            expect('{');
            Map<String, String> out = new LinkedHashMap<>();
            skipWs();
            if (peek() == '}') {
                i++;
                return out;
            }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                skipWs();
                char c = peek();
                if (c == '"') {
                    // 字符串值:保留
                    String val = parseString();
                    out.put(key, val);
                } else {
                    // 非字符串值(数字/布尔/null/对象/数组):跳过,不保留
                    skipNonStringValue();
                }
                skipWs();
                char d = peek();
                if (d == ',') {
                    i++;
                    continue;
                }
                if (d == '}') {
                    i++;
                    break;
                }
                throw error("期望 ',' 或 '}', 实际: " + repr(d));
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
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException ex) {
                                throw error("非法的 \\u 转义: " + hex);
                            }
                        }
                        default -> throw error("未知转义: \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw error("字符串未闭合");
        }

        /** 跳过非字符串值(数字、true、false、null,以及嵌套对象/数组)。 */
        private void skipNonStringValue() {
            char c = peek();
            if (c == '{') {
                skipBracketed('{', '}');
            } else if (c == '[') {
                skipBracketed('[', ']');
            } else {
                // 数字 / true / false / null:读到下一个 , 或 }
                while (i < s.length()) {
                    char d = s.charAt(i);
                    if (d == ',' || d == '}') {
                        break;
                    }
                    i++;
                }
            }
        }

        /** 跳过一个匹配的括号对(支持嵌套,且内部字符串也算入计数)。 */
        private void skipBracketed(char open, char close) {
            int depth = 0;
            boolean inString = false;
            boolean escape = false;
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (inString) {
                    if (escape) {
                        escape = false;
                    } else if (c == '\\') {
                        escape = true;
                    } else if (c == '"') {
                        inString = false;
                    }
                } else {
                    if (c == '"') {
                        inString = true;
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
                throw error("期望 '" + c + "', 实际: " + (i < s.length() ? repr(s.charAt(i)) : "<结束>"));
            }
            i++;
        }

        private String repr(char c) {
            if (c < 0x20) {
                return "\\u%04x".formatted((int) c);
            }
            return "'" + c + "'";
        }

        private IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("JSON 解析失败 @[" + i + "]: " + msg);
        }
    }
}
