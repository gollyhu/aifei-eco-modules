/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.internal;

import cn.hg.aifei.mq.Consumer;
import cn.hg.aifei.mq.Producer;

/**
 * 后端统一内部消息总线契约：一个后端实例产出生产者与消费者两个视图，并承担生命周期。
 *
 * <p>同一总线产出的两个视图共用一个后端实例。非承诺 API，仅供装配与实现使用。</p>
 */
public interface MessageBus extends AutoCloseable {

    /**
     * 返回本总线上的生产者视图。
     *
     * @return 生产者
     */
    Producer producer();

    /**
     * 返回本总线上的消费者视图。
     *
     * @return 消费者
     */
    Consumer consumer();

    /**
     * 关闭总线；重复调用是幂等的。关闭后发送与订阅视为状态非法。
     */
    @Override
    void close();
}
