/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.trace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
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
 * 架构门禁：以源码文本扫描断言包边界、公共面大小与离线纪律，不得为兼容错误用法而放宽。
 *
 * <pre>
 * ① noThirdPartyReference              import 只能来自 JDK / aifei 框架 / 本模块
 * ② frameworkScopeRules                cn.aifei.core.* 仅限 web 包；cn.aifei.log.* 仅限根包（非 web）；其余框架包一律禁止
 * ③ publicSurfaceExact                 根包公共类型精确 6 个、web 包精确 2 个，文件名集合精确相等
 * ④ noSqlReference                     全模块禁止 import java.sql.
 * ⑤ noJvmGlobalPropertyWrite           禁止改写 JVM 全局状态
 * ⑥ threadLocalOnlyInTraceContext      ThreadLocal 字面量仅限 TraceContext.java
 * ⑦ uuidOnlyInDefaultGenerator         UUID 字面量仅限 DefaultTraceIdGenerator.java
 * ⑧ java8Compatibility                 禁止 Java 8 之后的语法与集合工厂
 * ⑨ noServiceProvider                  禁止 SPI / ServiceLoader / META-INF/services
 * ⑩ testSourcesOfflineOnly             测试源码中的 URL 只允许回环地址
 * </pre>
 *
 * <p>扫描对象为 {@code src/main/java}（⑩ 为 {@code src/test/java}）下的全部 {@code .java}，因此本测试必须在
 * 模块根目录（{@code aifei-trace}）内执行。</p>
 */
public class ArchitectureBoundaryTest {

    /**
     * 主源码根目录，相对模块根。
     */
    private static final Path SOURCE_ROOT = Paths.get("src/main/java");

    /**
     * 测试源码根目录，相对模块根。
     */
    private static final Path TEST_ROOT = Paths.get("src/test/java");

    /**
     * 根包目录（相对 {@code src/main/java}）。
     */
    private static final String ROOT_PACKAGE = "cn/hg/aifei/trace/";

    /**
     * web 接入包目录（相对 {@code src/main/java}）。
     */
    private static final String WEB_PACKAGE = "cn/hg/aifei/trace/web/";

    /**
     * 根包公共类型的期望个数。
     */
    private static final int EXPECTED_PUBLIC_TYPES = 6;

    /**
     * web 包公共类型的期望个数。
     */
    private static final int EXPECTED_WEB_TYPES = 2;

    /**
     * 根包公共类型的期望文件名集合（唯一事实来源，防同名替换）。
     */
    private static final Set<String> EXPECTED_ROOT_TYPES = new LinkedHashSet<>(Arrays.asList(
            "TraceKit.java", "TraceContext.java", "TraceIdGenerator.java",
            "DefaultTraceIdGenerator.java", "TraceLog.java", "TraceLogFactory.java"));

    /**
     * web 包公共类型的期望文件名集合（唯一事实来源，防同名替换）。
     */
    private static final Set<String> EXPECTED_WEB_TYPES_SET = new LinkedHashSet<>(Arrays.asList(
            "TraceHandler.java", "TraceIdExtractor.java"));

    /**
     * 允许出现在测试源码里的回环 host。
     */
    private static final Set<String> LOOPBACK_HOSTS = new LinkedHashSet<>(Arrays.asList("127.0.0.1", "localhost"));

    /**
     * 测试源码中的 URL 字面量识别式（只看 authority，端口不影响判定；协议名大小写不敏感）。
     */
    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://([^/\\s\"'<>]+)", Pattern.CASE_INSENSITIVE);

    /**
     * 自证样本用协议名（拼接构造，避免样本自身触发门禁⑩）。
     */
    private static final String SAMPLE_HTTP = "http";

    /**
     * 自证样本用协议分隔符（拼接构造，避免样本自身触发门禁⑩）。
     */
    private static final String SAMPLE_SCHEME_SEP = "://";

    /**
     * 自证样本用外部 host（仅作字符串样本，从不发起任何请求）。
     */
    private static final String SAMPLE_EXTERNAL_HOST = "evil.example.com";

