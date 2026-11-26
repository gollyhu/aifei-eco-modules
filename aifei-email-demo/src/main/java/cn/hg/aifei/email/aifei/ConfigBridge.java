/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.aifei;

import cn.aifei.util.Prop;

/**
 * demo 桥接类：与配置解析同包（split package），调用包级配置校验方法，**不使用反射**。
 *
 * <p>仅存在于 demo 源码，不进入库 JAR。{@link #validate(Prop)} 只做全量解析与校验，
 * <b>绝不</b>触发装配入口的启动 / 回滚流程，因此不会污染正在运行的通道注册表。</p>
 */
public final class ConfigBridge {

    private ConfigBridge() {
    }

    /**
     * 对给定配置源执行全量解析与校验（不装配、不改全局状态）。
     *
     * @param prop 配置源
     * @throws cn.hg.aifei.email.MailException 配置非法（消息含键名与原值）
     */
    public static void validate(Prop prop) {
        EmailConfigLoader.load(prop);
    }
}
