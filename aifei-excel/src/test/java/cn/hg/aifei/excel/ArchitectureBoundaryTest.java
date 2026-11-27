/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.excel;

import org.junit.Test;

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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 架构门禁（14 例）：按源码文本断言分层边界、公共面收敛与 Java 8 底线。
 *
 * <p>执行位置必须为 aifei-excel 模块根目录（依赖相对路径 src/main/java），
 * 由 surefire workingDirectory 固定为 ${project.basedir} 保证。</p>
 */
public class ArchitectureBoundaryTest {

    private static final Path SOURCE_ROOT = Paths.get("src/main/java");
    private static final Path TEST_ROOT = Paths.get("src/test/java");
    private static final Path RESOURCE_ROOT = Paths.get("src/main/resources");
    private static final Path POM = Paths.get("pom.xml");

    private static final String ROOT_PACKAGE = "cn/hg/aifei/excel/";
    private static final String ADAPTER_PACKAGE = "cn/hg/aifei/excel/internal/easyexcel/";
    private static final String ROOT_PACKAGE_DOT = "cn.hg.aifei.excel";
    private static final int EXPECTED_ROOT_TYPES = 5;

    private static final Set<String> EXPECTED_ROOT_TYPES_SET = new LinkedHashSet<String>(Arrays.asList(
            "Excel.java", "ExcelKit.java", "ExcelException.java",
            "ExcelColumn.java", "ExcelIgnore.java"));

    /** ① EasyExcel / POI import 只允许在适配层。 */
    @Test
    public void easyExcelOnlyInAdapter() throws IOException {
        List<Path> files = javaFiles(SOURCE_ROOT);
        for (Path file : files) {
            String path = normalize(file);
            for (String line : imports(stripComments(read(file)))) {
                // imports() 已剥掉 "import " 前缀，此处匹配裸类名前缀。
                if (line.startsWith("com.alibaba.") || line.startsWith("org.apache.poi.")) {
                    assertTrue("第三方实现类型泄漏出适配层: " + path + " -> " + line,
                            path.contains(ADAPTER_PACKAGE));
                }
            }
        }
    }

    /** ② 全模块零 cn.aifei.* 引用（无接入包、无 log 例外）。 */
    @Test
    public void noFrameworkReference() throws IOException {
        assertNoImportInSources(SOURCE_ROOT, "cn.aifei.", "禁止引用框架包 cn.aifei.*");
    }

