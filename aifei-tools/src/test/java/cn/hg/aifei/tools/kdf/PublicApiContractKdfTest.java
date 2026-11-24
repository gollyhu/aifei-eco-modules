/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.kdf;

import org.junit.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * kdf 公共面形态测试（设计 §3.5 / §6.2）：精确 4 类型、逐字签名、KdfHash SHA256/SHA512、
 * 公共方法无 throws、签名零第三方类型、KdfSpec 不可变、class major 52。
 */
public class PublicApiContractKdfTest {

    @Test
    public void publicTypes_areFinalAndWellFormed() {
        assertTrue("KdfKit 须 public final", isPublicFinal(KdfKit.class));
        assertTrue("KdfSpec 须 public final", isPublicFinal(KdfSpec.class));
        assertTrue("KdfHash 须 public enum",
                KdfHash.class.isEnum() && Modifier.isPublic(KdfHash.class.getModifiers()));
        assertTrue("KdfException 须 public final", isPublicFinal(KdfException.class));
        assertTrue("KdfException 须继承 RuntimeException",
                RuntimeException.class.isAssignableFrom(KdfException.class));
    }

    @Test
    public void kdfKit_signaturesExactly() {
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "static byte[] derive(cn.hg.aifei.tools.kdf.KdfSpec, byte[])",
                "static byte[] derive(cn.hg.aifei.tools.kdf.KdfSpec, byte[], byte[])")),
                publicDeclared(KdfKit.class));
        Constructor<?>[] ctors = KdfKit.class.getDeclaredConstructors();
        assertEquals("KdfKit 仅 1 个私有构造", 1, ctors.length);
        assertTrue("KdfKit 构造须 private", Modifier.isPrivate(ctors[0].getModifiers()));
    }

    @Test
    public void kdfSpec_signaturesExactly() {
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "static cn.hg.aifei.tools.kdf.KdfSpec hkdf(cn.hg.aifei.tools.kdf.KdfHash, byte[], byte[], int)",
                "static cn.hg.aifei.tools.kdf.KdfSpec pbkdf2(cn.hg.aifei.tools.kdf.KdfHash, byte[], int, int)",
                "static cn.hg.aifei.tools.kdf.KdfSpec hmac(cn.hg.aifei.tools.kdf.KdfHash, int)",
                "java.lang.String getAlgorithm()",
                "cn.hg.aifei.tools.kdf.KdfHash getHash()",
                "byte[] getSalt()",
                "int getIterations()",
                "byte[] getInfo()",
                "int getKeyLen()")),
                publicDeclared(KdfSpec.class));
        Constructor<?>[] ctors = KdfSpec.class.getDeclaredConstructors();
        assertEquals("KdfSpec 仅 1 个私有构造", 1, ctors.length);
        assertTrue("KdfSpec 构造须 private", Modifier.isPrivate(ctors[0].getModifiers()));
    }

    @Test
    public void kdfSpec_isImmutable() {
        // HKDF：salt/info 修改不影响原对象
        byte[] salt = new byte[]{1, 2, 3};
        byte[] info = new byte[]{4, 5};
        KdfSpec hkdf = KdfSpec.hkdf(KdfHash.SHA256, salt, info, 32);
        salt[0] = 99;
        info[0] = 99;
        assertEquals("salt 应为副本", 1, hkdf.getSalt()[0]);
        assertEquals("info 应为副本", 4, hkdf.getInfo()[0]);

        // 外部修改 getter 返回值不影响本对象
        byte[] gotSalt = hkdf.getSalt();
        byte[] gotInfo = hkdf.getInfo();
        gotSalt[0] = 88;
        gotInfo[0] = 88;
        assertEquals("getSalt 须返回副本", 1, hkdf.getSalt()[0]);
        assertEquals("getInfo 须返回副本", 4, hkdf.getInfo()[0]);

        // PBKDF2：getInfo 返回 null、getIterations 有效
        KdfSpec pbkdf2 = KdfSpec.pbkdf2(KdfHash.SHA512, new byte[]{1}, 1000, 32);
        assertEquals("PBKDF2 iterations", 1000, pbkdf2.getIterations());
        assertEquals("PBKDF2 info 须 null", null, pbkdf2.getInfo());

        // HMAC：getSalt 返回 null、getIterations 返回 0、getInfo 返回 null
        KdfSpec hmac = KdfSpec.hmac(KdfHash.SHA256, 32);
        assertEquals("HMAC salt 须 null", null, hmac.getSalt());
        assertEquals("HMAC iterations 须 0", 0, hmac.getIterations());
        assertEquals("HMAC info 须 null", null, hmac.getInfo());
    }

    @Test
    public void kdfHash_constantsExactly() {
        assertEquals(Arrays.asList(KdfHash.SHA256, KdfHash.SHA512),
                Arrays.asList(KdfHash.values()));
    }

    @Test
    public void kdfException_twoPublicConstructorsOnly() {
        assertEquals("KdfException 仅 2 个公共构造", 2, KdfException.class.getConstructors().length);
        Set<String> sigs = new LinkedHashSet<String>();
        for (Constructor<?> c : KdfException.class.getConstructors()) {
            sigs.add(join(c.getParameterTypes()));
        }
        assertEquals(new LinkedHashSet<String>(Arrays.asList(
                "java.lang.String", "java.lang.String, java.lang.Throwable")), sigs);
        assertEquals("KdfException 无自有公共方法", 0, publicDeclared(KdfException.class).size());
    }

    @Test
    public void publicMethods_noThrowsClause() {
        Class<?>[] types = {KdfKit.class, KdfSpec.class, KdfHash.class, KdfException.class};
        for (Class<?> t : types) {
            for (Method m : t.getDeclaredMethods()) {
                if (Modifier.isPublic(m.getModifiers())) {
                    assertEquals(t.getSimpleName() + "#" + m.getName() + " 不得声明 throws",
                            0, m.getExceptionTypes().length);
                }
            }
            for (Constructor<?> c : t.getDeclaredConstructors()) {
                if (Modifier.isPublic(c.getModifiers())) {
                    assertEquals(t.getSimpleName() + " 公共构造不得声明 throws",
                            0, c.getExceptionTypes().length);
                }
            }
        }
    }

    @Test
    public void publicSignatures_noThirdPartyTypes() {
        Class<?>[] types = {KdfKit.class, KdfSpec.class, KdfHash.class, KdfException.class};
        for (Class<?> t : types) {
            for (Method m : t.getDeclaredMethods()) {
                if (!Modifier.isPublic(m.getModifiers())) {
                    continue;
                }
                assertPureJdkOrModule(m.getReturnType(), t.getSimpleName() + "#" + m.getName());
                for (Class<?> p : m.getParameterTypes()) {
                    assertPureJdkOrModule(p, t.getSimpleName() + "#" + m.getName());
                }
            }
        }
    }

    @Test
    public void mainClasses_majorVersionIsJava8() throws IOException {
        String[] classes = {
                "cn/hg/aifei/tools/kdf/KdfKit.class",
                "cn/hg/aifei/tools/kdf/KdfSpec.class",
                "cn/hg/aifei/tools/kdf/KdfHash.class",
                "cn/hg/aifei/tools/kdf/KdfException.class"};
        for (String name : classes) {
            assertEquals(name + " 的 class major 须为 52", 52, classMajor(name));
        }
    }

    // ---------------------------------------------------------------- 工具

    private static boolean isPublicFinal(Class<?> type) {
        int m = type.getModifiers();
        return Modifier.isPublic(m) && Modifier.isFinal(m);
    }

    private static int classMajor(String resource) throws IOException {
        InputStream raw = PublicApiContractKdfTest.class.getClassLoader().getResourceAsStream(resource);
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
        StringBuilder sb = new StringBuilder(typeName(type.getComponentType()));
        sb.append("[]");
        return sb.toString();
    }

    private static void assertPureJdkOrModule(Class<?> type, String where) {
        if (type.isPrimitive()) {
            return;
        }
        if (type.isArray()) {
            assertPureJdkOrModule(type.getComponentType(), where);
            return;
        }
        String name = type.getName();
        assertFalse(where + " 泄漏第三方/框架类型: " + name,
                name.startsWith("org.lionsoul.") || name.startsWith("com.google.zxing.")
                        || name.startsWith("cn.aifei.") || name.startsWith("cn.hg.aifei.cache."));
        assertTrue(where + " 出现非 JDK/本模块类型: " + name,
                name.startsWith("java.") || name.startsWith("javax.")
                        || name.startsWith("cn.hg.aifei.tools"));
    }
}
