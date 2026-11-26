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

    public static final String ALIPAY = "alipay";
    public static final String WXPAY = "wxpay";

    private static final Map<String, Set<String>> REQUIRED_KEYS;
    private static final Set<String> IMPLEMENTED;

    static {
        Map<String, Set<String>> required = new LinkedHashMap<String, Set<String>>();
        required.put(ALIPAY, Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
                "default-merchant-id", "env", "private-key"))));
        required.put(WXPAY, Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
                "default-merchant-id"))));
        String[] rest = {"qq", "jdpay", "unionpay", "paypal", "stripe", "fuiou", "xpay"};
        for (String type : rest) {
            required.put(type, Collections.unmodifiableSet(new LinkedHashSet<String>(
                    Arrays.asList("default-merchant-id"))));
        }
        REQUIRED_KEYS = Collections.unmodifiableMap(required);
        IMPLEMENTED = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(ALIPAY, WXPAY)));
    }

    private PayTypes() {
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
