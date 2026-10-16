/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.aifei;

import cn.aifei.aop.AopKit;
import cn.aifei.plugin.Plugin;
import cn.aifei.util.Prop;
import cn.hg.aifei.mq.Consumer;
import cn.hg.aifei.mq.Producer;
import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MqHolder;
import cn.hg.aifei.mq.internal.MqProvider;
import cn.hg.aifei.mq.internal.MqProviderRegistry;
import cn.hg.aifei.mq.internal.MessageBus;

/**
 * 消息能力的装配入口：应用启动时按 {@code mq.*} 配置创建总线并把生产者、消费者注册为
 * {@code @Inject} 单例；应用停止时注销单例并关闭总线。
 *
 * <p>在 {@code AppConfig#config(Plugins)} 内 {@code plugins.add(new MqPlugin(prop))} 完成装配。构造函数
 * 只持有 {@code Prop} 引用，不解析、不校验、不建总线；配置解析与建总线在 {@link #start()} 内完成，配置
 * 有误即抛出异常使应用启动失败，不留半装配状态。</p>
 */
public class MqPlugin implements Plugin {

    /**
     * 配置源；解析发生在 {@link #start()}。
     */
    private final Prop prop;

    /**
     * 后端注册表，装配时按 {@code mq.type} 取 provider。
     */
    private final MqProviderRegistry registry = new MqProviderRegistry();

    /**
     * 已装配的总线；{@code null} 表示尚未装配，{@link #start()} 与 {@link #stop()} 据此保持幂等。
     */
    private MessageBus bus;

    /**
     * 是否已完成装配。
     */
    private volatile boolean started;

    /**
     * 创建插件；只保存配置引用，不做任何解析、校验或建总线。
     *
     * @param prop 配置源
     * @throws IllegalArgumentException {@code prop} 为 {@code null}
     */
    public MqPlugin(Prop prop) {
        if (prop == null) {
            throw new IllegalArgumentException("prop can not be null");
        }
        this.prop = prop;
    }

    /**
     * 解析配置、创建总线并注册生产者与消费者单例；重复启动不会重复装配。
     *
     * <p>本方法只在<b>配置</b>层面失败即中止（不留半装配）；<b>不校验</b>后端连通性——如 {@code mq.type=rocketmq}
     * 时 nameserver 不可达，本方法<b>不会</b>失败，故障通常延迟到首次 {@code send} / 消费拉取才暴露。</p>
     *
     * @throws IllegalArgumentException 配置非法或 {@code mq.type} 指向未注册的后端
     * @throws cn.hg.aifei.mq.MqException 真实后端总线构建失败（如必填配置缺失、客户端启动失败）；抛出即应用启动
     *         失败，不留半装配状态
     */
    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        MqConfig config = MqConfig.parse(prop.getProperties(), registry.types());
        MqProvider provider = registry.getProvider(config.type());
        MessageBus created = provider.createBus(config);
        Producer producer = created.producer();
        Consumer consumer = created.consumer();
        MqHolder.register(producer, consumer);
        AopKit.get().addSingletonObject(Producer.class, producer);
        AopKit.get().addSingletonObject(Consumer.class, consumer);
        this.bus = created;
        this.started = true;
    }

    /**
     * 注销单例并关闭总线；重复停止不会重复关闭。
     */
    @Override
    public synchronized void stop() {
        if (!started) {
            return;
        }
        MqHolder.clear();
        bus.close();
        this.bus = null;
        this.started = false;
    }
}
