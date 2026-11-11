/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.demo.console;

import cn.aifei.log.Log;
import cn.aifei.log.LogFactory;

/**
 * ConsoleLogFactory：demo 自带的极简 LogFactory 实现（{@code System.out} 输出）。
 *
 * <p>定位：在 {@code DemoAppConfig} 的 {@code settings.setLogFactory(new ConsoleLogFactory())}
 * 装配语句中生效（与 {@code aifei-trace-demo} 同款）。aifei 内核默认的 {@code NoLogFactory}
 * 每次取日志即抛，必须给一个真实实现；demo 要求零日志绑定，故用 {@code System.out} 落地。
 * {@code IdPlugin#start()} 打印的「生效策略 + workerId」INFO 即经本工厂输出。</p>
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
