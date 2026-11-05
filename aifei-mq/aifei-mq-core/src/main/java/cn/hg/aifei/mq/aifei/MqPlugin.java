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
import cn.hg.aifei.mq.internal.BusConsumer;
import cn.hg.aifei.mq.internal.BusProducer;
import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MqHolder;
import cn.hg.aifei.mq.internal.MqProvider;
import cn.hg.aifei.mq.internal.MqProviderRegistry;
import cn.hg.aifei.mq.internal.MessageBus;
import java.util.ArrayList;
import java.util.List;

/**
 * 消息能力的装配入口：启动时按 {@code mq.*} 为「主实例 + {@code mq.names} 声明的命名实例」各建一条总线并登记
 * 生产者、消费者（主实例同时注册为 {@code @Inject} 单例），停止时注销全部实例并关闭全部总线。
 * 构造函数只持有 {@code Prop} 引用，解析与建总线全在 {@link #start()} 内完成：配置有误即启动失败，不留半装配状态。
 */
public class MqPlugin implements Plugin {

    private final Prop prop;

    private final MqProviderRegistry registry = new MqProviderRegistry();

    /** 本次装配建成的全部总线；{@link #start()} 与 {@link #stop()} 据此保持幂等。 */
    private final List<MessageBus> buses = new ArrayList<>();

    private volatile boolean started;

    /**
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
     * 逐实例解析配置、创建总线并登记（主实例在首位）；重复启动不重复装配，任一实例失败即注销已登记实例、关闭
     * 已建总线，不留半装配状态。只校验<b>配置</b>，<b>不校验</b>后端连通性：如 nameserver 不可达仍启动成功，
     * 故障延迟到首次 {@code send} / 消费拉取才暴露。
     * @throws IllegalArgumentException 配置非法或 {@code mq.names} 非法
     * @throws cn.hg.aifei.mq.MqException 某实例无对应后端实现（如缺少后端子模块），或其总线构建失败
     *         （如必填配置缺失、客户端启动失败）；抛出即应用启动失败，不留半装配状态
     */
    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        List<MessageBus> created = new ArrayList<>();
        try {
            for (String instanceName : instanceNames()) {
                MqConfig config = MqConfig.parse(prop.getProperties(), registry.types(), instanceName);
                MqProvider provider = registry.getProvider(config.type());
                MessageBus bus = provider.createBus(config);
                created.add(bus);
                MqHolder.register(instanceName, new BusProducer(bus), new BusConsumer(bus));
            }
            // @Inject 只对应主实例：AopKit 按类型注册、无按名通道，命名实例只能经 ProducerKit / ConsumerKit 取用
            AopKit.get().addSingletonObject(Producer.class, MqHolder.producer(MqConfig.MAIN_INSTANCE_NAME));
            AopKit.get().addSingletonObject(Consumer.class, MqHolder.consumer(MqConfig.MAIN_INSTANCE_NAME));
            buses.clear();
            buses.addAll(created);
            started = true;
        } catch (RuntimeException | Error e) {
            MqHolder.clear();
            rollback(created, e);
            throw e;
        }
    }

    /** 注销全部实例并关闭全部总线；重复停止不会重复关闭。 */
    @Override
    public synchronized void stop() {
        if (!started) {
            return;
        }
        MqHolder.clear();
        for (MessageBus bus : buses) {
            bus.close();
        }
        buses.clear();
        this.started = false;
    }

    private List<String> instanceNames() {
        List<String> names = new ArrayList<>();
        names.add(MqConfig.MAIN_INSTANCE_NAME);
        names.addAll(MqConfig.parseInstanceNames(prop.getProperties(), MqConfig.NAMES_KEY));
        return names;
    }

    /** 关闭失败不掩盖原始故障，改为挂到它的抑制异常上。 */
    private static void rollback(List<MessageBus> created, Throwable failure) {
        for (int i = created.size() - 1; i >= 0; i--) {
            try {
                created.get(i).close();
            } catch (RuntimeException e) {
                failure.addSuppressed(e);
            }
        }
    }
}
