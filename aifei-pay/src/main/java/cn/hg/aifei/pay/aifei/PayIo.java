/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.aifei;

import java.util.Map;

/**
 * 回调传输桥接：由接入方适配实际 HTTP 容器。
 */
public interface PayIo {

    String path();

    String param(String name);

    Map<String, String> params();

    String header(String name);

    Map<String, String> headers();

    String body();

    void write(int status, String response);
}
