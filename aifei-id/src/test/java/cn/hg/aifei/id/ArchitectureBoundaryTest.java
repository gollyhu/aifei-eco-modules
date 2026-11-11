/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * 架构门禁：以源码文本扫描断言包边界、公共面大小、唯一定义点与离线纪律，不得为兼容错误用法而放宽。
 *
 * <pre>
 * ① noThirdPartyReference          import 只能来自 JDK / aifei 框架 / 本模块 / 两个可选协同模块，无其它放行包
 * ② optionalModuleScopeRules       aifei-cache / aifei-lock 的类型只允许出现在 internal 包
 * ③ frameworkOnlyInBridgeExceptLog cn.aifei.log.* 只允许在 internal 与装配包；其余 cn.aifei.* 只允许在装配包
 * ④ publicSurfaceExact             根包公共类型精确 3 个、装配包精确 1 个，文件名集合精确相等
 * ⑤ noSqlReference                 全模块禁止 import java.sql.
 * ⑥ noJvmGlobalPropertyWrite       禁止改写 JVM 全局状态
 * ⑦ java8Compatibility             禁止 Java 8 之后的语法与集合工厂
 * ⑧ noServiceProvider              禁止 SPI / ServiceLoader / META-INF/services
 * ⑨ clockOnlyInSnowflake           时刻采样与纪元字面量仅限 SnowflakeIdGenerator.java
 * ⑩ cacheNamesOnlyInIdNames        分区名 / 段键前缀 / 计数键 / 锁名前缀字面量仅限 IdNames.java
 * ⑪ classForNameOnlyInIdDeps       Class.forName 仅限 IdDeps.java
 * ⑫ threadCreationOnlyInWorkerLease 建线程片段仅限 LockWorkerIdSource.java
 * ⑬ configKeysOnlyInIdConfig       配置键前缀字面量仅限 IdConfig.java
 * ⑭ testSourcesOfflineOnly         测试源码中的 URL 只允许回环地址
 * </pre>
 *
 * <p>扫描对象为 {@code src/main/java}（⑭ 为 {@code src/test/java}）下的全部 {@code .java}：路径分隔符先
 * 归一化为 {@code /}，注释先剥离再匹配字面量，因此版权头里的仓库地址与注释里的示例文本不参与判定；
 * 剥离注释不构成放宽——注释不会执行，代码里的违规必然被命中。因此本测试必须在模块根目录
 * （{@code aifei-id}）内执行。</p>
 *
 * <p>门禁与实现冲突时改实现，不得放宽或绕过断言；新增例外必须先改设计文档、再改本类。</p>
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
    private static final String ROOT_PACKAGE = "cn/hg/aifei/id/";

    /**
     * 装配包目录（相对 {@code src/main/java}）。
     */
    private static final String BRIDGE_PACKAGE = "cn/hg/aifei/id/aifei/";

    /**
     * 实现层包目录（相对 {@code src/main/java}）。
     */
    private static final String INTERNAL_PACKAGE = "cn/hg/aifei/id/internal/";

    /**
     * 框架日志包前缀：模块自身记录只用它，且只允许在 internal 与装配包出现。
     */
    private static final String LOG_PACKAGE = "cn.aifei.log.";

    /**
     * 可选协同模块前缀：缓存原语。
     */
    private static final String CACHE_PACKAGE = "cn.hg.aifei.cache.";

    /**
     * 可选协同模块前缀：分布式锁。
     */
    private static final String LOCK_PACKAGE = "cn.hg.aifei.lock.";

    /**
     * 根包公共类型的期望个数。
     */
    private static final int EXPECTED_PUBLIC_TYPES = 3;

    /**
     * 装配包公共类型的期望个数。
     */
    private static final int EXPECTED_BRIDGE_TYPES = 1;

    /**
     * 根包公共类型的期望文件名集合（唯一事实来源，防同名替换）。
     */
    private static final Set<String> EXPECTED_ROOT_TYPES = new LinkedHashSet<>(Arrays.asList(
            "IdGenerator.java", "IdKit.java", "IdException.java"));

    /**
     * 装配包公共类型的期望文件名集合（唯一事实来源，防同名替换）。
     */
    private static final Set<String> EXPECTED_BRIDGE_TYPES_SET = new LinkedHashSet<>(Arrays.asList(
            "IdPlugin.java"));

    /**
     * 时刻采样与纪元字面量的唯一定义点（相对 {@code src/main/java}）。
     */
    private static final String SNOWFLAKE_FILE = INTERNAL_PACKAGE + "SnowflakeIdGenerator.java";

    /**
     * 命名规则字面量的唯一定义点（相对 {@code src/main/java}）。
     */
    private static final String ID_NAMES_FILE = INTERNAL_PACKAGE + "IdNames.java";

    /**
     * 依赖探测字面量的唯一定义点（相对 {@code src/main/java}）。
     */
    private static final String ID_DEPS_FILE = INTERNAL_PACKAGE + "IdDeps.java";

    /**
     * 建线程字面量的唯一定义点（相对 {@code src/main/java}）。
     */
    private static final String WORKER_LEASE_FILE = INTERNAL_PACKAGE + "LockWorkerIdSource.java";

    /**
     * 配置键字面量的唯一定义点（相对 {@code src/main/java}）。
     */
    private static final String ID_CONFIG_FILE = INTERNAL_PACKAGE + "IdConfig.java";

    /**
     * 门禁⑨：只允许出现在雪花实现里的字面量。
     */
    private static final String[] CLOCK_FRAGMENTS = {"System.currentTimeMillis", "1704067200000"};

    /**
     * 门禁⑩：只允许出现在 {@code IdNames} 里的字面量（含双引号，即代码里的字符串字面量）。
     *
     * <p>带引号匹配是有意的：异常消息里出现的 {@code __ID__}、配置键里出现的 {@code id.worker.} 属于
     * <b>文本内容</b>而不是键拼装定义，剥离引号会把它们误判为散落的键拼装。</p>
     */
    private static final String[] NAME_FRAGMENTS = {"\"__ID__\"", "\"segment.\"", "\"worker\""};

    /**
     * 门禁⑬：只允许出现在 {@code IdConfig} 里的配置键前缀字面量（含左引号）。
     */
    private static final String CONFIG_KEY_FRAGMENT = "\"id.";

    /**
     * 门禁⑪：只允许出现在 {@code IdDeps} 里的反射字面量。
     */
    private static final String CLASS_FOR_NAME_FRAGMENT = "Class.forName";

    /**
     * 门禁⑫：只允许出现在租约来源里的建线程片段。
     */
    private static final String[] THREAD_FRAGMENTS = {"new Thread(", "Executors.", "ThreadPoolExecutor"};

    /**
     * 门禁⑥：禁止改写 JVM 全局状态的片段。
     */
    private static final String[] JVM_GLOBAL_WRITE_FRAGMENTS = {
            "System.setProperty",
            "System.getProperties().put",
            "System.setErr",
            "System.setOut"
    };

    /**
     * 门禁⑦：Java 8 之后才有的语法 / 集合工厂片段。
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
     * 门禁⑦：{@code instanceof} 模式匹配（类型后直接跟变量名）。
     */
    private static final Pattern INSTANCEOF_PATTERN =
            Pattern.compile("instanceof\\s+[A-Za-z_$][\\w$.]*\\s+[A-Za-z_$][\\w$]*");

    /**
     * 门禁⑭：测试源码中的 URL 字面量识别式（只看 host，端口不影响判定，协议名大小写不敏感）。
     */
    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://([^/\\s\"'<>]+)", Pattern.CASE_INSENSITIVE);

    /**
     * 允许出现在测试源码里的回环 host。
     */
    private static final Set<String> LOOPBACK_HOSTS =
            new LinkedHashSet<>(Arrays.asList("127.0.0.1", "localhost"));

    /**
     * 门禁①：import 只能来自 JDK、aifei 框架、本模块与两个可选协同模块，无其它放行包。
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
        assertTrue("import 只允许来自 java. / javax. / cn.aifei. / cn.hg.aifei.id. / "
                + CACHE_PACKAGE + " / " + LOCK_PACKAGE + "，违规：" + offenders, offenders.isEmpty());
    }

    /**
     * 门禁②：{@code aifei-cache} 与 {@code aifei-lock} 的类型只允许出现在 {@link #INTERNAL_PACKAGE} 下的
     * 文件里；出现在根包或装配包即视为可选依赖泄漏进公共面。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void optionalModuleScopeRules() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            for (String fqn : imports(read(file))) {
                boolean optionalModule = fqn.startsWith(CACHE_PACKAGE) || fqn.startsWith(LOCK_PACKAGE);
                if (optionalModule && !path.contains(INTERNAL_PACKAGE)) {
                    offenders.add(path + " -> " + fqn);
                }
            }
        }
        assertTrue(CACHE_PACKAGE + " 与 " + LOCK_PACKAGE + " 的类型只允许出现在 " + INTERNAL_PACKAGE
                + " 下，违规：" + offenders, offenders.isEmpty());
    }

    /**
     * 门禁③：{@code cn.aifei.log.*} 只允许出现在 internal 包与装配包（根包禁止）；其余 {@code cn.aifei.*}
     * 框架类型（plugin / util / aop / core / scanner 等）只允许出现在装配包。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void frameworkOnlyInBridgeExceptLog() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            for (String fqn : imports(read(file))) {
                if (!fqn.startsWith("cn.aifei.")) {
                    continue;
                }
                boolean allowed;
                if (fqn.startsWith(LOG_PACKAGE)) {
                    allowed = path.contains(INTERNAL_PACKAGE) || path.contains(BRIDGE_PACKAGE);
                } else {
                    allowed = path.contains(BRIDGE_PACKAGE);
                }
                if (!allowed) {
                    offenders.add(path + " -> " + fqn);
                }
            }
        }
        assertTrue(LOG_PACKAGE + "* 只允许出现在 " + INTERNAL_PACKAGE + " 与 " + BRIDGE_PACKAGE
                + "；其余 cn.aifei.* 只允许出现在 " + BRIDGE_PACKAGE + "，违规：" + offenders,
                offenders.isEmpty());
    }

    /**
     * 门禁④：根包直接子文件精确 3 个、装配包直接子文件精确 1 个，文件名集合均精确相等。
     *
     * @throws IOException 遍历目录失败
     */
    @Test
    public void publicSurfaceExact() throws IOException {
        List<String> rootTypes = new ArrayList<>();
        List<String> bridgeTypes = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (isDirectChild(path, ROOT_PACKAGE)) {
                rootTypes.add(fileName(path));
            }
            if (isDirectChild(path, BRIDGE_PACKAGE)) {
                bridgeTypes.add(fileName(path));
            }
        }
        assertEquals("根包公共类型应精确为 " + EXPECTED_PUBLIC_TYPES + " 个，实际为 " + rootTypes,
                EXPECTED_PUBLIC_TYPES, rootTypes.size());
        assertEquals("根包文件名集合应精确匹配，实际为 " + rootTypes,
                EXPECTED_ROOT_TYPES, new LinkedHashSet<>(rootTypes));
        assertEquals("装配包公共类型应精确为 " + EXPECTED_BRIDGE_TYPES + " 个，实际为 " + bridgeTypes,
                EXPECTED_BRIDGE_TYPES, bridgeTypes.size());
        assertEquals("装配包文件名集合应精确匹配，实际为 " + bridgeTypes,
                EXPECTED_BRIDGE_TYPES_SET, new LinkedHashSet<>(bridgeTypes));
    }

    /**
     * 门禁⑤：全模块不得引用 JDBC（本模块零 DB 底线）。
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
     * 门禁⑥：不得改写 JVM 全局状态（会波及同进程的其它模块）。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void noJvmGlobalPropertyWrite() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String code = stripComments(read(file));
            for (String fragment : JVM_GLOBAL_WRITE_FRAGMENTS) {
                if (code.contains(fragment)) {
                    offenders.add(normalize(file) + " -> " + fragment);
                }
            }
        }
        assertTrue("不得改写 JVM 全局状态，违规：" + offenders, offenders.isEmpty());
    }

    /**
     * 门禁⑦：不得出现 Java 8 之后的语法与集合工厂（Java 8 双约束，主 JAR class major = 52）。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void java8Compatibility() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String code = stripComments(read(file));
            for (String fragment : POST_JAVA8_FRAGMENTS) {
                if (code.contains(fragment)) {
                    offenders.add(normalize(file) + " -> " + fragment);
                }
            }
            Matcher matcher = INSTANCEOF_PATTERN.matcher(code);
            while (matcher.find()) {
                offenders.add(normalize(file) + " -> " + matcher.group());
            }
        }
        assertTrue("不得使用 Java 8 之后的语法与 API，违规：" + offenders, offenders.isEmpty());
    }

    /**
     * 门禁⑧：不得出现 {@code ServiceLoader} / {@code META-INF/services} 字面量，且
     * {@code src/main/resources} 下不得存在 {@code META-INF/services/} 目录（无 provider / 无 SPI 决策）。
     *
     * @throws IOException 读取源码或遍历目录失败
     */
    @Test
    public void noServiceProvider() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String code = stripComments(read(file));
            if (code.contains("ServiceLoader") || code.contains("META-INF/services")) {
                offenders.add(normalize(file));
            }
        }
        Path servicesDir = Paths.get("src/main/resources").resolve("META-INF/services");
        assertFalse("src/main/resources 下不得存在 META-INF/services 目录", Files.exists(servicesDir));
        assertTrue("不得出现 ServiceLoader / META-INF/services 字面量，违规：" + offenders,
                offenders.isEmpty());
    }

    /**
     * 门禁⑨：时刻采样（{@code System.currentTimeMillis}）与纪元字面量（{@code 1704067200000}）只允许出现在
     * {@code internal/SnowflakeIdGenerator.java}，防止第二处时刻采样与纪元复制导致位空间分裂。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void clockOnlyInSnowflake() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (path.endsWith(SNOWFLAKE_FILE)) {
                continue;
            }
            String code = stripComments(read(file));
            for (String fragment : CLOCK_FRAGMENTS) {
                if (code.contains(fragment)) {
                    offenders.add(path + " -> " + fragment);
                }
            }
        }
        assertTrue("时刻采样与纪元字面量只允许出现在 " + SNOWFLAKE_FILE + "，违规：" + offenders,
                offenders.isEmpty());
    }

    /**
     * 门禁⑩：分区名、段键前缀、workerId 计数键字面量只允许出现在 {@code internal/IdNames.java}。
     *
     * <p>锁名前缀 {@code id.worker.} 同样只允许由 {@code IdNames} 拼装，其 {@code id.} 部分引用
     * {@code IdConfig} 的模块前缀常量（配置键前缀的字面量唯一定义点，见门禁⑬），因此本模块源码中不存在
     * 该字面量整体，判定为「未在其它位置散落」。</p>
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void cacheNamesOnlyInIdNames() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (path.endsWith(ID_NAMES_FILE)) {
                continue;
            }
            String code = stripComments(read(file));
            for (String fragment : NAME_FRAGMENTS) {
                if (code.contains(fragment)) {
                    offenders.add(path + " -> " + fragment);
                }
            }
        }
        assertTrue("分区名 / 段键 / 计数键字面量只允许出现在 " + ID_NAMES_FILE + "，违规：" + offenders,
                offenders.isEmpty());
    }

    /**
     * 门禁⑪：{@code Class.forName} 只允许出现在 {@code internal/IdDeps.java}，防止反射 / 依赖探测散落
     * （也就防止暗装 provider）。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void classForNameOnlyInIdDeps() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (path.endsWith(ID_DEPS_FILE)) {
                continue;
            }
            if (stripComments(read(file)).contains(CLASS_FOR_NAME_FRAGMENT)) {
                offenders.add(path);
            }
        }
        assertTrue(CLASS_FOR_NAME_FRAGMENT + " 只允许出现在 " + ID_DEPS_FILE + "，违规：" + offenders,
                offenders.isEmpty());
    }

    /**
     * 门禁⑫：建线程片段只允许出现在 {@code internal/LockWorkerIdSource.java}——本模块只有租约续期需要
     * 后台线程。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void threadCreationOnlyInWorkerLease() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (path.endsWith(WORKER_LEASE_FILE)) {
                continue;
            }
            String code = stripComments(read(file));
            for (String fragment : THREAD_FRAGMENTS) {
                if (code.contains(fragment)) {
                    offenders.add(path + " -> " + fragment);
                }
            }
        }
        assertTrue("建线程片段只允许出现在 " + WORKER_LEASE_FILE + "，违规：" + offenders,
                offenders.isEmpty());
    }

    /**
     * 门禁⑬：配置键前缀字面量只允许出现在 {@code internal/IdConfig.java}，防止配置键硬编码散落。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void configKeysOnlyInIdConfig() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles(SOURCE_ROOT)) {
            String path = normalize(file);
            if (path.endsWith(ID_CONFIG_FILE)) {
                continue;
            }
            if (stripComments(read(file)).contains(CONFIG_KEY_FRAGMENT)) {
                offenders.add(path);
            }
        }
        assertTrue("配置键前缀字面量只允许出现在 " + ID_CONFIG_FILE + "，违规：" + offenders,
                offenders.isEmpty());
    }

    /**
     * 门禁⑭：测试源码中的 URL 字面量只允许回环地址（测试零联网纪律）。
     *
     * <p>只看代码字面量：注释先由状态机剥离，故版权头里的仓库地址不参与判定。</p>
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
        assertTrue("测试源码只允许回环地址（" + LOOPBACK_HOSTS + "），违规：" + offenders,
                offenders.isEmpty());
    }

    // ─────────── 扫描工具 ───────────

    /**
     * 收集指定根目录下全部 Java 源码文件。
     *
     * @param root 源码根目录
     * @return Java 源码文件列表
     * @throws IOException 遍历目录失败
     */
    private static List<Path> javaFiles(Path root) throws IOException {
        assertTrue("源码目录不存在，测试需在模块根目录执行：" + root.toAbsolutePath(), Files.exists(root));
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .filter(Files::isRegularFile)
                    .forEach(files::add);
        }
        assertFalse("未扫描到任何 Java 源码：" + root.toAbsolutePath(), files.isEmpty());
        return files;
    }

    /**
     * 判断 import 是否为允许的 JDK / 框架 / 本模块 / 可选协同模块类型。
     *
     * @param fqn import 的全限定名
     * @return 允许时 {@code true}
     */
    private static boolean isAllowedImport(String fqn) {
        return fqn.startsWith("java.")
                || fqn.startsWith("javax.")
                || fqn.startsWith("cn.aifei.")
                || fqn.startsWith("cn.hg.aifei.id.")
                || fqn.startsWith(CACHE_PACKAGE)
                || fqn.startsWith(LOCK_PACKAGE);
    }

    /**
     * 提取源码文本里全部 import 的全限定名。
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
     * 提取路径中的文件名。
     *
     * @param path 归一化后的源码路径
     * @return 文件名
     */
    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /**
     * 判断是否为指定包目录的直接子文件：位于包目录下，且包目录之后的相对路径不含 {@code /}。
     *
     * @param path 归一化后的源码路径
     * @param packagePrefix 包目录相对路径（如 {@code cn/hg/aifei/id/}）
     * @return 为该包直接子文件时 {@code true}
     */
    private static boolean isDirectChild(String path, String packagePrefix) {
        int index = path.lastIndexOf(packagePrefix);
        if (index < 0) {
            return false;
        }
        return !path.substring(index + packagePrefix.length()).contains("/");
    }

    /**
     * 以状态机剥离注释，只保留代码文本（含字符串 / 字符字面量的原文）。
     *
     * <p>状态区分「代码 / 字符串字面量 / 字符字面量 / 行注释 / 块注释」：只有处于代码态时 {@code //} 与
     * {@code /*} 才是注释起点，故字符串字面量里的内容原样保留；注释内的引号不影响状态机。行注释与块注释
     * 被删除，但其中的换行保留，避免相邻代码行被拼接。</p>
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
            if (state == STATE_CODE) {
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
            } else if (state == STATE_LINE_COMMENT) {
                if (current == '\n') {
                    state = STATE_CODE;
                    code.append(current);
                }
                index++;
            } else if (state == STATE_BLOCK_COMMENT) {
                if (current == '*' && next == '/') {
                    state = STATE_CODE;
                    index += 2;
                } else {
                    if (current == '\n') {
                        code.append(current);
                    }
                    index++;
                }
            } else {
                char terminator = state == STATE_STRING ? '"' : '\'';
                code.append(current);
                index++;
                if (current == '\\' && index < length) {
                    code.append(content.charAt(index));
                    index++;
                } else if (current == terminator) {
                    state = STATE_CODE;
                }
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
     * 从已剥离注释的代码文本中提取全部非回环 URL。
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
            if (!LOOPBACK_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
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
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
