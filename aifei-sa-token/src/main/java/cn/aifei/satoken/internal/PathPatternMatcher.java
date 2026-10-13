/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.satoken.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Ant 风格路径匹配器，用于替换 Sa-Token 默认的路由匹配策略。
 *
 * <p>aifei 内核的 {@code cn.aifei.router.Router} 只做精确路径匹配（不含通配语义），
 * 而 Sa-Token 的路径鉴权规则习惯写成 {@code /admin/**} 这类通配表达式，故此处自行编译。</p>
 *
 * <p>支持的语法：</p>
 * <ul>
 *   <li>{@code *} 匹配单层路径片段，不跨越 {@code /}</li>
 *   <li>{@code **} 匹配任意深度路径，且允许该段整体缺省（{@code /a/**} 同时匹配 {@code /a} 与 {@code /a/b/c}）</li>
 *   <li>{@code ?} 匹配单个字符，不跨越 {@code /}</li>
 *   <li>{@code {name}} 匹配单层路径变量</li>
 *   <li>{@code {name_}} 匹配跨层路径变量</li>
 * </ul>
 *
 * <p>表达式一经编译即缓存，匹配阶段无重复编译开销。</p>
 */
public final class PathPatternMatcher {

    /** 需要正则转义的字符集合 */
    private static final String SPECIAL = "\\[](){}.+^$|";

    private static final Map<String, PathPatternMatcher> CACHE = new ConcurrentHashMap<>();

    private final Pattern pattern;

    /**
     * 获取表达式对应的匹配器，同一表达式只编译一次。
     *
     * @param expr Ant 风格路径表达式，不能为空
     * @return 匹配器实例
     * @throws IllegalArgumentException 表达式为空
     */
    public static PathPatternMatcher get(String expr) {
        if (expr == null || expr.trim().isEmpty()) {
            throw new IllegalArgumentException("path expression can not be blank.");
        }
        String key = expr.trim();
        PathPatternMatcher cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        PathPatternMatcher created = new PathPatternMatcher(key);
        PathPatternMatcher previous = CACHE.putIfAbsent(key, created);
        return previous != null ? previous : created;
    }

    private PathPatternMatcher(String expr) {
        this.pattern = Pattern.compile(compile(expr), Pattern.CASE_INSENSITIVE);
    }

    /**
     * 判断给定请求路径是否匹配当前表达式。
     *
     * @param path 请求路径，可为空
     * @return 匹配返回 true，否则返回 false
     */
    public boolean matches(String path) {
        return path != null && pattern.matcher(path).matches();
    }

    /** 单遍扫描表达式并生成锚定正则，避免多轮 replace 造成的占位符依赖 */
    private static String compile(String expr) {
        StringBuilder sb = new StringBuilder(expr.length() + 16);
        sb.append('^');

        int i = 0;
        int len = expr.length();
        while (i < len) {
            char c = expr.charAt(i);

            if (c == '*') {
                if (i + 1 < len && expr.charAt(i + 1) == '*') {
                    appendDoubleStar(sb);
                    i += 2;
                } else {
                    sb.append("[^/]*");
                    i++;
                }
                continue;
            }

            if (c == '?') {
                sb.append("[^/]");
                i++;
                continue;
            }

            if (c == '{') {
                int end = expr.indexOf('}', i);
                if (end < 0) {
                    sb.append("\\{");
                    i++;
                    continue;
                }
                String name = expr.substring(i + 1, end);
                sb.append(name.endsWith("_") ? "(.+?)" : "([^/]+?)");
                i = end + 1;
                continue;
            }

            if (SPECIAL.indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
            i++;
        }

        return sb.append('$').toString();
    }

    /**
     * 追加 {@code **} 对应的正则。
     *
     * <p>紧邻的前导 {@code /} 会被并入可选组，使 {@code /a/**} 也能匹配 {@code /a} 本身。</p>
     */
    private static void appendDoubleStar(StringBuilder sb) {
        if (sb.length() > 1 && sb.charAt(sb.length() - 1) == '/') {
            sb.setLength(sb.length() - 1);
            sb.append("(/.*)?");
        } else {
            sb.append(".*");
        }
    }
}
