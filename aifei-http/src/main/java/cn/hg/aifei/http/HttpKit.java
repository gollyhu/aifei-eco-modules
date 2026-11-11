/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http;

import cn.hg.aifei.http.internal.HttpHolder;

/**
 * 出站 HTTP 的静态门面：供工具方法、脚本等拿不到注入点的场景取用已装配的客户端。
 *
 * <p>这是<b>有状态门面</b>——实例由装配插件在启动时写入，未装配或已停止时调用即失败（抛
 * {@link HttpException}，不静默返回 {@code null}）。</p>
 *
 * <p>全部方法只做转发：不重复校验、不转换异常、不加默认头。</p>
 */
public final class HttpKit {

    /**
     * 禁止创建工具类实例。
     */
    private HttpKit() {
    }

    /**
     * 取当前已装配的客户端。
     *
     * @return 客户端实例，与 {@code @Inject HttpClient} 注入的是同一个对象
     * @throws HttpException 未装配或已停止
     */
    public static HttpClient client() {
        return HttpHolder.client();
    }

    /**
     * 发送一次逻辑请求。
     *
     * @param request 请求构建器
     * @return 响应快照
     * @throws HttpException 未装配、已停止或传输层失败
     */
    public static HttpResponse exec(HttpRequest request) {
        return client().exec(request);
    }

    /**
     * 发送 GET 请求。
     *
     * @param url 请求 URL
     * @return 响应快照
     * @throws HttpException 未装配、已停止或传输层失败
     */
    public static HttpResponse get(String url) {
        return exec(HttpRequest.get(url));
    }

    /**
     * 发送 POST 请求。
     *
     * @param url  请求 URL
     * @param body 文本请求体
     * @return 响应快照
     * @throws HttpException 未装配、已停止或传输层失败
     */
    public static HttpResponse post(String url, String body) {
        return exec(HttpRequest.post(url).body(body));
    }

    /**
     * 发送 PUT 请求。
     *
     * @param url  请求 URL
     * @param body 文本请求体
     * @return 响应快照
     * @throws HttpException 未装配、已停止或传输层失败
     */
    public static HttpResponse put(String url, String body) {
        return exec(HttpRequest.put(url).body(body));
    }

    /**
     * 发送 DELETE 请求。
     *
     * @param url 请求 URL
     * @return 响应快照
     * @throws HttpException 未装配、已停止或传输层失败
     */
    public static HttpResponse delete(String url) {
        return exec(HttpRequest.delete(url));
    }

    /**
     * 发送 HEAD 请求。
     *
     * @param url 请求 URL
     * @return 响应快照（无响应体）
     * @throws HttpException 未装配、已停止或传输层失败
     */
    public static HttpResponse head(String url) {
        return exec(HttpRequest.head(url));
    }
}
