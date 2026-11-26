/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.demo.console;

import cn.aifei.log.Log;
import cn.aifei.log.LogFactory;

/** demo 自带 LogFactory：返回 {@link ConsoleLog}，替代内核默认 NoLogFactory。 */
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
