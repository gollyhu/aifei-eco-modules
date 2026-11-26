/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.support;

import cn.hg.aifei.sms.internal.sms4j.SmsBlendFactory;
import java.lang.reflect.Method;
import java.util.function.Function;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.universal.SupplierConfig;

/**
 * 测试辅助：经反射调用 {@code SmsBlendFactory} 的包级注入点（测试与模块不同包）。
 */
public final class MockBlendInstaller {

    private MockBlendInstaller() {
    }

    /**
     * @param factory 构建函数；{@code null} 表示恢复默认真实工厂
     */
    public static void install(Function<SupplierConfig, SmsBlend> factory) {
        try {
            Method method = SmsBlendFactory.class.getDeclaredMethod("setBlendFactory", Function.class);
            method.setAccessible(true);
            method.invoke(null, new Object[]{factory});
        } catch (Exception e) {
            throw new IllegalStateException("install mock blend factory failed", e);
        }
    }

    /** 恢复默认工厂，避免测试间相互污染。 */
    public static void reset() {
        install(null);
    }
}
