/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sentinel.demo;

import cn.aifei.core.Aifei;
import cn.hg.aifei.sentinel.demo.common.DemoAppConfig;

/** 启动入口 */
public class DemoApp {

    /**
     * 启动 demo 应用；HTTP 端口经 {@code -Dundertow.port} 传入（启动脚本默认 80，占用时可设 {@code DEMO_PORT} 走备用端口）。
     * 主页面 <http://localhost:80/>，运行模式由配置文件自动识别，脚本不手动切换。
     *
     * @param args 命令行参数，原样交给 aifei 内核
     */
    public static void main(String[] args) {
        Aifei.start(new DemoAppConfig(), args);
    }
}
