/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.aifei;

import cn.aifei.util.Prop;
import cn.hg.aifei.email.internal.MailChannelConfig;
import cn.hg.aifei.email.internal.MailErrors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * 邮件配置键名解析与校验的唯一定义点（设计 §6，门禁锁定配置前缀只出现在本类）。
 *
 * <p>通道列表由「通道名 + SMTP 服务器」键推导；必填项、整数项与默认通道规则均在装配期校验，
 * 错误消息含完整键名与原值。本类属接入层非承诺构件，方法与返回值按包内可见收敛，仅由装配入口调用，
 * 不作为公共 API 暴露。</p>
 */
public final class EmailConfigLoader {

    private static final String PREFIX = "email.";
    private static final String DEFAULT_CHANNEL_KEY = "email.default-channel";
    private static final String RESERVED_CHANNEL = "default-channel";
    private static final String SMTP_SERVER_KEY = "smtp-server";
    private static final String IS_SSL_KEY = "is-ssl";
    private static final String IS_AUTH_KEY = "is-auth";
    private static final String RETRY_INTERVAL_KEY = "retry-interval";
    private static final String MAX_RETRIES_KEY = "max-retries";
    private static final String DEFAULT_TRUE = "true";
    private static final String DEFAULT_RETRY_INTERVAL = "5";
    private static final String DEFAULT_MAX_RETRIES = "0";
    private static final String CHANNEL_PLACEHOLDER = "email.<name>." + SMTP_SERVER_KEY;

    private static final String[] REQUIRED_KEYS = {
        SMTP_SERVER_KEY, "port", "from-address", "username", "password"
    };
    private static final String[] INTEGER_KEYS = {RETRY_INTERVAL_KEY, MAX_RETRIES_KEY};

    private EmailConfigLoader() {
    }

    /**
     * 全量解析并校验所有通道配置。
     *
     * @param prop 配置源
     * @return 全部通道配置（不可变，按通道标识字典序；选项已填充默认值）
     * @throws cn.hg.aifei.email.MailException 配置源为空、未声明任何通道、必填项缺失、整数项格式非法
     */
    static List<MailChannelConfig> load(Prop prop) {
        requireProp(prop);
        Properties properties = prop.getProperties();
        List<MailChannelConfig> configs = new ArrayList<MailChannelConfig>();
        for (String name : channelNames(properties)) {
            validateRequired(properties, name);
            validateIntegers(properties, name);
            configs.add(new MailChannelConfig(name, optionsOf(properties, name)));
        }
        if (configs.isEmpty()) {
            throw MailErrors.requiredConfig(CHANNEL_PLACEHOLDER, null);
        }
        return Collections.unmodifiableList(configs);
    }

    /**
     * 解析默认通道标识：单通道可缺省取唯一通道；多通道必须显式声明且指向已声明通道。
     *
     * @param prop    配置源
     * @param configs 已解析的通道配置
     * @return 默认通道标识
     * @throws cn.hg.aifei.email.MailException 无通道、多通道缺默认通道、默认通道指向未知通道
     */
    static String defaultChannel(Prop prop, List<MailChannelConfig> configs) {
        requireProp(prop);
        if (configs == null || configs.isEmpty()) {
            throw MailErrors.requiredConfig(CHANNEL_PLACEHOLDER, null);
        }
        String declared = prop.get(DEFAULT_CHANNEL_KEY);
        if (declared == null || declared.trim().isEmpty()) {
            if (configs.size() == 1) {
                return configs.get(0).getName();
            }
            throw MailErrors.invalidDefaultChannel(declared);
        }
        Set<String> names = new TreeSet<String>();
        for (MailChannelConfig config : configs) {
            names.add(config.getName());
        }
        if (!names.contains(declared.trim())) {
            throw MailErrors.invalidDefaultChannel(declared);
        }
        return declared.trim();
    }

    private static void requireProp(Prop prop) {
        if (prop == null) {
            throw MailErrors.propNull();
        }
    }

    private static Set<String> channelNames(Properties properties) {
        String suffix = "." + SMTP_SERVER_KEY;
        Set<String> names = new TreeSet<String>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(PREFIX) || !key.endsWith(suffix)) {
                continue;
            }
            String name = key.substring(PREFIX.length(), key.length() - suffix.length());
            // 通道标识不得为空白、不得含 '.'（键归属会歧义）、不得占用默认通道键的保留字
            if (name.trim().isEmpty() || name.indexOf('.') >= 0 || RESERVED_CHANNEL.equals(name)) {
                continue;
            }
            names.add(name);
        }
        return names;
    }

    private static void validateRequired(Properties properties, String name) {
        for (String required : REQUIRED_KEYS) {
            String fullKey = PREFIX + name + "." + required;
            String value = properties.getProperty(fullKey);
            if (value == null || value.trim().isEmpty()) {
                throw MailErrors.requiredConfig(fullKey, value);
            }
        }
    }

    private static void validateIntegers(Properties properties, String name) {
        for (String key : INTEGER_KEYS) {
            String fullKey = PREFIX + name + "." + key;
            String value = properties.getProperty(fullKey);
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            try {
                Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                throw MailErrors.invalidInteger(fullKey, value);
            }
        }
    }

    private static Map<String, String> optionsOf(Properties properties, String name) {
        String prefix = PREFIX + name + ".";
        Map<String, String> options = new LinkedHashMap<String, String>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            String option = key.substring(prefix.length());
            if (option.isEmpty() || option.indexOf('.') >= 0) {
                continue;
            }
            options.put(option, properties.getProperty(key));
        }
        putDefault(options, IS_SSL_KEY, DEFAULT_TRUE);
        putDefault(options, IS_AUTH_KEY, DEFAULT_TRUE);
        putDefault(options, RETRY_INTERVAL_KEY, DEFAULT_RETRY_INTERVAL);
        putDefault(options, MAX_RETRIES_KEY, DEFAULT_MAX_RETRIES);
        return options;
    }

    private static void putDefault(Map<String, String> options, String key, String value) {
        String current = options.get(key);
        if (current == null || current.trim().isEmpty()) {
            options.put(key, value);
        }
    }
}
