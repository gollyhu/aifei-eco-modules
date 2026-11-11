/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.internal;

/**
 * 超时装配：逐请求取值与全局默认的合并只在本类定义。
 *
 * <p>逐请求值大于 0 时优先，否则取全局默认；两段超时相互独立，不存在整体 deadline。</p>
 */
public final class HttpTimeouts {

    private final int connectMillis;

    private final int readMillis;

    private HttpTimeouts(int connectMillis, int readMillis) {
        this.connectMillis = connectMillis;
        this.readMillis = readMillis;
    }

    /**
     * 合并逐请求超时与全局默认。
     *
     * @param requestConnectMillis 逐请求建连超时，{@code <= 0} 表示继承全局默认
     * @param requestReadMillis    逐请求读超时，{@code <= 0} 表示继承全局默认
     * @param config               全局配置，为 {@code null} 时按内置默认值处理
     * @return 生效的超时
     */
    public static HttpTimeouts resolve(int requestConnectMillis, int requestReadMillis, HttpConfig config) {
        int defaultConnect = config == null ? HttpConfig.DEFAULT_CONNECT_TIMEOUT : config.connectTimeout();
        int defaultRead = config == null ? HttpConfig.DEFAULT_READ_TIMEOUT : config.readTimeout();
        int connectMillis = requestConnectMillis > 0 ? requestConnectMillis : defaultConnect;
        int readMillis = requestReadMillis > 0 ? requestReadMillis : defaultRead;
        return new HttpTimeouts(connectMillis, readMillis);
    }

    /**
     * 生效的建连超时。
     *
     * @return 毫秒数
     */
    public int connectMillis() {
        return connectMillis;
    }

    /**
     * 生效的读超时。
     *
     * @return 毫秒数
     */
    public int readMillis() {
        return readMillis;
    }
}
