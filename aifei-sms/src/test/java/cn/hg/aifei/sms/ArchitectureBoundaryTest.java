/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms;

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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 架构门禁（14 例）：按源码文本断言分层边界、公共面收敛、唯一定义点与 Java 8 底线。
 *
 * <p>依赖相对路径，必须在模块根目录执行（surefire workingDirectory 固定为 ${project.basedir}）。</p>
 */
public class ArchitectureBoundaryTest {

    private static final Path SOURCE_ROOT = Paths.get("src/main/java");
    private static final Path TEST_ROOT = Paths.get("src/test/java");
    private static final Path POM = Paths.get("pom.xml");

    private static final String ROOT_PACKAGE = "cn/hg/aifei/sms/";
    private static final String SPI_PACKAGE = "cn/hg/aifei/sms/spi/";
    private static final String BRIDGE_PACKAGE = "cn/hg/aifei/sms/aifei/";
    private static final String ADAPTER_PACKAGE = "cn/hg/aifei/sms/internal/";
    private static final String SMS4J_PACKAGE = "cn/hg/aifei/sms/internal/sms4j/";

    private static final int EXPECTED_ROOT_TYPES = 5;
    private static final int EXPECTED_SPI_TYPES = 2;
    private static final Set<String> EXPECTED_ROOT_TYPES_SET = new LinkedHashSet<String>(Arrays.asList(
            "SmsClient.java", "SmsKit.java", "SmsMessage.java", "SmsResult.java", "SmsException.java"));
    private static final Set<String> EXPECTED_SPI_TYPES_SET = new LinkedHashSet<String>(Arrays.asList(
            "SmsReceiptListener.java", "SmsReceipt.java"));

    private static final String ERRORS_FILE = ADAPTER_PACKAGE + "SmsErrors.java";
    private static final String SUPPLIER_FILE = SMS4J_PACKAGE + "SmsSupplierTypes.java";
    private static final String LOADER_FILE = BRIDGE_PACKAGE + "SmsConfigLoader.java";
    private static final String DEPS_FILE = SMS4J_PACKAGE + "SmsDeps.java";

    /** ① sms4j 类型只能在 internal.sms4j 适配子包。 */
    @Test
    public void sms4jOnlyInAdapter() throws IOException {
        for (Path file : javaFiles(SOURCE_ROOT)) {
            for (String line : imports(stripComments(read(file)))) {
                if (line.startsWith("org.dromara.sms4j.")) {
                    assertTrue("sms4j 类型只能出现在 internal.sms4j: " + normalize(file) + " -> " + line,
                            normalize(file).startsWith(SMS4J_PACKAGE));
                }
            }
        }
    }

    /** ② cn.aifei.* 只能在 aifei 接入包。 */
    @Test
    public void frameworkOnlyInBridgePackage() throws IOException {
        for (Path file : javaFiles(SOURCE_ROOT)) {
            for (String line : imports(stripComments(read(file)))) {
                if (line.startsWith("cn.aifei.")) {
                    assertTrue("框架类型只能出现在 aifei 接入包: " + normalize(file) + " -> " + line,
                            normalize(file).startsWith(BRIDGE_PACKAGE));
                }
            }
        }
    }

    /** ③ 库模块零 java.sql / io.undertow，pom 零 JDBC / aifei-db。 */
    @Test
    public void noSqlOrUndertowInLibrary() throws IOException {
        for (Path file : javaFiles(SOURCE_ROOT)) {
            for (String line : imports(stripComments(read(file)))) {
                assertFalse("禁止 Web/SQL import: " + normalize(file) + " -> " + line,
                        line.startsWith("java.sql.") || line.startsWith("io.undertow."));
            }
        }
        String pom = stripComments(read(POM)).toLowerCase();
        for (String forbidden : Arrays.asList("aifei-db", "mysql-connector", "postgresql",
                "ojdbc", "hikari", "druid", "c3p0", "tomcat-jdbc")) {
            assertFalse("pom 禁止 DB 坐标: " + forbidden, pom.contains(forbidden));
        }
    }

