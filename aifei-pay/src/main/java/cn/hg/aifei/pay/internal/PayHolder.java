/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import cn.hg.aifei.pay.AliPayClient;

/**
 * 运行期实例持有：装配期写入、停止期清除，公共门面据此取当前可用的客户端与验签器。
 *
 * <p>未装配或已停止时取用一律失败（消息固定「支付宝客户端未装配或已停止」），不静默返回
 * {@code null}。写入、取用与清除共用同一把锁，避免装配与调用交错时读到半成品。</p>
 */
public final class PayHolder {

    /**
     * 写入、取用与清除共用的锁。
     */
    private static final Object LOCK = new Object();

    /**
     * 当前已装配的客户端，未装配时为 {@code null}。
     */
    private static AliPayClient client;

    /**
     * 当前已装配的验签器，未装配时为 {@code null}。
     */
    private static PayVerifier verifier;

    /**
     * 禁止创建工具类实例。
     */
    private PayHolder() {
    }

    /**
     * 写入当前实例，覆盖既有值。
     *
     * @param aliPayClient 已装配的客户端
     * @param payVerifier  已装配的验签器
     * @throws IllegalArgumentException 任一参数为 {@code null}
     */
    public static void install(AliPayClient aliPayClient, PayVerifier payVerifier) {
        if (aliPayClient == null) {
            throw new IllegalArgumentException("client 不能为 null");
        }
        if (payVerifier == null) {
            throw new IllegalArgumentException("verifier 不能为 null");
        }
        synchronized (LOCK) {
            client = aliPayClient;
            verifier = payVerifier;
        }
    }

    /**
     * 取当前客户端。
     *
     * @return 已装配的客户端，与 {@code @Inject AliPayClient} 注入的是同一个对象
     * @throws cn.hg.aifei.pay.AliPayException 未装配或已停止
     */
    public static AliPayClient client() {
        synchronized (LOCK) {
            if (client == null) {
                throw PayErrors.notInstalled();
            }
            return client;
        }
    }

    /**
     * 取当前验签器。
     *
     * @return 已装配的验签器
     * @throws cn.hg.aifei.pay.AliPayException 未装配或已停止
     */
    public static PayVerifier verifier() {
        synchronized (LOCK) {
            if (verifier == null) {
                throw PayErrors.notInstalled();
            }
            return verifier;
        }
    }

    /**
     * 清除当前实例；清除后取用立即失败。
     */
    public static void clear() {
        synchronized (LOCK) {
            client = null;
            verifier = null;
        }
    }
}
