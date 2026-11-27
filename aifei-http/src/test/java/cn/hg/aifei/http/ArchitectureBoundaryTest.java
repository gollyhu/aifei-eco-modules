/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http;

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
 * 架构门禁：以源码文本扫描断言包边界、公共面大小与离线纪律，不得为兼容错误用法而放宽。
 *
 * <pre>
 * ① noThirdPartyReference                    import 只能来自 JDK / aifei / 本模块
 * ② frameworkOnlyInBridgePackage             cn.aifei.*（除 cn.aifei.log.*）只允许出现在接入包
 * ③ noSqlReference                           全模块禁止 import java.sql.
 * ④ noJvmGlobalPropertyWrite                 禁止改写 JVM 全局 HTTP 属性（只读不写）
 * ⑤ publicSurfaceExact                       根包公共类型精确 6 个，且文件名集合精确相等
 * ⑥ resourceDerivationOnlyInHttpResource     资源名推导只允许在 internal.HttpResource
 * ⑦ jdkTransportOnlyInInternal               根包不得引用 JDK 传输类型
 * ⑧ java8Compatibility                       禁止 Java 8 之后的语法与集合工厂
 * ⑨ bridgePackageExists                      接入包存在且含 HttpPlugin.java
 * ⑩ testSourcesOfflineOnly                   测试源码中的 URL 只允许回环地址
 * </pre>
 *
 * <p>扫描对象为 {@code src/main/java}（⑩ 为 {@code src/test/java}）下的全部 {@code .java}，因此本测试必须在
 * 模块根目录（{@code aifei-http}）内执行。</p>
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
    private static final String ROOT_PACKAGE = "cn/hg/aifei/http/";

    /**
     * 框架类型唯一允许出现的接入包。
     */
    private static final String BRIDGE_PACKAGE = "cn/hg/aifei/http/aifei/";

    /**
     * 内部实现包（{@code cn.aifei.log.*} 与 JDK 传输类型允许出现的另一处）。
     */
    private static final String INTERNAL_PACKAGE = "cn/hg/aifei/http/internal/";

    /**
     * 模块自身记录允许使用的框架日志包前缀。
     */
    private static final String LOG_PACKAGE = "cn.aifei.log.";

    /**
     * 根包公共类型的期望个数。
     */
    private static final int EXPECTED_PUBLIC_TYPES = 6;

    /**
     * 根包公共类型的期望文件名集合（唯一事实来源，防同名替换）。
     */
    private static final Set<String> EXPECTED_ROOT_TYPES = new LinkedHashSet<>(Arrays.asList(
            "HttpClient.java",
            "HttpRequest.java",
            "HttpResponse.java",
            "HttpKit.java",
            "HttpException.java",
            "HttpInterceptor.java"
    ));

    /**
     * 根包公共类型的期望全限定名（反射口径）。
     */
    private static final String[] EXPECTED_PUBLIC_FQNS = {
            "cn.hg.aifei.http.HttpClient",
            "cn.hg.aifei.http.HttpRequest",
            "cn.hg.aifei.http.HttpResponse",
            "cn.hg.aifei.http.HttpKit",
            "cn.hg.aifei.http.HttpException",
            "cn.hg.aifei.http.HttpInterceptor"
    };

    /**
     * 允许出现在测试源码里的回环 host。
     */
    private static final Set<String> LOOPBACK_HOSTS = new LinkedHashSet<>(Arrays.asList("127.0.0.1", "localhost"));

    /**
     * 测试源码中的 URL 字面量识别式（只看 authority，端口不影响判定；协议名大小写不敏感，
     * 故 {@code HTTPS://} / {@code Http://} 同样被拦）。
     */
    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://([^/\\s\"'<>]+)", Pattern.CASE_INSENSITIVE);

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
     * 门禁①：import 只能来自 JDK、aifei 与本模块（本模块零第三方）。
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
        assertTrue("import 只允许来自 java. / javax. / cn.aifei. / cn.hg.aifei.http.，违规：" + offenders,
                offenders.isEmpty());
    }

    /**
     * 门禁②：框架类型只允许出现在接入包；{@code cn.aifei.log.*} 另允许出现在 {@code internal} 的记录点。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void frameworkOnlyInBridgePackage() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            for (String fqn : imports(read(file))) {
                if (!fqn.startsWith("cn.aifei.") || fqn.startsWith("cn.hg.aifei.http.")) {
                    continue;
                }
                boolean logOnly = fqn.startsWith(LOG_PACKAGE);
                boolean allowed = path.contains(BRIDGE_PACKAGE)
                        || (logOnly && path.contains(INTERNAL_PACKAGE));
                if (!allowed) {
                    offenders.add(path + " -> " + fqn);
                }
            }
        }
        assertTrue("cn.aifei.* 只允许出现在 " + BRIDGE_PACKAGE + "（" + LOG_PACKAGE + "* 另允许 "
                + INTERNAL_PACKAGE + "），违规：" + offenders, offenders.isEmpty());
    }

    /**
     * 门禁③：全模块不得引用 JDBC（本模块零 DB）。
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
     * 门禁④：不得改写 JVM 全局 HTTP 属性；相关键名只读不写，故连字面量都不允许出现。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void noJvmGlobalPropertyWrite() throws IOException {
        String[] forbidden = {
                "System.setProperty",
                "System.getProperties().put",
                "http.maxConnections",
                "http.keepAlive"
        };
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String content = read(file);
            for (String fragment : forbidden) {
                if (content.contains(fragment)) {
                    offenders.add(normalize(file) + " -> " + fragment);
                }
            }
        }
        assertTrue("不得改写 JVM 全局属性，也不得出现相关键名字面量，违规：" + offenders, offenders.isEmpty());
    }

    /**
     * 门禁⑤：根包直接子文件精确 6 个、文件名集合精确相等，且 6 个类型均为 public。
     *
     * @throws IOException 遍历目录失败
     */
    @Test
    public void publicSurfaceExact() throws IOException {
        List<String> rootTypes = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (isDirectRootFile(path)) {
                rootTypes.add(path.substring(path.lastIndexOf('/') + 1));
            }
        }
        assertEquals("根包公共类型应精确为 " + EXPECTED_PUBLIC_TYPES + " 个，实际为 " + rootTypes,
                EXPECTED_PUBLIC_TYPES, rootTypes.size());
        assertEquals("根包文件名集合应精确匹配，实际为 " + rootTypes,
                EXPECTED_ROOT_TYPES, new LinkedHashSet<>(rootTypes));

        for (String fqn : EXPECTED_PUBLIC_FQNS) {
            Class<?> type = load(fqn);
            assertTrue("公共类型必须为 public：" + fqn, Modifier.isPublic(type.getModifiers()));
        }
    }

    /**
     * 门禁⑥：资源名推导只允许在 {@code internal.HttpResource}，其余源码不得同时使用 {@code getHost()} 与
     * {@code getPath()}。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void resourceDerivationOnlyInHttpResource() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (path.endsWith(INTERNAL_PACKAGE + "HttpResource.java")) {
                continue;
            }
            String content = read(file);
            if (content.contains("getHost()") && content.contains("getPath()")) {
                offenders.add(path);
            }
        }
        assertTrue("资源名推导只允许出现在 internal/HttpResource.java，违规：" + offenders, offenders.isEmpty());
    }

    /**
     * 门禁⑦：传输细节不外溢——根包不得引用 {@code java.net.URL} / {@code java.net.HttpURLConnection}，
     * 后者只允许出现在 {@code internal}。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void jdkTransportOnlyInInternal() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            List<String> imports = imports(read(file));
            if (isDirectRootFile(path)) {
                for (String fqn : imports) {
                    if ("java.net.URL".equals(fqn) || "java.net.HttpURLConnection".equals(fqn)) {
                        offenders.add(path + " -> " + fqn);
                    }
                }
            }
            if (imports.contains("java.net.HttpURLConnection") && !path.contains(INTERNAL_PACKAGE)) {
                offenders.add(path + " -> java.net.HttpURLConnection");
            }
        }
        assertTrue("传输细节只允许出现在 " + INTERNAL_PACKAGE + "，违规：" + offenders, offenders.isEmpty());
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
     * 门禁⑨：接入包存在且含装配入口 {@code HttpPlugin.java}。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void bridgePackageExists() throws IOException {
        assertTrue("接入包不应为空：" + SOURCE_ROOT.resolve(BRIDGE_PACKAGE),
                Files.isDirectory(SOURCE_ROOT.resolve(BRIDGE_PACKAGE)));
        List<String> files = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            files.add(normalize(file));
        }
        assertTrue("HttpPlugin 应存在于接入包", containsSuffix(files, BRIDGE_PACKAGE + "HttpPlugin.java"));
    }

    /**
     * 门禁⑩：测试源码中的 URL 字面量只允许回环地址（零联网纪律）。
     *
     * <p>只看代码字面量：注释先由状态机剥离（区分代码 / 字符串字面量 / 字符字面量 / 行注释 / 块注释，
     * 处理 {@code \"}、{@code \\} 转义），故版权头里的官方仓库地址不参与判定；剥离注释不构成放宽——
     * 注释里出现地址不会发起任何请求，而字符串字面量里的 {@code //}、{@code /*} 原样保留，
     * 代码里出现外部地址必然被判失败（协议名大小写不敏感）。</p>
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
     *
     * <p>样本同行含「注释里的地址」与「代码里的地址」，断言只命中后者——若注释剥离失效或引号误入字符串态，
     * 命中数会变成 2 个（或样本尾部的代码地址被吞掉），断言即失败。</p>
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
     *
     * <p>样本第 2 行先在字符串里出现 {@code //}，紧随其后才是真正的外部地址——若 {@code //} 被当成行注释起点，
     * 该地址会被整行吞掉，命中数变 0；第 3 行是 {@code '\"'} 字符字面量 + 行注释里的地址，若字符字面量里的
     * 引号被误当字符串起点，注释里的地址会被保留，命中数变 2。正解为恰好 1 个命中。</p>
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
                || fqn.startsWith("cn.hg.aifei.http.");
    }

    /**
     * 判断是否为根包的直接子文件：位于根包目录下，且 {@link #ROOT_PACKAGE} 之后的相对路径不含 {@code /}。
     *
     * @param path 归一化后的源码路径
     * @return 为根包直接子文件时 {@code true}
     */
    private static boolean isDirectRootFile(String path) {
        int rootIndex = path.lastIndexOf(ROOT_PACKAGE);
        if (rootIndex < 0) {
            return false;
        }
        return !path.substring(rootIndex + ROOT_PACKAGE.length()).contains("/");
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
     * @return 以 {@code /} 分隔的相对路径
     */
    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    /**
     * 判断文件路径集合中是否存在以指定后缀结尾的项。
     *
     * @param files  归一化后的文件路径集合
     * @param suffix 期望的相对路径后缀
     * @return 存在时 {@code true}
     */
    private static boolean containsSuffix(List<String> files, String suffix) {
        for (String file : files) {
            if (file.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 反射加载公共类型。
     *
     * @param fqn 全限定名
     * @return 类型对象
     */
    private static Class<?> load(String fqn) {
        try {
            return Class.forName(fqn);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("公共类型不存在：" + fqn, e);
        }
    }

    /**
     * 以状态机剥离注释，只保留代码文本（含字符串 / 字符字面量的原文）。
     *
     * <p>状态区分「代码 / 字符串字面量 / 字符字面量 / 行注释 / 块注释」五种：只有处于代码态时
     * {@code //} 与 {@code /*} 才是注释起点，故字符串字面量里的 {@code "http://host/x"} 与 {@code "/*"}
     * 原样保留；字面量内处理 {@code \"}、{@code \\} 等转义；注释内的引号不影响状态机。
     * 行注释与块注释被删除，但其中的换行保留，避免相邻代码行被拼接。</p>
     *
     * <p>本项目为 Java 8，无文本块，故不处理 {@code """}。</p>
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
