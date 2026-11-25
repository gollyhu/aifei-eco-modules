/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Test;

/**
 * 架构门禁：G-01 ~ G-15（设计 §10）。
 *
 * <p>源码文本扫描前先剥离注释，避免版权头与说明文字误报；扫描根为模块根目录下的相对路径，
 * 故必须在模块根目录执行（surefire 的 workingDirectory 已固定）。</p>
 */
public class ArchitectureBoundaryTest {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static final Path BASE = Paths.get("").toAbsolutePath();

    private static final Path MAIN = BASE.resolve("src/main/java");

    private static final Path TEST = BASE.resolve("src/test/java");

    private static final Path MAIN_PKG = MAIN.resolve("cn/hg/aifei/audit");

    private static final Path DEMO = BASE.getParent().resolve("aifei-audit-demo");

    private static final List<String> ROOT_TYPES = Arrays.asList(
            "Audit.java", "AuditEvent.java", "AuditException.java", "AuditKit.java");

    private static final List<String> SPI_TYPES = Arrays.asList(
            "AuditActorProvider.java", "AuditResultJudge.java", "AuditSink.java");

    /**
     * internal 包内允许引用框架类型的登记表（文件 → 允许的前缀）：设计 §5.5，恒为 1 条。
     */
    private static final Map<String, String> INTERNAL_FRAMEWORK_ALLOWED = new HashMap<String, String>();

    /**
     * `cn.aifei.*` 在 `aifei` 包之外的期望出现位置集合（文件 -> 框架类型前缀），与登记表逐条对应。
     */
    private static final Set<String> EXPECTED_INTERNAL_FRAMEWORK_POINTS = new TreeSet<String>();

    static {
        INTERNAL_FRAMEWORK_ALLOWED.put("internal/SafeInvoker.java", "cn.aifei.log.");
        EXPECTED_INTERNAL_FRAMEWORK_POINTS.add("internal/SafeInvoker.java -> cn.aifei.log.");
    }

    private static final List<String> CATCH_ALLOWED = Arrays.asList(
            "internal/SafeInvoker.java", "internal/AuditSpi.java", "internal/AuditCollector.java");

    private static final Pattern CATCH_BLOCK = Pattern.compile("catch\\s*\\([^)]*\\)\\s*\\{", Pattern.DOTALL);

    private static final Pattern THROW_SOON = Pattern.compile("^[\\s\\S]{0,200}?\\bthrow\\b");

    private static final Pattern URL = Pattern.compile("https?://([^/'\"\\s<>()]+)");

    /**
     * 协议相对 URL（`//host/...`）：`(?<!:)` 排除被 scheme 冒号占用的 `//`，
     * 使 `http://127.0.0.1:18160/...` 只由 {@link #URL} 命中，不被重复扫描。
     */
    private static final Pattern PROTOCOL_RELATIVE_URL =
            Pattern.compile("(?<!:)//([A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?)");

    @Test
    public void g01RootPackageHasExactlyFourPublicTypes() throws Exception {
        Set<String> names = new TreeSet<String>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(MAIN_PKG)) {
            for (Path path : stream) {
                if (path.toString().endsWith(".java")) {
                    names.add(path.getFileName().toString());
                }
            }
        }
        Assert.assertEquals("根包顶层类型集合必须精确匹配", new TreeSet<String>(ROOT_TYPES), names);

