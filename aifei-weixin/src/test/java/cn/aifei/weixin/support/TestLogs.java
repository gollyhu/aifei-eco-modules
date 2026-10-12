/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.support;

import cn.aifei.log.LogKit;

/**
 * 单测日志工厂装配（SDK 源码使用 cn.aifei.log.Log，单测环境需先配置 LogFactory）
 */
public class TestLogs {

    private static boolean installed;

    public static synchronized void install() {
        if (!installed) {
            LogKit.get().setLogFactory(new NoopLogFactory());
            installed = true;
        }
    }

    private TestLogs() {
    }
}
