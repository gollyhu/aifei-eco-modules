/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 * 开发工程师 寇戴码（Enjoy 指令增量）
 */

package cn.hg.aifei.i18n.demo;

import cn.aifei.core.Aifei;

/** 启动入口。 */
public class DemoApp {

    /**
     * 启动 demo；HTTP 端口经 {@code -Dundertow.port} 传入（见 start-demo.bat 的 18170）。
     *
     * @param args 命令行参数，原样交给 aifei 内核
     */
    public static void main(String[] args) {
        Aifei.start(new DemoAppConfig(), args);
    }
}
