/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.internal;

import cn.hg.aifei.http.HttpException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 重试决策：幂等方法白名单、尝试次数换算、重试触发条件（网络层失败 / 5xx）只在本类定义。
 *
 * <p>出站重放可能造成重复下单之类的后果，故默认不重试，且只有 {@code GET} / {@code HEAD} 提供重试入口；
 * 4xx / 3xx / 2xx、解码失败、解压失败、超限、非法 URL 均属确定性失败，不重试。</p>
 */
public final class HttpRetry {

    /**
     * 幂等方法白名单（唯一来源）。
     */
    public static final Set<String> IDEMPOTENT_METHODS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList("GET", "HEAD"))
    );

    /**
     * 5xx 状态码下界（含）。
     */
    private static final int SERVER_ERROR_MIN = 500;

    /**
     * 5xx 状态码上界（含）。
     */
    private static final int SERVER_ERROR_MAX = 599;

    /**
     * 禁止创建工具类实例。
     */
    private HttpRetry() {
    }

    /**
     * 校验方法是否具备重试入口。
     *
     * @param method 请求方法
     * @throws IllegalArgumentException 方法非幂等（不静默忽略）
     */
    public static void requireIdempotent(String method) {
        if (!isIdempotent(method)) {
            throw new IllegalArgumentException(
                    "非幂等方法不支持重试: " + method + "（仅 " + IDEMPOTENT_METHODS + " 可开启重试）");
        }
    }

    /**
     * 换算本次调用最多尝试几次。
     *
     * @param method             请求方法
     * @param requestRetryTimes  逐请求重试次数，{@code < 0} 表示继承全局默认
     * @param configRetryTimes   全局默认重试次数
     * @return 最大尝试次数：非幂等方法恒为 1，否则 {@code 1 + 生效重试次数}
     */
    public static int maxAttempts(String method, int requestRetryTimes, int configRetryTimes) {
        if (!isIdempotent(method)) {
            return 1;
        }
        int retryTimes = requestRetryTimes >= 0 ? requestRetryTimes : configRetryTimes;
        if (retryTimes < 0) {
            retryTimes = 0;
        }
        return 1 + retryTimes;
    }

    /**
     * 判断网络层失败是否可重试。
     *
     * @param e 传输层抛出的异常
     * @return 属于网络层失败时 {@code true}
     */
    public static boolean shouldRetryOnFailure(HttpException e) {
        return e instanceof HttpIoException;
    }

    /**
     * 判断状态码是否可重试（仅 5xx）。
     *
     * @param statusCode 响应状态码
     * @return 属于 500–599 时 {@code true}
     */
    public static boolean shouldRetryOnStatus(int statusCode) {
        return statusCode >= SERVER_ERROR_MIN && statusCode <= SERVER_ERROR_MAX;
    }

    /**
     * 判断方法是否幂等，取值大小写不敏感。
     *
     * @param method 请求方法
     * @return 幂等时 {@code true}
     */
    private static boolean isIdempotent(String method) {
        return method != null && IDEMPOTENT_METHODS.contains(method.toUpperCase(Locale.ROOT));
    }
}
