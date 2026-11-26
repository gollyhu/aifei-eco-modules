/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.demo;

import cn.aifei.core.Aifei;

/** demo 启动入口；HTTP 端口经 -Dundertow.port=18180 传入（见 start-demo.bat）。 */
public class DemoApp {

    /**
     * @param args 命令行参数，原样交给 aifei 内核
     */
    public static void main(String[] args) {
        Aifei.start(new DemoAppConfig(), args);
    }
}
