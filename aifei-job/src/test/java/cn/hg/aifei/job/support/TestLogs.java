/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.support;

import cn.aifei.log.LogKit;

/**
 * 测试期日志装配入口：用例在 {@code @BeforeClass} 调用 {@link #install()}，把框架日志工厂换成 Noop 实现。
 *
 * <p>框架默认的 {@code NoLogFactory} 每次调用都抛 {@code IllegalStateException}；本模块的日志调用虽然都是惰性获取，
 * 但为了让用例不产生任何日志输出、也不引入任何日志绑定依赖，用例统一先装配空实现。</p>
 */
public final class TestLogs {

    /**
     * 禁止创建工具类实例。
     */
    private TestLogs() {
    }

    /**
     * 装配空日志工厂；幂等，可在每个测试类的 {@code @BeforeClass} 重复调用。
     */
    public static void install() {
        LogKit.get().setLogFactory(new NoopLogFactory());
    }
}
