/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

/**
 * 可选依赖探测：全模块唯一的 {@code Class.forName} 调用点。
 *
 * <p>内核依赖全部声明为 {@code optional}，缺失时必须在装配期或调用入口转成可读的
 * {@link cn.hg.aifei.pay.PayException}，不得让 {@code NoClassDefFoundError} 冒到调用方。</p>
 */
public final class PayDeps {

    /**
     * 支付宝 V3 交易接口封装（IJPay）。
     */
    private static final String IJPAY_TRADE = "com.ijpay.alipay.v3.AliPayTradeApi";

    /**
     * 支付宝 V2 交易门面（IJPay）。
     */
    private static final String IJPAY_TRADE_V2 = "com.ijpay.alipay.AliPayApi";

    /**
     * 支付宝 V2 SDK 验签工具。
     */
    private static final String ALIPAY_SIGNATURE_V2 = "com.alipay.api.internal.util.AlipaySignature";

    /**
     * 开放平台 V3 SDK 客户端。
     */
    private static final String ALIPAY_API_CLIENT = "com.alipay.v3.ApiClient";

    /**
     * 开放平台 SDK 验签工具。
     */
    private static final String ALIPAY_SIGNATURE = "com.alipay.v3.util.AlipaySignature";

    /**
     * 证书解析所需的 BouncyCastle provider。
     */
    private static final String BC_PROVIDER = "org.bouncycastle.jce.provider.BouncyCastleProvider";

    /**
     * IJPay 坐标与版本，与 pom 的 {@code ijpay.version} 保持一致。
     */
    private static final String IJPAY_GROUP = "com.github.javen205";
    private static final String IJPAY_ARTIFACT = "IJPay-AliPay-V3";
    private static final String IJPAY_ARTIFACT_V2 = "IJPay-AliPay";
    private static final String IJPAY_VERSION = "3.0.0";

    /**
     * 开放平台 V3 SDK 坐标与版本，与 pom 的 {@code alipay.v3.version} 保持一致。
     */
    private static final String SDK_GROUP = "com.alipay.sdk";
    private static final String SDK_ARTIFACT = "alipay-sdk-java-v3";
    private static final String SDK_VERSION = "3.1.89.ALL";

    /**
     * 开放平台 V2 SDK 坐标与版本，与 pom 的 {@code alipay.v2.version} 保持一致。
     */
    private static final String SDK_ARTIFACT_V2 = "alipay-sdk-java";
    private static final String SDK_VERSION_V2 = "4.40.1067.ALL";

    /**
     * BouncyCastle 坐标与版本，与 pom 的 {@code bcprov.version} 保持一致。
     */
    private static final String BC_GROUP = "org.bouncycastle";
    private static final String BC_ARTIFACT = "bcprov-jdk18on";
    private static final String BC_VERSION = "1.86";

    /**
     * 禁止创建工具类实例。
     */
    private PayDeps() {
    }

    /**
     * 探测交易与验签所需的内核依赖（IJPay + 开放平台 SDK）。
     */
    public static void requireKernel() {
        requireV3Kernel();
    }

    /**
     * 探测支付宝 V3 内核依赖。
     */
    public static void requireV3Kernel() {
        require(IJPAY_TRADE, IJPAY_GROUP, IJPAY_ARTIFACT, IJPAY_VERSION);
        require(ALIPAY_API_CLIENT, SDK_GROUP, SDK_ARTIFACT, SDK_VERSION);
        require(ALIPAY_SIGNATURE, SDK_GROUP, SDK_ARTIFACT, SDK_VERSION);
    }

    /**
     * 探测支付宝 V2 内核依赖。
     */
    public static void requireV2Kernel() {
        require(IJPAY_TRADE_V2, IJPAY_GROUP, IJPAY_ARTIFACT_V2, IJPAY_VERSION);
        require(ALIPAY_SIGNATURE_V2, SDK_GROUP, SDK_ARTIFACT_V2, SDK_VERSION_V2);
    }

    /**
     * 探测证书模式所需的 BouncyCastle（证书序列号计算与公钥提取都强依赖它）。
     */
    public static void requireBouncyCastle() {
        require(BC_PROVIDER, BC_GROUP, BC_ARTIFACT, BC_VERSION);
    }

    /**
     * 只加载不初始化目标类，避免触发其静态初始化副作用。
     *
     * @param className 目标类全限定名
     * @param groupId   缺失时的坐标提示
     * @param artifactId 缺失时的坐标提示
     * @param version   缺失时的坐标提示
     */
    private static void require(String className, String groupId, String artifactId, String version) {
        try {
            Class.forName(className, false, classLoader());
        } catch (ClassNotFoundException e) {
            throw PayErrors.missingDependency(groupId, artifactId, version, e);
        } catch (NoClassDefFoundError e) {
            throw PayErrors.missingDependency(groupId, artifactId, version, e);
        }
    }

    /**
     * 取探测用的类加载器：优先线程上下文（便于使用方隔离依赖），退回本类加载器。
     *
     * @return 类加载器
     */
    private static ClassLoader classLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader == null ? PayDeps.class.getClassLoader() : loader;
    }
}
