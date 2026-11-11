/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.trace.demo.console;

import cn.aifei.log.Log;
import cn.aifei.log.LogFactory;

/**
 * ConsoleLogFactory：demo 自带的极简 LogFactory 实现（{@code System.out} 输出）。
 *
 * <p>定位：作为 {@code TraceLogFactory} 的<b>被装饰对象</b>出现在
 * {@code DemoAppConfig} 的 {@code settings.setLogFactory(new TraceLogFactory(new ConsoleLogFactory()))}
 * 装配语句中，验证 LogFactory 装饰链 —— 全应用取到的 Log 实例是
 * {@code TraceLog}（拼 {@code [traceId] } 前缀）包装 {@code ConsoleLog}（{@code System.out} 落地）。
 * 本类不引入任何 slf4j / log4j 绑定。</p>
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
