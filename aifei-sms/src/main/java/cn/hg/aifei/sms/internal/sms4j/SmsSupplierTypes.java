/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.internal.sms4j;

import cn.hg.aifei.sms.internal.SmsErrors;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.dromara.sms4j.aliyun.config.AlibabaConfig;
import org.dromara.sms4j.api.universal.SupplierConfig;

/**
 * 厂商类型映射唯一定义点：合法值集合、必填键集合、
 * 配置项到 sms4j 配置对象 setter 的填充全部集中在本类。
 *
 * <p>首期白名单仅 {@code alibaba}；其余厂商按通道追加任务逐个登记（加映射 + 必填键 + 单测）。</p>
 */
public final class SmsSupplierTypes {

    private static final String ALIBABA = "alibaba";

    private static final Map<String, Set<String>> REQUIRED_KEYS;

    static {
        Map<String, Set<String>> required = new LinkedHashMap<String, Set<String>>();
        required.put(ALIBABA, Collections.unmodifiableSet(new LinkedHashSet<String>(
                Arrays.asList("access-key", "secret-key", "signature"))));
        REQUIRED_KEYS = Collections.unmodifiableMap(required);
    }

    private SmsSupplierTypes() {
    }

    /**
     * @return 本期支持的厂商类型集合（不可变）
     */
    public static Set<String> supported() {
        return REQUIRED_KEYS.keySet();
    }

    /**
     * @param type 厂商类型字面量
     * @return 是否支持
     */
    public static boolean isSupported(String type) {
        return type != null && REQUIRED_KEYS.containsKey(type);
    }

    /**
     * 取某厂商类型的必填配置键。
     *
     * @param type 厂商类型字面量
     * @return 必填键集合（不可变）；未知类型为空集合
     */
    public static Set<String> requiredKeys(String type) {
        Set<String> keys = type == null ? null : REQUIRED_KEYS.get(type);
        return keys == null ? Collections.<String>emptySet() : keys;
    }

    /**
     * 构建 sms4j 配置对象：按厂商类型实例化配置类并填充 setter，未知配置项忽略（便于透传新参数）。
     *
     * @param type    厂商类型字面量
     * @param channel 通道标识（作为 configId）
     * @param options 原始 kebab-case 配置项
     * @return sms4j 配置对象
     * @throws cn.hg.aifei.sms.SmsException 厂商类型未知
     */
    public static SupplierConfig newSupplierConfig(String type, String channel, Map<String, String> options) {
        if (!isSupported(type)) {
            throw SmsErrors.unknownChannelType("type", type);
        }
        if (ALIBABA.equals(type)) {
            return newAlibabaConfig(channel, options);
        }
        throw SmsErrors.unknownChannelType("type", type);
    }

    private static SupplierConfig newAlibabaConfig(String channel, Map<String, String> options) {
        AlibabaConfig config = new AlibabaConfig();
        config.setConfigId(channel);
        if (options == null) {
            return config;
        }
        for (Map.Entry<String, String> entry : options.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            if ("access-key".equals(key)) {
                config.setAccessKeyId(value);
            } else if ("secret-key".equals(key)) {
                config.setAccessKeySecret(value);
            } else if ("signature".equals(key)) {
                config.setSignature(value);
            } else if ("template-id".equals(key)) {
                config.setTemplateId(value);
            } else if ("region-id".equals(key)) {
                config.setRegionId(value);
            } else if ("endpoint".equals(key)) {
                config.setRequestUrl(value);
            } else if ("weight".equals(key)) {
                config.setWeight(Integer.valueOf(value.trim()));
            } else if ("max-retries".equals(key)) {
                config.setMaxRetries(Integer.parseInt(value.trim()));
            } else if ("retry-interval".equals(key)) {
                config.setRetryInterval(Integer.parseInt(value.trim()));
            } else if ("timeout".equals(key)) {
                config.setTimeout(Integer.parseInt(value.trim()));
            }
            // access-key-id / access-key-secret 为验签专用键，不填入发送配置；其它未知项忽略。
        }
        return config;
    }
}
