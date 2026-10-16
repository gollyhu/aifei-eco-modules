/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss;

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
import java.util.Set;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * 架构门禁（设计 §4.2）：以源码<b>文本</b>扫描断言包边界与公共面收敛，不得为兼容错误用法而放宽。
 *
 * <pre>
 * ① {@code org.dromara.x.file.storage} 字样只允许出现在 {@code cn.hg.aifei.oss.internal}
 * ② {@code cn.aifei.} 字样（除本模块自身 {@code cn.hg.aifei.oss.} 前缀）只允许出现在 {@code cn.hg.aifei.oss.aifei}
 * ③ 全模块禁止 {@code java.sql.} / {@code io.undertow.}
 * ④ 根包直接子 {@code .java} 精确 5 个，且文件名集合精确等于那 5 个
 * </pre>
 *
 * <p>扫描对象为 {@code src/main/java} 下全部 {@code .java}，因此本测试必须在模块根目录
 * （{@code aifei-oss}）内执行。门禁按源码文本匹配（含注释 / javadoc / 字符串，不区分注释）。</p>
 */
public class ArchitectureBoundaryTest {

    /**
     * 主源码根目录，相对模块根。
     */
    private static final Path SOURCE_ROOT = Paths.get("src/main/java");

    /**
     * 根包目录（相对 {@code src/main/java}）。
     */
    private static final String ROOT_PACKAGE = "cn/hg/aifei/oss/";

    /**
     * 适配层包路径；x-file-storage 字样只允许出现在其下。
     *
     * <p>锚定为根包之下的 {@code internal} 段，而<b>非</b>任意 {@code /internal/} 子串，以免新增
     * {@code cn.hg.aifei.oss.<x>.internal} 这类同构包被误豁免。</p>
     */
    private static final String ADAPTER_PACKAGE = "cn/hg/aifei/oss/internal/";

    /**
     * 接入层包路径片段；框架类型只允许出现在其下。
     */
    private static final String BRIDGE_SEGMENT = "cn/hg/aifei/oss/aifei/";

    /**
     * x-file-storage 的包名前缀字样。
     */
    private static final String XFS_MARKER = "org.dromara.x.file.storage";

    /**
     * 框架类型包名前缀字样。
     */
    private static final String FRAMEWORK_MARKER = "cn.aifei.";

    /**
     * 本模块自身包名前缀，属框架字样排除项。
     */
    private static final String SELF_MARKER = "cn.hg.aifei.oss.";

    /**
     * 全模块禁止出现的字样。
     */
    private static final Set<String> FORBIDDEN_MARKERS =
            new LinkedHashSet<>(Arrays.asList("java.sql.", "io.undertow."));

    /**
     * 根包公共类型的期望个数。
     */
    private static final int EXPECTED_PUBLIC_TYPES = 5;

    /**
     * 根包公共类型的期望文件名集合（唯一事实来源，防同名替换）。
     */
    private static final Set<String> EXPECTED_ROOT_TYPES = new LinkedHashSet<>(Arrays.asList(
            "OssClient.java",
            "OssKit.java",
            "OssFileInfo.java",
            "OssObject.java",
            "OssException.java"
    ));

    /**
     * 门禁①：x-file-storage 字样只允许出现在适配层包。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void xfsOnlyInAdapterPackage() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            String path = normalize(file);
            if (path.contains(ADAPTER_PACKAGE)) {
                continue;
            }
            if (read(file).contains(XFS_MARKER)) {
                offenders.add(path);
            }
        }
        assertTrue("字样 " + XFS_MARKER + " 只允许出现在 internal 包，违规文件：" + offenders,
                offenders.isEmpty());
    }

    /**
     * 门禁②：框架类型字样只允许出现在接入层包；本模块自身前缀不计入。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void frameworkOnlyInBridgePackage() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            String path = normalize(file);
            if (path.contains(BRIDGE_SEGMENT)) {
                continue;
            }
            if (read(file).replace(SELF_MARKER, "").contains(FRAMEWORK_MARKER)) {
                offenders.add(path);
            }
        }
        assertTrue("字样 " + FRAMEWORK_MARKER + "（除 " + SELF_MARKER + "）只允许出现在 " + BRIDGE_SEGMENT
                + "，违规文件：" + offenders, offenders.isEmpty());
    }

    /**
     * 门禁③：全模块不得引用 JDBC 与 Undertow（本模块不承担数据库 / 容器职责）。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void noSqlOrUndertowReference() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            String content = read(file);
            for (String marker : FORBIDDEN_MARKERS) {
                if (content.contains(marker)) {
                    offenders.add(normalize(file) + " -> " + marker);
                }
            }
        }
        assertTrue("不得出现 " + FORBIDDEN_MARKERS + "，违规文件：" + offenders, offenders.isEmpty());
    }

    /**
     * 门禁④：根包直接子 {@code .java} 精确 5 个，且文件名集合精确相等。
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
        assertTrue("源码目录不存在，测试需在模块根目录执行：" + SOURCE_ROOT.toAbsolutePath(),
                Files.exists(SOURCE_ROOT));
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
     * 判断是否为根包的直接子文件：位于根包目录下，且根包目录之后的相对路径不含 {@code /}。
     *
     * @param path 归一化后的源码路径
     * @return 为根包直接子文件时 {@code true}
     */
    private static boolean isDirectRootFile(String path) {
        int rootIndex = path.lastIndexOf(ROOT_PACKAGE);
        if (rootIndex < 0) {
            return false;
        }
        return path.substring(rootIndex + ROOT_PACKAGE.length()).indexOf('/') < 0;
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
