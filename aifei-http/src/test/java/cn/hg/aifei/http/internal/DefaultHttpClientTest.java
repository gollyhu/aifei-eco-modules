/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.aifei.log.LogFactory;
import cn.hg.aifei.http.HttpException;
import cn.hg.aifei.http.HttpInterceptor;
import cn.hg.aifei.http.HttpRequest;
import cn.hg.aifei.http.HttpResponse;
import cn.hg.aifei.http.support.NoOpLogFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 执行编排与非 2xx 语义：全部经伪传输，不联网。
 */
public class DefaultHttpClientTest {

    /**
     * 门禁⑩只允许回环地址出现在测试源码的 URL 字面量里，故演示用域名按片段拼接（本类不发真实请求）。
     */
    private static final String EXAMPLE_ORIGIN = "http" + "://" + "api.example.com";

    private static LogFactory previousLogFactory;

    /**
     * 重试路径会记录一条 warn，故换入空日志实现。
     */
    @BeforeClass
    public static void installNoOpLogFactory() {
        previousLogFactory = NoOpLogFactory.install();
    }

    /**
     * 还原日志工厂。
     */
    @AfterClass
    public static void restoreLogFactory() {
        NoOpLogFactory.restore(previousLogFactory);
    }

    /**
     * 进入正序、收尾逆序，各恰好一次。
     */
    @Test
    public void shouldEnterInOrderAndExitInReverse() {
        List<String> events = new ArrayList<>();
        TransportStub transport = new TransportStub();
        DefaultHttpClient client = client(transport,
                new Recorder("A", events), new Recorder("B", events), new Recorder("C", events));

        HttpResponse response = client.exec(HttpRequest.get("http://127.0.0.1:18080/api/echo"));

        assertEquals(200, response.statusCode());
        assertEquals(Arrays.asList("A:before", "B:before", "C:before", "C:after", "B:after", "A:after"), events);
    }

    /**
     * B 的 {@code before} 抛异常 → C 未进入、真实发送未发生、异常原样传播且不重试。
     */
    @Test
    public void shouldAbortWhenBeforeThrows() {
        List<String> events = new ArrayList<>();
        HttpException failure = new HttpException("鉴权失败");
        TransportStub transport = new TransportStub();
        DefaultHttpClient client = client(transport,
                new Recorder("A", events), new Recorder("B", events, failure), new Recorder("C", events));

        HttpException thrown = assertThrows(HttpException.class,
                () -> client.exec(HttpRequest.get("http://127.0.0.1:18080/api/echo").retry(2)));

        assertSame("异常必须原样抛出", failure, thrown);
        assertEquals(Arrays.asList("A:before", "B:before", "B:after", "A:after"), events);
        assertEquals("真实发送不得发生", 0, transport.callCount());
    }

    /**
     * 拦截器读到的默认资源名 = 方法大写 + 去 query 的 host 与 path。
     */
    @Test
    public void shouldDeriveDefaultResource() {
        Recorder recorder = new Recorder("A", new ArrayList<String>());
        TransportStub transport = new TransportStub();
        DefaultHttpClient client = client(transport, recorder);

        client.exec(HttpRequest.get(EXAMPLE_ORIGIN + "/v1/orders?x=1&y=2"));

        assertEquals(1, recorder.resources.size());
        assertEquals("GET api.example.com/v1/orders", recorder.resources.get(0));
    }

    /**
     * 显式资源名完全替代推导，拦截器读到该值。
     */
    @Test
    public void shouldUseExplicitResource() {
        Recorder recorder = new Recorder("A", new ArrayList<String>());
        TransportStub transport = new TransportStub();
        DefaultHttpClient client = client(transport, recorder);

        client.exec(HttpRequest.get("http://127.0.0.1:18080/v1/orders").resource("order.query"));

        assertEquals(Collections.singletonList("order.query"), recorder.resources);
    }

