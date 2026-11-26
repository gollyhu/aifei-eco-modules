/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import cn.hg.aifei.pay.PayException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * IJPay 渠道类型白名单：合法值、必填键与适配器落地状态的唯一定义点。
 */
public final class PayTypes {

    public static final String ALIPAY2 = "alipay2";
    public static final String ALIPAY3 = "alipay3";
    public static final String WXPAY = "wxpay";
    public static final String QQ = "qq";
    public static final String JDPAY = "jdpay";
    public static final String UNIONPAY = "unionpay";
    public static final String PAYPAL = "paypal";
    public static final String STRIPE = "stripe";
    public static final String FUIOU = "fuiou";
    public static final String XPAY = "xpay";

    private static final Map<String, Set<String>> REQUIRED_KEYS;
    private static final Set<String> IMPLEMENTED;

    static {
        Map<String, Set<String>> required = new LinkedHashMap<String, Set<String>>();
        Set<String> alipayKeys = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
                "default-merchant-id", "env", "private-key")));
        required.put(ALIPAY2, alipayKeys);
        required.put(ALIPAY3, alipayKeys);
        required.put(WXPAY, Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
                "default-merchant-id"))));
        required.put(QQ, keys("default-merchant-id", "app-id", "partner-key"));
        required.put(JDPAY, keys("default-merchant-id", "version", "rsa-private-key", "rsa-public-key", "des-key"));
        required.put(UNIONPAY, keys("default-merchant-id", "gateway-url", "api-key"));
        required.put(PAYPAL, keys("default-merchant-id", "client-id", "secret"));
        required.put(STRIPE, keys("default-merchant-id", "api-key"));
        required.put(FUIOU, keys("default-merchant-id", "mchnt-key"));
        required.put(XPAY, keys("default-merchant-id", "app-id", "api-key", "server-url"));
        REQUIRED_KEYS = Collections.unmodifiableMap(required);
        IMPLEMENTED = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(ALIPAY2, ALIPAY3,
                QQ, JDPAY, UNIONPAY, PAYPAL, STRIPE, FUIOU, XPAY)));
    }

    private PayTypes() {
    }

    private static Set<String> keys(String... values) {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(values)));
    }

    public static Set<String> supported() {
        return REQUIRED_KEYS.keySet();
    }

    public static Set<String> implemented() {
        return IMPLEMENTED;
    }

    public static boolean isSupported(String type) {
        return type != null && REQUIRED_KEYS.containsKey(type.trim().toLowerCase());
    }

    public static boolean isImplemented(String type) {
        return type != null && IMPLEMENTED.contains(type.trim().toLowerCase());
    }

    public static Set<String> requiredKeys(String type) {
        Set<String> keys = type == null ? null : REQUIRED_KEYS.get(type.trim().toLowerCase());
        return keys == null ? Collections.<String>emptySet() : keys;
    }

    public static String requireSupported(String key, String type) {
        if (!isSupported(type)) {
            throw new PayException("支付配置非法：" + key + "=" + type + "，合法类型为 " + supported());
        }
        return type.trim().toLowerCase();
    }
}
