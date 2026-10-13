/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.satoken.internal;

import cn.aifei.util.Prop;
import cn.dev33.satoken.config.SaTokenConfig;

import java.util.function.Consumer;

/**
 * Prop 到 {@link SaTokenConfig} 映射的唯一定义点。
 */
public final class SaTokenConfigLoader {

    /** 配置键前缀，同时作为配置域隔离标识 */
    private static final String PREFIX = "sa-token.";

    /**
     * 从配置源构建 Sa-Token 配置对象。
     *
     * @param prop 配置源，不能为空
     * @return 构建完成的配置对象
     * @throws IllegalArgumentException prop 为空，或某项取值格式非法
     */
    public static SaTokenConfig load(Prop prop) {
        if (prop == null) {
            throw new IllegalArgumentException("prop can not be null.");
        }

        SaTokenConfig config = new SaTokenConfig();
        setString(prop, "token-name", config::setTokenName);
        setLong(prop, "timeout", config::setTimeout);
        setLong(prop, "active-timeout", config::setActiveTimeout);
        setBoolean(prop, "dynamic-active-timeout", config::setDynamicActiveTimeout);
        setBoolean(prop, "is-concurrent", config::setIsConcurrent);
        setBoolean(prop, "is-share", config::setIsShare);
        setInt(prop, "max-login-count", config::setMaxLoginCount);
        setInt(prop, "max-try-times", config::setMaxTryTimes);
        setBoolean(prop, "is-read-body", config::setIsReadBody);
        setBoolean(prop, "is-read-header", config::setIsReadHeader);
        setBoolean(prop, "is-read-cookie", config::setIsReadCookie);
        setBoolean(prop, "is-lasting-cookie", config::setIsLastingCookie);
        setBoolean(prop, "is-write-header", config::setIsWriteHeader);
        setString(prop, "token-style", config::setTokenStyle);
        setString(prop, "token-prefix", config::setTokenPrefix);
        setInt(prop, "data-refresh-period", config::setDataRefreshPeriod);
        setBoolean(prop, "auto-renew", config::setAutoRenew);
        setBoolean(prop, "is-print", config::setIsPrint);
        setBoolean(prop, "is-log", config::setIsLog);
        setString(prop, "log-level", config::setLogLevel);
        setString(prop, "curr-domain", config::setCurrDomain);
        return config;
    }

    /** Prop.get 已保证去掉首尾空白、空值返回 null，故 null 即表示保持 Sa-Token 默认值 */
    private static void setString(Prop prop, String key, Consumer<String> setter) {
        String value = prop.get(PREFIX + key);
        if (value != null) {
            setter.accept(value);
        }
    }

    private static void setLong(Prop prop, String key, Consumer<Long> setter) {
        String value = prop.get(PREFIX + key);
        if (value != null) {
            setter.accept(parseLong(key, value));
        }
    }

    private static void setInt(Prop prop, String key, Consumer<Integer> setter) {
        String value = prop.get(PREFIX + key);
        if (value != null) {
            setter.accept(parseInt(key, value));
        }
    }

    private static void setBoolean(Prop prop, String key, Consumer<Boolean> setter) {
        String value = prop.get(PREFIX + key);
        if (value != null) {
            setter.accept(parseBoolean(key, value));
        }
    }

    private static long parseLong(String key, String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(configError(key, value, "整数"), e);
        }
    }

    private static int parseInt(String key, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(configError(key, value, "整数"), e);
        }
    }

    private static boolean parseBoolean(String key, String value) {
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        throw new IllegalArgumentException(configError(key, value, "true / false"));
    }

    private static String configError(String key, String value, String expected) {
        return "配置项 " + PREFIX + key + " 的取值非法：" + value + "（应为" + expected + "）";
    }

    private SaTokenConfigLoader() {}
}
