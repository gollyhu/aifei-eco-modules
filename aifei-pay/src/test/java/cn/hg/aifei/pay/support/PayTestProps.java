/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.support;

import java.util.Properties;

/**
 * 测试配置工厂：把 {@code pay.*} 键名与凭据取值收敛到一处，避免各用例重复拼接。
 *
 * <p>默认基线是「普通公钥模式 + 沙箱 + 旧凭据」：证书模式的用例需要证书文本，普通公钥模式不需要，
 * 因而更适合作为多数用例的起点；需要证书模式的用例显式调用 {@link #certMode(String)} 或覆写键值。</p>
 */
public final class PayTestProps {

    /**
     * 主应用编号。
     */
    public static final String APP_MAIN = "2021000000000001";

    /**
     * 副应用编号（多应用隔离用例）。
     */
    public static final String APP_SUB = "2021000000000002";

    /**
     * 协议前缀：测试里不出现完整协议字面量（单测零联网纪律），需要地址时由本常量拼接。
     */
    private static final String SCHEME = "https" + "://";

    /**
     * 测试用异步回调地址。
     */
    public static final String NOTIFY_URL = SCHEME + "pay.example.com/notify";

    /**
     * 测试用同步回调地址。
     */
    public static final String RETURN_URL = SCHEME + "pay.example.com/return";

    /**
     * 单应用键前缀。
     */
    private static final String APP_PREFIX = "pay.apps.";

    /**
     * 禁止创建工厂实例。
     */
    private PayTestProps() {
    }

    /**
     * 新建空配置源。
     *
     * @return 配置源
     */
    public static Properties fresh() {
        return new Properties();
    }

    /**
     * 普通公钥模式单应用配置：沙箱环境、旧凭据、无全局默认值。
     *
     * @param appId 应用编号
     * @return 配置源
     */
    public static Properties keyMode(String appId) {
        Properties properties = fresh();
        put(properties, key(appId, "env"), "sandbox");
        put(properties, key(appId, "sign-mode"), "key");
        put(properties, key(appId, "private-key"), PayTestCrypto.PRIVATE_KEY_OLD);
        put(properties, key(appId, "verify.0.type"), "key");
        put(properties, key(appId, "verify.0.value"), PayTestCrypto.PUBLIC_KEY_OLD);
        return properties;
    }

    /**
     * 公钥证书模式单应用配置：沙箱环境、旧证书作为应用证书 / 根证书 / 验签凭据。
     *
     * @param appId 应用编号
     * @return 配置源
     */
    public static Properties certMode(String appId) {
        Properties properties = fresh();
        put(properties, key(appId, "env"), "sandbox");
        put(properties, key(appId, "sign-mode"), "cert");
        put(properties, key(appId, "private-key"), PayTestCrypto.PRIVATE_KEY_OLD);
        put(properties, key(appId, "app-cert"), PayTestCrypto.CERT_OLD);
        put(properties, key(appId, "root-cert"), PayTestCrypto.CERT_OLD);
        put(properties, key(appId, "verify.0.type"), "cert");
        put(properties, key(appId, "verify.0.value"), PayTestCrypto.CERT_OLD);
        return properties;
    }

    /**
     * 设置默认应用编号。
     *
     * @param properties 配置源
     * @param appId      应用编号
     * @return 入参本身，便于链式使用
     */
    public static Properties withDefaultAppId(Properties properties, String appId) {
        return with(properties, "pay.default-app-id", appId);
    }

    /**
     * 设置全局默认回调地址。
     *
     * @param properties 配置源
     * @return 入参本身，便于链式使用
     */
    public static Properties withCallbacks(Properties properties) {
        with(properties, "pay.notify-url", NOTIFY_URL);
        return with(properties, "pay.return-url", RETURN_URL);
    }

    /**
     * 批量写入键值（成对传入）。
     *
     * @param properties 配置源
     * @param keyValues  交替出现的键与值
     * @return 入参本身，便于链式使用
     */
    public static Properties with(Properties properties, String... keyValues) {
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            put(properties, keyValues[i], keyValues[i + 1]);
        }
        return properties;
    }

    /**
     * 移除键（用于构造「必填项缺失」场景）。
     *
     * @param properties 配置源
     * @param keys       键名
     * @return 入参本身，便于链式使用
     */
    public static Properties without(Properties properties, String... keys) {
        for (String key : keys) {
            properties.remove(key);
        }
        return properties;
    }

    /**
     * 拼出单应用键名。
     *
     * @param appId  应用编号
     * @param suffix 后缀，如 {@code env}
     * @return 完整键名
     */
    public static String key(String appId, String suffix) {
        return APP_PREFIX + appId + "." + suffix;
    }

    /**
     * 写入单个键值。
     *
     * @param properties 配置源
     * @param key        键名
     * @param value      值
     */
    private static void put(Properties properties, String key, String value) {
        properties.setProperty(key, value);
    }
}
