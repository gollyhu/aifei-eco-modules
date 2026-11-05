/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.redisstream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.mq.internal.MqProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * redisstream 后端子模块的边界门禁（设计 §12 / §13）：第三方依赖与 ServiceLoader 声明均收敛在本实现模块内。
 *
 * <pre>
 * ① 第三方 import 只允许出现在实现包 {@code cn/hg/aifei/mq/redisstream/} 下（本模块其余位置不得出现）
 * ② 本模块不得引用 JDBC（消息后端不承担数据库职责）
 * ③ ServiceLoader 声明严格为 1 行，且其指向的 provider 标识为 redisstream（与静态常量 TYPE 一致）
 * </pre>
 *
 * <p>扫描对象为 {@code src/main/java}，故本测试需在模块根目录（{@code aifei-mq-redisstream}）内执行。门禁③只做
 * 类型反射校验，<b>不创建总线</b>，因此不会创建 Jedis 连接池或触碰网络，可在无 Redis 环境下运行。</p>
 */
public class RedisStreamBoundaryGateTest {

    /**
     * 主源码根目录，相对模块根。
     */
    private static final Path MAIN_SOURCE_DIR = Paths.get("src/main/java");

    /**
     * 实现包目录（相对 {@code src/main/java}）：第三方 import 仅允许出现在此目录下的文件内。
     */
    private static final String IMPL_DIR = "cn/hg/aifei/mq/redisstream/";

    /**
     * ServiceLoader 声明文件路径，相对模块根。
     */
    private static final Path PROVIDER_DECLARATION = Paths.get(
            "src/main/resources/META-INF/services/cn.hg.aifei.mq.internal.MqProvider");

    /**
     * 声明文件唯一允许出现的 provider 全限定名。
     */
    private static final String DECLARED_PROVIDER = "cn.hg.aifei.mq.redisstream.RedisStreamProvider";

    /**
     * 本后端的类型标识。
     */
    private static final String BACKEND_TYPE = "redisstream";

    /**
     * 门禁①：第三方 import 只允许出现在实现包内，本模块其它位置一律禁止。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void thirdPartyImportsStayInImplementationPackage() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : mainJavaFiles()) {
            String relative = slash(source);
            if (relative.contains(IMPL_DIR)) {
                continue;
            }
            for (String imported : importedTypes(readText(source))) {
                if (!isOwnOrPlatform(imported)) {
                    violations.add(relative + " -> " + imported);
                }
            }
        }
        assertTrue("第三方 import 只允许出现在 " + IMPL_DIR + "，违规：" + violations, violations.isEmpty());
    }

    /**
     * 门禁②：本模块不得引用 JDBC。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void noJdbcReference() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : mainJavaFiles()) {
            if (readText(source).contains("import java.sql.")) {
                violations.add(slash(source));
            }
        }
        assertTrue("不得出现 import java.sql.，违规文件：" + violations, violations.isEmpty());
    }

    /**
     * 门禁③：声明文件存在、恰 1 行，且其 provider 的类型标识为 redisstream（与 {@link RedisStreamProvider#TYPE} 一致）。
     *
     * <p>只反射实例化 provider 并比对标识，不调用 {@code createBus}，故不创建连接池与网络访问。</p>
     *
     * @throws IOException                  读取声明文件失败
     * @throws ReflectiveOperationException provider 类型不可实例化
     */
    @Test
    public void serviceDeclarationIsSingleAndMatchesType() throws IOException, ReflectiveOperationException {
        assertTrue("声明文件不存在：" + PROVIDER_DECLARATION.toAbsolutePath(),
                Files.isRegularFile(PROVIDER_DECLARATION));

        List<String> declared = new ArrayList<>();
        for (String raw : readText(PROVIDER_DECLARATION).split("\n")) {
            String line = raw.trim();
            if (!line.isEmpty()) {
                declared.add(line);
            }
        }
        assertEquals("本模块声明文件应恰有 1 个 provider，实际为 " + declared, 1, declared.size());
        assertEquals("声明内容应为 provider 全限定名", DECLARED_PROVIDER, declared.get(0));

        MqProvider provider = (MqProvider) Class.forName(DECLARED_PROVIDER)
                .getDeclaredConstructor()
                .newInstance();
        assertNotNull("provider 应可无参实例化", provider);
        assertEquals("声明的 provider 类型标识应为 " + BACKEND_TYPE, BACKEND_TYPE, provider.type());
        assertEquals("静态常量 TYPE 应与 type() 一致", BACKEND_TYPE, RedisStreamProvider.TYPE);
    }

    /**
     * 收集主源码下全部 Java 文件，作为门禁扫描对象。
     *
     * @return Java 源码文件列表
     * @throws IOException 遍历目录失败
     */
    private static List<Path> mainJavaFiles() throws IOException {
        assertTrue("源码目录不存在，测试需在模块根目录执行：" + MAIN_SOURCE_DIR.toAbsolutePath(),
                Files.isDirectory(MAIN_SOURCE_DIR));
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(MAIN_SOURCE_DIR)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .forEach(sources::add);
        }
        assertTrue("未扫描到任何 Java 源码", !sources.isEmpty());
        return sources;
    }

    /**
     * 判断 import 是否属于 JDK / 框架 / 本工程自有类型（即第三方依赖之外的许可集合）。
     *
     * @param type import 的全限定名
     * @return 属于自有或平台类型时 {@code true}
     */
    private static boolean isOwnOrPlatform(String type) {
        return type.startsWith("java.")
                || type.startsWith("javax.")
                || type.startsWith("cn.aifei.")
                || type.startsWith("cn.hg.aifei.mq.");
    }

    /**
     * 从源码文本提取全部 import 的全限定名（含 static import 目标）。
     *
     * @param content 源码全文
     * @return 全限定名列表
     */
    private static List<String> importedTypes(String content) {
        List<String> types = new ArrayList<>();
        for (String raw : content.split("\n")) {
            String line = raw.trim();
            if (!line.startsWith("import ")) {
                continue;
            }
            String body = line.substring("import ".length()).trim();
            if (body.startsWith("static ")) {
                body = body.substring("static ".length()).trim();
            }
            int semicolon = body.indexOf(';');
            types.add(semicolon >= 0 ? body.substring(0, semicolon).trim() : body);
        }
        return types;
    }

    /**
     * 归一化路径分隔符，使断言不受操作系统影响。
     *
     * @param path 文件路径
     * @return 以 {@code /} 分隔的路径字符串
     */
    private static String slash(Path path) {
        return path.toString().replace('\\', '/');
    }

    /**
     * 以 UTF-8 读取文本。
     *
     * @param path 文件路径
     * @return 文件全文
     * @throws IOException 读取失败
     */
    private static String readText(Path path) throws IOException {
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        assertNotNull("读取内容不应为 null：" + slash(path), content);
        return content;
    }
}
