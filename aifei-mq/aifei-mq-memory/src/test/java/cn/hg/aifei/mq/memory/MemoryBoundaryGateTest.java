/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.mq.internal.MessageBus;
import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MqProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * memory 后端子模块的边界门禁（设计 §12 / §13）：后端模块自带实现，不依赖 core 的契约基类。
 *
 * <pre>
 * ① 第三方 import 只允许出现在实现包 {@code cn/hg/aifei/mq/memory/} 下
 * ② 本模块不得引用 JDBC（后端不承担数据库职责）
 * ③ ServiceLoader 声明严格为 1 行且指向类型存在、标识为 memory
 * </pre>
 *
 * <p>扫描对象为 {@code src/main/java} 与 {@code src/main/resources}，因此本测试必须在模块根目录
 * （{@code aifei-mq-memory}）内执行。</p>
 */
public class MemoryBoundaryGateTest {

    /**
     * 主源码根目录，相对模块根。
     */
    private static final Path SOURCE_ROOT = Paths.get("src/main/java");

    /**
     * 实现包目录（相对 {@code src/main/java}），第三方 import 仅允许出现在此目录下。
     */
    private static final String IMPL_PACKAGE = "cn/hg/aifei/mq/memory/";

    /**
     * ServiceLoader 声明文件路径，相对模块根。
     */
    private static final Path SERVICES_FILE = Paths.get(
            "src/main/resources/META-INF/services/cn.hg.aifei.mq.internal.MqProvider");

    /**
     * 声明文件唯一允许出现的 provider 全限定名。
     */
    private static final String EXPECTED_PROVIDER = "cn.hg.aifei.mq.memory.MemoryMqProvider";

    /**
     * 内存后端的类型标识。
     */
    private static final String EXPECTED_TYPE = "memory";

    /**
     * 门禁①：第三方 import 只允许出现在实现包内，契约层其余位置一律禁止。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void thirdPartyOnlyInImplementationPackage() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            String path = normalize(file);
            if (path.contains(IMPL_PACKAGE)) {
                continue;
            }
            for (String fqn : imports(read(file))) {
                if (!isAllowedImport(fqn)) {
                    offenders.add(path + " -> " + fqn);
                }
            }
        }
        assertTrue("第三方 import 只允许出现在 " + IMPL_PACKAGE + "，违规：" + offenders, offenders.isEmpty());
    }

    /**
     * 门禁②：本模块不得引用 JDBC。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void noSqlReference() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            if (read(file).contains("import java.sql.")) {
                offenders.add(normalize(file));
            }
        }
        assertTrue("不得出现 import java.sql.，违规文件：" + offenders, offenders.isEmpty());
    }

    /**
     * 门禁③：声明文件存在、恰 1 行、内容精确等于 provider 全限定名，且该类型实现的标识为 memory。
     *
     * @throws IOException        读取声明文件失败
     * @throws ReflectiveOperationException provider 类型不可实例化
     */
    @Test
    public void servicesDeclarationMatchesType() throws IOException, ReflectiveOperationException {
        assertTrue("声明文件不存在：" + SERVICES_FILE.toAbsolutePath(), Files.isRegularFile(SERVICES_FILE));

        List<String> lines = new ArrayList<>();
        for (String raw : read(SERVICES_FILE).split("\n")) {
            String line = raw.trim();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        assertEquals("本模块声明文件应恰有 1 个 provider，实际为 " + lines, 1, lines.size());
        assertEquals("声明内容应为 provider 全限定名", EXPECTED_PROVIDER, lines.get(0));

        MqProvider provider = (MqProvider) Class.forName(EXPECTED_PROVIDER)
                .getDeclaredConstructor()
                .newInstance();
        assertNotNull(provider);
        assertEquals("声明的 provider 类型标识应为 " + EXPECTED_TYPE, EXPECTED_TYPE, provider.type());
        assertEquals("静态常量 TYPE 应与 type() 一致", EXPECTED_TYPE, MemoryMqProvider.TYPE);
        assertEquals("默认类型应指向内存后端", EXPECTED_TYPE, MqConfig.DEFAULT_TYPE);

        MessageBus bus = provider.createBus(MqConfig.parse(new Properties(), typesOf(provider),
                MqConfig.MAIN_INSTANCE_NAME));
        assertNotNull("provider 应能创建总线", bus);
        bus.close();
    }

    /**
     * 构造仅包含该 provider 的类型集合，满足配置校验的前置条件。
     *
     * @param provider 待声明的 provider
     * @return 类型集合
     */
    private static Set<String> typesOf(MqProvider provider) {
        Set<String> types = new LinkedHashSet<>();
        types.add(provider.type());
        return types;
    }

    /**
     * 收集主源码下全部 Java 文件，作为门禁扫描对象。
     *
     * @return Java 源码文件列表
     * @throws IOException 遍历目录失败
     */
    private static List<Path> javaFiles() throws IOException {
        assertTrue("源码目录不存在，测试需在模块根目录执行：" + SOURCE_ROOT.toAbsolutePath(),
                Files.exists(SOURCE_ROOT));
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(SOURCE_ROOT)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                    .filter(Files::isRegularFile)
                    .forEach(files::add);
        }
        assertTrue("未扫描到任何 Java 源码", !files.isEmpty());
        return files;
    }

    /**
     * 判断 import 是否为允许的 JDK / 框架 / 本工程类型。
     *
     * @param fqn import 的全限定名
     * @return 允许时 {@code true}
     */
    private static boolean isAllowedImport(String fqn) {
        return fqn.startsWith("java.")
                || fqn.startsWith("javax.")
                || fqn.startsWith("cn.aifei.")
                || fqn.startsWith("cn.hg.aifei.mq.");
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
     * 以 UTF-8 读取文本。
     *
     * @param path 文件路径
     * @return 文件全文
     * @throws IOException 读取失败
     */
    private static String read(Path path) throws IOException {
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        assertNotNull(content);
        return content;
    }
}
