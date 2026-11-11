/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.internal;

import cn.hg.aifei.http.HttpRequest;
import cn.hg.aifei.http.HttpResponse;

/**
 * 传输契约：真正发出一次尝试的实现形态。
 *
 * <p><b>不是扩展点</b>：本接口包级可见，应用侧在类型层面无法引用、也无法注册；全模块唯一实现是
 * {@link JdkHttpTransport}，无 {@code META-INF/services} 声明。它存在的唯一目的是让单测注入伪实现，
 * 从而在不联网的前提下覆盖链路顺序、重试与资源名等编排语义。</p>
 */
interface HttpTransport {

    /**
     * 发送一次尝试。
     *
     * @param request 请求
     * @param attempt 本次尝试序号，从 1 起
     * @return 单次尝试的响应快照（耗时与尝试次数为本次尝试口径，由调用方重建总度量）
     */
    HttpResponse execute(HttpRequest request, int attempt);
}
