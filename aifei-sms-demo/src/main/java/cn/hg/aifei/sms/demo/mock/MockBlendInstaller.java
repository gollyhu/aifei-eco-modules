/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.demo.mock;

import cn.hg.aifei.sms.internal.sms4j.SmsBlendFactory;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.entity.SmsResponse;
import org.dromara.sms4j.api.universal.SupplierConfig;

/**
 * 反射桥接：设置 {@code SmsBlendFactory} 的包级 mock 注入点，使 demo 全程零外网。
 */
public final class MockBlendInstaller {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private MockBlendInstaller() {
    }

    /**
     * 安装按通道生成不同流水号的 mock 工厂。
     */
    public static void install() {
        Function<SupplierConfig, SmsBlend> factory = new Function<SupplierConfig, SmsBlend>() {
            @Override
            public SmsBlend apply(SupplierConfig config) {
                SmsResponse response = new SmsResponse();
                response.setSuccess(true);
                Map<String, Object> data = new LinkedHashMap<String, Object>();
                data.put("BizId", "MOCK-" + config.getConfigId().toUpperCase()
                        + "-" + SEQUENCE.incrementAndGet());
                data.put("Message", "mock send accepted");
                response.setData(data);
                response.setConfigId(config.getConfigId());
                return new MockSmsBlend(config.getConfigId(), config.getSupplier(), response);
            }
        };
        try {
            Method method = SmsBlendFactory.class.getDeclaredMethod("setBlendFactory", Function.class);
            method.setAccessible(true);
            method.invoke(null, new Object[]{factory});
        } catch (Exception e) {
            throw new IllegalStateException("install mock blend factory failed", e);
        }
    }
}
