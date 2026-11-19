/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * 架构门禁（设计 §11.1）：以源码文本扫描 + 反射断言包边界、公共面精确集合与零自建规则数据，不得放宽。
 *
 * <pre>
 *  ① noThirdPartyReference          import 只能来自 JDK / aifei / 本模块（cache 仅 KvSource）
 *  ② frameworkScopeRules            Prop/Plugin/aop 仅在 aifei 包，core 仅在 web 包，log 仅在三处
 *  ③ publicSurfaceExact             根 6 / web 2 / aifei 1 文件名集合精确相等
 *  ④ publicSurfaceByReflection      target/classes 顶层 public 类型 FQN 精确等于 9 个
 *  ⑤ noSqlReference                 全模块不得 import java.sql.
 *  ⑥ noJvmGlobalPropertyWrite       不得改写 JVM 全局属性 / 标准输出错误流
 *  ⑦ noServiceProvider              不得出现 ServiceLoader / META-INF/services
 *  ⑧ java8Compatibility             不得使用 Java 8 之后的语法与集合工厂
 *  ⑨ localeRulesSingleDefinition    setLanguageTag 仅在 LocaleNormalizer
 *  ⑩ dirOnlyInLocaleMeta            ComponentOrientation 仅在 LocaleMeta，且无语言→方向映射表
 *  ⑪ noSelfBuiltRuleData            无日期/数字模式串、货币符号表、方向/货币硬编码集合
 *  ⑫ cacheDependencyOnlyInKvSource  cn.hg.aifei.cache. 仅在 KvSource 方法体
 *  ⑬ testSourcesOfflineOnly         测试源码 URL host 只能是回环地址
 * </pre>
 *
 * <p>扫描对象为 {@code src/main/java}（⑬ 为 {@code src/test/java}），须在模块根执行
 * （surefire {@code workingDirectory=${project.basedir}} 保证）。</p>
 */
public class ArchitectureBoundaryTest {

    private static final Path SOURCE_ROOT = Paths.get("src/main/java");
    private static final Path TEST_ROOT = Paths.get("src/test/java");
    private static final Path CLASSES_ROOT = Paths.get("target/classes");

    private static final String ROOT_PACKAGE = "cn/hg/aifei/i18n/";
    private static final String WEB_PACKAGE = "cn/hg/aifei/i18n/web/";
    private static final String AIFEI_PACKAGE = "cn/hg/aifei/i18n/aifei/";
    private static final String INTERNAL_PACKAGE = "cn/hg/aifei/i18n/internal/";
    private static final String DEFAULT_SRC = "cn/hg/aifei/i18n/defaultSource/";
    /** 唯一新增门禁例外：Enjoy 类型仅允许出现在指令实现包（design.md §16.8.1）。 */
    private static final String DIRECTIVE_PACKAGE = "cn/hg/aifei/i18n/internal/directive/";

    private static final int EXPECTED_PUBLIC_TYPES = 6;
    private static final Set<String> EXPECTED_ROOT_TYPES = new LinkedHashSet<>(Arrays.asList(
            "I18nKit.java", "FormatKit.java", "MessageSource.java",
            "LanguageBundle.java", "DateStyle.java", "I18nException.java"));
    private static final Set<String> EXPECTED_WEB_TYPES = new LinkedHashSet<>(Arrays.asList(
            "I18nHandler.java", "LocaleExtractor.java"));
    private static final Set<String> EXPECTED_AIFEI_TYPES = new LinkedHashSet<>(Arrays.asList(
            "I18nPlugin.java"));
    private static final Set<String> EXPECTED_PUBLIC_FQNS = new LinkedHashSet<>(Arrays.asList(
            "cn.hg.aifei.i18n.I18nKit",
            "cn.hg.aifei.i18n.FormatKit",
            "cn.hg.aifei.i18n.MessageSource",
            "cn.hg.aifei.i18n.LanguageBundle",
            "cn.hg.aifei.i18n.DateStyle",
            "cn.hg.aifei.i18n.I18nException",
            "cn.hg.aifei.i18n.web.I18nHandler",
            "cn.hg.aifei.i18n.web.LocaleExtractor",
            "cn.hg.aifei.i18n.aifei.I18nPlugin"));

    private static final Set<String> LOG_RECORDERS = new LinkedHashSet<>(Arrays.asList(
            "FallbackChain.java", "BundleExporter.java", "LocaleMeta.java"));

    private static final Set<String> LOOPBACK_HOSTS = new LinkedHashSet<>(Arrays.asList("127.0.0.1", "localhost"));
    private static final Pattern URL_PATTERN = Pattern.compile("https?://([^/\\s\"'<>]+)", Pattern.CASE_INSENSITIVE);

