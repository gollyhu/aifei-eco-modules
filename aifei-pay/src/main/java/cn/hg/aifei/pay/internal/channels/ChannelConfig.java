/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal.channels;

import cn.hg.aifei.pay.PayException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 非支付宝通道的不可变配置快照：装配期一次建成，调用期只读。
 */
public final class ChannelConfig {

    private final String channelName;
    private final String type;
    private final String merchantId;
    private final Map<String, String> values;

    ChannelConfig(String channelName, String type, String merchantId, Map<String, String> values) {
        this.channelName = channelName;
        this.type = type;
        this.merchantId = merchantId;
        this.values = Collections.unmodifiableMap(new LinkedHashMap<String, String>(values));
    }

    public String channelName() {
        return channelName;
    }

    public String type() {
        return type;
    }

    public String merchantId() {
        return merchantId;
    }

    /**
     * 取相对键配置值，缺失或空白返回 {@code null}。
     */
    public String value(String key) {
        String value = values.get(key);
        return value == null || value.trim().isEmpty() ? null : value;
    }

    /**
     * 取配置值，缺失时回退默认值。
     */
    public String value(String key, String defaultValue) {
        String value = value(key);
        return value == null ? defaultValue : value;
    }

    /**
     * 取必填配置值。
     *
     * @throws PayException 键缺失或空白
     */
    public String require(String key) {
        String value = value(key);
        if (value == null) {
            throw new PayException("支付配置缺失：pay." + channelName + "." + key);
        }
        return value;
    }
}
