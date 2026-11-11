/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.support;

import cn.aifei.log.Log;
import cn.aifei.log.LogFactory;

/**
 * 单测用空日志工厂：产出的 {@link NoopLog} 只丢弃输出，不引入任何日志绑定。
 *
 * <p>装配入口在启动成功后会记录一条 INFO（策略 + workerId），内核默认日志工厂取用即抛异常、会被装配层
 * 吞掉——用例因此无法确认该记录路径真的可用。预装本工厂后该路径真实执行且零副作用，同时保持「零日志
 * 绑定」纪律。</p>
 */
public final class NoopLogFactory implements LogFactory {

    /**
     * 返回无名空日志。
     *
     * @return 空日志
     */
    @Override
    public Log getLog() {
        return new NoopLog("noop");
    }

    /**
     * 返回以类名命名的空日志。
     *
     * @param clazz 日志所属类型
     * @return 空日志
     */
    @Override
    public Log getLog(Class<?> clazz) {
        return new NoopLog(clazz.getName());
    }

    /**
     * 返回以名称命名的空日志。
     *
     * @param name 日志名称
     * @return 空日志
     */
    @Override
    public Log getLog(String name) {
        return new NoopLog(name);
    }
}