    /** ③ 库模块本体零 Web / 零 DB。 */
    @Test
    public void noWebOrSqlInLibrary() throws IOException {
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String text = stripComments(read(file));
            for (String line : imports(text)) {
                assertFalse("库模块禁止 Web/SQL import: " + normalize(file) + " -> " + line,
                        line.startsWith("io.undertow.") || line.startsWith("java.sql."));
            }
        }
    }

    /** ④ 库模块零 slf4j 引用与日志绑定。 */
    @Test
    public void noSlf4jBinding() throws IOException {
        assertNoImportInSources(SOURCE_ROOT, "org.slf4j.", "库模块禁止引用日志门面");
        String pom = stripComments(read(POM));
        for (String artifact : Arrays.asList("slf4j-simple", "logback", "log4j")) {
            assertFalse("pom 禁止声明日志绑定: " + artifact, pom.contains(artifact));
        }
    }

    /** ⑤ 根包直接子 .java 精确 5 个且文件名集合精确相等。 */
    @Test
    public void publicSurfaceExact() throws IOException {
        List<String> names = new ArrayList<String>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (isDirectChild(path, ROOT_PACKAGE)) {
                names.add(file.getFileName().toString());
            }
        }
        assertEquals("根包公共类型数量必须为 5", EXPECTED_ROOT_TYPES, names.size());
        assertEquals("根包公共类型文件名集合不符", EXPECTED_ROOT_TYPES_SET,
                new LinkedHashSet<String>(names));
    }

    /** ⑥ 五个公共类型源码零第三方 / 零框架字面量。 */
    @Test
    public void publicSignaturesNoThirdParty() throws IOException {
        for (String name : EXPECTED_ROOT_TYPES_SET) {
            Path file = SOURCE_ROOT.resolve(ROOT_PACKAGE).resolve(name);
            String text = stripComments(read(file));
            for (String forbidden : Arrays.asList("com.alibaba.", "org.apache.poi.", "cn.aifei.")) {
                assertFalse("公共类型不得含第三方/框架字面量: " + name + " -> " + forbidden,
                        text.contains(forbidden));
            }
        }
    }

    /** ⑦ pom 零 java.sql / JDBC 驱动 / 连接池坐标。 */
    @Test
    public void noSqlOrJdbcInPom() throws IOException {
        String pom = stripComments(read(POM));
        for (String forbidden : Arrays.asList("java.sql", "mysql-connector", "postgresql",
                "ojdbc", "hikari", "druid", "c3p0", "tomcat-jdbc")) {
            assertFalse("pom 禁止 DB 相关坐标/字面量: " + forbidden,
                    pom.toLowerCase().contains(forbidden.toLowerCase()));
        }
    }

    /** ⑧ 零 Java 8 之后的语法 / API。 */
    @Test
    public void java8Compatibility() throws IOException {
        String[] forbidden = {"List.of(", "Map.of(", "Set.of(", "record ", "var ",
                "\"\"\"", "sealed "};
        java.util.regex.Pattern patternMatch = java.util.regex.Pattern
                .compile("instanceof\\s+[\\w.<>]+\\s+\\w+");
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String text = stripComments(read(file));
            for (String token : forbidden) {
                assertFalse("Java 8 不兼容字面量: " + normalize(file) + " -> " + token,
                        text.contains(token));
            }
            assertFalse("Java 8 不支持 instanceof 模式匹配: " + normalize(file),
                    patternMatch.matcher(text).find());
        }
    }

    /** ⑨ 无 provider / 无 SPI / 无 converterClass 属性。 */
    @Test
    public void noServiceProvider() throws IOException {
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String text = stripComments(read(file));
            for (String forbidden : Arrays.asList("ServiceLoader", "META-INF/services", "converterClass")) {
                assertFalse("禁止 provider/SPI: " + normalize(file) + " -> " + forbidden,
                        text.contains(forbidden));
            }
        }
        Path services = RESOURCE_ROOT.resolve("META-INF/services");
        assertFalse("不得存在 META-INF/services 目录", Files.exists(services));
    }

    /** ⑩ Class.forName 只允许在 ExcelDeps。 */
    @Test
    public void classForNameOnlyInDeps() throws IOException {
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String text = stripComments(read(file));
            if (!text.contains("Class.forName")) {
                continue;
            }
            assertTrue("Class.forName 只允许出现在 ExcelDeps.java: " + normalize(file),
                    normalize(file).equals(ADAPTER_PACKAGE + "ExcelDeps.java"));
        }
    }

    /** ⑪ 注解形状精确：ExcelColumn 仅 value/index，ExcelIgnore 无属性。 */
    @Test
    public void annotationShapeExact() throws IOException {
        String column = stripComments(read(SOURCE_ROOT.resolve(ROOT_PACKAGE).resolve("ExcelColumn.java")));
        assertEquals("ExcelColumn 必须恰有 value 与 index 两个属性", 2, countAnnotationMethods(column));
        assertTrue("ExcelColumn 声明 value 属性", column.contains("String value()"));
        assertTrue("ExcelColumn 声明 index 属性", column.contains("int index()"));
        String ignore = stripComments(read(SOURCE_ROOT.resolve(ROOT_PACKAGE).resolve("ExcelIgnore.java")));
        assertEquals("ExcelIgnore 不得声明属性方法", 0, countAnnotationMethods(ignore));
    }

    /** ⑫ 默认批大小 1000 字面量只在 Excel.java。 */
    @Test
    public void batchSizeDefaultOnlyInExcel() throws IOException {
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (path.equals(ROOT_PACKAGE + "Excel.java")) {
                continue;
            }
            assertFalse("默认批大小字面量只允许在 Excel.java: " + path,
                    stripComments(read(file)).contains("1000"));
        }
        assertTrue("Excel.java 必须声明默认批大小 1000",
                stripComments(read(SOURCE_ROOT.resolve(ROOT_PACKAGE).resolve("Excel.java"))).contains("1000"));
    }

    /** ⑬ 异常消息前缀 excel: 只允许在 ExcelErrors.java。 */
    @Test
    public void errorMessagesOnlyInErrors() throws IOException {
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (path.equals(ADAPTER_PACKAGE + "ExcelErrors.java")) {
                continue;
            }
            assertFalse("异常消息前缀只允许在 ExcelErrors.java: " + path,
                    stripComments(read(file)).contains("excel: "));
        }
        assertTrue("ExcelErrors.java 必须承载消息前缀",
                stripComments(read(SOURCE_ROOT.resolve(ADAPTER_PACKAGE).resolve("ExcelErrors.java")))
                        .contains("excel: "));
    }

    /** ⑭ 测试源码零外网：http(s) 字面量 host 只能是 127.0.0.1 或 localhost。 */
    @Test
    public void testSourcesOfflineOnly() throws IOException {
        for (Path file : javaFiles(TEST_ROOT)) {
            String text = stripComments(read(file));
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("https?://([^\\s\"'()/]+)").matcher(text);
            while (matcher.find()) {
                String host = matcher.group(1).toLowerCase();
                assertTrue("测试禁止外网地址: " + normalize(file) + " -> " + matcher.group(),
                        "127.0.0.1".equals(host) || "localhost".equals(host));
            }
        }
    }

    // ---------------------------------------------------------------- 工具方法

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static String normalize(Path file) {
        String path = file.toString().replace('\\', '/');
        String prefixDir = SOURCE_ROOT.toString().replace('\\', '/') + "/";
        String testPrefix = TEST_ROOT.toString().replace('\\', '/') + "/";
        if (path.startsWith(prefixDir)) {
            return path.substring(prefixDir.length());
        }
        if (path.startsWith(testPrefix)) {
            return path.substring(testPrefix.length());
        }
        return path;
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        if (!Files.exists(root)) {
            return new ArrayList<Path>();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }
    }

    private static boolean isDirectChild(String path, String packagePath) {
        if (!path.startsWith(packagePath)) {
            return false;
        }
        String rest = path.substring(packagePath.length());
        return !rest.contains("/");
    }

    private static List<String> imports(String javaSource) {
        List<String> result = new ArrayList<String>();
        for (String line : javaSource.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("import ")) {
                result.add(trimmed.substring("import ".length()).trim());
            }
        }
        return result;
    }

    private static int countAnnotationMethods(String javaSource) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?m)^\\s*(?:public\\s+)?[\\w.<>?\\[\\]]+\\s+(\\w+)\\s*\\(\\s*\\)\\s*(?:default\\s+[^;]+)?\\s*;")
                .matcher(javaSource);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * 剥离 // 与 /* 注释，保留字符串与 URL 中的双斜杠内容不被误判。
     */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        boolean inString = false;
        boolean inChar = false;
        while (i < n) {
            char c = source.charAt(i);
            char next = i + 1 < n ? source.charAt(i + 1) : '\0';
            if (inString) {
                out.append(c);
                if (c == '\\' && i + 1 < n) {
                    out.append(next);
                    i += 2;
                    continue;
                }
                if (c == '"') {
                    inString = false;
                }
                i++;
                continue;
            }
            if (inChar) {
                out.append(c);
                if (c == '\\' && i + 1 < n) {
                    out.append(next);
                    i += 2;
                    continue;
                }
                if (c == '\'') {
                    inChar = false;
                }
                i++;
                continue;
            }
            if (c == '"') {
                inString = true;
                out.append(c);
                i++;
                continue;
            }
            if (c == '\'') {
                inChar = true;
                out.append(c);
                i++;
                continue;
            }
            if (c == '/' && next == '/') {
                while (i < n && source.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
                continue;
            }
            if (c == '/' && next == '*') {
                out.append(' ').append(' ');
                i += 2;
                while (i < n && !(source.charAt(i) == '*' && i + 1 < n && source.charAt(i + 1) == '/')) {
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) {
                    out.append(' ').append(' ');
                    i += 2;
                }
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static void assertNoImportInSources(Path root, String forbidden, String message)
            throws IOException {
        for (Path file : javaFiles(root)) {
            for (String line : imports(stripComments(read(file)))) {
                assertFalse(message + ": " + normalize(file) + " -> " + line,
                        line.startsWith(forbidden));
            }
        }
    }
}
