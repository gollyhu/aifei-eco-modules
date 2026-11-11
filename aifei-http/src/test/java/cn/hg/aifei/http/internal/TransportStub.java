/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.internal;

import cn.hg.aifei.http.HttpRequest;
import cn.hg.aifei.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * 伪传输实现：录制每次调用的请求与尝试序号，按预设脚本返回响应或抛出指定异常。
 *
 * <p>供编排用例在不联网的前提下覆盖链路顺序、重试与资源名语义；脚本用尽后按 {@code 200} 空体响应兜底。</p>
 */
final class TransportStub implements HttpTransport {

    /**
     * 脚本里的一次「返回指定状态码」动作。
     */
    private static final String STATUS_ACTION = "status";

    /**
     * 脚本里的一次「抛出指定异常」动作。
     */
    private static final String ERROR_ACTION = "error";

    private final List<HttpRequest> requests = new ArrayList<>();

    private final List<Integer> attempts = new ArrayList<>();

    private final Deque<Object[]> plan = new ArrayDeque<>();

    /**
     * 追加一步：返回指定状态码与固定响应体。
     *
     * @param statuses 依次返回的状态码
     * @return 本伪实现
     */
    TransportStub thenStatus(int... statuses) {
        for (int status : statuses) {
            plan.add(new Object[]{STATUS_ACTION, Integer.valueOf(status)});
        }
        return this;
    }

    /**
     * 追加一步：返回完整响应。
     *
     * @param response 响应
     * @return 本伪实现
     */
    TransportStub thenResponse(HttpResponse response) {
        plan.add(new Object[]{STATUS_ACTION, response});
        return this;
    }

    /**
     * 追加一步：抛出指定异常。
     *
     * @param errors 依次抛出的异常
     * @return 本伪实现
     */
    TransportStub thenError(RuntimeException... errors) {
        for (RuntimeException error : errors) {
            plan.add(new Object[]{ERROR_ACTION, error});
        }
        return this;
    }

    /**
     * 被调用的次数。
     *
     * @return 调用次数
     */
    int callCount() {
        return requests.size();
    }

    /**
     * 每次调用收到的请求。
     *
     * @return 不可变视图
     */
    List<HttpRequest> requests() {
        return Collections.unmodifiableList(requests);
    }

    /**
     * 每次调用收到的尝试序号。
     *
     * @return 不可变视图
     */
    List<Integer> attempts() {
        return Collections.unmodifiableList(attempts);
    }

    /**
     * 执行一次尝试；脚本用尽时返回固定响应体为 {@code ok} 的 200。
     *
     * @param request 请求
     * @param attempt 尝试序号
     * @return 响应
     */
    @Override
    public HttpResponse execute(HttpRequest request, int attempt) {
        requests.add(request);
        attempts.add(Integer.valueOf(attempt));
        Object[] step = plan.poll();
        if (step == null) {
            return HttpResponse.of(200, null, "ok".getBytes(StandardCharsets.UTF_8), 1L, attempt);
        }
        if (ERROR_ACTION.equals(step[0])) {
            throw (RuntimeException) step[1];
        }
        Object payload = step[1];
        if (payload instanceof HttpResponse) {
            return (HttpResponse) payload;
        }
        int status = ((Integer) payload).intValue();
        return HttpResponse.of(status, null, ("status-" + status).getBytes(StandardCharsets.UTF_8), 1L, attempt);
    }
}
