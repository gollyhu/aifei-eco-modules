/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 构建约束：主 JAR 内全部 class 的 major version 必须为 52（Java 8 字节码）。
 */
public class ClassFileVersionTest {

    private static final int JAVA_8_MAJOR_VERSION = 52;

    /**
     * 纯 IDE 环境下 {@code target/classes} 可能不存在，此时跳过断言。
     */
    @Test
    public void shouldCompileToJava8Bytecode() throws IOException {
        File classesDir = new File("target/classes");
        if (!classesDir.isDirectory()) {
            // 未经过 Maven 编译时不做断言，避免误报
            return;
        }

        List<File> classFiles = new ArrayList<>();
        try (Stream<java.nio.file.Path> paths = Files.walk(classesDir.toPath())) {
            paths.filter(path -> path.toString().endsWith(".class"))
                    .forEach(path -> classFiles.add(path.toFile()));
        }

        assertFalse("未找到任何编译产物", classFiles.isEmpty());
        for (File classFile : classFiles) {
            int majorVersion = readMajorVersion(classFile);
            assertTrue(classFile.getPath() + " 的 major version 必须是 "
                            + JAVA_8_MAJOR_VERSION + "，实际=" + majorVersion,
                    majorVersion == JAVA_8_MAJOR_VERSION);
        }
    }

    private static int readMajorVersion(File classFile) throws IOException {
        try (InputStream input = Files.newInputStream(classFile.toPath());
             DataInputStream data = new DataInputStream(input)) {
            int magic = data.readInt();
            if (magic != 0xCAFEBABE) {
                throw new IOException("不是有效的 class 文件: " + classFile.getPath());
            }
            data.readUnsignedShort(); // minor version
            return data.readUnsignedShort();
        }
    }
}
