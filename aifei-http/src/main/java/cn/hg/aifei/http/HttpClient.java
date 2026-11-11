/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http;

/**
 * 出站 HTTP 的发送视图：装配完成后由容器提供 {@code @Inject} 单例，业务代码只构造请求、读响应。
 *
 * <p>{@link #exec(HttpRequest)} 内部完成资源名冻结、重试决策、逐次尝试（穿拦截器链、发送、收尾）与度量写回；
 * <b>非 2xx 不抛异常</b>——3xx / 4xx / 5xx 一律正常返回，由业务读 {@link HttpResponse#statusCode()} 判断。</p>
 *
 * <p>不声明关闭方法：传输实现每次请求自建连接、读完即归还 JDK 连接缓存，连接池受 JVM 全局属性约束，
 * 模块侧无跨请求资源需要释放。</p>
 */
public interface HttpClient {

    /**
     * 发送一次逻辑请求。
     *
     * @param request 请求构建器
     * @return 响应快照（含状态码、头、体、总耗时与总尝试次数）
     * @throws HttpException            传输层失败、未装配、响应体超限、解码失败、重试等待被中断
     * @throws IllegalArgumentException 请求为 {@code null}，或调用方参数当场写错
     */
    HttpResponse exec(HttpRequest request);
}
