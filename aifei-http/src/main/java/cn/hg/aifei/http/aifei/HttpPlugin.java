/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.aifei;

import cn.aifei.aop.AopKit;
import cn.aifei.plugin.Plugin;
import cn.aifei.util.Prop;
import cn.hg.aifei.http.HttpClient;
import cn.hg.aifei.http.HttpException;
import cn.hg.aifei.http.HttpInterceptor;
import cn.hg.aifei.http.internal.DefaultHttpClient;
import cn.hg.aifei.http.internal.HttpConfig;
import cn.hg.aifei.http.internal.HttpHolder;
import java.util.ArrayList;
import java.util.List;

/**
 * 出站 HTTP 的装配入口：解析 {@code http.*}、建客户端、注册 {@code @Inject} 单例、写入门面实例。
 *
 * <p>构造期只持配置源引用，不解析、不校验、不建客户端；拦截器按 {@link #addInterceptor} 的调用顺序累积，
 * {@link #start()} 时快照为不可变列表交给客户端，因此链顺序是源码事实、可复现。</p>
 *
 * <p>启动失败不留半装配：配置非法或容器注册失败时不写入门面实例。停止只清门面（容器无注销 API），
 * 已注入字段仍持旧实例属已知边界。</p>
 */
public class HttpPlugin implements Plugin {

    private final Prop prop;

    /**
     * 按注册顺序累积的拦截器；停止不清空，再次启动沿用。
     */
    private final List<HttpInterceptor> interceptors = new ArrayList<>();

    /**
     * 首次装配建成的客户端；再次启动复用它，避免重复向容器注册。
     */
    private DefaultHttpClient client;

    private volatile boolean started;

    /**
     * @param prop 配置源
     * @throws IllegalArgumentException {@code prop} 为 {@code null}
     */
    public HttpPlugin(Prop prop) {
        if (prop == null) {
            throw new IllegalArgumentException("prop 不能为 null");
        }
        this.prop = prop;
    }

    /**
     * 注册拦截器，按调用顺序决定进入顺序（收尾为其逆序）。
     *
     * @param interceptor 拦截器
     * @return 本插件（链式）
     * @throws IllegalArgumentException {@code interceptor} 为 {@code null}
     * @throws HttpException            已启动
     */
    public synchronized HttpPlugin addInterceptor(HttpInterceptor interceptor) {
        if (interceptor == null) {
            throw new IllegalArgumentException("interceptor 不能为 null");
        }
        if (started) {
            throw new HttpException("HTTP 客户端已启动，不能再注册拦截器");
        }
        this.interceptors.add(interceptor);
        return this;
    }

    /**
     * 装配客户端并登记为 {@code @Inject} 单例；重复启动不作任何事。
     *
     * @throws HttpException 配置非法（消息含键名与原值）
     */
    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        HttpConfig config = HttpConfig.parse(prop.getProperties());
        if (client == null) {
            DefaultHttpClient created = new DefaultHttpClient(config, interceptors);
            HttpHolder.install(created);
            try {
                AopKit.get().addSingletonObject(HttpClient.class, created);
            } catch (RuntimeException e) {
                HttpHolder.clear();
                throw e;
            } catch (Error e) {
                HttpHolder.clear();
                throw e;
            }
            this.client = created;
        } else {
            HttpHolder.install(client);
        }
        this.started = true;
    }

    /**
     * 清除门面实例；重复停止不作任何事。
     */
    @Override
    public synchronized void stop() {
        if (!started) {
            return;
        }
        HttpHolder.clear();
        this.started = false;
    }
}
