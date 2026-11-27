/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.internal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.aifei.log.LogFactory;
import cn.hg.aifei.http.HttpException;
import cn.hg.aifei.http.HttpInterceptor;
import cn.hg.aifei.http.HttpRequest;
import cn.hg.aifei.http.HttpResponse;
import cn.hg.aifei.http.support.LoopbackServer;
import cn.hg.aifei.http.support.NoOpLogFactory;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 真传输集成用例（覆盖重试、超时、拦截、资源名、编码、gzip、体上限等）：
 * 只访问本机回环端点，断网环境可全绿。
 */
public class JdkHttpTransportTest {

    /**
     * 超时用例的宽容余量：建连侧。
     */
    private static final long CONNECT_TOLERANCE_MILLIS = 500L;

    /**
     * 超时用例的宽容余量：读侧。
     */
    private static final long READ_TOLERANCE_MILLIS = 800L;

    /**
     * 未被监听的回环端口（连接立即被拒绝）。
     */
    private static final String CLOSED_LOOPBACK_URL = "http://127.0.0.1:1/x";

    private static LoopbackServer server;

    private static LogFactory previousLogFactory;

    /**
     * 启动回环端点，并换入空日志实现（重试路径会记录一条 warn）。
     *
     * @throws IOException 绑定端口失败
     */
    @BeforeClass
    public static void startServer() throws IOException {
        previousLogFactory = NoOpLogFactory.install();
        server = new LoopbackServer();
    }

    /**
     * 关闭回环端点并还原日志工厂。
     */
    @AfterClass
    public static void stopServer() {
        server.close();
        NoOpLogFactory.restore(previousLogFactory);
    }

    /**
     * 每个用例前清空端点计数。
     */
    @Before
    public void resetServer() {
        server.reset();
    }

    /**
     * 未开启重试时端点计数为 1。
     */
    @Test
    public void shouldNotRetryByDefault() {
        HttpResponse response = client(config()).exec(HttpRequest.get(server.url("/flaky?fail=1")));

        assertEquals(503, response.statusCode());
        assertEquals(1, response.attempts());
        assertEquals(1, server.requestCount());
    }

    /**
     * GET + {@code retry(2)}，前 2 次 503、第 3 次 200 → 计数 3、尝试 3。
     */
    @Test
    public void shouldRetryServerErrorForIdempotentMethod() {
        HttpResponse response = client(config()).exec(
                HttpRequest.get(server.url("/flaky?fail=2")).retry(2));

        assertEquals(200, response.statusCode());
        assertEquals(3, response.attempts());
        assertEquals(3, server.requestCount());
    }

    /**
     * POST 无法开启重试（当场拒绝），未开启时端点计数为 1。
     */
    @Test
    public void shouldRejectRetryForPost() {
        String url = server.url("/flaky?fail=1");

        assertThrows(IllegalArgumentException.class, () -> HttpRequest.post(url).retry(1));

        HttpResponse response = client(config()).exec(HttpRequest.post(url));

        assertEquals(503, response.statusCode());
        assertEquals(1, server.requestCount());
    }

    /**
     * 连接未监听端口 → {@link HttpException}，cause 为底层 IO 异常，耗时受限。
     */
    @Test
    public void shouldFailWhenConnectRefused() {
        HttpRequest request = HttpRequest.get(CLOSED_LOOPBACK_URL).connectTimeout(300);
        long start = System.currentTimeMillis();

        HttpException e = assertThrows(HttpException.class, () -> client(config()).exec(request));

        long elapsed = System.currentTimeMillis() - start;
        assertTrue("cause 应为底层 IO 异常", e.getCause() instanceof IOException);
        assertEquals("请求失败: GET " + CLOSED_LOOPBACK_URL, e.getMessage());
        assertEquals(1, e.attempts());
        assertTrue("耗时应有界，实际 " + elapsed, elapsed <= 300L + CONNECT_TOLERANCE_MILLIS);
    }

    /**
     * 读超时 → {@link HttpException}，耗时至少达到读超时。
     */
    @Test
    public void shouldFailOnReadTimeout() {
        HttpRequest request = HttpRequest.get(server.url("/delay?ms=600")).readTimeout(100);
        long start = System.currentTimeMillis();

        assertThrows(HttpException.class, () -> client(config()).exec(request));

        long elapsed = System.currentTimeMillis() - start;
        assertTrue("耗时应达到读超时，实际 " + elapsed, elapsed >= 100L);
        assertTrue("耗时应受限，实际 " + elapsed, elapsed <= 100L + READ_TOLERANCE_MILLIS);
    }

