/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单个 appId 的不可变配置快照：装配期一次性建成，调用期只读。
 *
 * <p>凭据字段存的是**读取后的文本内容**（{@code @file:} 引用已在解析期展开），不再持有路径。</p>
 */
public final class PayAppConfig {

    /**
     * 签名方式配置值：公钥证书模式。
     */
    static final String SIGN_MODE_CERT = "cert";

    /**
     * 签名方式配置值：普通公钥模式。
     */
    static final String SIGN_MODE_KEY = "key";

    private final String appId;

    private final String env;

    private final String gatewayUrl;

    private final String signMode;

    private final String privateKey;

    private final String appCert;

    private final String rootCert;

    private final String charset;

    private final String signType;

    private final int connectTimeout;

    private final int readTimeout;

    private final String pageHttpMethod;

    private final String notifyUrl;

    private final String returnUrl;

    private final List<PayVerifyKey> verifyKeys;

    /**
     * 由配置解析器构造（唯一构造入口）。
     *
     * @param appId          应用编号
     * @param env            环境标签
     * @param gatewayUrl     网关地址
     * @param signMode       签名方式
     * @param privateKey     应用私钥内容
     * @param appCert        应用公钥证书内容，{@code key} 模式下为 {@code null}
     * @param rootCert       支付宝根证书内容，{@code key} 模式下为 {@code null}
     * @param charset        字符集
     * @param signType       签名类型
     * @param connectTimeout 连接超时（毫秒）
     * @param readTimeout    读取超时（毫秒）
     * @param pageHttpMethod 页面类下单的 HTTP 方法
     * @param notifyUrl      全局默认异步回调地址，可为 {@code null}
     * @param returnUrl      全局默认同步回调地址，可为 {@code null}
     * @param verifyKeys     有序验签凭据列表
     */
    PayAppConfig(String appId, String env, String gatewayUrl, String signMode, String privateKey, String appCert,
                 String rootCert, String charset, String signType, int connectTimeout, int readTimeout,
                 String pageHttpMethod, String notifyUrl, String returnUrl, List<PayVerifyKey> verifyKeys) {
        this.appId = require(appId, "appId");
        this.env = require(env, "env");
        this.gatewayUrl = require(gatewayUrl, "gatewayUrl");
        this.signMode = require(signMode, "signMode");
        this.privateKey = require(privateKey, "privateKey");
        this.appCert = appCert;
        this.rootCert = rootCert;
        this.charset = require(charset, "charset");
        this.signType = require(signType, "signType");
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.pageHttpMethod = require(pageHttpMethod, "pageHttpMethod");
        this.notifyUrl = notifyUrl;
        this.returnUrl = returnUrl;
        if (verifyKeys == null || verifyKeys.isEmpty()) {
            throw new IllegalArgumentException("verifyKeys 不能为空");
        }
        this.verifyKeys = Collections.unmodifiableList(new ArrayList<>(verifyKeys));
    }

    /**
     * @return 应用编号
     */
    public String appId() {
        return appId;
    }

    /**
     * @return 环境标签 {@code sandbox} / {@code production}
     */
    public String env() {
        return env;
    }

    /**
     * @return 网关地址
     */
    public String gatewayUrl() {
        return gatewayUrl;
    }

    /**
     * @return 签名方式 {@code cert} / {@code key}
     */
    public String signMode() {
        return signMode;
    }

    /**
     * @return 是否为公钥证书模式
     */
    public boolean isCertMode() {
        return SIGN_MODE_CERT.equals(signMode);
    }

    /**
     * @return 应用私钥内容
     */
    public String privateKey() {
        return privateKey;
    }

    /**
     * @return 应用公钥证书内容；{@code key} 模式下为 {@code null}
     */
    public String appCert() {
        return appCert;
    }

    /**
     * @return 支付宝根证书内容；{@code key} 模式下为 {@code null}
     */
    public String rootCert() {
        return rootCert;
    }

    /**
     * @return 字符集
     */
    public String charset() {
        return charset;
    }

    /**
     * @return 签名类型
     */
    public String signType() {
        return signType;
    }

    /**
     * @return 连接超时（毫秒）
     */
    public int connectTimeout() {
        return connectTimeout;
    }

    /**
     * @return 读取超时（毫秒）
     */
    public int readTimeout() {
        return readTimeout;
    }

    /**
     * @return 页面类下单的 HTTP 方法
     */
    public String pageHttpMethod() {
        return pageHttpMethod;
    }

    /**
     * @return 全局默认异步回调地址，可为 {@code null}
     */
    public String notifyUrl() {
        return notifyUrl;
    }

    /**
     * @return 全局默认同步回调地址，可为 {@code null}
     */
    public String returnUrl() {
        return returnUrl;
    }

    /**
     * @return 有序验签凭据列表（轮换期新旧并存）
     */
    public List<PayVerifyKey> verifyKeys() {
        return verifyKeys;
    }

    /**
     * 校验必填字段非空。
     *
     * @param value 字段值
     * @param name  字段名
     * @return 原值
     */
    private static String require(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value;
    }
}
