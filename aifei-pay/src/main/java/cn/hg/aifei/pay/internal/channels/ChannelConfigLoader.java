/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal.channels;

import cn.hg.aifei.pay.internal.PayResource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 通道配置快照构建：把 {@code pay.<name>.*} 收拢为相对键映射，凭据统一展开 {@code @file:}。
 */
public final class ChannelConfigLoader {

    private ChannelConfigLoader() {
    }

    public static ChannelConfig load(Properties properties, String channelName, String type, String merchantId) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        String prefix = "pay." + channelName + ".";
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            String suffix = key.substring(prefix.length());
            if ("type".equals(suffix) || "default-merchant-id".equals(suffix) || suffix.isEmpty()) {
                continue;
            }
            values.put(suffix, PayResource.read(key, properties.getProperty(key)));
        }
        fallback(values, "notify-url", properties.getProperty("pay.notify-url"));
        fallback(values, "return-url", properties.getProperty("pay.return-url"));
        return new ChannelConfig(channelName, type, merchantId, values);
    }

    private static void fallback(Map<String, String> values, String key, String global) {
        if ((!values.containsKey(key) || values.get(key).trim().isEmpty()) && global != null
                && !global.trim().isEmpty()) {
            values.put(key, global.trim());
        }
    }
}