    /**
     * 全局读超时 5000、逐请求 100 → 按 100 触发。
     */
    @Test
    public void shouldPreferRequestReadTimeout() {
        HttpConfig config = config(HttpConfig.KEY_READ_TIMEOUT, "5000");
        HttpRequest request = HttpRequest.get(server.url("/delay?ms=300")).readTimeout(100);
        long start = System.currentTimeMillis();

        assertThrows(HttpException.class, () -> client(config).exec(request));

        long elapsed = System.currentTimeMillis() - start;
        assertTrue("逐请求超时应生效，实际 " + elapsed, elapsed < 1000L);
    }

    /**
     * 拦截器抛异常 → 真实发送未发生（端点计数 0）。
     */
    @Test
    public void shouldNotSendWhenInterceptorThrows() {
        HttpInterceptor failing = attempt -> {
            throw new HttpException("鉴权失败");
        };

        assertThrows(HttpException.class,
                () -> client(config(), failing).exec(HttpRequest.get(server.url("/echo/query"))));

        assertEquals(0, server.requestCount());
    }

    /**
     * 重试各次读到的资源名完全相同。
     */
    @Test
    public void shouldKeepResourceStableOnRetry() {
        List<String> resources = new ArrayList<>();
        HttpInterceptor recorder = new HttpInterceptor() {
            @Override
            public void before(HttpAttempt attempt) {
                resources.add(attempt.resource());
            }
        };

        client(config(), recorder).exec(HttpRequest.get(server.url("/flaky?fail=2")).retry(2));

        assertEquals(3, resources.size());
        assertEquals("GET 127.0.0.1:" + port() + "/flaky", resources.get(0));
        assertEquals(resources.get(0), resources.get(1));
        assertEquals(resources.get(0), resources.get(2));
    }

    /**
     * 5xx 正常返回且响应体可读；404 即便开启重试也不重试。
     */
    @Test
    public void shouldReturnNonSuccessResponses() {
        HttpResponse serverError = client(config()).exec(HttpRequest.get(server.url("/status?code=500")));

        assertEquals(500, serverError.statusCode());
        assertEquals("status=500", serverError.bodyAsString());

        server.reset();
        HttpResponse notFound = client(config()).exec(
                HttpRequest.get(server.url("/status?code=404")).retry(2));

        assertEquals(404, notFound.statusCode());
        assertEquals(1, server.requestCount());
    }

    /**
     * 非法 URL → {@link HttpException}，cause 为 {@link MalformedURLException}。
     */
    @Test
    public void shouldFailOnIllegalUrl() {
        HttpException e = assertThrows(HttpException.class,
                () -> client(config()).exec(HttpRequest.get("not-a-url")));

        assertTrue(e.getMessage().contains("not-a-url"));
        assertTrue(e.getCause() instanceof MalformedURLException);
    }

    /**
     * 中文 + emoji 往返一致；GBK 响应按 GBK 解码；二进制字节级相等。
     */
    @Test
    public void shouldRoundTripTextAndBinary() {
        String chinese = "中文🙂";
        HttpResponse textEcho = client(config()).exec(HttpRequest.post(server.url("/echo/body"))
                .contentType("text/plain; charset=UTF-8")
                .body(chinese));
        assertEquals(chinese, textEcho.bodyAsString());

        byte[] binary = new byte[]{0x00, 0x41, 0x7F, (byte) 0xFF};
        HttpResponse binaryEcho = client(config()).exec(HttpRequest.post(server.url("/echo/body"))
                .contentType("application/octet-stream")
                .body(binary));
        assertArrayEquals(binary, binaryEcho.bodyAsBytes());

        HttpResponse gbk = client(config()).exec(HttpRequest.get(server.url("/gbk")));
        assertEquals("中文响应", gbk.bodyAsString());
    }

    /**
     * 状态码 / 头（大小写不敏感 + 多值）/ 体 / 耗时 / 尝试次数均可读。
     */
    @Test
    public void shouldExposeResponseMetrics() {
        HttpResponse response = client(config()).exec(HttpRequest.get(server.url("/echo/query?x=1")));

        assertEquals(200, response.statusCode());
        assertNotNull("头查找应大小写不敏感", response.header("content-type"));
        assertEquals(Arrays.asList("a=1", "b=2"), response.headers("SET-COOKIE"));
        assertEquals("x=1", response.bodyAsString());
        assertTrue("耗时应非负", response.elapsedMillis() >= 0L);
        assertEquals(1, response.attempts());
    }