    /** ④ 全模块（含测试）零 org.slf4j 引用，pom 零日志绑定实现。 */
    @Test
    public void noSlf4jBinding() throws IOException {
        for (Path root : Arrays.asList(SOURCE_ROOT, TEST_ROOT)) {
            for (Path file : javaFiles(root)) {
                for (String line : imports(stripComments(read(file)))) {
                    assertFalse("禁止引用日志门面: " + normalize(file) + " -> " + line,
                            line.startsWith("org.slf4j."));
                }
            }
        }
        String pom = stripComments(read(POM));
        for (String artifact : Arrays.asList("slf4j-simple", "logback", "log4j")) {
            assertFalse("pom 禁止声明日志绑定: " + artifact, pom.contains(artifact));
        }
    }

    /** ⑤ 根包精确 5、spi 精确 2，文件名集合精确相等。 */
    @Test
    public void publicSurfaceExact() throws IOException {
        Set<String> rootNames = new LinkedHashSet<String>();
        Set<String> spiNames = new LinkedHashSet<String>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (isDirectChild(path, ROOT_PACKAGE)) {
                rootNames.add(file.getFileName().toString());
            }
            if (isDirectChild(path, SPI_PACKAGE)) {
                spiNames.add(file.getFileName().toString());
            }
        }
        assertEquals("根包公共类型数量必须为 5", EXPECTED_ROOT_TYPES, rootNames.size());
        assertEquals("根包公共类型集合必须精确相等", EXPECTED_ROOT_TYPES_SET, rootNames);
        assertEquals("spi 公共类型数量必须为 2", EXPECTED_SPI_TYPES, spiNames.size());
        assertEquals("spi 公共类型集合必须精确相等", EXPECTED_SPI_TYPES_SET, spiNames);
    }

    /** ⑥ 根包 + spi 公共签名零第三方 / 零框架类型字面量。 */
    @Test
    public void publicSignaturesNoThirdParty() throws IOException {
        for (Set<String> names : Arrays.asList(EXPECTED_ROOT_TYPES_SET, EXPECTED_SPI_TYPES_SET)) {
            for (String name : names) {
                String text = stripComments(read(SOURCE_ROOT.resolve(
                        names == EXPECTED_ROOT_TYPES_SET ? ROOT_PACKAGE : SPI_PACKAGE).resolve(name)));
                for (String forbidden : Arrays.asList("org.dromara.sms4j.", "cn.aifei.")) {
                    assertFalse("公共签名不得含第三方/框架类型: " + name + " -> " + forbidden,
                            text.contains(forbidden));
                }
            }
        }
    }

    /** ⑦ Class.forName 只允许在 SmsDeps。 */
    @Test
    public void classForNameOnlyInDeps() throws IOException {
        for (Path file : javaFiles(SOURCE_ROOT)) {
            if (stripComments(read(file)).contains("Class.forName")) {
                assertTrue("Class.forName 只允许出现在 SmsDeps.java: " + normalize(file),
                        normalize(file).equals(DEPS_FILE));
            }
        }
    }

    /** ⑧ 异常消息前缀 sms: 只允许在 SmsErrors。 */
    @Test
    public void errorMessagesOnlyInErrors() throws IOException {
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (path.equals(ERRORS_FILE)) {
                continue;
            }
            assertFalse("异常消息前缀只允许在 SmsErrors.java: " + path,
                    stripComments(read(file)).contains("sms: "));
        }
        assertTrue("SmsErrors.java 必须承载消息前缀", stripComments(read(SOURCE_ROOT.resolve(ERRORS_FILE)))
                .contains("sms: "));
    }

    /** ⑨ Java 8 语法 / API 底线。 */
    @Test
    public void java8Compatibility() throws IOException {
        String[] forbidden = {"List.of(", "Map.of(", "Set.of(", "record ", "\"\"\"", "sealed "};
        Pattern varDecl = Pattern.compile("\\bvar\\s+[A-Za-z_]\\w*\\s*=");
        Pattern patternInstanceof = Pattern.compile("instanceof\\s+[\\w.<>]+\\s+\\w+");
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String text = stripComments(read(file));
            for (String token : forbidden) {
                assertFalse("Java 8 不兼容字面量: " + normalize(file) + " -> " + token, text.contains(token));
            }
            assertFalse("禁止 var 局部声明: " + normalize(file), varDecl.matcher(text).find());
            assertFalse("禁止 instanceof 模式匹配: " + normalize(file), patternInstanceof.matcher(text).find());
        }
    }

    /** ⑩ pom 禁 starter / solon / Spring。 */
    @Test
    public void noStarterDependency() throws IOException {
        String pom = stripComments(read(POM));
        for (String forbidden : Arrays.asList("sms4j-spring-boot-starter", "sms4j-solon-plugin",
                "org.springframework", "org.noear")) {
            assertFalse("pom 禁止 starter/solon 依赖: " + forbidden, pom.contains(forbidden));
        }
    }

    /** ⑪ supplier 字面量映射只允许在 SmsSupplierTypes。 */
    @Test
    public void supplierTypesOnlyInFactory() throws IOException {
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (path.equals(SUPPLIER_FILE)) {
                continue;
            }
            assertFalse("supplier 字面量只能在 SmsSupplierTypes.java: " + path,
                    stripComments(read(file)).contains("\"alibaba\""));
        }
        assertTrue("SmsSupplierTypes.java 必须定义 alibaba 映射",
                stripComments(read(SOURCE_ROOT.resolve(SUPPLIER_FILE))).contains("\"alibaba\""));
    }

    /** ⑫ 接入构件存在且引用框架包。 */
    @Test
    public void pluginBridgeContract() throws IOException {
        for (String name : Arrays.asList("SmsPlugin.java", "SmsHandler.java")) {
            Path file = SOURCE_ROOT.resolve(BRIDGE_PACKAGE).resolve(name);
            assertTrue("aifei 接入构件缺失: " + name, Files.exists(file));
            assertTrue("接入构件须 import cn.aifei.*: " + name,
                    stripComments(read(file)).contains("import cn.aifei."));
        }
    }

    /** ⑬ 配置键名解析只允许在 SmsConfigLoader。 */
    @Test
    public void configKeysOnlyInLoader() throws IOException {
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (path.equals(LOADER_FILE)) {
                continue;
            }
            String text = stripComments(read(file));
            assertFalse("配置前缀解析只能在 SmsConfigLoader.java: " + path, text.contains("\"sms.\""));
            assertFalse("默认通道键只能在 SmsConfigLoader.java: " + path, text.contains("sms.default-channel"));
        }
        String loader = stripComments(read(SOURCE_ROOT.resolve(LOADER_FILE)));
        assertTrue("SmsConfigLoader 必须定义配置前缀", loader.contains("\"sms.\""));
        assertTrue("SmsConfigLoader 必须定义默认通道键", loader.contains("sms.default-channel"));
    }

    /** ⑭ 测试源码零外网 host。 */
    @Test
    public void testSourcesOfflineOnly() throws IOException {
        Pattern url = Pattern.compile("https?://([^\\s\"'()/]+)");
        for (Path file : javaFiles(TEST_ROOT)) {
            Matcher matcher = url.matcher(stripComments(read(file)));
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
        for (Path root : Arrays.asList(SOURCE_ROOT, TEST_ROOT)) {
            String prefix = root.toString().replace('\\', '/') + "/";
            if (path.startsWith(prefix)) {
                return path.substring(prefix.length());
            }
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
        return !path.substring(packagePath.length()).contains("/");
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

    /** 剥离 // 与 /* 注释，保留字符串 / URL 中的双斜杠内容不被误判。 */
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
}
