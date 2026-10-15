/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

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
import java.util.stream.Stream;
import org.junit.Test;

/**
 * 架构门禁（设计 §12）：以源码文本扫描断言包边界与公共面大小，不得为兼容错误用法而放宽。
 *
 * <pre>
 * ① 第三方 / 其它模块 import 只允许出现在实现包（根包 / internal / aifei 包均禁止）
 * ② import cn.aifei.*（除 cn.hg.aifei.mq.*）只允许出现在接入包 cn.hg.aifei.mq.aifei
 * ③ 全模块禁止 import java.sql.
 * ④ 接入包存在且含 MqPlugin.java
 * ⑤ 根包公共类型精确 7 个，且文件名集合精确等于那 7 个
 * </pre>
 *
 * <p>扫描对象为 {@code src/main/java} 下全部 {@code .java}，因此本测试必须在模块根目录（{@code aifei-mq}）内执行。</p>
 */
public class ArchitectureBoundaryTest {

    /**
     * 主源码根目录，相对模块根。
     */
    private static final Path SOURCE_ROOT = Paths.get("src/main/java");

    /**
     * 根包目录（相对 {@code src/main/java}）。
     */
    private static final String ROOT_PACKAGE = "cn/hg/aifei/mq/";

    /**
     * 框架类型唯一允许出现的接入包。
     */
    private static final String BRIDGE_PACKAGE = "cn/hg/aifei/mq/aifei/";

    /**
     * 根包公共类型的期望个数（设计 §3.8）。
     */
    private static final int EXPECTED_PUBLIC_TYPES = 7;

    /**
     * 根包公共类型的期望文件名集合（唯一事实来源，防同名替换）。
     */
    private static final Set<String> EXPECTED_ROOT_TYPES = new LinkedHashSet<>(Arrays.asList(
            "Message.java",
            "Producer.java",
            "Consumer.java",
            "MessageListener.java",
            "SendResult.java",
            "MqKit.java",
            "MqException.java"
    ));

    /**
     * 门禁①：第三方 / 其它模块 import 只允许出现在实现包。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void thirdPartyOnlyInImplementationPackage() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            String path = normalize(file);
            if (inImplementationPackage(path)) {
                continue;
            }
            for (String fqn : imports(read(file))) {
                if (!isAllowedImport(fqn)) {
                    offenders.add(path + " -> " + fqn);
                }
            }
        }
        assertTrue("第三方 / 其它模块 import 只允许出现在实现包，违规文件：" + offenders, offenders.isEmpty());
    }

    /**
     * 门禁②：框架类型只允许出现在接入包，内部与实现包不得绑定 aifei 内核。
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
        assertTrue("import cn.aifei.*（除 cn.hg.aifei.mq.*）只允许出现在 " + BRIDGE_PACKAGE
                + "，违规文件：" + offenders, offenders.isEmpty());
    }

    /**
     * 门禁③：全模块不得引用 JDBC（本模块不承担数据库职责）。
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
     * 门禁④：接入包存在且非空，装配入口文件齐备。
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
        assertTrue("MqPlugin 应存在于接入层包",
                containsSuffix(files, BRIDGE_PACKAGE + "MqPlugin.java"));
    }

    /**
     * 门禁⑤：根包公共类型精确 7 个，且文件名集合精确相等。
     *
     * @throws IOException 遍历目录失败
     */
    @Test
    public void publicSurfaceExact() throws IOException {
        List<String> rootTypes = new ArrayList<>();
        for (Path file : javaFiles()) {
            String path = normalize(file);
            if (isDirectRootFile(path)) {
                rootTypes.add(path.substring(path.lastIndexOf('/') + 1));
            }
        }
        assertEquals("根包公共类型应精确为 " + EXPECTED_PUBLIC_TYPES + " 个，实际为 " + rootTypes,
                EXPECTED_PUBLIC_TYPES, rootTypes.size());
        assertEquals("根包文件名集合应精确匹配，实际为 " + rootTypes,
                EXPECTED_ROOT_TYPES, new LinkedHashSet<>(rootTypes));
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
     * 判断文件是否位于实现包：既不是根包直接子文件，也不属于 {@code internal} 或 {@code aifei} 包。
     *
     * @param path 归一化后的源码路径
     * @return 位于实现包时 {@code true}
     */
    private static boolean inImplementationPackage(String path) {
        return !isDirectRootFile(path)
                && !path.contains("/internal/")
                && !path.contains(BRIDGE_PACKAGE);
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
                || fqn.startsWith("cn.hg.aifei.mq.");
    }

    /**
     * 判断是否为根包的直接子文件：位于根包目录下，且 ROOT_PACKAGE 之后的相对路径不含 {@code /}。
     *
     * @param path 归一化后的源码路径
     * @return 为根包直接子文件时 {@code true}
     */
    private static boolean isDirectRootFile(String path) {
        int rootIndex = path.lastIndexOf(ROOT_PACKAGE);
        if (rootIndex < 0) {
            return false;
        }
        String relative = path.substring(rootIndex + ROOT_PACKAGE.length());
        return !relative.contains("/");
    }

    /**
     * 判断源码文本是否含框架类型 import；{@code cn.hg.aifei.mq.*} 属本模块自身，不算框架类型。
     *
     * @param content 源码全文
     * @return 含非本模块的 aifei import 时 {@code true}
     */
    private static boolean hasFrameworkImport(String content) {
        for (String raw : content.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("import cn.aifei.") && !line.startsWith("import cn.hg.aifei.mq.")) {
                return true;
            }
        }
        return false;
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
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        assertNotNull(content);
        return content;
    }
}
