/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.internal;

import java.lang.reflect.Method;

/**
 * 任务名的唯一定义点：任务名 = {@code 类全名#方法名}（如 {@code com.acme.OrderJob#clean}）。
 *
 * <p>任务名是 {@code JobKit.names()} / {@code state()} 的 key、事件快照的取值与全部错误消息的定位口径，
 * 因此只在此处拼装，扫描、记账与错误消息一律经本方法取名，不得各自拼字符串。</p>
 */
public final class JobNames {

    /**
     * 类全名与方法名之间的分隔符。
     */
    private static final String SEPARATOR = "#";

    /**
     * 禁止创建工具类实例。
     */
    private JobNames() {
    }

    /**
     * 取方法对应的任务名。同名重载方法会得到相同的任务名，随后在注册表处按重名判失败，符合「不静默去重」的口径。
     *
     * @param method 任务方法
     * @return 任务名，格式为「类全名#方法名」
     */
    public static String of(Method method) {
        return method.getDeclaringClass().getName() + SEPARATOR + method.getName();
    }
}