    /**
     * 自证样本用外部 URL（拼接构造，故本文件源码中不出现完整的非回环 URL 字面量）。
     */
    private static final String SAMPLE_EXTERNAL_URL =
            SAMPLE_HTTP + SAMPLE_SCHEME_SEP + SAMPLE_EXTERNAL_HOST + "/steal";

    /**
     * 外部 URL 命中 {@link #URL_PATTERN} 后应被记录的文本（{@code matcher.group()}，不含 path）。
     */
    private static final String SAMPLE_EXTERNAL_MATCH =
            SAMPLE_HTTP + SAMPLE_SCHEME_SEP + SAMPLE_EXTERNAL_HOST;

    /**
     * Java 8 之后才有的语法 / 集合工厂片段。
     */
    private static final String[] POST_JAVA8_FRAGMENTS = {
            "List.of(",
            "Map.of(",
            "Set.of(",
            "record ",
            "var ",
            "\"\"\"",
            "sealed "
    };

    /**
     * 门禁⑤：禁止改写 JVM 全局状态的片段。
     */
    private static final String[] JVM_GLOBAL_WRITE_FRAGMENTS = {
            "System.setProperty",
            "System.getProperties().put",
            "System.setErr",
            "System.setOut"
    };

    /**
     * 门禁①：import 只能来自 JDK、aifei 框架与本模块（本模块零第三方，无放行包）。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void noThirdPartyReference() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            for (String fqn : imports(read(file))) {
                if (!isAllowedImport(fqn)) {
                    offenders.add(path + " -> " + fqn);
                }
            }
        }
        assertTrue("import 只允许来自 java. / javax. / cn.aifei. / cn.hg.aifei.trace.，违规：" + offenders,
                offenders.isEmpty());
    }

    /**
     * 门禁②：框架范围规则——{@code cn.aifei.core.*} 仅允许出现在 web 包；{@code cn.aifei.log.*} 仅允许出现在根包
     * 且不得出现在 web 包；其余任何 {@code cn.aifei.*} 框架包（plugin / aop / util 等）一律禁止。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void frameworkScopeRules() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            for (String fqn : imports(read(file))) {
                if (!fqn.startsWith("cn.aifei.") || fqn.startsWith("cn.hg.aifei.trace.")) {
                    continue;
                }
                boolean allowed;
                if (fqn.startsWith("cn.aifei.core.")) {
                    allowed = path.contains(WEB_PACKAGE);
                } else if (fqn.startsWith("cn.aifei.log.")) {
                    allowed = path.contains(ROOT_PACKAGE) && !path.contains(WEB_PACKAGE);
                } else {
                    allowed = false;
                }
                if (!allowed) {
                    offenders.add(path + " -> " + fqn);
                }
            }
        }
        assertTrue("cn.aifei.core.* 仅限 " + WEB_PACKAGE + "；cn.aifei.log.* 仅限根包且非 web 包；"
                + "其余 cn.aifei.* 框架包一律禁止，违规：" + offenders, offenders.isEmpty());
    }

    /**
     * 门禁③：根包直接子文件精确 6 个、web 包直接子文件精确 2 个，文件名集合均精确相等。
     *
     * @throws IOException 遍历目录失败
     */
    @Test
    public void publicSurfaceExact() throws IOException {
        List<String> rootTypes = new ArrayList<>();
        List<String> webTypes = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (isDirectPackageFile(path, ROOT_PACKAGE)) {
                rootTypes.add(fileName(path));
            }
            if (isDirectPackageFile(path, WEB_PACKAGE)) {
                webTypes.add(fileName(path));
            }
        }
        assertEquals("根包公共类型应精确为 " + EXPECTED_PUBLIC_TYPES + " 个，实际为 " + rootTypes,
                EXPECTED_PUBLIC_TYPES, rootTypes.size());
        assertEquals("根包文件名集合应精确匹配，实际为 " + rootTypes,
                EXPECTED_ROOT_TYPES, new LinkedHashSet<>(rootTypes));
        assertEquals("web 包公共类型应精确为 " + EXPECTED_WEB_TYPES + " 个，实际为 " + webTypes,
                EXPECTED_WEB_TYPES, webTypes.size());
        assertEquals("web 包文件名集合应精确匹配，实际为 " + webTypes,
                EXPECTED_WEB_TYPES_SET, new LinkedHashSet<>(webTypes));
    }

    /**
     * 门禁④：全模块不得引用 JDBC（本模块零 DB 底线）。
     *
     * @throws IOException 读取源码失败
     */
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

    /**
     * 门禁⑤：不得改写 JVM 全局状态。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void noJvmGlobalPropertyWrite() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String content = read(file);
            for (String fragment : JVM_GLOBAL_WRITE_FRAGMENTS) {
                if (content.contains(fragment)) {
                    offenders.add(normalize(file) + " -> " + fragment);
                }
            }
        }
        assertTrue("不得改写 JVM 全局状态，违规：" + offenders, offenders.isEmpty());
    }

    /**
     * 门禁⑥：ThreadLocal 字面量只允许出现在 TraceContext.java（上下文持有单点）。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void threadLocalOnlyInTraceContext() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (path.endsWith(ROOT_PACKAGE + "TraceContext.java")) {
                continue;
            }
            if (read(file).contains("ThreadLocal")) {
                offenders.add(path);
            }
        }
        assertTrue("ThreadLocal 字面量只允许出现在 TraceContext.java，违规：" + offenders, offenders.isEmpty());
    }

    /**
     * 门禁⑦：UUID 字面量只允许出现在 DefaultTraceIdGenerator.java（生成算法单点）。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void uuidOnlyInDefaultGenerator() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (path.endsWith(ROOT_PACKAGE + "DefaultTraceIdGenerator.java")) {
                continue;
            }
            if (read(file).contains("UUID")) {
                offenders.add(path);
            }
        }
        assertTrue("UUID 字面量只允许出现在 DefaultTraceIdGenerator.java，违规：" + offenders, offenders.isEmpty());
    }

    /**
     * 门禁⑧：不得出现 Java 8 之后的语法与集合工厂（Java 8 双约束）。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void java8Compatibility() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String content = read(file);
            for (String fragment : POST_JAVA8_FRAGMENTS) {
                if (content.contains(fragment)) {
                    offenders.add(normalize(file) + " -> " + fragment);
                }
            }
        }
        assertTrue("不得使用 Java 8 之后的语法与 API，违规：" + offenders, offenders.isEmpty());
    }

    /**
     * 门禁⑨：全模块不得出现 ServiceLoader / META-INF/services 字面量，
     * 且 {@code src/main/resources} 下不得存在 {@code META-INF/services/} 目录（无 Plugin / 无 SPI 决策）。
     *
     * @throws IOException 读取源码或遍历目录失败
     */
    @Test
    public void noServiceProvider() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String content = read(file);
            if (content.contains("ServiceLoader") || content.contains("META-INF/services")) {
                offenders.add(normalize(file));
            }
        }
        Path resourcesRoot = Paths.get("src/main/resources");
        Path servicesDir = resourcesRoot.resolve("META-INF/services");
        assertFalse("src/main/resources 下不得存在 META-INF/services 目录", Files.exists(servicesDir));
        assertTrue("不得出现 ServiceLoader / META-INF/services 字面量，违规：" + offenders, offenders.isEmpty());
    }

    /**
     * 门禁⑩：测试源码中的 URL 字面量只允许回环地址（零联网纪律）。
     *
     * <p>只看代码字面量：注释先由状态机剥离（区分代码 / 字符串字面量 / 字符字面量 / 行注释 / 块注释，
     * 处理 {@code \"}、{@code \\} 转义），故版权头里的仓库地址不参与判定；剥离注释不构成放宽——
     * 注释里出现地址不会发起任何请求，而代码里出现外部地址必然被判失败（协议名大小写不敏感）。</p>
     *
     * @throws IOException 读取源码失败
     */
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

    /**
     * 门禁⑩ 自证：代码字符串字面量里的外部地址必须被判违规（即门禁会失败）。
     *
     * <p>样本为内联构造的源码片段，与真实文件无关；URL 用拼接生成，故本用例自身不会触发门禁⑩。</p>
     */
    @Test
    public void shouldFlagExternalUrlInCodeLiteral() {
        String sample = "class Sample {\n"
                + "    void run() {\n"
                + "        String url = \"" + SAMPLE_EXTERNAL_URL + "\";\n"
                + "    }\n"
                + "}\n";
        List<String> offenders = nonLoopbackUrls(stripComments(sample));
        assertEquals("代码字面量里的外部地址必须被拦下：" + offenders,
                Arrays.asList(SAMPLE_EXTERNAL_MATCH), offenders);
    }

    /**
     * 门禁⑩ 自证：注释里的外部地址不判违规，且注释里的引号不得把状态机带进字符串态。
     */
    @Test
    public void shouldIgnoreUrlsInsideComments() {
        String sample = "// " + SAMPLE_EXTERNAL_URL + "\n"
                + "// don't let an unbalanced \"quote switch state\n"
                + "/* block " + SAMPLE_EXTERNAL_URL + "\n"
                + "   still inside the block comment */\n"
                + "class Sample {\n"
                + "    String url = \"" + SAMPLE_EXTERNAL_URL + "\";\n"
                + "}\n";
        List<String> offenders = nonLoopbackUrls(stripComments(sample));
        assertEquals("注释里的地址不参与判定，代码里的必须命中：" + offenders,
                Arrays.asList(SAMPLE_EXTERNAL_MATCH), offenders);
    }

    /**
     * 门禁⑩ 自证：字符串字面量里的 {@code //}、字符字面量里的 {@code "} 都不得被误判为注释 / 字符串起点。
     */
    @Test
    public void shouldKeepSlashesInsideStringAndCharLiteral() {
        String sample = "class Sample {\n"
                + "    String marker = \"//\"; String url = \"" + SAMPLE_EXTERNAL_URL + "\";\n"
                + "    char quote = '\"'; // " + SAMPLE_EXTERNAL_URL + "\n"
                + "}\n";
        List<String> offenders = nonLoopbackUrls(stripComments(sample));
        assertEquals("字符串里的 // 不是注释起点，注释里的 \" 不是字符串起点：" + offenders,
                Arrays.asList(SAMPLE_EXTERNAL_MATCH), offenders);
    }

    /**
     * 门禁⑩ 自证：协议名大小写不影响判定（{@code HTTPS://} / {@code Http://} 同样判违规）。
     */
    @Test
    public void shouldFlagSchemeRegardlessOfCase() {
        String upperUrl = "HTTPS" + SAMPLE_SCHEME_SEP + SAMPLE_EXTERNAL_HOST + "/upper";
        String mixedUrl = "Http" + SAMPLE_SCHEME_SEP + SAMPLE_EXTERNAL_HOST + "/mixed";
        String sample = "class Sample {\n"
                + "    String a = \"" + upperUrl + "\";\n"
                + "    String b = \"" + mixedUrl + "\";\n"
                + "}\n";
        List<String> offenders = nonLoopbackUrls(stripComments(sample));
        assertEquals("协议名大小写不得造成漏判：" + offenders,
                Arrays.asList("HTTPS" + SAMPLE_SCHEME_SEP + SAMPLE_EXTERNAL_HOST,
                        "Http" + SAMPLE_SCHEME_SEP + SAMPLE_EXTERNAL_HOST),
                offenders);
    }

    /**
     * 门禁⑩ 自证：除号与字符串里的孤立 {@code /} 不得被误判为 URL。
     */
    @Test
    public void shouldNotFlagPlainSlash() {
        String sample = "class Sample {\n"
                + "    int half = 10 / 2;\n"
                + "    String ratio = \"a / b\";\n"
                + "    int port = 8000;\n"
                + "}\n";
        List<String> offenders = nonLoopbackUrls(stripComments(sample));
        assertTrue("普通斜杠不是 URL，不应命中：" + offenders, offenders.isEmpty());
    }

    /**
     * 收集指定根目录下全部 Java 文件。
     *
     * @param root 源码根目录
     * @return Java 源码文件列表
     * @throws IOException 遍历目录失败
     */
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

    /**
     * 判断 import 是否为允许的 JDK / 框架 / 本模块类型。
     *
     * @param fqn import 的全限定名
     * @return 允许时 {@code true}
     */
    private static boolean isAllowedImport(String fqn) {
        return fqn.startsWith("java.")
                || fqn.startsWith("javax.")
                || fqn.startsWith("cn.aifei.")
                || fqn.startsWith("cn.hg.aifei.trace.");
    }

    /**
     * 判断是否为指定包目录的直接子文件：位于包目录下，且包目录之后的相对路径不含 {@code /}。
     *
     * @param path          归一化后的源码路径
     * @param packagePrefix 包目录相对路径（如 {@code cn/hg/aifei/trace/}）
     * @return 为该包直接子文件时 {@code true}
     */
    private static boolean isDirectPackageFile(String path, String packagePrefix) {
        int rootIndex = path.lastIndexOf(packagePrefix);
        if (rootIndex < 0) {
            return false;
        }
        return !path.substring(rootIndex + packagePrefix.length()).contains("/");
    }

    /**
     * 提取路径中的文件名。
     *
     * @param path 归一化后的源码路径
     * @return 文件名
     */
    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /**
     * 从源码文本提取全部 import 的全限定名。
     *
     * @param content 源码全文
     * @return 全限定名列表
     */
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

    /**
     * 归一化路径分隔符，使断言不受操作系统影响。
     *
     * @param path 源码路径
     * @return 以 {@code /} 分隔的路径
     */
    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    /**
     * 以状态机剥离注释，只保留代码文本（含字符串 / 字符字面量的原文）。
     *
     * <p>状态区分「代码 / 字符串字面量 / 字符字面量 / 行注释 / 块注释」五种：只有处于代码态时
     * {@code //} 与 {@code /*} 才是注释起点，故字符串字面量里的 {@code "http://host/x"} 与 {@code "/*"}
     * 原样保留；字面量内处理 {@code \"}、{@code \\} 等转义；注释内的引号不影响状态机。
     * 行注释与块注释被删除，但其中的换行保留，避免相邻代码行被拼接。</p>
     *
     * <p>本项目为 Java 8，无文本块，故不处理 {@code \"\"\"}。</p>
     *
     * @param content 源码全文
     * @return 去掉注释后的文本
     */
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

    /**
     * 剥离注释状态机的状态：普通代码。
     */
    private static final int STATE_CODE = 0;

    /**
     * 剥离注释状态机的状态：行注释。
     */
    private static final int STATE_LINE_COMMENT = 1;

    /**
     * 剥离注释状态机的状态：块注释。
     */
    private static final int STATE_BLOCK_COMMENT = 2;

    /**
     * 剥离注释状态机的状态：字符串字面量。
     */
    private static final int STATE_STRING = 3;

    /**
     * 剥离注释状态机的状态：字符字面量。
     */
    private static final int STATE_CHAR = 4;

    /**
     * 从已剥离注释的代码文本中提取全部非回环 URL（命中即视为违规）。
     *
     * @param codeText 已剥离注释的代码文本
     * @return 非回环 URL 的命中文本列表（保持出现顺序，形如 {@code http://host}）
     */
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

    /**
     * 以 UTF-8 读取源码文本。
     *
     * @param path 源码路径
     * @return 源码全文
     * @throws IOException 读取失败
     */
    private static String read(Path path) throws IOException {
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        assertNotNull(content);
        return content;
    }
}
