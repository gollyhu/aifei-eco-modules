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
import java.util.ArrayList;
import java.util.List;

/**
 * 消息能力的装配入口：应用启动时按 {@code mq.*} 配置为「主实例 + {@code mq.names} 声明的命名实例」各创建
 * 一条总线，并把生产者、消费者登记为运行期实例（主实例同时注册为 {@code @Inject} 单例）；应用停止时注销
 * 全部实例并关闭全部总线。
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
     * 后端注册表，装配时按各实例的类型取 provider。
     */
    private final MqProviderRegistry registry = new MqProviderRegistry();

    /**
     * 本次装配建成的全部总线；空表示尚未装配，{@link #start()} 与 {@link #stop()} 据此保持幂等。
     */
    private final List<MessageBus> buses = new ArrayList<>();

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
     * 逐实例解析配置、创建总线并登记；重复启动不会重复装配。
     *
     * <p>主实例在首位，其后是 {@code mq.names} 声明的命名实例。任一实例失败即中止并回滚——已登记的实例全部
     * 注销、已建成的总线全部关闭，不留半装配状态。本方法只在<b>配置</b>层面失败即中止；<b>不校验</b>后端
     * 连通性——如某实例类型为 {@code rocketmq} 而 nameserver 不可达，本方法<b>不会</b>失败，故障通常延迟到
     * 首次 {@code send} / 消费拉取才暴露。</p>
     *
     * @throws IllegalArgumentException 配置非法、{@code mq.names} 非法，或某实例的类型指向未注册的后端
     * @throws cn.hg.aifei.mq.MqException 某实例的总线构建失败（如必填配置缺失、客户端启动失败）；抛出即应用
     *         启动失败，不留半装配状态
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
                MqHolder.register(instanceName, bus.producer(), bus.consumer());
            }
            // @Inject 只对应主实例：AopKit 按类型注册、无按名通道，命名实例只能经 ProducerUtil / ConsumerUtil 取用
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

    /**
     * 注销全部实例并关闭全部总线；重复停止不会重复关闭。
     */
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

    // ─────────── 配置解析（全部在 start() 内执行） ───────────

    /**
     * 返回待装配的实例名列表：主实例在最前，其后是 {@code mq.names} 声明的命名实例。
     */
    private List<String> instanceNames() {
        List<String> names = new ArrayList<>();
        names.add(MqConfig.MAIN_INSTANCE_NAME);
        names.addAll(MqConfig.parseInstanceNames(prop.getProperties(), MqConfig.NAMES_KEY));
        return names;
    }

    /**
     * 关闭本次装配已建成的总线；关闭失败不掩盖原始故障，改为挂到它的抑制异常上。
     */
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
