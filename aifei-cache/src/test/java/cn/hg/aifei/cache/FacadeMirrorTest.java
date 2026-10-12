/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 门面完整性 —— {@link CacheUtil} / {@link CounterUtil} 的方法集合必须与
 * {@link Cache} / {@link Counter} 的业务方法逐一对应（含 {@link java.time.Duration} 与
 * {@code int ttlSeconds} 双重重载）；写路径的省略 TTL 重载仅限显式白名单列出的签名。
 *
 * <p>用反射比对签名，避免后续给接口加方法时漏掉门面。</p>
 *
 * <p>同时钉死根包的公共类型集合，防止公共面在无意间扩张。</p>
 */
public class FacadeMirrorTest {

    /**
     * <b>刻意不镜像到门面的契约方法</b>：它是配置自述，业务代码不需要经门面访问。
     *
     * <p>静态门面一律作用于主缓存，因此不镜像不影响可用性 —— 需要时用
     * {@code CacheUtil.use().namespace()}（或 {@code CounterUtil.use()}）取实例上的值。</p>
     */
    private static final Set<String> CONTRACT_ONLY = new HashSet<>(
            Arrays.asList("namespace()"));

    /**
     * {@code Cache} 的每个实例方法都能在 {@code CacheUtil} 找到同签名的静态方法
     * （{@link #CONTRACT_ONLY} 中的方法除外）。
     */
    @Test
    public void shouldMirrorCacheInterfaceInCacheUtil() {
        assertMirrors(Cache.class, CacheUtil.class, CONTRACT_ONLY);

        Set<String> facadeOnly = new HashSet<>();
        facadeOnly.add("use()");
        facadeOnly.add("use(String)");
        assertTrue("门面新增方法必须同步到接口: " + extraMethods(CacheUtil.class, Cache.class, facadeOnly),
                extraMethods(CacheUtil.class, Cache.class, facadeOnly).isEmpty());
    }

    /**
     * {@code Counter} 的每个实例方法都能在 {@code CounterUtil} 找到同签名的静态方法
     * （{@link #CONTRACT_ONLY} 中的方法除外）。
     */
    @Test
    public void shouldMirrorCounterInterfaceInCounterUtil() {
        assertMirrors(Counter.class, CounterUtil.class, CONTRACT_ONLY);

        Set<String> facadeOnly = new HashSet<>();
        facadeOnly.add("use()");
        facadeOnly.add("use(String)");
        assertTrue("门面新增方法必须同步到接口: " + extraMethods(CounterUtil.class, Counter.class, facadeOnly),
                extraMethods(CounterUtil.class, Counter.class, facadeOnly).isEmpty());
    }

    /**
     * {@code namespace()} 必须<b>没有</b>静态镜像 —— 防止有人"顺手补齐"。
     */
    @Test
    public void shouldNotMirrorContractOnlyMethods() {
        for (String signature : CONTRACT_ONLY) {
            assertFalse("门面不应提供 " + signature + " 的静态镜像: " + CacheUtil.class.getSimpleName(),
                    facadeSignatures(CacheUtil.class).contains(signature));
            assertFalse("门面不应提供 " + signature + " 的静态镜像: " + CounterUtil.class.getSimpleName(),
                    facadeSignatures(CounterUtil.class).contains(signature));
        }
    }

