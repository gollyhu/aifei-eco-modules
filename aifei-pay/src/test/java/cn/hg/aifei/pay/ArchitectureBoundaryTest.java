/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 架构门禁（设计 §10）：以源码文本扫描 + 产物字节码断言保证包边界、公共面大小、Java 8 基线与离线纪律。
 *
 * <pre>
 * G-01 g01PublicSurfaceExact             根包 4 类型 + model 包 4 类型集合精确相等且 public
 * G-02 g02NoThirdPartyInPublicSurface    公共面零第三方 / 框架 import 与继承
 * G-03 g03NoSqlAndNoOtherChannels        零 JDBC、零其它支付渠道
 * G-04 g04LayerBoundary                  第三方类型只在登记的 internal 文件出现
 * G-05 g05Java8AndBytecode               产物 class major == 52，源码无 Java 9+ 语法与集合工厂
 * G-06 g06DependencyDiscipline           直接依赖 6 项、无日志门面、三个 exclusion 精确存在
 * a01  a01NoContainerArtifacts           零容器化产物、主源码无入口方法
 * a02  a02NoForbiddenDocWord             文档不含被禁词根
 * a03  a03NoGlobalMutableState           无进程级可变全局状态
 * a04  a04OfflineOnly                    单测零联网、主源码的网关字面量只在一处
 * </pre>
 *
 * <p>扫描对象为 {@code src/main/java}（a04 另扫 {@code src/test/java}，a02 扫文档），扫描前统一剥离注释，
 * 避免版权头与说明文字误报。路径均为模块根相对路径，因此本测试必须在 {@code aifei-pay/} 内执行
 * （{@code surefire} 的 {@code workingDirectory} 已固定为模块根）。</p>
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
     * 模块根目录。
     */
    private static final Path MODULE_ROOT = Paths.get(".");

    /**
     * 编译产物目录，相对模块根。
     */
    private static final Path CLASSES_ROOT = Paths.get("target/classes");

    /**
     * 构建文件路径。
     */
    private static final Path POM = Paths.get("pom.xml");

    /**
     * 根包目录（相对 {@code src/main/java}）。
     */
    private static final String ROOT_PACKAGE = "cn/hg/aifei/pay";

    /**
     * 根包目录（带尾斜杠）。
     */
    private static final String ROOT_DIR = ROOT_PACKAGE + "/";

    /**
     * 值对象包目录（带尾斜杠）。
     */
    private static final String MODEL_DIR = ROOT_DIR + "model/";

    /**
     * 实现层包目录（带尾斜杠）。
     */
    private static final String INTERNAL_DIR = ROOT_DIR + "internal/";

    /**
     * 框架接入包目录（带尾斜杠）。
     */
    private static final String BRIDGE_DIR = ROOT_DIR + "aifei/";

    /**
     * 根包公共类型的期望文件名集合。
     */
    private static final Set<String> EXPECTED_ROOT_TYPES = new LinkedHashSet<>(Arrays.asList(
            "AliPayClient.java",
            "AliPayKit.java",
            "AliPayNotifyUtil.java",
            "AliPayException.java"
    ));

    /**
     * 值对象包的期望文件名集合。
     */
    private static final Set<String> EXPECTED_MODEL_TYPES = new LinkedHashSet<>(Arrays.asList(
            "PayOrderResult.java",
            "PayQueryResult.java",
            "PayNotifyResult.java",
            "PayRefundResult.java"
    ));

    /**
     * 根包公共类型全限定名。
     */
    private static final String[] ROOT_FQNS = {
            "cn.hg.aifei.pay.AliPayClient",
            "cn.hg.aifei.pay.AliPayKit",
            "cn.hg.aifei.pay.AliPayNotifyUtil",
            "cn.hg.aifei.pay.AliPayException"
    };

    /**
     * 值对象全限定名。
     */
    private static final String[] MODEL_FQNS = {
            "cn.hg.aifei.pay.model.PayOrderResult",
            "cn.hg.aifei.pay.model.PayQueryResult",
            "cn.hg.aifei.pay.model.PayNotifyResult",
            "cn.hg.aifei.pay.model.PayRefundResult"
    };

    /**
     * 必须为 public 嵌套枚举的嵌套类型全限定名。
     */
    private static final String[] NESTED_ENUMS = {
            "cn.hg.aifei.pay.model.PayOrderResult$Channel",
            "cn.hg.aifei.pay.model.PayOrderResult$PayloadType",
            "cn.hg.aifei.pay.model.PayNotifyResult$NotifyType"
    };

    /**
     * 公共面禁止出现的第三方 / 框架包前缀。
     */
    private static final String[] PUBLIC_FORBIDDEN_PREFIXES = {
            "com.ijpay.",
            "com.alipay.",
            "com.alibaba.fastjson",
            "cn.aifei."
    };

    /**
     * 公共面禁止出现的继承 / 实现片段。
     */
    private static final String[] PUBLIC_FORBIDDEN_DECLARATIONS = {
            "extends com.ijpay",
            "extends com.alipay",
            "implements com.ijpay",
            "implements com.alipay"
    };

    /**
     * 其它支付渠道的包前缀：本模块只做支付宝。
     */
    private static final String[] OTHER_CHANNEL_PREFIXES = {
            "com.ijpay.wxpay",
            "com.ijpay.qq",
            "com.ijpay.jdpay",
            "com.ijpay.unionpay",
            "com.ijpay.paypal",
            "com.ijpay.stripe",
            "com.ijpay.fuiou",
            "com.ijpay.xpay"
    };

    /**
     * 其它渠道的构件名片段。
     */
    private static final String[] OTHER_CHANNEL_ARTIFACTS = {
            "IJPay-WxPay",
            "IJPay-Servlet-"
    };

    /**
     * {@code cn.aifei.*} 的例外登记表：文件（模块根相对路径）→ 允许的前缀，必须精确相等。
     */
    private static final Map<String, String> FRAMEWORK_EXCEPTIONS = new LinkedHashMap<>();

    static {
        FRAMEWORK_EXCEPTIONS.put("src/main/java/" + INTERNAL_DIR + "PayErrors.java", "cn.aifei.log.");
        FRAMEWORK_EXCEPTIONS.put("src/main/java/" + INTERNAL_DIR + "PayVerifier.java", "cn.aifei.log.");
    }

    /**
     * {@code com.ijpay.*} 的允许文件集合。
     */
    private static final Set<String> IJPAY_ALLOWED = new LinkedHashSet<>(Arrays.asList(
            "src/main/java/" + INTERNAL_DIR + "IjpayPayKernel.java",
            "src/main/java/" + INTERNAL_DIR + "PayClients.java"
    ));

    /**
     * {@code com.alipay.*} 的允许文件集合。
     */
    private static final Set<String> ALIPAY_ALLOWED = new LinkedHashSet<>(Arrays.asList(
            "src/main/java/" + INTERNAL_DIR + "IjpayPayKernel.java",
            "src/main/java/" + INTERNAL_DIR + "PayClients.java",
            "src/main/java/" + INTERNAL_DIR + "PayVerifyKey.java",
            "src/main/java/" + INTERNAL_DIR + "PayVerifier.java"
    ));

    /**
     * 内核调用点文件（必须实际引用 {@code com.ijpay.*}）。
     */
    private static final String IJPAY_KERNEL_FILE = "src/main/java/" + INTERNAL_DIR + "IjpayPayKernel.java";

    /**
     * 验签入口文件（必须实际引用 {@code com.alipay.*}）。
     */
    private static final String PAY_VERIFIER_FILE = "src/main/java/" + INTERNAL_DIR + "PayVerifier.java";

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
     * 继承内核类型的片段（组合非继承）。
     */
    private static final String[] INHERITANCE_FORBIDDEN = {
            "extends com.ijpay.",
            "extends com.alipay."
    };

    /**
     * {@code instanceof} 模式匹配（类型后紧跟绑定变量名）。
     */
    private static final Pattern INSTANCEOF_PATTERN =
            Pattern.compile("instanceof\\s+[\\w.$]+\\s+[A-Za-z_$]");

    /**
     * 期望的直接依赖集合。
     */
    private static final Set<String> EXPECTED_DEPENDENCIES = new LinkedHashSet<>(Arrays.asList(
            "cn.aifei:aifei-all:provided",
            "com.github.javen205:IJPay-AliPay-V3:optional",
            "com.alipay.sdk:alipay-sdk-java-v3:optional",
            "org.dom4j:dom4j:optional",
            "org.bouncycastle:bcprov-jdk18on:optional",
            "junit:junit:test"
    ));

    /**
     * 期望的 SDK 依赖 exclusion 集合。
     */
    private static final Set<String> EXPECTED_EXCLUSIONS = new LinkedHashSet<>(Arrays.asList(
            "dom4j:dom4j",
            "org.bouncycastle:bcprov-jdk15on",
            "org.openapitools:jackson-databind-nullable"
    ));

    /**
     * 日志门面 / 绑定的坐标片段。
     */
    private static final String[] LOGGING_FRAGMENTS = {
            "slf4j",
            "logback",
            "log4j",
            "commons-logging",
            "tinylog"
    };

    /**
     * 禁止出现的进程级可变全局状态片段。
     */
    private static final String[] GLOBAL_STATE_FRAGMENTS = {
            "Configuration.setDefaultApiClient",
            "Configuration.getDefaultApiClient",
            "AliPayApiConfigKit",
            "Security.addProvider",
            "System.setProperty",
            "System.getProperties().put"
    };

    /**
     * 容器化产物文件名（大小写不敏感）。
     */
    private static final String[] CONTAINER_FILE_NAMES = {
            "Dockerfile",
            "docker-compose",
            "compose.yaml",
            "compose.yml"
    };

    /**
     * 容器化产物目录名（大小写不敏感）。
     */
    private static final String[] CONTAINER_DIR_NAMES = {
            "k8s",
            "kubernetes",
            "helm",
            "charts"
    };

    /**
     * 主源码中不允许出现的入口方法声明。
     */
    private static final String MAIN_METHOD = "public static void main(String[";

    /**
     * 被禁词根（拼接构造，避免本文件自身命中检索）。
     */
    private static final String FORBIDDEN_WORD = "work" + "buddy";

    /**
     * 唯一允许出现网关字面量的主源码文件。
     */
    private static final String GATEWAY_CONSTANTS_FILE = ROOT_DIR + "AliPayKit.java";

    /**
     * 协议标记（拼接构造：本文件位于测试源码，不得出现完整协议字面量）。
     */
    private static final String HTTPS_MARK = "https" + "://";

    /**
     * 主 JAR 的期望 class major（Java 8）。
     */
    private static final int JAVA8_MAJOR = 52;

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
     * G-01：根包 4 类型与 model 包 4 类型集合精确相等，且 4+4 个类型与三个嵌套枚举均为 public。
     *
     * @throws IOException 遍历目录失败
     */
    @Test
    public void g01PublicSurfaceExact() throws IOException {
        Set<String> rootTypes = directJavaFileNames(SOURCE_ROOT.resolve(ROOT_DIR));
        assertEquals("根包公共类型应精确为 4 个，实际为 " + rootTypes, 4, rootTypes.size());
        assertEquals("根包文件名集合应精确匹配，实际为 " + rootTypes, EXPECTED_ROOT_TYPES, rootTypes);

        Set<String> modelTypes = directJavaFileNames(SOURCE_ROOT.resolve(MODEL_DIR));
        assertEquals("model 包公共类型应精确为 4 个，实际为 " + modelTypes, 4, modelTypes.size());
        assertEquals("model 包文件名集合应精确匹配，实际为 " + modelTypes, EXPECTED_MODEL_TYPES, modelTypes);

        for (String fqn : ROOT_FQNS) {
            assertTrue("根包公共类型必须为 public：" + fqn, Modifier.isPublic(load(fqn).getModifiers()));
        }
        for (String fqn : MODEL_FQNS) {
            assertTrue("值对象必须为 public：" + fqn, Modifier.isPublic(load(fqn).getModifiers()));
        }
        for (String fqn : NESTED_ENUMS) {
            Class<?> type = load(fqn);
            assertTrue("嵌套类型必须为 public：" + fqn, Modifier.isPublic(type.getModifiers()));
            assertTrue("嵌套类型必须为枚举：" + fqn, type.isEnum());
        }
    }

    /**
     * G-02：根包与 model 包不得出现第三方 / 框架 import，也不得继承第三方类型。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void g02NoThirdPartyInPublicSurface() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (!isPublicSurface(path)) {
                continue;
            }
            String code = stripComments(read(file));
            for (String fqn : imports(code)) {
                for (String prefix : PUBLIC_FORBIDDEN_PREFIXES) {
                    if (fqn.startsWith(prefix)) {
                        offenders.add(path + " -> " + fqn);
                    }
                }
            }
            for (String fragment : PUBLIC_FORBIDDEN_DECLARATIONS) {
                if (code.contains(fragment)) {
                    offenders.add(path + " -> " + fragment);
                }
            }
        }
        assertTrue("公共面（根包 + model）不得引用第三方 / 框架类型，违规：" + offenders, offenders.isEmpty());
    }

    /**
     * G-03：全模块零 JDBC、零其它支付渠道。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void g03NoSqlAndNoOtherChannels() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            String code = stripComments(read(file));
            if (code.contains("import java.sql.")) {
                offenders.add(path + " -> import java.sql.");
            }
            for (String fqn : imports(code)) {
                for (String prefix : OTHER_CHANNEL_PREFIXES) {
                    if (fqn.startsWith(prefix)) {
                        offenders.add(path + " -> " + fqn);
                    }
                }
            }
            for (String artifact : OTHER_CHANNEL_ARTIFACTS) {
                if (code.contains(artifact)) {
                    offenders.add(path + " -> " + artifact);
                }
            }
        }
        assertTrue("本模块零 DB、只做支付宝渠道，违规：" + offenders, offenders.isEmpty());
    }

    /**
     * G-04：第三方类型只在登记的 internal 文件出现，{@code cn.aifei.*} 例外表精确相等。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void g04LayerBoundary() throws IOException {
        Map<String, String> frameworkActual = new LinkedHashMap<>();
        Set<String> ijpayActual = new LinkedHashSet<>();
        Set<String> alipayActual = new LinkedHashSet<>();
        List<String> outsideInternal = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            String code = stripComments(read(file));
            for (String fqn : imports(code)) {
                if (fqn.startsWith("cn.aifei.") && !path.contains(BRIDGE_DIR)) {
                    String prefix = fqn.startsWith("cn.aifei.log.") ? "cn.aifei.log." : fqn;
                    frameworkActual.put(path, prefix);
                }
                if (fqn.startsWith("com.ijpay.")) {
                    ijpayActual.add(path);
                }
                if (fqn.startsWith("com.alipay.")) {
                    alipayActual.add(path);
                }
                boolean thirdParty = fqn.startsWith("com.ijpay.") || fqn.startsWith("com.alipay.");
                if (thirdParty && !path.contains(INTERNAL_DIR)) {
                    outsideInternal.add(path + " -> " + fqn);
                }
            }
        }
        assertEquals("cn.aifei.* 的例外登记表必须精确相等，实际为 " + frameworkActual,
                FRAMEWORK_EXCEPTIONS, frameworkActual);
        // 「只允许出现在」是白名单语义（子集断言）：允许集合内的文件并非都必须出现该前缀。
        assertAllowed("com.ijpay.", IJPAY_ALLOWED, ijpayActual);
        assertAllowed("com.alipay.", ALIPAY_ALLOWED, alipayActual);
        assertTrue("内核调用点必须存在：" + IJPAY_KERNEL_FILE, ijpayActual.contains(IJPAY_KERNEL_FILE));
        assertTrue("验签入口必须引用 SDK：" + PAY_VERIFIER_FILE, alipayActual.contains(PAY_VERIFIER_FILE));
        assertTrue("internal/ 之外不得出现第三方 import，违规：" + outsideInternal, outsideInternal.isEmpty());
    }

    /**
     * 断言实际出现的文件集合是允许集合的子集。
     *
     * @param prefix  包前缀（异常消息用）
     * @param allowed 允许的文件集合
     * @param actual  实际的文件集合
     */
    private static void assertAllowed(String prefix, Set<String> allowed, Set<String> actual) {
        List<String> offenders = new ArrayList<>();
        for (String path : actual) {
            if (!allowed.contains(path)) {
                offenders.add(path);
            }
        }
        assertTrue(prefix + " 只允许出现在 " + allowed + "，越界：" + offenders, offenders.isEmpty());
    }

    /**
     * G-05：产物字节码 major == 52，源码无 Java 9+ 语法 / 集合工厂，且不继承内核类型。
     *
     * @throws IOException 读取源码或产物失败
     */
    @Test
    public void g05Java8AndBytecode() throws IOException {
        List<Path> classFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(CLASSES_ROOT)) {
            stream.filter(p -> p.toString().endsWith(".class")).filter(Files::isRegularFile)
                    .forEach(classFiles::add);
        }
        assertFalse("未找到编译产物，测试需在 compile 之后执行：" + CLASSES_ROOT.toAbsolutePath(),
                classFiles.isEmpty());
        List<String> wrongMajor = new ArrayList<>();
        for (Path classFile : classFiles) {
            int major = majorVersion(Files.readAllBytes(classFile));
            if (major != JAVA8_MAJOR) {
                wrongMajor.add(normalize(classFile) + " -> " + major);
            }
        }
        assertEquals("主 JAR 全部 class 的 major 必须为 " + JAVA8_MAJOR + "，违规：" + wrongMajor,
                new ArrayList<String>(), wrongMajor);

        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            String code = stripComments(read(file));
            for (String fragment : POST_JAVA8_FRAGMENTS) {
                if (code.contains(fragment)) {
                    offenders.add(path + " -> " + fragment);
                }
            }
            for (String fragment : INHERITANCE_FORBIDDEN) {
                if (code.contains(fragment)) {
                    offenders.add(path + " -> " + fragment);
                }
            }
            Matcher matcher = INSTANCEOF_PATTERN.matcher(code);
            if (matcher.find()) {
                offenders.add(path + " -> " + matcher.group());
            }
        }
        assertTrue("不得使用 Java 8 之后的语法 / API，也不得继承内核类型，违规：" + offenders,
                offenders.isEmpty());
    }

    /**
     * G-06：直接依赖集合精确 6 项、无日志门面依赖、SDK 的三个 exclusion 精确存在。
     *
     * @throws Exception 解析 pom 失败
     */
    @Test
    public void g06DependencyDiscipline() throws Exception {
        Element project = pomProject();
        Element dependencies = childElement(project, "dependencies");
        assertTrue("pom.xml 必须声明 <dependencies>", dependencies != null);

        Set<String> actual = new LinkedHashSet<>();
        Set<String> excluded = new LinkedHashSet<>();
        for (Element dependency : childElements(dependencies, "dependency")) {
            String groupId = childText(dependency, "groupId");
            String artifactId = childText(dependency, "artifactId");
            String scope = childText(dependency, "scope");
            boolean optional = "true".equalsIgnoreCase(childText(dependency, "optional"));
            String effective = scope != null ? scope : (optional ? "optional" : "compile");
            String coordinate = groupId + ":" + artifactId;
            actual.add(coordinate + ":" + effective);
            for (Element exclusions : childElements(dependency, "exclusions")) {
                for (Element exclusion : childElements(exclusions, "exclusion")) {
                    excluded.add(childText(exclusion, "groupId") + ":" + childText(exclusion, "artifactId"));
                }
            }
        }
        assertEquals("直接依赖集合必须精确匹配，实际为 " + actual, EXPECTED_DEPENDENCIES, actual);
        assertEquals("SDK 依赖的 exclusion 集合必须精确匹配，实际为 " + excluded, EXPECTED_EXCLUSIONS,
                excluded);

        List<String> logging = new ArrayList<>();
        for (String coordinate : actual) {
            String lower = coordinate.toLowerCase(Locale.ROOT);
            for (String fragment : LOGGING_FRAGMENTS) {
                if (lower.contains(fragment)) {
                    logging.add(coordinate);
                }
            }
        }
        assertTrue("不得直接依赖任何日志门面 / 绑定，违规：" + logging, logging.isEmpty());
    }

    /**
     * 附加①：模块内不得出现容器化产物，主源码不得出现入口方法。
     *
     * @throws IOException 遍历目录失败
     */
    @Test
    public void a01NoContainerArtifacts() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(MODULE_ROOT)) {
            List<Path> all = new ArrayList<>();
            stream.forEach(all::add);
            for (Path path : all) {
                String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (Files.isDirectory(path)) {
                    if (Arrays.asList(CONTAINER_DIR_NAMES).contains(name)) {
                        offenders.add(normalize(path) + "（容器编排目录）");
                    }
                    continue;
                }
                for (String containerFile : CONTAINER_FILE_NAMES) {
                    String marker = containerFile.toLowerCase(Locale.ROOT);
                    if (name.equals(marker) || name.startsWith(marker + "-")
                            || name.startsWith(marker + ".")) {
                        offenders.add(normalize(path));
                    }
                }
                if (name.endsWith(".dockerfile")) {
                    offenders.add(normalize(path));
                }
            }
        }
        assertTrue("不得产出任何容器化产物，违规：" + offenders, offenders.isEmpty());

        List<String> mains = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            if (stripComments(read(file)).contains(MAIN_METHOD)) {
                mains.add(normalize(file));
            }
        }
        assertTrue("库模块的主源码不得包含入口方法，违规：" + mains, mains.isEmpty());
    }

    /**
     * 附加②：README / AGENTS / docs 不得包含项目禁用词根。
     *
     * @throws IOException 读取文档失败
     */
    @Test
    public void a02NoForbiddenDocWord() throws IOException {
        List<Path> docs = new ArrayList<>();
        docs.add(Paths.get("README.md"));
        docs.add(Paths.get("AGENTS.md"));
        Path docsDir = Paths.get("docs");
        if (Files.isDirectory(docsDir)) {
            try (Stream<Path> stream = Files.walk(docsDir)) {
                stream.filter(p -> p.toString().endsWith(".md")).filter(Files::isRegularFile).forEach(docs::add);
            }
        }
        List<String> offenders = new ArrayList<>();
        for (Path doc : docs) {
            if (!Files.isRegularFile(doc)) {
                continue;
            }
            String content = read(doc).toLowerCase(Locale.ROOT);
            if (content.contains(FORBIDDEN_WORD)) {
                offenders.add(normalize(doc));
            }
        }
        assertTrue("文档不得包含项目禁用词根，违规：" + offenders, offenders.isEmpty());
    }

    /**
     * 附加③：不得使用进程级可变全局状态。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void a03NoGlobalMutableState() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            String code = stripComments(read(file));
            for (String fragment : GLOBAL_STATE_FRAGMENTS) {
                if (code.contains(fragment)) {
                    offenders.add(path + " -> " + fragment);
                }
            }
        }
        assertTrue("不得使用进程级可变全局状态，违规：" + offenders, offenders.isEmpty());
    }

    /**
     * 附加④：测试源码零联网字面量；主源码的协议字面量只允许出现在网关常量文件。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void a04OfflineOnly() throws IOException {
        List<String> testOffenders = new ArrayList<>();
        for (Path file : javaFiles(TEST_ROOT)) {
            if (stripComments(read(file)).toLowerCase(Locale.ROOT).contains(HTTPS_MARK)) {
                testOffenders.add(normalize(file));
            }
        }
        assertTrue("测试源码不得出现任何协议地址字面量（单测零联网），违规：" + testOffenders,
                testOffenders.isEmpty());

        List<String> mainOffenders = new ArrayList<>();
        int gatewayConstants = 0;
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            String code = stripComments(read(file)).toLowerCase(Locale.ROOT);
            int count = countOccurrences(code, HTTPS_MARK);
            if (count == 0) {
                continue;
            }
            if (path.endsWith(GATEWAY_CONSTANTS_FILE)) {
                gatewayConstants = count;
            } else {
                mainOffenders.add(path + " -> " + count);
            }
        }
        assertTrue("主源码的协议字面量只允许出现在 " + GATEWAY_CONSTANTS_FILE + "，违规：" + mainOffenders,
                mainOffenders.isEmpty());
        assertEquals("网关常量应恰好两个", 2, gatewayConstants);
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
            stream.filter(p -> p.toString().endsWith(".java")).filter(Files::isRegularFile)
                    .forEach(files::add);
        }
        assertFalse("未扫描到任何 Java 源码：" + root.toAbsolutePath(), files.isEmpty());
        return files;
    }

    /**
     * 列出目录下直接的 {@code .java} 文件名。
     *
     * @param dir 目录
     * @return 文件名集合
     * @throws IOException 遍历目录失败
     */
    private static Set<String> directJavaFileNames(Path dir) throws IOException {
        assertTrue("包目录不存在：" + dir.toAbsolutePath(), Files.isDirectory(dir));
        Set<String> names = new LinkedHashSet<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile).map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".java")).forEach(names::add);
        }
        return names;
    }

    /**
     * 判断路径是否属于公共面（根包直接子文件或 model 包直接子文件）。
     *
     * @param path 归一化后的源码路径
     * @return 属于公共面时 {@code true}
     */
    private static boolean isPublicSurface(String path) {
        int modelIndex = path.lastIndexOf(MODEL_DIR);
        if (modelIndex >= 0) {
            return !path.substring(modelIndex + MODEL_DIR.length()).contains("/");
        }
        int rootIndex = path.lastIndexOf(ROOT_DIR);
        if (rootIndex < 0) {
            return false;
        }
        return !path.substring(rootIndex + ROOT_DIR.length()).contains("/");
    }

    /**
     * 从已剥离注释的代码文本提取全部 import 的全限定名（含 static import）。
     *
     * @param code 代码文本
     * @return 全限定名列表
     */
    private static List<String> imports(String code) {
        List<String> fqns = new ArrayList<>();
        for (String raw : code.split("\n")) {
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
     * @param path 路径
     * @return 以 {@code /} 分隔的路径
     */
    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    /**
     * 统计子串出现次数。
     *
     * @param text   文本
     * @param needle 子串
     * @return 次数
     */
    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }

    /**
     * 读取 class 文件的 major version。
     *
     * @param bytes class 文件内容
     * @return major version
     */
    private static int majorVersion(byte[] bytes) {
        assertTrue("class 文件长度异常", bytes.length > 8);
        assertEquals("class 文件魔数必须为 CAFEBABE", (byte) 0xCA, bytes[0]);
        assertEquals("class 文件魔数必须为 CAFEBABE", (byte) 0xFE, bytes[1]);
        return ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
    }

    /**
     * 反射加载类型。
     *
     * @param fqn 全限定名
     * @return 类型对象
     */
    private static Class<?> load(String fqn) {
        try {
            return Class.forName(fqn);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("类型不存在：" + fqn, e);
        }
    }

    /**
     * 解析构建文件。
     *
     * @return {@code <project>} 元素
     * @throws Exception 解析失败
     */
    private static Element pomProject() throws Exception {
        assertTrue("构建文件不存在：" + POM.toAbsolutePath(), Files.isRegularFile(POM));
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new File(POM.toAbsolutePath().toString()));
        return document.getDocumentElement();
    }

    /**
     * 取父元素的第一个同名直接子元素。
     *
     * @param parent 父元素
     * @param name   子元素名
     * @return 子元素；不存在时 {@code null}
     */
    private static Element childElement(Element parent, String name) {
        List<Element> elements = childElements(parent, name);
        return elements.isEmpty() ? null : elements.get(0);
    }

    /**
     * 取父元素的全部同名直接子元素。
     *
     * @param parent 父元素
     * @param name   子元素名
     * @return 子元素列表
     */
    private static List<Element> childElements(Element parent, String name) {
        List<Element> elements = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && name.equals(node.getNodeName())) {
                elements.add((Element) node);
            }
        }
        return elements;
    }

    /**
     * 取直接子元素的文本内容。
     *
     * @param parent 父元素
     * @param name   子元素名
     * @return 文本；不存在时 {@code null}
     */
    private static String childText(Element parent, String name) {
        Element element = childElement(parent, name);
        return element == null ? null : element.getTextContent().trim();
    }

    /**
     * 以状态机剥离注释，只保留代码文本（含字符串 / 字符字面量的原文）。
     *
     * <p>状态区分「代码 / 字符串字面量 / 字符字面量 / 行注释 / 块注释」五种：只有处于代码态时
     * {@code //} 与 {@code /} + {@code *} 才是注释起点，故字符串字面量里的地址与注释标记原样保留；
     * 字面量内处理转义；注释内的引号不影响状态机；注释被删除但保留换行。</p>
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
                case STATE_CHAR:
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
                default:
                    code.append(current);
                    index++;
                    break;
            }
        }
        return code.toString();
    }

    /**
     * 以 UTF-8 读取文本文件。
     *
     * @param path 文件路径
     * @return 文件全文
     * @throws IOException 读取失败
     */
    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
