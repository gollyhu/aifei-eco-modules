/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.xml;

import org.junit.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * xml 公共面形态测试：逐字签名（含 Map&lt;String,String&gt; 泛型）、
 * 异常两构造、serialVersionUID=1L、无 throws、公共层零第三方/来源协议串字面量、major 52。
 */
public class PublicApiContractXmlTest {

    @Test
    public void publicTypes_areFinalAndWellFormed() {
        assertTrue("XmlUtil 须 public final", isPublicFinal(XmlUtil.class));
        assertTrue("XmlException 须 public final", isPublicFinal(XmlException.class));
        assertTrue("XmlException 须继承 RuntimeException",
                RuntimeException.class.isAssignableFrom(XmlException.class));
        Constructor<?>[] ctors = XmlUtil.class.getDeclaredConstructors();
        assertEquals("XmlUtil 仅 1 个构造", 1, ctors.length);
        assertTrue("XmlUtil 构造须 private", Modifier.isPrivate(ctors[0].getModifiers()));
    }

    @Test
    public void xmlUtil_signaturesExactly() throws Exception {
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "static java.util.Map parse(java.lang.String)",
                "static java.lang.String escape(java.lang.String)")),
                publicDeclared(XmlUtil.class));
        Type returnType = XmlUtil.class.getDeclaredMethod("parse", String.class).getGenericReturnType();
        assertTrue("parse 返回类型须为参数化 Map", returnType instanceof ParameterizedType);
        ParameterizedType parameterized = (ParameterizedType) returnType;
        assertEquals(Map.class, parameterized.getRawType());
        assertEquals(Arrays.asList(String.class, String.class),
                Arrays.asList(parameterized.getActualTypeArguments()));
        for (Method m : XmlUtil.class.getDeclaredMethods()) {
            assertTrue("XmlUtil 公共方法须全 static: " + m.getName(),
                    Modifier.isStatic(m.getModifiers()));
        }
    }

    @Test
    public void xmlException_twoPublicConstructorsOnly() throws Exception {
        assertEquals("XmlException 仅 2 个公共构造", 2, XmlException.class.getConstructors().length);
        Set<String> sigs = new LinkedHashSet<String>();
        for (Constructor<?> c : XmlException.class.getConstructors()) {
            sigs.add(join(c.getParameterTypes()));
        }
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "java.lang.String", "java.lang.String, java.lang.Throwable")), sigs);
        assertEquals("XmlException 无自有公共方法", 0, publicDeclared(XmlException.class).size());
        Field uid = XmlException.class.getDeclaredField("serialVersionUID");
        int expectedMods = Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL;
        assertEquals("serialVersionUID 须 private static final long",
                expectedMods, uid.getModifiers() & expectedMods);
        assertEquals(long.class, uid.getType());
        uid.setAccessible(true);
        assertEquals("serialVersionUID 须为 1L", 1L, uid.getLong(null));
    }

    @Test
    public void publicMethods_noThrowsPureSignaturesAndMajor52() throws IOException {
        Class<?>[] types = {XmlUtil.class, XmlException.class};
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
                "src/main/java/cn/hg/aifei/tools/xml/XmlUtil.java",
                "src/main/java/cn/hg/aifei/tools/xml/XmlException.java"};
        for (String source : sources) {
            String text = new String(Files.readAllBytes(Paths.get(source)), StandardCharsets.UTF_8);
            for (String forbidden : Arrays.asList("javax.crypto.", "java.security.",
                    "org.lionsoul.", "com.google.zxing.",
                    "cn.aifei.", "cn.hg.aifei.cache.",
                    "\"<xml>\"", "ToUserName", "FromUserName")) {
                assertFalse(source + " 出现禁止字面量: " + forbidden, text.contains(forbidden));
            }
        }

        assertEquals("XmlUtil.class major 52", 52,
                classMajor("cn/hg/aifei/tools/xml/XmlUtil.class"));
        assertEquals("XmlException.class major 52", 52,
                classMajor("cn/hg/aifei/tools/xml/XmlException.class"));
    }

    // ---------------------------------------------------------------- 工具

    private static boolean isPublicFinal(Class<?> type) {
        int m = type.getModifiers();
        return Modifier.isPublic(m) && Modifier.isFinal(m);
    }

    private static int classMajor(String resource) throws IOException {
        InputStream raw = PublicApiContractXmlTest.class.getClassLoader().getResourceAsStream(resource);
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