    /**
     * query 中文 / 空格 / {@code &} 编码正确；自定义头可达；{@code byte[]} 体字节级到达。
     */
    @Test
    public void shouldEncodeQueryAndDeliverHeadersAndBody() throws IOException {
        String value = "中文 空格&x";
        String expected = "q=" + URLEncoder.encode(value, "UTF-8");

        HttpResponse queryEcho = client(config()).exec(
                HttpRequest.get(server.url("/echo/query")).query("q", value));
        assertEquals(expected, queryEcho.bodyAsString());

        HttpResponse headerEcho = client(config()).exec(
                HttpRequest.get(server.url("/echo/header")).header("X-Demo", "v1"));
        assertEquals("v1", headerEcho.bodyAsString());

        byte[] binary = "字节".getBytes(StandardCharsets.UTF_8);
        HttpResponse bodyEcho = client(config()).exec(HttpRequest.put(server.url("/echo/body"))
                .contentType("application/octet-stream")
                .body(binary));
        assertArrayEquals(binary, bodyEcho.bodyAsBytes());
    }

    /**
     * 默认不发送 {@code Accept-Encoding}；302 不跟随；关闭 gzip 时原样返回压缩字节。
     */
    @Test
    public void shouldNotAcceptGzipByDefaultNorFollowRedirect() {
        HttpResponse encoding = client(config()).exec(HttpRequest.get(server.url("/echo/accept-encoding")));
        assertEquals("accept-encoding=null", encoding.bodyAsString());

        HttpResponse redirect = client(config()).exec(HttpRequest.get(server.url("/redirect")));
        assertEquals(302, redirect.statusCode());

        byte[] raw = client(config()).exec(HttpRequest.get(server.url("/gzip"))).bodyAsBytes();
        assertFalse("未开启 gzip 时不得解压", Arrays.equals("gzip-payload".getBytes(StandardCharsets.UTF_8), raw));
    }

    /**
     * 显式开启 gzip 后自动发送协商头并解压。
     */
    @Test
    public void shouldDecompressGzipWhenEnabled() {
        HttpResponse response = client(config(HttpConfig.KEY_GZIP, "true"))
                .exec(HttpRequest.get(server.url("/gzip")));

        assertEquals("gzip-payload", response.bodyAsString());
    }

    /**
     * 超限 → {@link HttpException}（消息含上限值）；未超限正常返回。
     */
    @Test
    public void shouldFailWhenResponseExceedsLimit() {
        HttpConfig config = config(HttpConfig.KEY_MAX_RESPONSE_BYTES, "32");

        HttpException e = assertThrows(HttpException.class,
                () -> client(config).exec(HttpRequest.get(server.url("/big?bytes=64"))));
        assertTrue("消息应含生效上限值：" + e.getMessage(), e.getMessage().contains("32"));

        HttpResponse ok = client(config).exec(HttpRequest.get(server.url("/big?bytes=16")));
        assertEquals(16, ok.bodyAsBytes().length);
    }

    /**
     * 端点端口。
     *
     * @return 端口号
     */
    private static int port() {
        String base = server.baseUrl();
        return Integer.parseInt(base.substring(base.lastIndexOf(':') + 1));
    }

    /**
     * 构造客户端（真传输）。
     *
     * @param config       配置
     * @param interceptors 拦截器
     * @return 客户端
     */
    private static DefaultHttpClient client(HttpConfig config, HttpInterceptor... interceptors) {
        return new DefaultHttpClient(config, Arrays.asList(interceptors));
    }

    /**
     * 构造默认配置：重试间隔置 0，避免用例变慢。
     *
     * @return 配置
     */
    private static HttpConfig config() {
        return config(HttpConfig.KEY_RETRY_INTERVAL, "0");
    }

    /**
     * 构造配置：在「重试间隔 0」基础上覆盖指定键。
     *
     * @param key   键名
     * @param value 取值
     * @return 配置
     */
    private static HttpConfig config(String key, String value) {
        Properties properties = new Properties();
        properties.setProperty(HttpConfig.KEY_RETRY_INTERVAL, "0");
        properties.setProperty(key, value);
        return HttpConfig.parse(properties);
    }
}
