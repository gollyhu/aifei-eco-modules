/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.internal;

import cn.hg.aifei.http.HttpClient;
import cn.hg.aifei.http.HttpException;

/**
 * 运行期实例持有：装配期写入、停止期清除，静态门面据此取当前可用的客户端。
 *
 * <p>未装配或已停止时取用一律失败（抛 {@link HttpException}），不静默返回 {@code null}。</p>
 */
public final class HttpHolder {

    /**
     * 写入、取用与清除共用的锁。
     */
    private static final Object LOCK = new Object();

    /**
     * 当前已装配的客户端，未装配时为 {@code null}。
     */
    private static HttpClient client;

    /**
     * 禁止创建工具类实例。
     */
    private HttpHolder() {
    }

    /**
     * 写入当前客户端，覆盖既有值。
     *
     * @param httpClient 已装配的客户端
     * @throws IllegalArgumentException {@code httpClient} 为 {@code null}
     */
    public static void install(HttpClient httpClient) {
        if (httpClient == null) {
            throw new IllegalArgumentException("client 不能为 null");
        }
        synchronized (LOCK) {
            client = httpClient;
        }
    }

    /**
     * 取当前客户端。
     *
     * @return 已装配的客户端，与 {@code @Inject HttpClient} 注入的是同一个对象
     * @throws HttpException 未装配或已停止
     */
    public static HttpClient client() {
        synchronized (LOCK) {
            if (client == null) {
                throw new HttpException("HTTP 客户端未装配或已停止");
            }
            return client;
        }
    }

    /**
     * 清除当前客户端；清除后取用立即失败。
     */
    public static void clear() {
        synchronized (LOCK) {
            client = null;
        }
    }
}
