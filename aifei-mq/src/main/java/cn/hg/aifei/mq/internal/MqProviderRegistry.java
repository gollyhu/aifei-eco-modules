/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.internal;

import cn.hg.aifei.mq.memory.MemoryMqProvider;
import cn.hg.aifei.mq.rocketmq.RocketMqProvider;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 后端 provider 注册表：后端类型标识到复用的无状态 provider 的映射。
 *
 * <p>新增后端 = 新增 provider 实现并在静态初始化块注册一行，装配逻辑无需改动；类型白名单由
 * {@link #types()} 派生。</p>
 */
public final class MqProviderRegistry {

    /**
     * 类型标识到无状态 provider 的映射；保持注册顺序以便异常消息稳定。
     */
    private static final Map<String, MqProvider> PROVIDERS = new LinkedHashMap<>();

    static {
        PROVIDERS.put(MemoryMqProvider.TYPE, new MemoryMqProvider());
        PROVIDERS.put(RocketMqProvider.TYPE, new RocketMqProvider());
    }

    /**
     * 取指定类型复用的无状态 provider。
     *
     * @param type 后端类型
     * @return provider
     * @throws IllegalArgumentException {@code type} 未注册
     */
    public MqProvider getProvider(String type) {
        MqProvider provider = PROVIDERS.get(type);
        if (provider == null) {
            throw new IllegalArgumentException(
                    "unsupported mq type: " + type + ", supported types: " + types()
            );
        }
        return provider;
    }

    /**
     * 返回已注册的类型集合，供配置校验的类型白名单派生使用。
     *
     * @return 已注册类型集合
     */
    public Set<String> types() {
        return Collections.unmodifiableSet(PROVIDERS.keySet());
    }
}
