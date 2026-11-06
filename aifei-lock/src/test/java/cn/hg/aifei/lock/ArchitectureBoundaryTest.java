/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.lock;

import static org.junit.Assert.assertEquals;
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
 * 架构门禁：以源码文本扫描断言包边界与公共面大小，不得为兼容错误用法而放宽。
 *
 * <pre>
 * ① cn.hg.aifei.cache.* 只允许出现在 lock/cache 包
 * ② cn.aifei.*（框架类型）只允许出现在 lock/aifei 包，唯一例外是日志门面 cn.aifei.log
 * ③ 根包直属 .java 文件数精确 3 个（Lock / LockKit / LockException）
 * </pre>
 *
 * <p>扫描对象为 {@code src/main/java} 下全部 {@code .java}，因此本测试必须在模块根目录内执行。</p>
 */
public class ArchitectureBoundaryTest {

    /**
     * 主源码根目录，相对模块根。
     */
    private static final Path SOURCE_ROOT = Paths.get("src/main/java");

    /**
     * cache 类型的唯一允许包。
     */
    private static final String CACHE_PACKAGE = "cn/hg/aifei/lock/cache/";

    /**
     * 框架类型的唯一允许包。
     */
    private static final String BRIDGE_PACKAGE = "cn/hg/aifei/lock/aifei/";

    /**
     * 根包目录（相对 {@code src/main/java}）。
     */
    private static final String ROOT_PACKAGE = "cn/hg/aifei/lock/";

    /**
     * 允许出现在任意包的框架类型前缀。
     */
    private static final String LOG_PACKAGE = "import cn.aifei.log.";

    /**
     * 根包直属文件数与期望文件名。
     */
    private static final int EXPECTED_ROOT_FILES = 3;

    /**
     * 门禁①：cache 类型只允许出现在 cache 实现包。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void cacheTypesOnlyInCachePackage() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            if (read(file).contains("cn.hg.aifei.cache") && !normalize(file).contains(CACHE_PACKAGE)) {
                offenders.add(normalize(file));
            }
        }
        assertTrue("cn.hg.aifei.cache.* 只允许出现在 " + CACHE_PACKAGE + "，违规文件：" + offenders,
                offenders.isEmpty());
    }

    /**
     * 门禁②：框架类型只允许出现在接入包，日志门面例外。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    public void frameworkTypesOnlyInBridgePackage() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            if (hasFrameworkImport(read(file)) && !normalize(file).contains(BRIDGE_PACKAGE)) {
                offenders.add(normalize(file));
            }
        }
        assertTrue("cn.aifei.*（日志门面除外）只允许出现在 " + BRIDGE_PACKAGE + "，违规文件：" + offenders,
                offenders.isEmpty());
    }

    /**
     * 门禁③：根包直属 .java 精确 3 个，实现类不得放进根包。
     *
     * @throws IOException 遍历目录失败
     */
    @Test
    public void rootPackageExactThreeFiles() throws IOException {
        List<String> rootFiles = new ArrayList<>();
        for (Path file : javaFiles()) {
            String path = normalize(file);
            if (isDirectRootFile(path)) {
                rootFiles.add(path.substring(path.lastIndexOf('/') + 1));
            }
        }
        assertEquals("根包直属 .java 应精确为 " + EXPECTED_ROOT_FILES + " 个，实际为 " + rootFiles,
                EXPECTED_ROOT_FILES, rootFiles.size());
        assertTrue("根包应含 Lock.java", rootFiles.contains("Lock.java"));
        assertTrue("根包应含 LockKit.java", rootFiles.contains("LockKit.java"));
        assertTrue("根包应含 LockException.java", rootFiles.contains("LockException.java"));
    }

    /**
     * 收集主源码下全部 Java 文件。
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
     * 判断是否为根包直接子文件：位于根包目录下且不含子目录层级。
     *
     * @param path 归一化后的源码路径
     * @return 为根包直接子文件时 {@code true}
     */
    private static boolean isDirectRootFile(String path) {
        int rootIndex = path.lastIndexOf(ROOT_PACKAGE);
        if (rootIndex < 0) {
            return false;
        }
        return !path.substring(rootIndex + ROOT_PACKAGE.length()).contains("/");
    }

    /**
     * 判断源码是否引用了框架类型；日志门面允许出现在任意包。
     *
     * @param content 源码全文
     * @return 含非本模块且非日志门面的 aifei import 时 {@code true}
     */
    private static boolean hasFrameworkImport(String content) {
        for (String raw : content.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("import cn.aifei.") && !line.startsWith(LOG_PACKAGE)) {
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
