/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.codec;

import org.junit.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * codec 公共面形态测试：逐字签名、异常两构造、
 * serialVersionUID=1L、无 throws、签名白名单、公共层零配置键/传输头/第三方字面量、major 52。
 */
public class PublicApiContractCodecTest {

    @Test
    public void publicTypes_areFinalAndWellFormed() {
        assertTrue("CodecUtil 须 public final", isPublicFinal(CodecUtil.class));
        assertTrue("CodecException 须 public final", isPublicFinal(CodecException.class));
        assertTrue("CodecException 须继承 RuntimeException",
                RuntimeException.class.isAssignableFrom(CodecException.class));
        Constructor<?>[] ctors = CodecUtil.class.getDeclaredConstructors();
        assertEquals("CodecUtil 仅 1 个构造", 1, ctors.length);
        assertTrue("CodecUtil 构造须 private", Modifier.isPrivate(ctors[0].getModifiers()));
    }

    @Test
    public void codecUtil_signaturesExactly() {
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "static byte[] gunzip(byte[])")),
                publicDeclared(CodecUtil.class));
        for (Method m : CodecUtil.class.getDeclaredMethods()) {
            assertTrue("CodecUtil 公共方法须全 static: " + m.getName(),
                    Modifier.isStatic(m.getModifiers()));
        }
    }

    @Test
    public void codecException_twoPublicConstructorsOnly() throws Exception {
        assertEquals("CodecException 仅 2 个公共构造", 2, CodecException.class.getConstructors().length);
        Set<String> sigs = new LinkedHashSet<String>();
        for (Constructor<?> c : CodecException.class.getConstructors()) {
            sigs.add(join(c.getParameterTypes()));
        }
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "java.lang.String", "java.lang.String, java.lang.Throwable")), sigs);
        assertEquals("CodecException 无自有公共方法", 0, publicDeclared(CodecException.class).size());
        Field uid = CodecException.class.getDeclaredField("serialVersionUID");
        int expectedMods = Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL;
        assertEquals("serialVersionUID 须 private static final long",
                expectedMods, uid.getModifiers() & expectedMods);
        assertEquals(long.class, uid.getType());
        uid.setAccessible(true);
        assertEquals("serialVersionUID 须为 1L", 1L, uid.getLong(null));
    }

    @Test
    public void publicMethods_noThrowsPureSignaturesAndMajor52() throws IOException {
        Class<?>[] types = {CodecUtil.class, CodecException.class};
        for (Class<?> type : types) {
            for (Method m : type.getDeclaredMethods()) {
                if (!Modifier.isPublic(m.getModifiers())) {
                    continue;
                }
                String where = type.getSimpleName() + "#" + m.getName();
                assertEquals(where + " 不得声明 throws", 0, m.getExceptionTypes().length);
                assertAllowedType(m.getReturnType(), where);
                for (Class<?> p : m.getParameterTypes()) {
                    assertAllowedType(p, where);
                }
            }
            for (Constructor<?> c : type.getDeclaredConstructors()) {
                if (Modifier.isPublic(c.getModifiers())) {
                    assertEquals(type.getSimpleName() + " 公共构造不得声明 throws",
                            0, c.getExceptionTypes().length);
                }
            }
        }

        String[] sources = {
                "src/main/java/cn/hg/aifei/tools/codec/CodecUtil.java",
                "src/main/java/cn/hg/aifei/tools/codec/CodecException.java"};
        for (String source : sources) {
            String text = new String(Files.readAllBytes(Paths.get(source)), StandardCharsets.UTF_8);
            for (String forbidden : Arrays.asList("javax.crypto.", "java.security.",
                    "org.lionsoul.", "com.google.zxing.",
                    "cn.aifei.", "cn.hg.aifei.cache.",
                    "http.gzip", "Accept-Encoding", "HttpConfig")) {
                assertFalse(source + " 出现禁止字面量: " + forbidden, text.contains(forbidden));
            }
        }

        assertEquals("CodecUtil.class major 52", 52,
                classMajor("cn/hg/aifei/tools/codec/CodecUtil.class"));
        assertEquals("CodecException.class major 52", 52,
                classMajor("cn/hg/aifei/tools/codec/CodecException.class"));
    }

    // ---------------------------------------------------------------- 工具

    private static boolean isPublicFinal(Class<?> type) {
        int m = type.getModifiers();
        return Modifier.isPublic(m) && Modifier.isFinal(m);
    }

    private static int classMajor(String resource) throws IOException {
        InputStream raw = PublicApiContractCodecTest.class.getClassLoader().getResourceAsStream(resource);
        assertNotNull("找不到 class 资源: " + resource, raw);
        try {
            DataInputStream in = new DataInputStream(raw);
            assertEquals("非 class 文件", 0xCAFEBABE, in.readInt());
            in.readUnsignedShort();
            return in.readUnsignedShort();
        } finally {
            raw.close();
        }
    }

    private static Set<String> publicDeclared(Class<?> type) {
        Set<String> sigs = new LinkedHashSet<String>();
        for (Method m : type.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers())) {
                sigs.add((Modifier.isStatic(m.getModifiers()) ? "static " : "")
                        + typeName(m.getReturnType()) + " " + m.getName()
                        + "(" + join(m.getParameterTypes()) + ")");
            }
        }
        return sigs;
    }

    private static String join(Class<?>[] types) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(typeName(types[i]));
        }
        return sb.toString();
    }

    private static String typeName(Class<?> type) {
        if (!type.isArray()) {
            return type.getName();
        }
        return typeName(type.getComponentType()) + "[]";
    }

    private static void assertAllowedType(Class<?> type, String where) {
        if (type.isPrimitive()) {
            assertTrue(where + " 出现非白名单基本类型: " + type.getName(),
                    type == boolean.class || type == int.class);
            return;
        }
        if (type.isArray()) {
            assertEquals(where + " 仅允许 byte[]", byte.class, type.getComponentType());
            return;
        }
        assertTrue(where + " 出现非白名单类型: " + type.getName(),
                type == String.class || type == java.util.Map.class);
    }
}
