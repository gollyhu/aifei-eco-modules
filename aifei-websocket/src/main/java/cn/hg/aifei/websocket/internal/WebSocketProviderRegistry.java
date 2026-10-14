/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.internal;

import cn.hg.aifei.websocket.javawebsocket.JavaWebSocketProvider;
import cn.hg.aifei.websocket.spi.WebSocketProvider;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 传输后端注册表：{@code type} 到复用的无状态 provider 的映射，是后端的组合根。
 *
 * <p>注册表在静态初始化块里注册本模块默认后端 {@code "javawebsocket"}，因此通用装配路径（{@code WebSocketKit}、
 * 接入层 {@code Plugin}）无需知道任何具体后端。新增后端只需实现一个 provider 并在此增加一行注册
 * （见设计文档 §5.4）。</p>
 *
 * <p>方法签名不含第三方类型，故引用实现包的 provider 不破坏 {@code optional} 依赖隔离。注册表本身按
 * {@link #getProvider(String)} 只读握手，注册动作仅发生在类初始化，实例方法可安全跨线程调用。</p>
 */
public final class WebSocketProviderRegistry {

    /**
     * 类型标识到无状态 provider 的映射；保持注册顺序，使异常消息中的已注册类型集合稳定。
     */
    private static final Map<String, WebSocketProvider> PROVIDERS = new LinkedHashMap<>();

    static {
        PROVIDERS.put("javawebsocket", new JavaWebSocketProvider());
    }

    /**
     * 注册一个传输后端 provider。
     *
     * @param type 后端类型标识
     * @param provider 后端 provider；必须无状态
     * @throws IllegalArgumentException {@code type} 为空白，或 {@code provider} 为 {@code null}
     * @throws IllegalStateException 该类型已注册
     */
    public void register(String type, WebSocketProvider provider) {
        String normalized = WebSocketValidator.requireType(type);
        if (provider == null) {
            throw new IllegalArgumentException("provider can not be null");
        }
        if (PROVIDERS.containsKey(normalized)) {
            throw new IllegalStateException("web socket provider already registered for type: " + normalized);
        }
        PROVIDERS.put(normalized, provider);
    }

    /**
     * 取指定类型复用的无状态 provider；调用方应紧接着调 {@code buildClient} / {@code buildServer}。
     *
     * @param type 后端类型标识
     * @return 对应的无状态 provider
     * @throws IllegalArgumentException 该类型未注册；消息含已注册类型集合
     */
    public WebSocketProvider getProvider(String type) {
        WebSocketProvider provider = PROVIDERS.get(type);
        if (provider == null) {
            throw new IllegalArgumentException(
                    "unsupported web socket type: " + type + ", supported types: " + types());
        }
        return provider;
    }

    /**
     * 返回已注册的类型集合，供配置校验的类型白名单派生使用。
     *
     * @return 已注册类型集合；不可变
     */
    public Set<String> types() {
        return Collections.unmodifiableSet(PROVIDERS.keySet());
    }
}
