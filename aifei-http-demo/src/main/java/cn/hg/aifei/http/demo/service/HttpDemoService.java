/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.demo.service;

import cn.aifei.aop.Inject;
import cn.aifei.core.Para;
import cn.aifei.core.Path;
import cn.aifei.demo.common.arch.Out;
import cn.aifei.util.PropKit;
import cn.aifei.util.StrUtil;
import cn.hg.aifei.http.HttpClient;
import cn.hg.aifei.http.HttpException;
import cn.hg.aifei.http.HttpRequest;
import cn.hg.aifei.http.HttpResponse;
import cn.hg.aifei.http.demo.common.DemoAppConfig;
import cn.hg.aifei.http.demo.demo.HttpTrace;
import cn.hg.aifei.http.demo.echo.EchoState;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 演示 Service：用真实装配的 {@code @Inject HttpClient} 请求本机 {@code /demo/echo}，覆盖需求 §八 的全部场景。
 *
 * <p>所有端点均为 GET，返回 {@link Out}（HTTP 恒 200，业务语义由 {@code code} 承载，{@code 0} 为成功）。
 * 「预期会失败」的演示（读超时、连接被拒）同样返回 {@code code=0}，失败细节放在 {@code error} 字段里，
 * 页面据此渲染错误面板——这样前端不必把「演示失败」当成接口故障。</p>
 *
 * <p><b>零外网</b>：所有请求目标都由 {@link DemoAppConfig#selfBaseUrl()} 拼出，或为 {@code 127.0.0.1:1}
 * 这个本机未监听端口；{@code /http/demo/send} 传入的路径含 {@code ://} 即拒绝。</p>
 */
@Path("/http/demo")
public class HttpDemoService {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    /**
     * 响应体预览最大长度（字符）。
     */
    private static final int MAX_BODY_PREVIEW = 4000;

    /**
     * 本机未监听端口：用于演示连接被拒（仍属回环，不出网）。
     */
    private static final String CONNECT_REFUSED_URL = "http://127.0.0.1:1/aifei-http-demo-never-listening";

    /**
     * 装配期注册的客户端单例；为空说明插件未装配，所有演示端点会给出明确消息而不是静默失败。
     */
    @Inject
    HttpClient client;

    /**
     * 演示页视图：由 Enjoy 从 {@code webapp/view/http/index.html} 渲染（模板根为 {@code webapp/view}）。
     *
     * @return 模板名，必须以 {@code /} 开头
     */
    @Path("page")
    public String page() {
        return "/http/index.html";
    }

    /**
     * 现场快照：模块配置七键、是否装配、已登记拦截器、累计请求 / 尝试 / 重试次数、echo 计数与 JVM 连接池属性。
     *
     * @return 装配与运行态快照
     */
    @Path("state")
    public Out state() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("httpConfig", httpConfig());
        data.put("clientInjected", client != null);
        data.put("interceptors", HttpTrace.interceptors());
        data.put("selfBaseUrl", DemoAppConfig.selfBaseUrl());
        data.put("echoUrl", DemoAppConfig.echoUrl());
        data.put("requestCount", HttpTrace.requestCount());
        data.put("attemptCount", HttpTrace.attemptCount());
        data.put("retryCount", HttpTrace.retryCount());
        data.put("traceEventCount", HttpTrace.eventCount());
        data.put("traceCapacity", HttpTrace.CAPACITY);
        data.put("lastResource", HttpTrace.lastResource());
        data.put("echoCounters", EchoState.snapshot());
        data.put("jvmProperties", jvmProperties());
        return Out.ok().data(data);
    }

    /**
     * 基本 GET：把 {@code status} / {@code delay} / {@code failFirst} 透传给 echo，返回状态码、耗时与响应。
     *
     * @param status 期望状态码（100–599）
     * @param delay echo 侧延迟毫秒数
     * @param failFirst echo 侧前 N 次返回 503
     * @param key echo 侧计数分组名
     * @return 请求描述 + 响应（或错误）详情
     */
    @Path("get")
    public Out get(@Para(name = "status") String status,
                   @Para(name = "delay") String delay,
                   @Para(name = "failFirst") String failFirst,
                   @Para(name = "key") String key) {
        int statusCode = parseInt(status, 200);
        long delayMillis = Math.max(parseLong(delay, 0L), 0L);
        int failFirstTimes = Math.max(parseInt(failFirst, 0), 0);
        String counterKey = StrUtil.isBlank(key) ? "demo-get" : key.trim();
        EchoState.reset(counterKey);
        HttpRequest request = HttpRequest.get(DemoAppConfig.echoUrl())
                .query("status", statusCode)
                .query("delay", delayMillis)
                .query("failFirst", failFirstTimes)
                .query("key", counterKey);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("request", describe(request));
        data.putAll(runRequest(request));
        return Out.ok().data(data);
    }

    /**
     * 重试对照：同一条件下「默认不重试」与「显式 retry(N)」各跑一次，返回两次的尝试次数。
     *
     * @param failFirst echo 侧前 N 次返回 503，默认 2
     * @param retryTimes 显式重试次数，默认 2
     * @param intervalMillis 显式重试间隔（毫秒），缺省用配置 {@code http.retry-interval}
     * @return 两次运行结果 + 第二次运行的穿链记录
     */
    @Path("retry")
    public Out retry(@Para(name = "failFirst") String failFirst,
                     @Para(name = "retryTimes") String retryTimes,
                     @Para(name = "intervalMillis") String intervalMillis) {
        int failFirstTimes = parseInt(failFirst, 2);
        if (failFirstTimes < 1) {
            failFirstTimes = 2;
        }
        int times = parseInt(retryTimes, 2);
        if (times < 1) {
            times = 2;
        }
        long interval = parseLong(intervalMillis, -1L);

        // ① 默认不重试：配置 http.retry-times = 0，首次 503 即返回
        EchoState.reset("demo-retry-default");
        HttpRequest withoutRetry = HttpRequest.get(DemoAppConfig.echoUrl())
                .query("failFirst", failFirstTimes)
                .query("key", "demo-retry-default");
        Map<String, Object> withoutRetryResult = runRequest(withoutRetry);

        // ② 显式开启重试：前 N 次 503 触发重试，第 N+1 次成功
        EchoState.reset("demo-retry-explicit");
        HttpRequest withRetry = HttpRequest.get(DemoAppConfig.echoUrl())
                .query("failFirst", failFirstTimes)
                .query("key", "demo-retry-explicit");
        if (interval >= 0L) {
            withRetry.retry(times, interval);
        } else {
            withRetry.retry(times);
        }
        HttpTrace.reset();
        Map<String, Object> withRetryResult = runRequest(withRetry);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("failFirst", failFirstTimes);
        data.put("retryTimes", times);
        data.put("configRetryTimes", PropKit.get("http.retry-times", "0"));
        data.put("configRetryInterval", PropKit.get("http.retry-interval", "200"));
        data.put("withoutRetry", withoutRetryResult);
        data.put("withRetry", withRetryResult);
        data.put("trace", HttpTrace.snapshot());
        data.put("explain", "http.retry-times 默认 0 = 不重试：首次即返回 503 的响应；显式 retry(N) 后 5xx 触发重试，"
                + "attempts 应为 N+1（4xx / 3xx / 2xx 不重试）");
        return Out.ok().data(data);
    }

    /**
     * 超时对照：正常（不超时）/ 读超时 / 连接被拒，三条分支返回异常类型、消息、cause 与耗时。
     *
     * @param delay echo 侧延迟毫秒数（读超时分支用），默认 1500
     * @param readTimeout 读超时分支的逐请求读超时（毫秒），默认 300
     * @param connectTimeout 连接被拒分支的建连超时（毫秒），默认 500
     * @return 三条分支的运行结果与期望说明
     */
    @Path("timeout")
    public Out timeout(@Para(name = "delay") String delay,
                       @Para(name = "readTimeout") String readTimeout,
                       @Para(name = "connectTimeout") String connectTimeout) {
        long delayMillis = Math.max(parseLong(delay, 1500L), 1L);
        int readTimeoutMillis = Math.max(parseInt(readTimeout, 300), 1);
        int connectTimeoutMillis = Math.max(parseInt(connectTimeout, 500), 1);

        // ① 未超时：回环 100ms 延迟 + 全局读超时（http.read-timeout）
        EchoState.reset("demo-timeout-normal");
        HttpRequest normal = HttpRequest.get(DemoAppConfig.echoUrl())
                .query("delay", 100L)
                .query("key", "demo-timeout-normal");
        Map<String, Object> normalResult = runRequest(normal);

        // ② 读超时：echo 延迟 > 逐请求读超时 → HttpException（cause 为 SocketTimeoutException）
        EchoState.reset("demo-timeout-read");
        HttpRequest readTimeoutRequest = HttpRequest.get(DemoAppConfig.echoUrl())
                .query("delay", delayMillis)
                .query("key", "demo-timeout-read")
                .readTimeout(readTimeoutMillis);
        Map<String, Object> readTimeoutResult = runRequest(readTimeoutRequest);

        // ③ 连接被拒：127.0.0.1 上未监听端口 → HttpException（cause 为 ConnectException）
        HttpRequest connectRefused = HttpRequest.get(CONNECT_REFUSED_URL).connectTimeout(connectTimeoutMillis);
        Map<String, Object> connectRefusedResult = runRequest(connectRefused);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("configReadTimeout", PropKit.get("http.read-timeout", "10000"));
        data.put("configConnectTimeout", PropKit.get("http.connect-timeout", "5000"));
        data.put("delayMillis", delayMillis);
        data.put("readTimeoutMillis", readTimeoutMillis);
        data.put("connectTimeoutMillis", connectTimeoutMillis);
        data.put("normal", normalResult);
        data.put("readTimeout", readTimeoutResult);
        data.put("connectRefused", connectRefusedResult);
        data.put("readTimeoutAsExpected", !Boolean.TRUE.equals(readTimeoutResult.get("ok")));
        data.put("connectRefusedAsExpected", !Boolean.TRUE.equals(connectRefusedResult.get("ok")));
        data.put("expectation", "读超时分支应抛 HttpException（cause 为 SocketTimeoutException），耗时不少于逐请求读超时；"
                + "连接被拒分支应抛 HttpException（cause 为 ConnectException）；两者均不返回 HttpResponse");
        return Out.ok().data(data);
    }

    /**
     * 拦截器与资源名对照：先按默认推导跑一次，再按显式 {@code resource} 跑一次，返回 before / after 顺序与推导结果。
     *
     * @param resource 显式资源名；缺省用 {@code demo.resource.explicit}
     * @param status echo 侧响应状态码
     * @return 两次运行的穿链顺序、事件流与资源名
     */
    @Path("interceptor")
    public Out interceptor(@Para(name = "resource") String resource,
                          @Para(name = "status") String status) {
        int statusCode = parseInt(status, 200);
        String explicitResource = StrUtil.isBlank(resource) ? "demo.resource.explicit" : resource.trim();

        // ① 默认推导
        HttpTrace.reset();
        EchoState.reset("demo-interceptor-default");
        HttpRequest byDefault = HttpRequest.get(DemoAppConfig.echoUrl())
                .query("status", statusCode)
                .query("key", "demo-interceptor-default");
        Map<String, Object> defaultResult = runRequest(byDefault);
        Map<String, Object> defaultRun = new LinkedHashMap<String, Object>();
        defaultRun.put("beforeOrder", HttpTrace.order("before"));
        defaultRun.put("afterOrder", HttpTrace.order("after"));
        defaultRun.put("resource", HttpTrace.lastResource());
        defaultRun.put("events", HttpTrace.snapshot());
        defaultRun.putAll(defaultResult);

        // ② 显式覆盖
        HttpTrace.reset();
        EchoState.reset("demo-interceptor-explicit");
        HttpRequest byExplicit = HttpRequest.get(DemoAppConfig.echoUrl())
                .query("status", statusCode)
                .query("key", "demo-interceptor-explicit")
                .resource(explicitResource);
        Map<String, Object> explicitResult = runRequest(byExplicit);
        Map<String, Object> explicitRun = new LinkedHashMap<String, Object>();
        explicitRun.put("beforeOrder", HttpTrace.order("before"));
        explicitRun.put("afterOrder", HttpTrace.order("after"));
        explicitRun.put("resource", HttpTrace.lastResource());
        explicitRun.put("events", HttpTrace.snapshot());
        explicitRun.putAll(explicitResult);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("interceptors", HttpTrace.interceptors());
        data.put("defaultResource", defaultRun.get("resource"));
        data.put("explicitResource", explicitResource);
        data.put("defaultRun", defaultRun);
        data.put("explicitRun", explicitRun);
        data.put("resourceRule", "默认资源名 = METHOD + 空格 + host[:port] + path（去 query、method 大写）；"
                + "resource() 完全替代默认推导；一次 exec 内冻结一次，重试各次恒定");
        data.put("chainRule", "before 按登记顺序进入（正序），after 逆序收尾；每次尝试各穿链一次");
        return Out.ok().data(data);
    }

    /**
     * gzip 演示：观察模块实际发出的 {@code Accept-Encoding}，以及模块对 {@code Content-Encoding: gzip}
     * 响应体的解压结果（与 {@code http.gzip} 配置对应）。
     *
     * @return 探针请求、压缩请求与解压判定
     */
    @Path("gzip")
    public Out gzip() {
        String gzipConfig = PropKit.get("http.gzip", "false");
        boolean moduleGzip = gzipConfig != null && "true".equalsIgnoreCase(gzipConfig.trim());

        // ① 探针：echo 把收到的 Accept-Encoding 回写到响应头 X-Echo-Accept-Encoding
        EchoState.reset("demo-gzip-probe");
        HttpRequest probe = HttpRequest.get(DemoAppConfig.echoUrl()).query("key", "demo-gzip-probe");
        Map<String, Object> probeResult = runRequest(probe);

        // ② 压缩响应：echo 用 gzip 压缩一段含中文与 emoji 的文本
        EchoState.reset("demo-gzip");
        String text = "aifei-http gzip 解压演示：中文与 emoji 😀 往返正确";
        HttpRequest compressed = HttpRequest.get(DemoAppConfig.echoUrl())
                .query("gzip", 1)
                .query("key", "demo-gzip")
                .query("text", text);
        Map<String, Object> compressedResult = runRequest(compressed);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("moduleGzip", Boolean.valueOf(moduleGzip));
        data.put("echoSawAcceptEncoding", responseHeader(probeResult, "X-Echo-Accept-Encoding"));
        data.put("expectedText", text);
        data.put("probe", probeResult);
        data.put("compressed", compressedResult);
        data.put("decompressed", Boolean.valueOf(text.equals(responseBody(compressedResult))));
        data.put("note", "http.gzip=true 时模块自动发 Accept-Encoding: gzip 并在模块内解压；"
                + "改为 false 后模块不发该头、也不解压（压缩字节原样返回，bodyAsString 读到的不是原文）");
        return Out.ok().data(data);
    }

    /**
     * 重定向演示：echo 返回 302，模块固定不跟随，3xx 原样返回。
     *
     * @return 状态码、Location 与判定结果
     */
    @Path("redirect")
    public Out redirect() {
        EchoState.reset("demo-redirect");
        HttpRequest request = HttpRequest.get(DemoAppConfig.echoUrl())
                .query("redirect", 1)
                .query("key", "demo-redirect");
        Map<String, Object> result = runRequest(request);
        Map<String, Object> response = responseOf(result);
        int status = response == null ? 0 : parseInt(String.valueOf(response.get("status")), 0);
        boolean isRedirectStatus = status == 301 || status == 302 || status == 303 || status == 307 || status == 308;

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("request", describe(request));
        data.putAll(result);
        data.put("location", responseHeader(result, "Location"));
        data.put("followedRedirect", Boolean.valueOf(!isRedirectStatus));
        data.put("note", "模块固定 setInstanceFollowRedirects(false)：302 原样返回，不提供 http.follow-redirects 配置键");
        return Out.ok().data(data);
    }

    /**
     * 通用发送：按页面构造的方法 / 路径 / 查询串 / 请求头 / 请求体 / 显式资源名 / 逐请求超时 / 重试发出一次请求。
     *
     * <p>只接受<b>本机相对路径</b>（{@code path}）：含 {@code ://} 一律拒绝，保证零外网。</p>
     *
     * @param method 请求方法，默认 GET
     * @param path 本机相对路径，默认 {@code /demo/echo}
     * @param query 查询串原文（可带 {@code ?}），由页面自行编码
     * @param headers 请求头，多行文本，每行 {@code Name: Value}
     * @param body 请求体文本
     * @param contentType 内容类型（决定文本体编码）
     * @param resource 显式资源名
     * @param connectTimeout 逐请求建连超时（毫秒），{@code > 0} 才生效
     * @param readTimeout 逐请求读超时（毫秒），{@code > 0} 才生效
     * @param retryTimes 显式重试次数，缺省不调用 {@code retry()}（继承 {@code http.retry-times}）
     * @param intervalMillis 显式重试间隔（毫秒），缺省用配置
     * @return 「即将发出的请求」描述 + 响应（或错误）+ 本次穿链记录
     */
    @Path("send")
    public Out send(@Para(name = "method") String method,
                    @Para(name = "path") String path,
                    @Para(name = "query") String query,
                    @Para(name = "headers") String headers,
                    @Para(name = "body") String body,
                    @Para(name = "contentType") String contentType,
                    @Para(name = "resource") String resource,
                    @Para(name = "connectTimeout") String connectTimeout,
                    @Para(name = "readTimeout") String readTimeout,
                    @Para(name = "retryTimes") String retryTimes,
                    @Para(name = "intervalMillis") String intervalMillis) {
        String requestMethod = StrUtil.isBlank(method) ? "GET" : method.trim().toUpperCase(Locale.ROOT);
        String url;
        try {
            url = DemoAppConfig.resolveSelfUrl(path);
        } catch (IllegalArgumentException e) {
            return Out.fail(e.getMessage());
        }
        if (!StrUtil.isBlank(query)) {
            String queryString = query.trim();
            url = url + (queryString.startsWith("?") ? queryString : "?" + queryString);
        }

        HttpRequest request;
        try {
            request = HttpRequest.of(requestMethod, url);
        } catch (IllegalArgumentException e) {
            return Out.fail("构造请求失败: " + e.getMessage());
        }

        List<String> rejected = new ArrayList<String>();
        if (!StrUtil.isBlank(contentType)) {
            request.contentType(contentType.trim());
        }
        if (!StrUtil.isBlank(body)) {
            request.body(body);
        }
        if (!StrUtil.isBlank(resource)) {
            try {
                request.resource(resource.trim());
            } catch (IllegalArgumentException e) {
                rejected.add("显式资源名被拒绝: " + e.getMessage());
            }
        }
        int connectTimeoutMillis = parseInt(connectTimeout, 0);
        if (connectTimeoutMillis > 0) {
            request.connectTimeout(connectTimeoutMillis);
        }
        int readTimeoutMillis = parseInt(readTimeout, 0);
        if (readTimeoutMillis > 0) {
            request.readTimeout(readTimeoutMillis);
        }
        appendHeaders(request, headers, rejected);
        int times = parseInt(retryTimes, -1);
        if (times >= 0) {
            long interval = parseLong(intervalMillis, -1L);
            try {
                if (interval >= 0L) {
                    request.retry(times, interval);
                } else {
                    request.retry(times);
                }
            } catch (IllegalArgumentException e) {
                // 非幂等方法（如 POST）调用 retry 会被当场拒绝，这本身是演示点，不当作接口失败
                rejected.add("重试被拒绝: " + e.getMessage());
            }
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("request", describe(request));
        data.put("rejected", rejected);
        HttpTrace.reset();
        data.putAll(runRequest(request));
        data.put("trace", HttpTrace.snapshot());
        return Out.ok().data(data);
    }

    /**
     * 重置 echo 端点的 {@code failFirst} 计数分组，并把清理前的计数一并返回。
     *
     * @return 清理前的计数器与清理后的空快照
     */
    @Path("echo/state/reset")
    public Out resetEchoState() {
        Map<String, Object> cleared = EchoState.resetAll();
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("clearedCounters", cleared);
        data.put("counters", EchoState.snapshot());
        data.put("note", "已清空 echo 端点全部 failFirst 计数分组");
        return Out.ok().data(data);
    }

    // ===== 内部工具 =====================================================================

    /**
     * 发一次请求并把结果整理成「成功响应」或「异常详情」，演示预期失败也走这里。
     */
    private Map<String, Object> runRequest(HttpRequest request) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        long start = System.currentTimeMillis();
        try {
            HttpResponse response = exec(request);
            result.put("ok", Boolean.TRUE);
            result.put("response", describeResponse(response));
        } catch (RuntimeException e) {
            result.put("ok", Boolean.FALSE);
            result.put("error", describeError(e, start));
        }
        result.put("serviceElapsedMillis", Long.valueOf(System.currentTimeMillis() - start));
        return result;
    }

    /**
     * 唯一发送入口：客户端未装配时给出明确异常（由 {@link #runRequest(HttpRequest)} 转成 error 字段）。
     */
    private HttpResponse exec(HttpRequest request) {
        if (client == null) {
            throw new IllegalStateException("HttpClient 未装配：请确认 DemoAppConfig#config(Plugins) 已登记 HttpPlugin");
        }
        return client.exec(request);
    }

    private Map<String, Object> describeResponse(HttpResponse response) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("status", Integer.valueOf(response.statusCode()));
        data.put("elapsedMillis", Long.valueOf(response.elapsedMillis()));
        data.put("attempts", Integer.valueOf(response.attempts()));
        data.put("headers", new LinkedHashMap<String, Object>(response.headers()));
        byte[] bytes = response.bodyAsBytes();
        data.put("bodySize", Integer.valueOf(bytes == null ? 0 : bytes.length));
        try {
            String text = response.bodyAsString();
            data.put("body", preview(text));
            data.put("bodyLength", Integer.valueOf(text == null ? 0 : text.length()));
        } catch (RuntimeException e) {
            data.put("body", null);
            data.put("decodeError", describeError(e, System.currentTimeMillis()));
        }
        return data;
    }

    private Map<String, Object> describeError(Throwable error, long start) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("type", error.getClass().getName());
        data.put("simpleType", error.getClass().getSimpleName());
        data.put("message", error.getMessage());
        Throwable cause = error.getCause();
        data.put("causeType", cause == null ? null : cause.getClass().getName());
        data.put("causeMessage", cause == null ? null : cause.getMessage());
        data.put("attempts", Integer.valueOf(error instanceof HttpException
                ? ((HttpException) error).attempts() : 0));
        data.put("elapsedMillis", Long.valueOf(System.currentTimeMillis() - start));
        return data;
    }

    private Map<String, Object> describe(HttpRequest request) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("method", request.method());
        data.put("url", request.url());
        data.put("headers", new LinkedHashMap<String, Object>(request.headers()));
        byte[] body = request.body();
        data.put("bodySize", Integer.valueOf(body == null ? 0 : body.length));
        data.put("body", body == null ? null : new String(body, UTF8));
        data.put("explicitResource", request.explicitResource());
        data.put("connectTimeout", Integer.valueOf(request.connectTimeout()));
        data.put("readTimeout", Integer.valueOf(request.readTimeout()));
        data.put("retryTimes", Integer.valueOf(request.retryTimes()));
        data.put("retryIntervalMillis", Long.valueOf(request.retryIntervalMillis()));
        return data;
    }

    /**
     * 逐行解析 {@code Name: Value} 形式的请求头并追加（同名不覆盖，用于演示多值头）。
     */
    private void appendHeaders(HttpRequest request, String headers, List<String> rejected) {
        if (StrUtil.isBlank(headers)) {
            return;
        }
        String[] lines = headers.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            int index = line.indexOf(':');
            if (index <= 0 || index == line.length() - 1) {
                rejected.add("请求头格式非法（需 Name: Value）: " + line);
                continue;
            }
            String name = line.substring(0, index).trim();
            String value = line.substring(index + 1).trim();
            try {
                request.addHeader(name, value);
            } catch (IllegalArgumentException e) {
                rejected.add(e.getMessage());
            }
        }
    }

    private Map<String, Object> httpConfig() {
        Map<String, Object> http = new LinkedHashMap<String, Object>();
        http.put("http.connect-timeout", PropKit.get("http.connect-timeout", "5000"));
        http.put("http.read-timeout", PropKit.get("http.read-timeout", "10000"));
        http.put("http.retry-times", PropKit.get("http.retry-times", "0"));
        http.put("http.retry-interval", PropKit.get("http.retry-interval", "200"));
        http.put("http.max-response-bytes", PropKit.get("http.max-response-bytes", "8388608"));
        http.put("http.gzip", PropKit.get("http.gzip", "false"));
        http.put("http.user-agent", PropKit.get("http.user-agent", "aifei-http/1.0.0"));
        return http;
    }

    /**
     * JVM 连接池全局属性（只读展示，模块 v1 只读不写）。
     */
    private Map<String, Object> jvmProperties() {
        Map<String, Object> jvm = new LinkedHashMap<String, Object>();
        String maxConnections = System.getProperty("http.maxConnections");
        String keepAlive = System.getProperty("http.keepAlive");
        jvm.put("http.maxConnections", maxConnections == null ? "未设置（JDK 默认 5）" : maxConnections);
        jvm.put("http.keepAlive", keepAlive == null ? "未设置（JDK 默认 true）" : keepAlive);
        jvm.put("note", "只读展示：aifei-http v1 不写这两个全局属性，连接池行为受它们约束");
        return jvm;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> responseOf(Map<String, Object> result) {
        Object response = result.get("response");
        return response instanceof Map ? (Map<String, Object>) response : null;
    }

    private String responseHeader(Map<String, Object> result, String name) {
        Map<String, Object> response = responseOf(result);
        if (response == null) {
            return null;
        }
        Object headers = response.get("headers");
        if (!(headers instanceof Map)) {
            return null;
        }
        for (Map.Entry<?, ?> e : ((Map<?, ?>) headers).entrySet()) {
            if (e.getKey() != null && name.equalsIgnoreCase(String.valueOf(e.getKey()))) {
                Object value = e.getValue();
                if (value instanceof List && !((List<?>) value).isEmpty()) {
                    return String.valueOf(((List<?>) value).get(0));
                }
                return value == null ? null : String.valueOf(value);
            }
        }
        return null;
    }

    private String responseBody(Map<String, Object> result) {
        Map<String, Object> response = responseOf(result);
        if (response == null) {
            return null;
        }
        Object body = response.get("body");
        return body == null ? null : String.valueOf(body);
    }

    private String preview(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_BODY_PREVIEW ? text : text.substring(0, MAX_BODY_PREVIEW) + "...(已截断)";
    }

    private static int parseInt(String raw, int defaultValue) {
        if (StrUtil.isBlank(raw)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long parseLong(String raw, long defaultValue) {
        if (StrUtil.isBlank(raw)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
