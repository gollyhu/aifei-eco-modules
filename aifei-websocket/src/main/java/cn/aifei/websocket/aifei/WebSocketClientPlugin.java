/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.websocket.aifei;

import cn.aifei.aop.AopKit;
import cn.aifei.plugin.Plugin;
import cn.aifei.util.Prop;
import cn.aifei.websocket.ClientConfig;
import cn.aifei.websocket.ClientListener;
import cn.aifei.websocket.WebSocketClient;
import cn.aifei.websocket.WebSocketKit;
import cn.aifei.websocket.spi.MessageCodec;

/**
 * 客户端装配入口：应用启动时按 {@code websocket.client.*} 配置构建客户端、发起建连，并把它注册为
 * {@code @Inject WebSocketClient} 单例；应用停止时关闭客户端。
 *
 * <p>在 {@code AifeiConfig#config(Plugins)} 内 {@code plugins.add(new WebSocketClientPlugin(prop, listener))}
 * 或 {@code plugins.add(new WebSocketClientPlugin(prop, listener, codec))} 完成装配。构造函数只持有
 * {@code Prop}、监听器与编解码器引用，不解析、不校验、不建连；配置解析与建连在 {@link #start()} 内完成，
 * 配置有误即抛出异常使应用启动失败，不留半装配状态。</p>
 *
 * <p>监听器与编解码器都是对象引用，配置里表达不了，因此一并经构造函数传入：监听器为必传，编解码器为可选
 * （传 {@code null} 即不使用对象编解码）。</p>
 *
 * <p>与服务端各自独立装配：只出站连第三方服务的应用仅装配本插件，无需配置 {@code websocket.server.*}。</p>
 */
public class WebSocketClientPlugin implements Plugin {

    /**
     * 配置源；解析发生在 {@link #start()}。
     */
    private final Prop prop;

    /**
     * 客户端回调；建连时交给实例。
     */
    private final ClientListener listener;

    /**
     * 消息编解码器；{@code null} 表示不使用对象编解码。
     */
    private final MessageCodec codec;

    /**
     * 已装配的客户端实例；{@code null} 表示尚未装配，{@link #start()} 与 {@link #stop()} 据此保持幂等。
     */
    private WebSocketClient client;

    /**
     * 创建插件（不使用对象编解码）；只保存配置与监听器引用，不做任何解析、校验或建连。
     *
     * @param prop 配置源
     * @param listener 客户端回调
     * @throws IllegalArgumentException {@code prop} 或 {@code listener} 为 {@code null}
     */
    public WebSocketClientPlugin(Prop prop, ClientListener listener) {
        this(prop, listener, null);
    }

    /**
     * 创建插件并注入消息编解码器；只保存配置、监听器与编解码器引用，不做任何解析、校验或建连。
     *
     * <p>编解码器是对象引用、配置里表达不了，故经本构造函数传入；{@code codec} 非 {@code null} 时会在
     * {@link #start()} 内设入配置对象，从而使 {@code send(Object)} 可用。</p>
     *
     * @param prop 配置源
     * @param listener 客户端回调
     * @param codec 消息编解码器；可为 {@code null}，表示不使用对象编解码
     * @throws IllegalArgumentException {@code prop} 或 {@code listener} 为 {@code null}
     */
    public WebSocketClientPlugin(Prop prop, ClientListener listener, MessageCodec codec) {
        if (prop == null) {
            throw new IllegalArgumentException("prop can not be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener can not be null");
        }
        this.prop = prop;
        this.listener = listener;
        this.codec = codec;
    }

    /**
     * 解析配置、构建客户端、发起建连并注册 {@code @Inject} 单例；重复启动不会重复建连。
     *
     * <p>构造函数注入了编解码器时设入配置对象，之后才创建实例，确保对象编解码在主路径可用。</p>
     *
     * @throws IllegalArgumentException 配置缺失、非法或后端类型未注册
     */
    @Override
    public synchronized void start() {
        if (client != null) {
            return;
        }
        ClientConfig config = WebSocketConfigLoader.loadClientConfig(prop);
        if (codec != null) {
            config.setCodec(codec);
        }
        WebSocketClient created = WebSocketKit.createClient(config, listener);
        created.connect();
        AopKit.get().addSingletonObject(WebSocketClient.class, created);
        this.client = created;
    }

    /**
     * 关闭已装配的客户端；重复停止不会重复关闭。
     */
    @Override
    public synchronized void stop() {
        if (client == null) {
            return;
        }
        client.close();
        client = null;
    }
}
