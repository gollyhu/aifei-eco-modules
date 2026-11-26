/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import cn.hg.aifei.pay.PayException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * 通道配置解析：从 {@code pay.<name>.type} 推导通道列表，并把 alipay 通道映射为既有应用配置域。
 */
public final class PayChannelConfigLoader {

    private static final String PREFIX = "pay.";
    private static final String TYPE_SUFFIX = ".type";
    private static final String DEFAULT_CHANNEL_KEY = "pay.default-channel";
    private static final String MERCHANT_KEY = "default-merchant-id";
    private static final Set<String> GLOBAL_KEYS = Collections.unmodifiableSet(new TreeSet<String>(
            java.util.Arrays.asList("pay.connect-timeout", "pay.read-timeout",
                    "pay.page-http-method", "pay.notify-url", "pay.return-url")));

    private PayChannelConfigLoader() {
    }

    public static List<ChannelSpec> load(Properties properties) {
        if (properties == null) {
            throw new PayException("支付配置源不能为 null");
        }
        List<ChannelSpec> specs = new ArrayList<ChannelSpec>();
        for (String name : channelNames(properties)) {
            String typeKey = PREFIX + name + TYPE_SUFFIX;
            String type = PayTypes.requireSupported(typeKey, properties.getProperty(typeKey));
            String merchantId = required(properties, PREFIX + name + "." + MERCHANT_KEY);
            for (String required : PayTypes.requiredKeys(type)) {
                if (!MERCHANT_KEY.equals(required)) {
                    required(properties, PREFIX + name + "." + required);
                }
            }
            specs.add(new ChannelSpec(name, type, merchantId));
        }
        if (specs.isEmpty()) {
            throw new PayException("支付配置缺失：pay.<name>.type");
        }
        return Collections.unmodifiableList(specs);
    }

    public static String defaultChannel(Properties properties, List<ChannelSpec> specs) {
        String declared = properties.getProperty(DEFAULT_CHANNEL_KEY);
        if (declared != null && !declared.trim().isEmpty()) {
            for (ChannelSpec spec : specs) {
                if (spec.name().equals(declared.trim())) {
                    return declared.trim();
                }
            }
            throw new PayException("支付默认通道未登记：" + declared);
        }
        if (specs.size() == 1) {
            return specs.get(0).name();
        }
        throw new PayException("支付配置缺失：" + DEFAULT_CHANNEL_KEY);
    }

    public static Properties legacyAlipayProperties(Properties source, ChannelSpec spec) {
        Properties target = new Properties();
        for (String key : GLOBAL_KEYS) {
            String value = source.getProperty(key);
            if (value != null && !value.trim().isEmpty()) {
                target.setProperty(key, value);
            }
        }
        target.setProperty("pay.default-app-id", spec.merchantId());
        String prefix = PREFIX + spec.name() + ".";
        String appPrefix = PayConfig.APP_PREFIX + spec.merchantId() + ".";
        for (String key : source.stringPropertyNames()) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            String suffix = key.substring(prefix.length());
            if (suffix.isEmpty() || TYPE_SUFFIX.substring(1).equals(suffix) || MERCHANT_KEY.equals(suffix)) {
                continue;
            }
            String value = source.getProperty(key);
            if (value != null) {
                target.setProperty(appPrefix + suffix, value);
            }
        }
        return target;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new PayException("支付配置缺失：" + key);
        }
        return value.trim();
    }

    private static Set<String> channelNames(Properties properties) {
        Set<String> names = new TreeSet<String>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(PREFIX) || !key.endsWith(TYPE_SUFFIX)) {
                continue;
            }
            String name = key.substring(PREFIX.length(), key.length() - TYPE_SUFFIX.length());
            if (!name.isEmpty() && name.indexOf('.') < 0 && !name.trim().isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * 单个通道的配置摘要。
     */
    public static final class ChannelSpec {

        private final String name;
        private final String type;
        private final String merchantId;

        ChannelSpec(String name, String type, String merchantId) {
            this.name = name;
            this.type = type;
            this.merchantId = merchantId;
        }

        public String name() {
            return name;
        }

        public String type() {
            return type;
        }

        public String merchantId() {
            return merchantId;
        }
    }
}
