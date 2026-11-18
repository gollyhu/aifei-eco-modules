/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.demo.biz;

import cn.aifei.core.Path;
import cn.aifei.demo.common.arch.Out;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 对照用的未标注动作：同一包内被拦截器覆盖，但方法上没有 {@code @Audit}，故零事件。
 */
@Path("/audit/demo")
public class PlainOperationService {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    /**
     * 场景 1 对照：未标注动作，点击后事件列表不应新增。
     *
     * @return 业务返回值
     */
    @Path("unannotated")
    public Out unannotated() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("returnValue", "no-audit-" + SEQUENCE.incrementAndGet());
        return Out.ok().data(data);
    }
}
