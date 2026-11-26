/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.support;

import cn.aifei.util.Prop;

/**
 * 测试配置构造工具：{@link Prop} 不提供键值写入入口，故统一在此按 {@code Properties} 写入，
 * 避免各测试类各自拼写键名与合法配置样板。
 */
public final class TestProps {

    private TestProps() {
    }

    /**
     * @return 空配置源
     */
    public static Prop empty() {
        return new Prop();
    }

    /**
     * 写入单个配置项。
     *
     * @param prop  配置源
     * @param key   键名
     * @param value 值
     */
    public static void set(Prop prop, String key, String value) {
        prop.getProperties().setProperty(key, value);
    }

    /**
     * 删除单个配置项。
     *
     * @param prop 配置源
     * @param key  键名
     */
    public static void remove(Prop prop, String key) {
        prop.getProperties().remove(key);
    }

    /**
     * 追加一条合法通道（SMTP 服务器 / 端口 / 发件人 / 用户名 / 密码）。
     *
     * @param prop   配置源
     * @param name   通道标识
     * @param server SMTP 服务器地址
     * @param port   端口
     * @param from   发件人地址（同时作为用户名）
     */
    public static void appendValidChannel(Prop prop, String name, String server, String port, String from) {
        set(prop, "email." + name + ".smtp-server", server);
        set(prop, "email." + name + ".port", port);
        set(prop, "email." + name + ".from-address", from);
        set(prop, "email." + name + ".username", from);
        set(prop, "email." + name + ".password", "secret");
    }

    /**
     * 单通道配置（缺省默认通道，用于验证「单通道可缺省」）。
     *
     * @param server SMTP 服务器地址
     * @return 配置源
     */
    public static Prop singleChannel(String server) {
        Prop prop = empty();
        appendValidChannel(prop, "qq", server, "465", "a@qq.test");
        set(prop, "email.qq.nick-name", "Aifei");
        return prop;
    }

    /**
     * 双通道配置（默认通道为 {@code qq}；{@code corp} 覆盖 SSL 与重试次数）。
     *
     * @param qqServer   qq 通道的 SMTP 服务器地址
     * @param corpServer corp 通道的 SMTP 服务器地址
     * @return 配置源
     */
    public static Prop twoChannels(String qqServer, String corpServer) {
        Prop prop = empty();
        set(prop, "email.default-channel", "qq");
        appendValidChannel(prop, "qq", qqServer, "465", "a@qq.test");
        set(prop, "email.qq.nick-name", "Aifei");
        appendValidChannel(prop, "corp", corpServer, "587", "b@corp.test");
        set(prop, "email.corp.is-ssl", "false");
        set(prop, "email.corp.max-retries", "3");
        return prop;
    }

    /**
     * 未声明任何通道的空壳配置（可用 {@link #set(Prop, String, String)} 逐步补键）。
     *
     * @return 配置源
     */
    public static Prop channelSkeleton(String name, String server) {
        Prop prop = empty();
        appendValidChannel(prop, name, server, "465", "a@qq.test");
        return prop;
    }
}
