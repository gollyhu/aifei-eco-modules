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
    public static void main(String[] args) {
        Aifei.start(new DemoAppConfig(), args);
    }
}
