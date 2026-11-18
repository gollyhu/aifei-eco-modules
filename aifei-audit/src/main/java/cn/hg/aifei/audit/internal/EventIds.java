/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.internal;

import java.util.UUID;

/**
 * 事件号生成唯一实现点（非承诺 API）：{@code UUID} 去横线的 32 位十六进制，零依赖、无线程争用。
 */
public final class EventIds {

    private EventIds() {
    }

    /**
     * @return 模块内唯一的事件号
     */
    public static String next() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