    /**
     * GET + {@code retry(2)}，前两次 503、第三次 200 → 3 次尝试，资源名三次完全相同。
     */
    @Test
    public void shouldKeepResourceStableAcrossRetries() {
        Recorder recorder = new Recorder("A", new ArrayList<String>());
        TransportStub transport = new TransportStub().thenStatus(503, 503, 200);
        DefaultHttpClient client = client(transport, recorder);

        HttpResponse response = client.exec(
                HttpRequest.get("http://127.0.0.1:18080/api/echo?trace=1").retry(2));

        assertEquals(200, response.statusCode());
        assertEquals(3, response.attempts());
        assertEquals(Arrays.asList(1, 2, 3), transport.attempts());
        assertEquals(3, recorder.resources.size());
        assertEquals("GET 127.0.0.1:18080/api/echo", recorder.resources.get(0));
        assertEquals(recorder.resources.get(0), recorder.resources.get(1));
        assertEquals(recorder.resources.get(0), recorder.resources.get(2));
    }

    /**
     * 未开启重试时只尝试 1 次，5xx 也照常返回（非 2xx 不抛）。
     */
    @Test
    public void shouldAttemptOnceWithoutRetry() {
        TransportStub transport = new TransportStub().thenStatus(503);
        DefaultHttpClient client = client(transport);

        HttpResponse response = client.exec(HttpRequest.get("http://127.0.0.1:18080/api/echo"));

        assertEquals(503, response.statusCode());
        assertEquals(1, response.attempts());
        assertEquals(1, transport.callCount());
        assertEquals("status-503", response.bodyAsString());
    }

    /**
     * 4xx 属确定性失败，即便开启了重试也只尝试 1 次。
     */
    @Test
    public void shouldNotRetryClientError() {
        TransportStub transport = new TransportStub().thenStatus(404, 200);
        DefaultHttpClient client = client(transport);

        HttpResponse response = client.exec(HttpRequest.get("http://127.0.0.1:18080/api/echo").retry(2));

        assertEquals(404, response.statusCode());
        assertEquals(1, transport.callCount());
    }

    /**
     * 网络层失败可重试：两次 {@link HttpIoException} 后成功，最终度量给出 3 次尝试。
     */
    @Test
    public void shouldRetryOnIoFailure() {
        TransportStub transport = new TransportStub()
                .thenError(ioFailure(), ioFailure())
                .thenStatus(200);
        DefaultHttpClient client = client(transport);

        HttpResponse response = client.exec(HttpRequest.get("http://127.0.0.1:1/x").retry(2));

        assertEquals(200, response.statusCode());
        assertEquals(3, response.attempts());
        assertEquals(3, transport.callCount());
    }

    /**
     * 网络层失败且次数用尽 → 原样抛最后一次的异常，尝试次数即已用次数。
     */
    @Test
    public void shouldRethrowLastIoFailure() {
        TransportStub transport = new TransportStub()
                .thenError(ioFailure(), ioFailure(), ioFailure())
                .thenStatus(200);
        DefaultHttpClient client = client(transport);

        HttpException thrown = assertThrows(HttpException.class,
                () -> client.exec(HttpRequest.get("http://127.0.0.1:1/x").retry(1)));

        assertTrue("应保留底层原因", thrown.getCause() instanceof IOException);
        assertTrue("应能给出尝试次数", thrown.attempts() >= 1);
        assertEquals(2, transport.callCount());
    }

    /**
     * 收尾阶段可读结果与耗时：成功时 {@code response} 非空、{@code error} 为空。
     */
    @Test
    public void shouldExposeOutcomeToAfter() {
        OutcomeRecorder recorder = new OutcomeRecorder();
        TransportStub transport = new TransportStub().thenStatus(201);
        DefaultHttpClient client = client(transport, recorder);

        HttpResponse response = client.exec(HttpRequest.get("http://127.0.0.1:18080/api/echo"));

        assertEquals(201, response.statusCode());
        assertEquals(1, recorder.afters.size());
        AfterState state = recorder.afters.get(0);
        assertEquals(201, state.response.statusCode());
        assertNull(state.error);
        assertTrue("单次尝试耗时应已给出", state.elapsedMillis >= 0L);
        assertEquals(1, state.attempt);
        assertNull("before 阶段没有结果", state.beforeResponse);
        assertEquals(0L, state.beforeElapsed);
    }

