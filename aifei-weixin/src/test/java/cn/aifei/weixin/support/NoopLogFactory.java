/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.support;

import cn.aifei.log.Log;
import cn.aifei.log.LogFactory;

/**
 * 单测用空日志工厂
 */
public class NoopLogFactory implements LogFactory {

    @Override
    public Log getLog() {
        return new NoopLog("noop");
    }

    @Override
    public Log getLog(Class<?> clazz) {
        return new NoopLog(clazz.getName());
    }

    @Override
    public Log getLog(String name) {
        return new NoopLog(name);
    }
}
