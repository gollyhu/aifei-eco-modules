/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.hg.aifei.cache.redis;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.apache.fury.Fury;
import org.apache.fury.ThreadSafeFury;
import org.apache.fury.config.CompatibleMode;
import org.apache.fury.config.Language;
import org.apache.fury.resolver.AllowListChecker;
import org.apache.fury.resolver.AllowListChecker.CheckLevel;

/**
 * 使用 Fury 序列化和反序列化 Redis 缓存值。
 *
 * <p>下方的 5 个构建参数共同决定线上字节格式，属数据格式，发布后不得更改：序列化语言
 * {@code Language.JAVA}、兼容模式 {@code CompatibleMode.COMPATIBLE}、开启引用跟踪、关闭类注册
 * （{@code requireClassRegistration(false)}）并抑制注册告警，最终经 {@code buildThreadSafeFury()}
 * 取得静态共享实例。
 *
 * <p>反序列化按白名单放行：JDK 内置类型自动放行，框架自身类型与数组维度在类内登记，业务值类型由
 * {@code cache.jedis.allowedPackages} 声明。数组值是否放行只看元素类，与数组层数无关。
 */
final class FuryRedisValueCodec implements RedisValueCodec {

    /**
     * 无条件拒绝的类名或前缀（{@code *} 结尾表前缀），先于放行判定生效，即使被
     * {@code cache.jedis.allowedPackages} 粗粒度覆盖也不放行。
     */
    private static final List<String> FORBIDDEN_CLASSES = Arrays.asList(
            "javax.naming.*",
            "javax.script.*",
            "javax.management.BadAttributeValueExpException",
            "javax.management.ImmutableDescriptor",
            "com.sun.rowset.*",
            "com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl",
            "java.lang.Runtime",
            "java.lang.ProcessBuilder"
    );

    /**
     * 框架自身会写入字节流的类型：哨兵枚举。必须用枚举类型的全限定名登记，写成
     * {@code cn.hg.aifei.cache.internal.NullValue$INSTANCE} 会被拒绝。
     */
    private static final String SENTINEL_CLASS = "cn.hg.aifei.cache.internal.NullValue";

    private static final AllowListChecker CHECKER = createChecker();

    private static final ThreadSafeFury FURY = createFury();

    /**
     * 数组维度的放行模式：校验器按类名字符串做前缀判定，而数组类名形如 {@code [Lcom.foo.Bar;}
     * 或 {@code [[I}，按维度逐条列举不现实；前缀 {@code [} 与维度无关，一条即可覆盖一维与多维。
     * 它只放开数组这一维度，数组的元素类仍照常受 {@code cache.jedis.allowedPackages} 约束。
     */
    private static final String ARRAY_CLASS_PATTERN = "[*";

    /**
     * 建立白名单校验器：先登记拒绝项，再登记框架自身类型与数组维度。
     */
    private static AllowListChecker createChecker() {
        AllowListChecker checker = new AllowListChecker(CheckLevel.STRICT);
        checker.disallowClasses(FORBIDDEN_CLASSES);
        checker.allowClass(SENTINEL_CLASS);
        checker.allowClass(ARRAY_CLASS_PATTERN);
        return checker;
    }

    /**
     * 建立线程安全实例并挂上白名单校验器；校验器只做放行判定，不参与编码，字节格式不变。
     */
    private static ThreadSafeFury createFury() {
        ThreadSafeFury fury = Fury.builder()
                .withLanguage(Language.JAVA)
                .withCompatibleMode(CompatibleMode.COMPATIBLE)
                .withRefTracking(true)
                .requireClassRegistration(false)
                .suppressClassRegistrationWarnings(true)
                .buildThreadSafeFury();
        fury.setClassChecker(CHECKER);
        return fury;
    }

    /**
     * 放行业务值类型所在的包，取值为包名（{@code *} 结尾时原样作为模式使用）。
     *
     * <p>作用范围是进程级：底层序列化器是静态共享实例，多次调用只做并集累加，不会撤销既有放行项。
     */
    static void allowPackages(Collection<String> packages) {
        if (packages == null || packages.isEmpty()) {
            return;
        }
        for (String packageName : packages) {
            CHECKER.allowClass(toClassPattern(packageName));
        }
    }

    /**
     * 把包名归一为校验器接受的类名模式：{@code com.foo} 与 {@code com.foo.*} 等价。
     */
    private static String toClassPattern(String packageName) {
        String trimmed = packageName.trim();
        return trimmed.endsWith("*") ? trimmed : trimmed + ".*";
    }

    /**
     * 将对象序列化为字节数组。
     */
    @Override
    public byte[] serialize(Object value) {
        return FURY.serialize(value);
    }

    /**
     * 将字节数组反序列化为对象。
     */
    @Override
    public Object deserialize(byte[] bytes) {
        return FURY.deserialize(bytes);
    }
}
