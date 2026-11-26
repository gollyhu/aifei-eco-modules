/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.aifei;

import cn.aifei.util.Prop;
import cn.hg.aifei.sms.internal.SmsChannelConfig;
import cn.hg.aifei.sms.internal.SmsErrors;
import cn.hg.aifei.sms.internal.sms4j.SmsSupplierTypes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * {@code sms.*} 配置键名解析与校验的唯一定义点（设计 §6.3 / 门禁 ⑬）。
 *
 * <p>通道列表由 {@code sms.<name>.type} 键推导；类型合法性、必填项与默认通道规则均在装配期校验，
 * 错误消息含完整键名与原值。本类属接入层非承诺构件，仅由装配入口调用。</p>
 */
public final class SmsConfigLoader {

    private static final String PREFIX = "sms.";
    private static final String TYPE_SUFFIX = ".type";
    private static final String DEFAULT_CHANNEL_KEY = "sms.default-channel";
    private static final String TYPE_KEY = "type";
    private static final String CHANNEL_PLACEHOLDER = "sms.<name>.type";

    private SmsConfigLoader() {
    }

    /**
     * 全量解析并校验所有通道配置。
     *
     * @param prop 配置源
     * @return 全部通道配置（不可变，按通道标识字典序）
     * @throws cn.hg.aifei.sms.SmsException 配置源为空 / 无通道 / 类型未知 / 必填项缺失
     */
    public static List<SmsChannelConfig> load(Prop prop) {
        requireProp(prop);
        Properties properties = prop.getProperties();
        List<SmsChannelConfig> configs = new ArrayList<SmsChannelConfig>();
        for (String name : channelNames(properties)) {
            String typeKey = PREFIX + name + TYPE_SUFFIX;
            String type = properties.getProperty(typeKey);
            if (type == null || type.trim().isEmpty()) {
                throw SmsErrors.requiredConfig(typeKey, type);
            }
            if (!SmsSupplierTypes.isSupported(type)) {
                throw SmsErrors.unknownChannelType(typeKey, type);
            }
            for (String required : SmsSupplierTypes.requiredKeys(type)) {
                String fullKey = PREFIX + name + "." + required;
                String value = properties.getProperty(fullKey);
                if (value == null || value.trim().isEmpty()) {
                    throw SmsErrors.requiredConfig(fullKey, value);
                }
            }
            configs.add(new SmsChannelConfig(name, type, optionsOf(properties, name)));
        }
        if (configs.isEmpty()) {
            throw SmsErrors.requiredConfig(CHANNEL_PLACEHOLDER, null);
        }
        return Collections.unmodifiableList(configs);
    }

    /**
     * 解析默认通道标识：单通道可缺省取唯一通道；多通道必须显式声明且指向已声明通道。
     *
     * @param prop    配置源
     * @param configs 已解析的通道配置
     * @return 默认通道标识
     * @throws cn.hg.aifei.sms.SmsException 无通道 / 多通道缺默认通道 / 默认通道指向未知通道
     */
    public static String defaultChannel(Prop prop, List<SmsChannelConfig> configs) {
        requireProp(prop);
        if (configs == null || configs.isEmpty()) {
            throw SmsErrors.requiredConfig(CHANNEL_PLACEHOLDER, null);
        }
        String declared = prop.get(DEFAULT_CHANNEL_KEY);
        if (declared == null || declared.trim().isEmpty()) {
            if (configs.size() == 1) {
                return configs.get(0).getName();
            }
            throw SmsErrors.invalidDefaultChannel(declared);
        }
        Set<String> names = new TreeSet<String>();
        for (SmsChannelConfig config : configs) {
            names.add(config.getName());
        }
        if (!names.contains(declared)) {
            throw SmsErrors.invalidDefaultChannel(declared);
        }
        return declared;
    }

    private static void requireProp(Prop prop) {
        if (prop == null) {
            throw SmsErrors.propNull();
        }
    }

    private static Set<String> channelNames(Properties properties) {
        Set<String> names = new TreeSet<String>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(PREFIX) || !key.endsWith(TYPE_SUFFIX)) {
                continue;
            }
            String name = key.substring(PREFIX.length(), key.length() - TYPE_SUFFIX.length());
            // 通道标识含 '.' 会造成键归属歧义，跳过此类键（如 sms.a.b.type）。
            if (!name.isEmpty() && name.indexOf('.') < 0 && !name.trim().isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    private static Map<String, String> optionsOf(Properties properties, String name) {
        String prefix = PREFIX + name + ".";
        Map<String, String> options = new LinkedHashMap<String, String>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            String option = key.substring(prefix.length());
            if (option.isEmpty() || TYPE_KEY.equals(option) || option.indexOf('.') >= 0) {
                continue;
            }
            options.put(option, properties.getProperty(key));
        }
        return options;
    }
}
