/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.demo.console;

import cn.aifei.log.Log;
import cn.aifei.log.LogFactory;

/**
 * ConsoleLogFactory：demo 自带的极简 LogFactory 实现（System.out 输出）。
 *
 * <p>aifei 内核默认 NoLogFactory 每次取日志即抛，必须给一个真实实现；demo 要求零日志绑定。</p>
 */
public class ConsoleLogFactory implements LogFactory {

    @Override
    public Log getLog() {
        return new ConsoleLog("anonymous");
    }

    @Override
    public Log getLog(Class<?> clazz) {
        return new ConsoleLog(clazz.getName());
    }

    @Override
    public Log getLog(String name) {
        return new ConsoleLog(name);
    }
}
