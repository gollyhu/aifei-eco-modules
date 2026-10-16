/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * 构建约束（设计 §8）：主 JAR 内全部 class 的 major version 必须为 52（Java 8 字节码）。
 */
public class ClassFileVersionTest {

    /**
     * Java 8 的 class 文件 major version。
     */
    private static final int JAVA_8_MAJOR_VERSION = 52;

    /**
     * class 文件的魔数。
     */
    private static final int CLASS_MAGIC = 0xCAFEBABE;

    /**
     * 读取 {@code target/classes} 下全部 {@code .class}，断言 major version = 52。
     *
     * @throws IOException 读取构建产物失败
     */
    @Test
    public void mainClassesAreJava8() throws IOException {
        File classesDir = new File("target/classes");
        assertTrue("构建产物目录不存在，本测试须经 Maven 构建后执行（先跑 mvn clean verify）: "
                + classesDir.getAbsolutePath(), classesDir.isDirectory());

        List<File> classFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(classesDir.toPath())) {
            paths.filter(path -> path.toString().endsWith(".class"))
                    .forEach(path -> classFiles.add(path.toFile()));
        }

        assertFalse("未找到任何编译产物", classFiles.isEmpty());
        for (File classFile : classFiles) {
            int majorVersion = readMajorVersion(classFile);
            assertTrue(classFile.getPath() + " 的 major version 必须是 " + JAVA_8_MAJOR_VERSION
                    + "，实际=" + majorVersion, majorVersion == JAVA_8_MAJOR_VERSION);
        }
    }

    /**
     * 读取单个 class 文件的 major version。
     *
     * @param classFile class 文件
     * @return major version
     * @throws IOException 读取失败或文件不是合法 class
     */
    private static int readMajorVersion(File classFile) throws IOException {
        try (InputStream input = Files.newInputStream(classFile.toPath());
             DataInputStream data = new DataInputStream(input)) {
            int magic = data.readInt();
            if (magic != CLASS_MAGIC) {
                throw new IOException("不是有效的 class 文件: " + classFile.getPath());
            }
            data.readUnsignedShort();
            return data.readUnsignedShort();
        }
    }
}
