/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.websocket.demo;

import cn.aifei.core.Aifei;
import cn.aifei.websocket.demo.common.DemoAppConfig;

/** 启动入口 */
public class DemoApp {

    /**
     * 启动 demo 应用；HTTP 端口经 {@code -Dundertow.port} 传入（默认见 start-demo.bat）。
     *
     * @param args 命令行参数，原样交给 aifei 内核
     */
    public static void main(String[] args) {
        Aifei.start(new DemoAppConfig(), args);
    }
}
