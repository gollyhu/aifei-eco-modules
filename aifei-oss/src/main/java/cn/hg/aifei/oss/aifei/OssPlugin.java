/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss.aifei;

import cn.aifei.aop.AopKit;
import cn.aifei.plugin.Plugin;
import cn.aifei.util.Prop;
import cn.hg.aifei.oss.OssClient;
import cn.hg.aifei.oss.OssException;
import cn.hg.aifei.oss.internal.OssClients;
import cn.hg.aifei.oss.internal.OssConfig;
import cn.hg.aifei.oss.internal.OssStorageFactory;
import java.util.List;
import java.util.Map;

/**
 * 对象存储能力的装配入口：应用启动时按 {@code oss.*} 配置为每个存储平台各装配一个
 * {@link OssClient} 并登记，同时把<b>默认平台</b>的客户端注册为可 {@code @Inject} 的单例；应用停止时
 * 清空登记表，{@link #start()} 与 {@link #stop()} 均幂等。
 *
 * <p>在 {@code AppConfig#config(Plugins)} 内 {@code plugins.add(new OssPlugin(prop))} 完成装配。
 * 构造函数只持有 {@link Prop} 引用，不解析、不校验、不建连；配置解析与装配全部发生在 {@link #start()}
 * 内，配置有误（未声明任何平台、平台类型未知、必填项缺失、多平台时缺 {@code oss.default-platform} 等）
 * 即抛异常使应用启动失败，且<b>失败即回滚</b>，不留半装配状态。</p>
 *
 * <p>云厂商 SDK 缺失属<b>运行期</b>故障：平台客户端惰性建连，通常延迟到首次使用该平台才暴露，本插件
 * 不在启动期强制加载各平台 SDK。</p>
 *
 * <p><b>停止语义说明</b>：框架未提供注销 {@code @Inject} 单例的公开 API（对齐既有 {@code MqPlugin}
 * 范式），故 {@link #stop()} 清空的是本模块的客户端登记表与默认客户端，已注册的单例对象在应用生命周期内
 * 保持有效。</p>
 */
public class OssPlugin implements Plugin {

    /**
     * 配置源；解析发生在 {@link #start()}。
     */
    private final Prop prop;

    /**
     * 是否已完成装配；{@link #start()} 与 {@link #stop()} 据此保持幂等。
     */
    private volatile boolean started;

    /**
     * 创建插件；只保存配置引用，不做任何解析、校验或建连。
     *
     * @param prop 配置源
     * @throws OssException {@code prop} 为 {@code null}
     */
    public OssPlugin(Prop prop) {
        if (prop == null) {
            throw new OssException("prop can not be null");
        }
        this.prop = prop;
    }

    /**
     * 解析配置、装配全部平台的客户端并登记；重复启动不会重复装配。
     *
     * <p>任一环节失败即中止并回滚：清空已登记的客户端与默认客户端后再上抛，不留半装配状态。</p>
     *
     * @throws OssException 配置非法（未声明平台、平台类型未知、必填项缺失等），或装配失败
     */
    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        try {
            List<OssConfig> configs = OssConfigLoader.load(prop);
            String defaultPlatform = OssConfigLoader.defaultPlatform(prop, configs);
            Map<String, OssClient> clients = OssStorageFactory.buildClients(defaultPlatform, configs);
            for (Map.Entry<String, OssClient> client : clients.entrySet()) {
                OssClients.register(client.getKey(), client.getValue());
            }
            OssClient defaultClient = clients.get(defaultPlatform);
            OssClients.registerDefault(defaultClient);
            // @Inject OssClient 对应默认平台；框架按类型注册、无按名通道，命名平台只能经 OssKit.use(name) 取用
            AopKit.get().addSingletonObject(OssClient.class, defaultClient);
            started = true;
        } catch (RuntimeException | Error e) {
            OssClients.clear();
            throw e;
        }
    }

    /**
     * 清空客户端登记表与默认客户端；重复停止不会重复清理。
     */
    @Override
    public synchronized void stop() {
        if (!started) {
            return;
        }
        OssClients.clear();
        started = false;
    }
}