        for (String name : ROOT_TYPES) {
            String typeName = "cn.hg.aifei.audit." + name.substring(0, name.length() - ".java".length());
            Class<?> type = Class.forName(typeName);
            Assert.assertTrue("根包类型必须 public: " + typeName, Modifier.isPublic(type.getModifiers()));
        }
        Class<?> result = Class.forName("cn.hg.aifei.audit.AuditEvent$Result");
        Assert.assertTrue("Result 必须是嵌套枚举", result.isEnum());
        Assert.assertTrue(Modifier.isPublic(result.getModifiers()));
    }

    @Test
    public void g02SpiPackageHasExactlyThreeTypes() throws Exception {
        Set<String> names = new TreeSet<String>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(MAIN_PKG.resolve("spi"))) {
            for (Path path : stream) {
                if (path.toString().endsWith(".java")) {
                    names.add(path.getFileName().toString());
                }
            }
        }
        Assert.assertEquals("spi 包顶层类型集合必须精确匹配", new TreeSet<String>(SPI_TYPES), names);
    }

    @Test
    public void g03FrameworkTypesStayInAccessPackageOrRegisteredInternalPoints() throws Exception {
        Assert.assertTrue("缺少 AuditPlugin", Files.exists(MAIN_PKG.resolve("aifei/AuditPlugin.java")));
        Assert.assertTrue("缺少 AuditInterceptor", Files.exists(MAIN_PKG.resolve("aifei/AuditInterceptor.java")));
        Assert.assertEquals("internal 框架引用登记表只允许 1 条（不得为其它 internal 类型开口子）",
                1, INTERNAL_FRAMEWORK_ALLOWED.size());

        Set<String> observed = new TreeSet<String>();
        for (Path file : javaFiles(MAIN)) {
            String text = stripComments(read(file));
            String relative = relativePath(file);
            if (relative.startsWith("aifei/")) {
                continue;
            }
            String registered = INTERNAL_FRAMEWORK_ALLOWED.get(relative);
            int from = 0;
            int index;
            while ((index = text.indexOf("cn.aifei.", from)) >= 0) {
                int end = index;
                while (end < text.length() && isFqnChar(text.charAt(end))) {
                    end++;
                }
                String fqn = text.substring(index, end);
                boolean covered = registered != null && fqn.startsWith(registered);
                observed.add(relative + " -> " + (covered ? registered : fqn));
                from = index + 1;
            }
        }
        Assert.assertEquals("cn.aifei.* 出现位置集合必须精确等于 aifei 包 ∪ 唯一 internal 登记点",
                EXPECTED_INTERNAL_FRAMEWORK_POINTS, observed);
    }

    @Test
    public void g04NoStorageAccess() throws Exception {
        assertAbsentInMains("java.sql.", "禁止任何存储访问");
    }

    @Test
    public void g05NoVipReferences() throws Exception {
        assertAbsent(MAIN, "cn.aifei.vip.");
        assertAbsent(TEST, "cn.aifei.vip.");
    }

    @Test
    public void g06NoEcosystemModuleReferences() throws Exception {
        String[] prefixes = {"cn.hg.aifei.satoken", "cn.hg.aifei.mq", "cn.hg.aifei.trace",
                "cn.hg.aifei.cache", "cn.hg.aifei.lock", "cn.hg.aifei.sentinel", "cn.hg.aifei.weixin",
                "cn.hg.aifei.oss", "cn.hg.aifei.job", "cn.hg.aifei.http", "cn.hg.aifei.id",
                "cn.hg.aifei.excel", "cn.hg.aifei.websocket"};
        for (int i = 0; i < prefixes.length; i++) {
            assertAbsent(MAIN, prefixes[i]);
            assertAbsent(TEST, prefixes[i]);
        }
    }

    @Test
    public void g07NoThreadCreation() throws Exception {
        String[] tokens = {"new Thread(", "Executors.", "ThreadPoolExecutor", "ForkJoinPool",
                "java.util.Timer", "ScheduledExecutorService"};
        for (int i = 0; i < tokens.length; i++) {
            assertAbsentInMains(tokens[i], "禁止线程 / 线程池 / 定时器");
        }
    }

    @Test
    public void g08MaskingAndTruncationHaveSingleDefinitionPoint() throws Exception {
        for (Path file : javaFiles(MAIN)) {
            String text = stripComments(read(file));
            String relative = relativePath(file);
            boolean ownsLogic = "internal/Truncator.java".equals(relative);
            if (text.contains("length() >")) {
                Assert.assertTrue("length() > 只允许出现在 Truncator: " + relative, ownsLogic);
            }
            if (text.contains("substring(")) {
                Assert.assertTrue("substring( 只允许出现在 Truncator: " + relative, ownsLogic);
            }
            if (text.contains("\"<masked>\"")) {
                Assert.assertTrue("掩码占位符只允许在 Truncator 定义: " + relative, ownsLogic);
            }
        }
        String truncator = stripComments(read(MAIN_PKG.resolve("internal/Truncator.java")));
        Assert.assertTrue(truncator.contains("static String summary("));
        Assert.assertTrue(truncator.contains("static String truncate("));
    }

    @Test
    public void g09CatchIsLimitedToRegisteredIsolationPoints() throws Exception {
        for (Path file : javaFiles(MAIN)) {
            String text = stripComments(read(file));
            String relative = relativePath(file);
            if (!text.contains("catch (")) {
                continue;
            }
            Assert.assertTrue("catch 只允许出现在白名单文件: " + relative, CATCH_ALLOWED.contains(relative));
            if ("internal/AuditCollector.java".equals(relative)) {
                Matcher matcher = CATCH_BLOCK.matcher(text);
                boolean found = false;
                while (matcher.find()) {
                    found = true;
                    String tail = text.substring(matcher.end());
                    Assert.assertTrue("AuditCollector 的 catch 必须在 200 字符内重新抛出",
                            THROW_SOON.matcher(tail).find());
                }
                Assert.assertTrue("AuditCollector 应至少有一个 catch 块", found);
            }
        }
        for (Path file : javaFiles(MAIN_PKG)) {
            String relative = relativePath(file);
            if (relative.startsWith("internal/")) {
                continue;
            }
            Assert.assertFalse("根包 / spi / aifei 包不得出现 catch: " + relative,
                    stripComments(read(file)).contains("catch ("));
        }
    }

    @Test
    public void g10NoStandardOutput() throws Exception {
        assertAbsentInMains("System.out", "禁止标准输出");
        assertAbsentInMains("System.err", "禁止标准错误输出");
        assertAbsentInMains("printStackTrace", "禁止 printStackTrace");
    }

    @Test
    public void g11NoLoggingBinding() throws Exception {
        assertAbsentInMains("org.slf4j.", "禁止日志绑定");
        assertAbsentInMains("log4j", "禁止日志绑定");
        assertAbsentInMains("logback", "禁止日志绑定");
    }

    @Test
    public void g12PublicSurfaceIsImplementationFreeAndNamingIsEnforced() throws Exception {
        for (Path file : javaFiles(MAIN_PKG)) {
            String relative = relativePath(file);
            boolean publicSurface = relative.indexOf('/') < 0 || relative.startsWith("spi/");
            if (!publicSurface) {
                continue;
            }
            String text = stripComments(read(file));
            String[] lines = text.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (!line.startsWith("import ")) {
                    continue;
                }
                boolean allowed = line.startsWith("import java.") || line.startsWith("import javax.")
                        || line.startsWith("import cn.hg.aifei.audit.");
                Assert.assertTrue("公共面不得依赖实现或第三方类型: " + relative + " -> " + line, allowed);
            }
        }
        Assert.assertTrue("必须存在 Kit 门面", Files.exists(MAIN_PKG.resolve("AuditKit.java")));

        String forbidden = "Audit" + "Util";
        for (Path file : javaFiles(MAIN)) {
            Assert.assertFalse("命名纪律：门面不得改名 " + file,
                    file.getFileName().toString().contains(forbidden));
            Assert.assertFalse("命名纪律：源码不得出现 " + relativePath(file),
                    stripComments(read(file)).contains(forbidden));
        }
        for (Path file : javaFiles(TEST)) {
            Assert.assertFalse("命名纪律：测试源码不得出现该类型名: " + file,
                    file.getFileName().toString().contains(forbidden));
        }
    }

    @Test
    public void g13DemoIsOffline() throws Exception {
        Path demoSource = DEMO.resolve("src");
        if (!Files.isDirectory(demoSource)) {
            return;
        }
        for (Path file : allFiles(demoSource)) {
            String name = file.getFileName().toString();
            String text = read(file);
            if (name.endsWith(".java") || name.endsWith(".js") || name.endsWith(".css")) {
                text = stripComments(text);
            }
            Matcher matcher = URL.matcher(text);
            while (matcher.find()) {
                String host = matcher.group(1);
                boolean allowed = host.equals("127.0.0.1") || host.startsWith("127.0.0.1:")
                        || host.equals("localhost") || host.startsWith("localhost:");
                Assert.assertTrue("demo 只允许本机 host: " + file + " -> " + host, allowed);
            }
            Matcher relative = PROTOCOL_RELATIVE_URL.matcher(text);
            while (relative.find()) {
                String host = relative.group(1);
                boolean allowed = host.equals("127.0.0.1") || host.startsWith("127.0.0.1:")
                        || host.equals("localhost") || host.startsWith("localhost:");
                Assert.assertTrue("demo 协议相对 URL 只允许本机 host: " + file + " -> " + host, allowed);
            }
        }
    }

    @Test
    public void g14Java8SourceConstraints() throws Exception {
        // 设计 §10「门禁实现的两点口径登记」第 2 点：var / record / sealed 用语法模式而非字面量，
        // 避免误伤 `for (String record : RECORDS)` 这类合法 Java 8 代码；紧，不放宽。
        Pattern[] patterns = {
                Pattern.compile("\\bvar\\s+[A-Za-z_$]\\w*\\s*="),
                Pattern.compile("\\brecord\\s+[A-Z]\\w*\\s*\\("),
                Pattern.compile("\\bsealed\\s+(class|interface)\\b")};
        String[] tokens = {"\"\"\"", "List.of(", "Map.of(", "Set.of("};
        for (Path root : Arrays.asList(MAIN, TEST)) {
            for (Path file : javaFiles(root)) {
                String text = stripComments(read(file));
                for (int i = 0; i < patterns.length; i++) {
                    Assert.assertFalse("Java 8 不允许该语法: " + file,
                            patterns[i].matcher(text).find());
                }
                for (int i = 0; i < tokens.length; i++) {
                    Assert.assertFalse("Java 8 不允许该语法（" + tokens[i] + "）: " + file,
                            text.contains(tokens[i]));
                }
            }
        }
    }

    @Test
    public void g15ClockEventIdConfigAnnotationHaveSingleDefinitionPoint() throws Exception {
        assertOnlyInMain("System.nanoTime", "internal/Clock.java");
        assertOnlyInMain("System.currentTimeMillis", "internal/Clock.java");
        assertOnlyInMain("UUID", "internal/EventIds.java");
        assertOnlyInMain("\"audit.", "internal/AuditConfig.java");
        assertOnlyInMain("getAnnotation(Audit.class)", "internal/AuditAnnotations.java");
    }

    private static String relativePath(Path file) {
        return MAIN_PKG.relativize(file).toString().replace('\\', '/');
    }

    private static boolean isFqnChar(char c) {
        return Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '$';
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), UTF8);
    }

    /**
     * 列出源码文件；门禁用例自身存放了全部被禁字面量，作为被测对象豁免。
     *
     * <p>此豁免对应设计 §10「门禁实现的两点口径登记」第 1 点：豁免对象是门禁实现（不进交付 JAR），
     * 其余 {@code src/test/java} 文件仍全量扫描，故不构成对被测代码的放宽。</p>
     */
    private static List<Path> javaFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<Path>();
        if (!Files.isDirectory(root)) {
            return files;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("ArchitectureBoundaryTest.java"))
                    .forEach(path -> files.add(path));
        }
        return files;
    }

    private static List<Path> allFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<Path>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile).forEach(path -> files.add(path));
        }
        return files;
    }

    private static void assertAbsent(Path root, String token) throws IOException {
        for (Path file : javaFiles(root)) {
            Assert.assertFalse("禁止出现 " + token + ": " + file,
                    stripComments(read(file)).contains(token));
        }
    }

    private static void assertAbsentInMains(String token, String reason) throws IOException {
        for (Path file : javaFiles(MAIN)) {
            Assert.assertFalse(reason + "（" + token + "）: " + file,
                    stripComments(read(file)).contains(token));
        }
    }

    private static void assertOnlyInMain(String token, String expectedRelative) throws IOException {
        for (Path file : javaFiles(MAIN)) {
            String relative = relativePath(file);
            if (stripComments(read(file)).contains(token)) {
                Assert.assertEquals("唯一定义点: " + token, expectedRelative, relative);
            }
        }
        String expected = stripComments(read(MAIN_PKG.resolve(expectedRelative)));
        Assert.assertTrue("唯一定义点必须真实存在: " + token, expected.contains(token));
    }

    /**
     * 剥离行注释、块注释（保留字符串字面量，供字面量断言使用）。
     *
     * @param source 源码
     * @return 剥离注释后的文本
     */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                while (i < source.length() && source.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < source.length()
                        && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) {
                    i++;
                }
                i += 2;
            } else if (c == '"') {
                out.append(c);
                i++;
                while (i < source.length()) {
                    char d = source.charAt(i);
                    out.append(d);
                    i++;
                    if (d == '\\' && i < source.length()) {
                        out.append(source.charAt(i));
                        i++;
                    } else if (d == '"') {
                        break;
                    }
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
