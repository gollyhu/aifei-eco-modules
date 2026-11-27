/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.crypto;

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
 * crypto 公共面形态测试：逐字签名、异常仅两个公共构造、
 * serialVersionUID=1L、公共方法无 throws、签名类型白名单、公共层零 JDK 加密/第三方字面量、major 52。
 */
public class PublicApiContractCryptoTest {

    @Test
    public void publicTypes_areFinalAndWellFormed() {
        assertTrue("CryptoUtil 须 public final", isPublicFinal(CryptoUtil.class));
        assertTrue("CryptoException 须 public final", isPublicFinal(CryptoException.class));
        assertTrue("CryptoException 须继承 RuntimeException",
                RuntimeException.class.isAssignableFrom(CryptoException.class));
        Constructor<?>[] ctors = CryptoUtil.class.getDeclaredConstructors();
        assertEquals("CryptoUtil 仅 1 个构造", 1, ctors.length);
        assertTrue("CryptoUtil 构造须 private", Modifier.isPrivate(ctors[0].getModifiers()));
    }

    @Test
    public void cryptoUtil_signaturesExactly() {
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "static java.lang.String sha1Hex(java.lang.String)",
                "static java.lang.String sha1Base64(java.lang.String)",
                "static java.lang.String hmacSha1(java.lang.String, java.lang.String)",
                "static java.lang.String hmacSha256(java.lang.String, java.lang.String)",
                "static boolean constantTimeEquals(java.lang.String, java.lang.String)")),
                publicDeclared(CryptoUtil.class));
        for (Method m : CryptoUtil.class.getDeclaredMethods()) {
            assertTrue("CryptoUtil 公共方法须全 static: " + m.getName(),
                    Modifier.isStatic(m.getModifiers()));
        }
    }

    @Test
    public void cryptoException_twoPublicConstructorsOnly() throws Exception {
        assertEquals("CryptoException 仅 2 个公共构造", 2, CryptoException.class.getConstructors().length);
        Set<String> sigs = new LinkedHashSet<String>();
        for (Constructor<?> c : CryptoException.class.getConstructors()) {
            sigs.add(join(c.getParameterTypes()));
        }
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "java.lang.String", "java.lang.String, java.lang.Throwable")), sigs);
        assertEquals("CryptoException 无自有公共方法", 0, publicDeclared(CryptoException.class).size());
        Field uid = CryptoException.class.getDeclaredField("serialVersionUID");
        int expectedMods = Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL;
        assertEquals("serialVersionUID 须 private static final long",
                expectedMods, uid.getModifiers() & expectedMods);
        assertEquals(long.class, uid.getType());
        uid.setAccessible(true);
        assertEquals("serialVersionUID 须为 1L", 1L, uid.getLong(null));
    }

    @Test
    public void publicMethods_noThrowsPureSignaturesAndMajor52() throws IOException {
        Class<?>[] types = {CryptoUtil.class, CryptoException.class};
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
                "src/main/java/cn/hg/aifei/tools/crypto/CryptoUtil.java",
                "src/main/java/cn/hg/aifei/tools/crypto/CryptoException.java"};
        for (String source : sources) {
            String text = new String(Files.readAllBytes(Paths.get(source)), StandardCharsets.UTF_8);
            for (String forbidden : Arrays.asList("javax.crypto.", "java.security.",
                    "org.lionsoul.", "com.google.zxing.", "cn.aifei.", "cn.hg.aifei.cache.")) {
                assertFalse(source + " 出现禁止字面量: " + forbidden, text.contains(forbidden));
            }
        }

        assertEquals("CryptoUtil.class major 52", 52,
                classMajor("cn/hg/aifei/tools/crypto/CryptoUtil.class"));
        assertEquals("CryptoException.class major 52", 52,
                classMajor("cn/hg/aifei/tools/crypto/CryptoException.class"));
    }

    // ---------------------------------------------------------------- 工具

    private static boolean isPublicFinal(Class<?> type) {
        int m = type.getModifiers();
        return Modifier.isPublic(m) && Modifier.isFinal(m);
    }

    private static int classMajor(String resource) throws IOException {
        InputStream raw = PublicApiContractCryptoTest.class.getClassLoader().getResourceAsStream(resource);
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
