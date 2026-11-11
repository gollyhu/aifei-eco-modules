/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.internal;

import cn.aifei.log.Log;
import cn.hg.aifei.http.HttpClient;
import cn.hg.aifei.http.HttpException;
import cn.hg.aifei.http.HttpInterceptor;
import cn.hg.aifei.http.HttpRequest;
import cn.hg.aifei.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 执行编排：资源名冻结一次、重试决策、逐次尝试（穿拦截器链 + 发送 + 收尾）、度量写回。
 *
 * <p>拦截器链顺序、收尾范围、异常原样传播、重试循环与「将重试」日志只在本类定义：</p>
 * <ul>
 *   <li>{@code before} 正序、{@code after} 逆序；只有 {@code before} 已被调用过的拦截器才收尾；</li>
 *   <li>任一 {@code before} 抛异常 → 后续不再进入、不发送、不重试，异常原样抛出；</li>
 *   <li>资源名在 {@link #exec(HttpRequest)} 入口冻结，重试各次与异常路径取值恒等；</li>
 *   <li>非 2xx 不抛，只有网络层失败与 5xx 才进入重试，退避为固定间隔。</li>
 * </ul>
 */
public final class DefaultHttpClient implements HttpClient {

    private final HttpConfig config;

    private final List<HttpInterceptor> interceptors;

    private final HttpTransport transport;

    /**
     * 生产用构造器：传输实现固定为 {@link JdkHttpTransport}。
     *
     * @param config       配置域
     * @param interceptors 按注册顺序排列的拦截器，可为 {@code null}（等价无拦截器）
     * @throws IllegalArgumentException {@code config} 为 {@code null}
     */
    public DefaultHttpClient(HttpConfig config, List<HttpInterceptor> interceptors) {
        this(config, interceptors, new JdkHttpTransport(config));
    }

    /**
     * 单测注入用构造器：包级可见，应用侧无法引用。
     *
     * @param config       配置域
     * @param interceptors 按注册顺序排列的拦截器，可为 {@code null}
     * @param transport    传输实现
     * @throws IllegalArgumentException {@code config} 或 {@code transport} 为 {@code null}
     */
    DefaultHttpClient(HttpConfig config, List<HttpInterceptor> interceptors, HttpTransport transport) {
        if (config == null) {
            throw new IllegalArgumentException("config 不能为 null");
        }
        if (transport == null) {
            throw new IllegalArgumentException("transport 不能为 null");
        }
        this.config = config;
        this.interceptors = interceptors == null
                ? Collections.<HttpInterceptor>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(interceptors));
        this.transport = transport;
    }

    /**
     * 发送一次逻辑请求。
     *
     * @param request 请求构建器
     * @return 响应快照（度量口径为整个调用的总耗时与总尝试次数）
     * @throws HttpException            传输层失败、响应体超限、解码失败、重试等待被中断
     * @throws IllegalArgumentException 请求为 {@code null}
     */
    @Override
    public HttpResponse exec(HttpRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request 不能为 null");
        }
        long start = System.currentTimeMillis();
        String resource = HttpResource.of(request.explicitResource(), request.method(), request.url());
        int maxAttempts = HttpRetry.maxAttempts(request.method(), request.retryTimes(), config.retryTimes());
        long intervalMillis = request.retryIntervalMillis() >= 0L
                ? request.retryIntervalMillis()
                : config.retryInterval();

        int attempt = 0;
        while (true) {
            attempt++;
            AttemptView view = new AttemptView(request, resource, attempt);
            long attemptStart = System.currentTimeMillis();
            int entered = 0;
            HttpResponse response = null;
            RuntimeException failure = null;
            try {
                for (int i = 0; i < interceptors.size(); i++) {
                    entered = i + 1;
                    interceptors.get(i).before(view);
                }
                response = transport.execute(request, attempt);
            } catch (RuntimeException e) {
                failure = e;
            }
            view.complete(response, failure, System.currentTimeMillis() - attemptStart);
            afterReverse(entered, view);

            boolean retryable;
            if (failure == null) {
                retryable = HttpRetry.shouldRetryOnStatus(response.statusCode());
            } else {
                retryable = failure instanceof HttpException
                        && HttpRetry.shouldRetryOnFailure((HttpException) failure);
            }
            if (!retryable || attempt >= maxAttempts) {
                if (failure != null) {
                    throw failure;
                }
                return completed(response, start, attempt);
            }
            logRetry(resource, attempt, maxAttempts, failure, response);
            sleep(intervalMillis, attempt);
        }
    }

    /**
     * 逆序收尾；只对 {@code before} 已被调用过的拦截器执行。收尾自身抛出的异常不掩盖已发生的失败，
     * 改为挂到它的抑制异常上。
     *
     * @param entered 已进入的拦截器个数（含抛出者）
     * @param view    本次尝试的只读视图
     */
    private void afterReverse(int entered, AttemptView view) {
        RuntimeException firstAfterFailure = null;
        for (int i = entered - 1; i >= 0; i--) {
            try {
                interceptors.get(i).after(view);
            } catch (RuntimeException e) {
                Throwable pending = view.error();
                if (pending != null) {
                    pending.addSuppressed(e);
                } else if (firstAfterFailure == null) {
                    firstAfterFailure = e;
                } else {
                    firstAfterFailure.addSuppressed(e);
                }
            }
        }
        if (view.error() == null && firstAfterFailure != null) {
            throw firstAfterFailure;
        }
    }

    /**
     * 重建最终度量：总耗时与总尝试次数。
     *
     * @param response 最后一次成功响应
     * @param start    调用起始时刻
     * @param attempts 总尝试次数
     * @return 度量已写回的响应快照
     */
    private static HttpResponse completed(HttpResponse response, long start, int attempts) {
        return HttpResponse.of(response.statusCode(), response.headers(), response.bodyAsBytes(),
                System.currentTimeMillis() - start, attempts);
    }

    /**
     * 记录「将重试」：出站高频，模块只在这一处记录。
     *
     * @param resource    资源名
     * @param attempt     已完成的尝试序号
     * @param maxAttempts 最大尝试次数
     * @param failure     本次尝试的失败原因，成功时为 {@code null}
     * @param response    本次尝试的响应，失败时为 {@code null}
     */
    private static void logRetry(String resource, int attempt, int maxAttempts,
                                RuntimeException failure, HttpResponse response) {
        Log log = Log.get(DefaultHttpClient.class);
        String reason = failure != null
                ? failure.getClass().getName() + ": " + failure.getMessage()
                : "status=" + response.statusCode();
        log.warn("HTTP 请求将重试: " + resource + "，attempt=" + attempt + "/" + maxAttempts + "，原因=" + reason);
    }

    /**
     * 按固定间隔等待；被中断时恢复中断标志并失败。
     *
     * @param millis  等待毫秒数，非正数表示不等待
     * @param attempt 当前尝试序号
     * @throws HttpException 等待被中断
     */
    private static void sleep(long millis, int attempt) {
        if (millis <= 0L) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpException("重试等待被中断", e, attempt);
        }
    }

    /**
     * 一次尝试的只读视图：进入阶段只有请求信息，收尾阶段另含结果与耗时。
     */
    private static final class AttemptView implements HttpInterceptor.HttpAttempt {

        private final HttpRequest request;

        private final String resource;

        private final int attempt;

        private HttpResponse response;

        private Throwable error;

        private long elapsedMillis;

        private AttemptView(HttpRequest request, String resource, int attempt) {
            this.request = request;
            this.resource = resource;
            this.attempt = attempt;
        }

        /**
         * 写入本次尝试的结果。
         *
         * @param response      成功响应，失败时为 {@code null}
         * @param error         失败原因，成功时为 {@code null}
         * @param elapsedMillis 本次尝试耗时
         */
        private void complete(HttpResponse response, Throwable error, long elapsedMillis) {
            this.response = response;
            this.error = error;
            this.elapsedMillis = elapsedMillis;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public String method() {
            return request.method();
        }

        @Override
        public String url() {
            return request.url();
        }

        @Override
        public String resource() {
            return resource;
        }

        @Override
        public int attempt() {
            return attempt;
        }

        @Override
        public HttpResponse response() {
            return response;
        }

        @Override
        public Throwable error() {
            return error;
        }

        @Override
        public long elapsedMillis() {
            return elapsedMillis;
        }
    }
}