    /**
     * 实现专用的能力不得下沉到公共接口：{@link Cache} / {@link Counter} 不声明任何 {@code static}
     * 方法。
     *
     * <p>哨兵编解码与物理 key 拼装的唯一定义点分别在 {@code internal.AbstractCache} 与
     * {@code internal.AbstractCounter}；把其中任何一个搬回接口都会让本用例转红。</p>
     */
    @Test
    public void shouldNotDeclareStaticsOnContracts() {
        for (Class<?> contract : new Class<?>[]{Cache.class, Counter.class}) {
            Set<String> statics = new TreeSet<>();
            for (Method method : contract.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())) {
                    statics.add(signature(method));
                }
            }
            assertTrue(contract.getSimpleName()
                            + " 不得声明 static 方法（实现专用能力放在 internal 基类）: " + statics,
                    statics.isEmpty());
        }
    }

    /**
     * 公共接口也不声明任何常量：属数据格式的计数保留前缀放在 {@code internal.Config}。
     *
     * <p>常量不是方法，但出现在接口上就是对使用者的承诺；把 {@code COUNTER_KEY_PREFIX} 一类常量
     * 搬回 {@link Counter} / {@link Cache} 会让本用例转红。</p>
     */
    @Test
    public void shouldNotDeclareConstantsOnContracts() {
        for (Class<?> contract : new Class<?>[]{Cache.class, Counter.class}) {
            Set<String> constants = new TreeSet<>();
            for (Field field : contract.getDeclaredFields()) {
                constants.add(field.getName());
            }
            assertTrue(contract.getSimpleName()
                            + " 不得声明常量（框架级常量集中在 internal.Config）: " + constants,
                    constants.isEmpty());
        }
    }

    /**
     * 写路径只放行显式白名单列出的「省略 TTL」重载（缺省取实例 {@code ttl()}）；其余同名重载仍必须
     * 以 {@link java.time.Duration} 或 {@code int} 结尾 —— 护栏不撤，防止新的省略 TTL 重载悄悄混入。
     */
    @Test
    public void shouldNotProvideTtlOmittingOverloads() {
        assertOnlyWhitelistedTtlOmittingOverloads(
                Cache.class,
                new String[]{"put", "putIfAbsent", "expire"},
                new HashSet<>(Arrays.asList(
                        "put(String,String,Object)",
                        "putIfAbsent(String,String,Object)",
                        "expire(String,String)")));
        assertOnlyWhitelistedTtlOmittingOverloads(
                Counter.class,
                new String[]{"increase", "increaseAndRefreshTtl", "decrease", "decreaseAndRefreshTtl"},
                new HashSet<>(Arrays.asList(
                        "increase(String,String,long)",
                        "increaseAndRefreshTtl(String,String,long)",
                        "decrease(String,String,long)",
                        "decreaseAndRefreshTtl(String,String,long)")));
    }

    /**
     * 逐个同名重载检查末位参数类型，白名单之外必须是 {@code Duration} 或 {@code int}。
     */
    private static void assertOnlyWhitelistedTtlOmittingOverloads(Class<?> contract, String[] names,
                                                                 Set<String> allowed) {
        for (String name : names) {
            for (Method method : contract.getDeclaredMethods()) {
                if (!method.getName().equals(name) || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                String signature = signature(method);
                if (allowed.contains(signature)) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                Class<?> last = params[params.length - 1];
                assertTrue(contract.getSimpleName() + "." + name
                                + " 的重载必须以 Duration 或 int ttlSeconds 结尾（或属显式白名单），实际: "
                                + signature,
                        last == java.time.Duration.class || last == int.class);
            }
        }
    }

    /**
     * 门面与工具类本身必须是不可实例化的 final 类。
     */
    @Test
    public void shouldBeFinalUtilityClasses() {
        for (Class<?> type : new Class<?>[]{CacheUtil.class, CounterUtil.class}) {
            assertTrue(type + " 必须是 final", Modifier.isFinal(type.getModifiers()));
            assertEquals(type + " 只能有一个私有构造", 1, type.getDeclaredConstructors().length);
            assertTrue(type + " 的构造必须是 private",
                    Modifier.isPrivate(type.getDeclaredConstructors()[0].getModifiers()));
        }
    }

    /**
     * 根包对外只暴露 5 个公共类型：{@link Cache} / {@link Counter} / {@link CacheUtil} /
     * {@link CounterUtil} / {@link CachePlugin}。
     *
     * <p>包内还有一个包私有的哨兵类型，它不构成公共面。公共类型集合是对外承诺的一部分，
     * 因此在此钉死，防止无意间扩张。</p>
     *
     * <p>必须经 {@code getProtectionDomain()} 定位主类目录：测试类与主类同包，而 surefire 的
     * classpath 中 {@code target/test-classes} 排在 {@code target/classes} 之前，用
     * {@code getResource("")} 会枚举到测试类所在的目录。</p>
     */
    @Test
    public void shouldExposeExactlyFivePublicTypes() throws Exception {
        File classesDirectory =
                new File(Cache.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        File packageDirectory =
                new File(classesDirectory, Cache.class.getPackage().getName().replace('.', '/'));
        File[] files = packageDirectory.listFiles();
        assertTrue("无法枚举根包目录: " + packageDirectory, files != null);

        Set<String> publicTypes = new TreeSet<>();
        for (File file : files) {
            String fileName = file.getName();
            if (!fileName.endsWith(".class") || fileName.contains("$")) {
                continue;
            }
            String simpleName = fileName.substring(0, fileName.length() - ".class".length());
            Class<?> type = Class.forName(Cache.class.getPackage().getName() + "." + simpleName);
            if (Modifier.isPublic(type.getModifiers())) {
                publicTypes.add(type.getSimpleName());
            }
        }

        assertEquals(new TreeSet<>(Arrays.asList(
                "Cache", "CacheUtil", "CachePlugin", "Counter", "CounterUtil")), publicTypes);
    }

    private static void assertMirrors(Class<?> contract, Class<?> facade, Set<String> contractOnly) {
        Set<String> facadeSignatures = facadeSignatures(facade);
        Set<String> missing = new TreeSet<>();
        for (Method method : contract.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            String signature = signature(method);
            if (contractOnly.contains(signature)) {
                continue;
            }
            if (!facadeSignatures.contains(signature)) {
                missing.add(signature);
            }
        }
        assertTrue(contract.getSimpleName() + " 的下列方法在 " + facade.getSimpleName()
                + " 中缺少镜像: " + missing, missing.isEmpty());
    }

    private static Set<String> extraMethods(Class<?> facade, Class<?> contract, Set<String> allowed) {
        Set<String> contractSignatures = new LinkedHashSet<>();
        for (Method method : contract.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) {
                contractSignatures.add(signature(method));
            }
        }
        Set<String> extra = new TreeSet<>();
        for (String signature : facadeSignatures(facade)) {
            if (!contractSignatures.contains(signature) && !allowed.contains(signature)) {
                extra.add(signature);
            }
        }
        return extra;
    }

    private static Set<String> facadeSignatures(Class<?> facade) {
        Set<String> signatures = new LinkedHashSet<>();
        for (Method method : facade.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers())) {
                signatures.add(signature(method));
            }
        }
        return signatures;
    }

    private static String signature(Method method) {
        StringBuilder builder = new StringBuilder(method.getName()).append('(');
        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(params[i].getSimpleName());
        }
        return builder.append(')').toString();
    }
}
