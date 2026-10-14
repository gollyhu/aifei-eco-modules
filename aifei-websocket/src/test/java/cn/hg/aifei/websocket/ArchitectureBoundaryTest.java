/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
 * 架构门禁（设计 §6.8）：以源码文本扫描断言包边界，不得为兼容错误用法而放宽。
 *
 * <pre>
 * ① {@code org.java_websocket} 只允许出现在 cn.hg.aifei.websocket.javawebsocket 包
 * ② import cn.aifei.*（除 cn.hg.aifei.websocket.*）只允许出现在 cn.hg.aifei.websocket.aifei 包
 * ③ 全模块禁止 io.undertow. 与 import java.sql.
 * ④ 接入包非空且两个 Plugin 文件存在
 * </pre>
 *
 * <p>扫描对象为 {@code src/main/java} 下全部 {@code .java}，因此本测试必须在模块根目录（{@code aifei-websocket}）内执行。</p>
 */
public class ArchitectureBoundaryTest {

    /**
     * 主源码根目录，相对模块根。
     */
    private static final Path SOURCE_ROOT = Paths.get("src/main/java");

    /**
     * 第三方类型唯一允许出现的实现包（以 {@code /} 结尾，避免误命中同前缀包）。
     */
    private static final String IMPL_PACKAGE = "cn/hg/aifei/websocket/javawebsocket/";

    /**
     * 框架类型唯一允许出现的接入包。
     */
    private static final String BRIDGE_PACKAGE = "cn/hg/aifei/websocket/aifei/";

    /**
     * 门禁①：第三方传输库类型只允许出现在实现包，公共面与内部共享层不得引用。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void thirdPartyOnlyInImplPackage() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            if (read(file).contains("org.java_websocket") && !normalize(file).contains(IMPL_PACKAGE)) {
                offenders.add(normalize(file));
            }
        }
        assertTrue("org.java_websocket.* 只允许出现在 " + IMPL_PACKAGE + "，违规文件：" + offenders, offenders.isEmpty());
    }

    /**
     * 门禁②：框架类型只允许出现在接入包，业务共享层不得绑定 aifei 内核。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void frameworkOnlyInBridgePackage() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            if (hasFrameworkImport(read(file)) && !normalize(file).contains(BRIDGE_PACKAGE)) {
                offenders.add(normalize(file));
            }
        }
        assertTrue("import cn.aifei.*（除 cn.hg.aifei.websocket.*）只允许出现在 " + BRIDGE_PACKAGE
                + "，违规文件：" + offenders, offenders.isEmpty());
    }

    /**
     * 门禁③：全模块不得引用 Undertow 与 JDBC（本模块不承担 Web 容器与数据库职责）。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void noUndertowOrSqlReference() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            String content = read(file);
            if (content.contains("io.undertow.") || content.contains("import java.sql.")) {
                offenders.add(normalize(file));
            }
        }
        assertTrue("不得出现 io.undertow. 或 import java.sql.，违规文件：" + offenders, offenders.isEmpty());
    }

    /**
     * 门禁④：接入包存在且非空，两个装配入口文件齐备。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void aifeiBridgePackageExists() throws IOException {
        assertTrue("接入层包不应为空", Files.isDirectory(SOURCE_ROOT.resolve(BRIDGE_PACKAGE)));

        List<String> files = new ArrayList<>();
        for (Path file : javaFiles()) {
            files.add(normalize(file));
        }
        assertTrue("WebSocketClientPlugin 应存在于接入层包",
                containsSuffix(files, BRIDGE_PACKAGE + "WebSocketClientPlugin.java"));
        assertTrue("WebSocketServerPlugin 应存在于接入层包",
                containsSuffix(files, BRIDGE_PACKAGE + "WebSocketServerPlugin.java"));
    }

    /**
     * 收集主源码下全部 Java 文件，作为门禁扫描对象。
     *
     * @return Java 源码文件列表
     * @throws IOException 遍历目录失败
     */
    private static List<Path> javaFiles() throws IOException {
        assertTrue("源码目录不存在，测试需在模块根目录执行：" + SOURCE_ROOT.toAbsolutePath(), Files.exists(SOURCE_ROOT));
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(SOURCE_ROOT)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                    .filter(Files::isRegularFile)
                    .forEach(files::add);
        }
        assertFalse("未扫描到任何 Java 源码", files.isEmpty());
        return files;
    }

    /**
     * 判断源码文本是否含框架类型 import；{@code cn.hg.aifei.websocket.*} 属本模块自身，不算框架类型。
     *
     * @param content 源码全文
     * @return 含非本模块的 aifei import 时 {@code true}
     */
    private static boolean hasFrameworkImport(String content) {
        for (String raw : content.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("import cn.aifei.") && !line.startsWith("import cn.hg.aifei.websocket.")) {
                return true;
            }
        }
        return false;
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
     * 判断文件路径集合中是否存在以指定后缀结尾的项（路径含 {@code src/main/java} 前缀，故按后缀匹配）。
     *
     * @param files 归一化后的文件路径集合
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
