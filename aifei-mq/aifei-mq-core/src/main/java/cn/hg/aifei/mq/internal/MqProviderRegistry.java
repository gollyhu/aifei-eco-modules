/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.internal;

import cn.hg.aifei.mq.MqException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * 后端 provider 注册表：后端类型标识到复用的无状态 provider 的映射。
 *
 * <p>发现方式为 JDK {@link ServiceLoader}：启动时一次性读取 classpath 上的
 * {@code META-INF/services/cn.hg.aifei.mq.internal.MqProvider}，按 {@link MqProvider#type()} 建索引。新增
 * 后端 = 引入一个携带该 services 文件的后端子模块，装配逻辑与本类都无需改动；类型白名单由
 * {@link #types()} 派生。</p>
 *
 * <p>实现约束：provider 必须是 {@code public} 类、具备 {@code public} 无参构造器（{@code ServiceLoader}
 * 靠反射实例化），且无状态（不持有实例字段，可被多线程复用）。同一 {@code type} 只允许一个实现，重复即
 * 视为装配期错误而非静默择一。</p>
 */
public final class MqProviderRegistry {

    /**
     * 未发现后端实现时追加的提示：说明如何补齐依赖。编译期内联到调用方，不触发本类初始化。
     */
    static final String BACKEND_HINT = "，请引入对应后端子模块（如 io.github.gollyhu:aifei-mq-memory:1.0.0）";

    /**
     * 类型标识到无状态 provider 的映射；保持发现顺序以便异常消息稳定。
     */
    private static final Map<String, MqProvider> PROVIDERS = index(ServiceLoader.load(MqProvider.class));

    /**
     * 按 {@code type()} 为 provider 集合建索引，保持遍历顺序。
     *
     * <p>包内可见，便于单测直接构造「同一 type 多实现」「空集合」等无法经 classpath 复现的场景。</p>
     *
     * @param providers provider 集合
     * @return 类型标识到 provider 的不可变映射
     * @throws MqException provider 未给出有效类型，或同一类型存在多个实现
     */
    static Map<String, MqProvider> index(Iterable<MqProvider> providers) {
        Map<String, List<MqProvider>> grouped = new LinkedHashMap<>();
        if (providers != null) {
            for (MqProvider provider : providers) {
                if (provider == null) {
                    continue;
                }
                String type = provider.type();
                if (type == null || type.trim().isEmpty()) {
                    throw new MqException("mq provider must declare a non-blank type: "
                            + provider.getClass().getName());
                }
                List<MqProvider> sameType = grouped.get(type);
                if (sameType == null) {
                    sameType = new ArrayList<>();
                    grouped.put(type, sameType);
                }
                sameType.add(provider);
            }
        }
        Map<String, MqProvider> indexed = new LinkedHashMap<>();
        for (Map.Entry<String, List<MqProvider>> entry : grouped.entrySet()) {
            List<MqProvider> sameType = entry.getValue();
            if (sameType.size() > 1) {
                throw new MqException("mq.type=" + entry.getKey()
                        + " 存在多个后端实现，无法确定使用哪一个：" + classNames(sameType));
            }
            indexed.put(entry.getKey(), sameType.get(0));
        }
        return Collections.unmodifiableMap(indexed);
    }

    /**
     * 取指定类型复用的无状态 provider。
     *
     * @param type 后端类型
     * @return provider
     * @throws MqException {@code type} 未发现实现；消息含 {@code mq.type=<type>} 与补齐依赖的提示，不含任何
     *         配置取值
     */
    public MqProvider getProvider(String type) {
        MqProvider provider = PROVIDERS.get(type);
        if (provider == null) {
            throw unsupportedType(type, PROVIDERS.keySet());
        }
        return provider;
    }

    /**
     * 返回已发现的类型集合，供配置校验的类型白名单派生使用。
     *
     * @return 已发现的类型集合
     */
    public Set<String> types() {
        return PROVIDERS.keySet();
    }

    /**
     * 构造「类型无对应后端实现」的异常；消息口径与配置校验处一致。
     *
     * @param type 后端类型
     * @param knownTypes 当前已发现的类型
     * @return 待抛出的异常
     */
    static MqException unsupportedType(String type, Set<String> knownTypes) {
        return new MqException("unsupported mq.type=" + type + BACKEND_HINT
                + "；已发现类型: " + knownTypes);
    }

    /**
     * 取 provider 实现类名，用于冲突消息。
     *
     * @param providers provider 列表
     * @return 类名列表
     */
    private static List<String> classNames(List<MqProvider> providers) {
        List<String> names = new ArrayList<>(providers.size());
        for (MqProvider provider : providers) {
            names.add(provider.getClass().getName());
        }
        return names;
    }
}