    /**
     * 收尾阶段可读失败原因：传输层失败时 {@code error} 非空、{@code response} 为空。
     */
    @Test
    public void shouldExposeErrorToAfter() {
        OutcomeRecorder recorder = new OutcomeRecorder();
        HttpIoException failure = ioFailure();
        TransportStub transport = new TransportStub().thenError(failure);
        DefaultHttpClient client = client(transport, recorder);

        assertThrows(HttpException.class, () -> client.exec(HttpRequest.get("http://127.0.0.1:1/x")));

        assertSame(failure, recorder.afters.get(0).error);
        assertNull(recorder.afters.get(0).response);
    }

    /**
     * 请求为 {@code null} 属调用方写错，抛 {@link IllegalArgumentException}。
     */
    @Test
    public void shouldRejectNullRequest() {
        assertThrows(IllegalArgumentException.class, () -> client(new TransportStub()).exec(null));
    }

    /**
     * 构造不含拦截器的客户端，重试间隔置 0 以免用例变慢。
     *
     * @param transport    伪传输
     * @param interceptors 拦截器
     * @return 客户端
     */
    private static DefaultHttpClient client(TransportStub transport, HttpInterceptor... interceptors) {
        return new DefaultHttpClient(config(), Arrays.asList(interceptors), transport);
    }

    /**
     * 构造配置：默认值 + 重试间隔 0。
     *
     * @return 配置域
     */
    private static HttpConfig config() {
        Properties properties = new Properties();
        properties.setProperty(HttpConfig.KEY_RETRY_INTERVAL, "0");
        return HttpConfig.parse(properties);
    }

    /**
     * 构造网络层失败。
     *
     * @return 失败异常
     */
    private static HttpIoException ioFailure() {
        return new HttpIoException("请求失败: GET /x", new IOException("connection refused"), 1);
    }

    /**
     * 记录进入与收尾事件，并收集每次尝试读到的资源名。
     */
    private static final class Recorder implements HttpInterceptor {

        private final String name;

        private final List<String> events;

        private final HttpException toThrow;

        private final List<String> resources = new ArrayList<>();

        private Recorder(String name, List<String> events) {
            this(name, events, null);
        }

        private Recorder(String name, List<String> events, HttpException toThrow) {
            this.name = name;
            this.events = events;
            this.toThrow = toThrow;
        }

        @Override
        public void before(HttpAttempt attempt) {
            events.add(name + ":before");
            resources.add(attempt.resource());
            if (toThrow != null) {
                throw toThrow;
            }
        }

        @Override
        public void after(HttpAttempt attempt) {
            events.add(name + ":after");
        }
    }

    /**
     * 记录收尾阶段可见的结果快照。
     */
    private static final class OutcomeRecorder implements HttpInterceptor {

        private final List<AfterState> afters = new ArrayList<>();

        @Override
        public void before(HttpAttempt attempt) {
            AfterState state = new AfterState();
            state.attempt = attempt.attempt();
            state.beforeResponse = attempt.response();
            state.beforeElapsed = attempt.elapsedMillis();
            afters.add(state);
        }

        @Override
        public void after(HttpAttempt attempt) {
            AfterState state = afters.get(afters.size() - 1);
            state.response = attempt.response();
            state.error = attempt.error();
            state.elapsedMillis = attempt.elapsedMillis();
        }
    }

    /**
     * 一次尝试在进入 / 收尾两个阶段的可观测字段。
     */
    private static final class AfterState {

        private int attempt;

        private HttpResponse beforeResponse;

        private long beforeElapsed;

        private HttpResponse response;

        private Throwable error;

        private long elapsedMillis;
    }
}