    private static final int STATE_CODE = 0;
    private static final int STATE_LINE_COMMENT = 1;
    private static final int STATE_BLOCK_COMMENT = 2;
    private static final int STATE_STRING = 3;
    private static final int STATE_CHAR = 4;

    private static final String[] POST_JAVA8_FRAGMENTS = {
            "List.of(", "Map.of(", "Set.of(", "record ", "var ", "\"\"\"", "sealed "
    };

    /** 门禁⑪禁止的模式串 / 自建数据表片段。 */
    private static final String[] SELF_BUILT_RULE_FRAGMENTS = {
            "yyyy-MM-dd", "#,##0", "\"US$\"", "\"$\"", "\"€\""
    };

    /** ① import 白名单前缀。 */
    private static boolean isAllowedImport(String fqn) {
        return fqn.startsWith("java.") || fqn.startsWith("javax.")
                || fqn.startsWith("cn.aifei.") || fqn.startsWith("cn.hg.aifei.i18n.")
                || fqn.startsWith("cn.hg.aifei.cache.");
    }

    @Test
    public void noThirdPartyReference() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            for (String fqn : imports(read(file))) {
                if (!isAllowedImport(fqn)) {
                    offenders.add(path + " -> " + fqn);
                }
                if (fqn.startsWith("cn.hg.aifei.cache.") && !path.endsWith(DEFAULT_SRC + "KvSource.java")) {
                    offenders.add(path + " -> " + fqn + "（cache 仅允许 KvSource）");
                }
            }
        }
        assertTrue("import 仅允许 java./javax./cn.aifei./cn.hg.aifei.i18n.（cache 仅 KvSource），违规：" + offenders,
                offenders.isEmpty());
    }

    @Test
    public void frameworkScopeRules() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            for (String fqn : imports(read(file))) {
                if (!fqn.startsWith("cn.aifei.") || fqn.startsWith("cn.hg.aifei.")) {
                    continue;
                }
                boolean aifeiOnly = fqn.startsWith("cn.aifei.util.Prop")
                        || fqn.startsWith("cn.aifei.plugin.Plugin") || fqn.startsWith("cn.aifei.aop.");
                boolean webOnly = fqn.startsWith("cn.aifei.core.");
                boolean logOnly = fqn.startsWith("cn.aifei.log.");
                // 唯一新增例外：Enjoy 类型仅允许 internal/directive/（design.md §16.8.1）
                boolean enjoyOnly = fqn.startsWith("cn.aifei.enjoy.");
                boolean allowed = (aifeiOnly && path.contains(AIFEI_PACKAGE))
                        || (webOnly && path.contains(WEB_PACKAGE))
                        || (logOnly && path.contains(INTERNAL_PACKAGE) && isLogRecorder(path))
                        || (logOnly && path.contains(AIFEI_PACKAGE))
                        || (enjoyOnly && path.contains(DIRECTIVE_PACKAGE));
                if (!allowed) {
                    offenders.add(path + " -> " + fqn);
                }
            }
        }
        assertTrue("框架类型包边界违规（enjoy 仅允许 internal/directive）：" + offenders, offenders.isEmpty());
    }

    @Test
    public void publicSurfaceExact() throws IOException {
        assertEquals("根包公共类型应精确 6 个", EXPECTED_ROOT_TYPES, directFiles(ROOT_PACKAGE));
        assertEquals("web 包公共类型应精确 2 个", EXPECTED_WEB_TYPES, directFiles(WEB_PACKAGE));
        assertEquals("aifei 包公共类型应精确 1 个", EXPECTED_AIFEI_TYPES, directFiles(AIFEI_PACKAGE));
        assertEquals("公共类型总数应为 9", 9,
                EXPECTED_ROOT_TYPES.size() + EXPECTED_WEB_TYPES.size() + EXPECTED_AIFEI_TYPES.size());
        assertEquals("根包期望常量个数", EXPECTED_PUBLIC_TYPES, EXPECTED_ROOT_TYPES.size());
    }

    @Test
    public void publicSurfaceByReflection() throws IOException {
        if (!Files.isDirectory(CLASSES_ROOT)) {
            return;
        }
        Set<String> actual = new LinkedHashSet<>();
        try (Stream<Path> paths = Files.walk(CLASSES_ROOT)) {
            for (Path path : (Iterable<Path>) paths.filter(p -> p.toString().endsWith(".class")
                    && !p.getFileName().toString().contains("$"))::iterator) {
                String rel = CLASSES_ROOT.relativize(path).toString().replace('\\', '/');
                String fqn = rel.substring(0, rel.length() - ".class".length()).replace('/', '.');
                // 公共面只含根包 / web / aifei 三包的顶层类型；internal 与 defaultSource 为非承诺实现，不计入。
                String prefix = "cn.hg.aifei.i18n.";
                if (!fqn.startsWith(prefix)) {
                    continue;
                }
                String tail = fqn.substring(prefix.length());
                if (tail.contains(".") && !tail.startsWith("web.") && !tail.startsWith("aifei.")) {
                    continue;
                }
                try {
                    Class<?> type = Class.forName(fqn);
                    if (Modifier.isPublic(type.getModifiers())) {
                        actual.add(fqn);
                    }
                } catch (Throwable ignored) {
                    // 非本模块类或无法加载，不计入
                }
            }
        }
        assertEquals("反射口径的顶层 public 类型集合应精确为 9 个", EXPECTED_PUBLIC_FQNS, actual);
    }

    @Test
    public void noSqlReference() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            if (read(file).contains("import java.sql.")) {
                offenders.add(normalize(file));
            }
        }
        assertTrue("不得出现 import java.sql.，违规：" + offenders, offenders.isEmpty());
    }

    @Test
    public void noJvmGlobalPropertyWrite() throws IOException {
        String[] forbidden = {"System.setProperty", "System.getProperties().put", "System.setOut", "System.setErr"};
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String content = read(file);
            for (String fragment : forbidden) {
                if (content.contains(fragment)) {
                    offenders.add(normalize(file) + " -> " + fragment);
                }
            }
        }
        assertTrue("不得改写 JVM 全局属性 / 标准流，违规：" + offenders, offenders.isEmpty());
    }

    @Test
    public void noServiceProvider() throws IOException {
        String[] forbidden = {"ServiceLoader", "META-INF/services"};
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String content = read(file);
            for (String fragment : forbidden) {
                if (content.contains(fragment)) {
                    offenders.add(normalize(file) + " -> " + fragment);
                }
            }
        }
        assertTrue("不得使用 SPI，违规：" + offenders, offenders.isEmpty());
        Path spiDir = SOURCE_ROOT.resolve("../resources/META-INF/services");
        assertFalse("不得存在 META-INF/services 目录：" + spiDir, Files.isDirectory(spiDir));
    }

    @Test
    public void java8Compatibility() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String content = stripComments(read(file));
            for (String fragment : POST_JAVA8_FRAGMENTS) {
                if (content.contains(fragment)) {
                    offenders.add(normalize(file) + " -> " + fragment);
                }
            }
        }
        assertTrue("不得使用 Java 8 之后的语法 / API，违规：" + offenders, offenders.isEmpty());
    }

    @Test
    public void localeRulesSingleDefinition() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            String code = stripComments(read(file));
            if (code.contains("setLanguageTag(") && !path.endsWith(INTERNAL_PACKAGE + "LocaleNormalizer.java")) {
                offenders.add(path + " -> setLanguageTag");
            }
            if (code.contains("parseAcceptLanguage(") && !path.endsWith(INTERNAL_PACKAGE + "LocaleResolver.java")
                    && !path.endsWith(WEB_PACKAGE + "I18nHandler.java")) {
                offenders.add(path + " -> parseAcceptLanguage");
            }
        }
        assertTrue("locale 规范化 / 请求头解析各只有一个定义点，违规：" + offenders, offenders.isEmpty());
    }

    @Test
    public void dirOnlyInLocaleMeta() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            String code = stripComments(read(file));
            if (code.contains("ComponentOrientation")
                    && !path.endsWith(INTERNAL_PACKAGE + "LocaleMeta.java")) {
                offenders.add(path + " -> ComponentOrientation");
            }
        }
        assertTrue("dir 判定只允许在 LocaleMeta，违规：" + offenders, offenders.isEmpty());
        // Q9：不得存在语言码→方向的映射表（同时含 rtl 常量与语言码数组的字面量）
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String code = stripComments(read(file));
            if (code.contains("\"rtl\"") && code.matches("(?s).*\\{\\s*\"(ar|he|fa|ur)\".*")) {
                offenders.add(normalize(file) + " -> 疑似语言方向映射表");
            }
        }
        assertTrue("禁止自建语言码→方向映射表，违规：" + offenders, offenders.isEmpty());
    }

    @Test
    public void noSelfBuiltRuleData() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            String code = stripComments(read(file));
            for (String fragment : SELF_BUILT_RULE_FRAGMENTS) {
                if (code.contains(fragment)) {
                    offenders.add(path + " -> " + fragment);
                }
            }
        }
        assertTrue("禁止自建格式规则数据（模式串/货币符号表），违规：" + offenders, offenders.isEmpty());
    }

    @Test
    public void cacheDependencyOnlyInKvSource() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (!path.endsWith(DEFAULT_SRC + "KvSource.java")) {
                continue;
            }
            String code = stripComments(read(file));
            for (String line : code.split("\n")) {
                String trimmed = line.trim();
                boolean fieldOrSignature = trimmed.startsWith("private ") || trimmed.startsWith("protected ")
                        || trimmed.startsWith("public ");
                if (fieldOrSignature && trimmed.contains("cn.hg.aifei.cache")) {
                    offenders.add(path + " -> " + trimmed);
                }
            }
        }
        assertTrue("cache 类型不得出现在 KvSource 字段/签名，违规：" + offenders, offenders.isEmpty());
    }

    @Test
    public void testSourcesOfflineOnly() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(TEST_ROOT)) {
            for (String url : nonLoopbackUrls(stripComments(read(file)))) {
                offenders.add(normalize(file) + " -> " + url);
            }
        }
        assertTrue("测试源码只允许回环地址（" + LOOPBACK_HOSTS + "），违规：" + offenders, offenders.isEmpty());
    }

    // ─────────── 工具方法 ───────────

    private static boolean isLogRecorder(String path) {
        for (String name : LOG_RECORDERS) {
            if (path.endsWith("/" + name)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> directFiles(String packagePath) throws IOException {
        Set<String> files = new LinkedHashSet<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            int idx = path.lastIndexOf(packagePath);
            if (idx < 0) {
                continue;
            }
            String rest = path.substring(idx + packagePath.length());
            if (!rest.contains("/")) {
                files.add(rest);
            }
        }
        return files;
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        assertTrue("源码目录不存在，测试需在模块根目录执行：" + root.toAbsolutePath(), Files.exists(root));
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                    .filter(Files::isRegularFile)
                    .forEach(files::add);
        }
        assertFalse("未扫描到任何 Java 源码：" + root.toAbsolutePath(), files.isEmpty());
        return files;
    }

    private static List<String> imports(String content) {
        List<String> fqns = new ArrayList<>();
        for (String raw : content.split("\n")) {
            String line = raw.trim();
            if (!line.startsWith("import ")) {
                continue;
            }
            String body = line.substring("import ".length()).trim();
            if (body.startsWith("static ")) {
                body = body.substring("static ".length()).trim();
            }
            int end = body.indexOf(';');
            fqns.add(end >= 0 ? body.substring(0, end).trim() : body);
        }
        return fqns;
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static List<String> nonLoopbackUrls(String codeText) {
        List<String> offenders = new ArrayList<>();
        Matcher matcher = URL_PATTERN.matcher(codeText);
        while (matcher.find()) {
            String authority = matcher.group(1);
            int colon = authority.indexOf(':');
            String host = colon >= 0 ? authority.substring(0, colon) : authority;
            if (!LOOPBACK_HOSTS.contains(host.toLowerCase(java.util.Locale.ROOT))) {
                offenders.add(matcher.group());
            }
        }
        return offenders;
    }

    private static String read(Path path) throws IOException {
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        assertNotNull(content);
        return content;
    }

    private static String stripComments(String content) {
        StringBuilder code = new StringBuilder(content.length());
        int state = STATE_CODE;
        int index = 0;
        int length = content.length();
        while (index < length) {
            char current = content.charAt(index);
            char next = index + 1 < length ? content.charAt(index + 1) : '\0';
            switch (state) {
                case STATE_CODE:
                    if (current == '/' && next == '/') {
                        state = STATE_LINE_COMMENT;
                        index += 2;
                    } else if (current == '/' && next == '*') {
                        state = STATE_BLOCK_COMMENT;
                        index += 2;
                    } else if (current == '"') {
                        state = STATE_STRING;
                        code.append(current);
                        index++;
                    } else if (current == '\'') {
                        state = STATE_CHAR;
                        code.append(current);
                        index++;
                    } else {
                        code.append(current);
                        index++;
                    }
                    break;
                case STATE_LINE_COMMENT:
                    if (current == '\n') {
                        state = STATE_CODE;
                        code.append(current);
                    }
                    index++;
                    break;
                case STATE_BLOCK_COMMENT:
                    if (current == '*' && next == '/') {
                        state = STATE_CODE;
                        index += 2;
                    } else {
                        if (current == '\n') {
                            code.append(current);
                        }
                        index++;
                    }
                    break;
                case STATE_STRING:
                case STATE_CHAR: {
                    char terminator = state == STATE_STRING ? '"' : '\'';
                    code.append(current);
                    index++;
                    if (current == '\\' && index < length) {
                        code.append(content.charAt(index));
                        index++;
                    } else if (current == terminator) {
                        state = STATE_CODE;
                    }
                    break;
                }
                default:
                    code.append(current);
                    index++;
                    break;
            }
        }
        return code.toString();
    }
}
