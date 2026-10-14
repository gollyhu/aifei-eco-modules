/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.internal;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 会话标识生成的唯一定义点：以进程内单调递增的计数器保证同一 JVM 内唯一，格式不承诺。
 *
 * <p>计数器是静态的，因此同一进程内的多个服务端实例共享同一序列，标识不会跨实例冲突。</p>
 */
public final class SessionIds {

    /**
     * 进程内会话标识序列。
     */
    private static final AtomicLong SEQUENCE = new AtomicLong();

    /**
     * 禁止创建工具类实例。
     */
    private SessionIds() {
    }

    /**
     * 生成下一个会话标识。
     *
     * @return 本次生成的会话标识；进程内唯一
     */
    public static String next() {
        return Long.toString(SEQUENCE.incrementAndGet());
    }
}
