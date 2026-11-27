/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.internal.sms4j;

import cn.hg.aifei.sms.internal.SmsErrors;

/**
 * sms4j 依赖探测唯一 {@code Class.forName} 点。
 *
 * <p>第二参 {@code false} 表示只加载不初始化；缺失时包装为可读异常，不裸抛 {@code NoClassDefFoundError}。</p>
 */
public final class SmsDeps {

    private static final String SMS_FACTORY_CLASS = "org.dromara.sms4j.core.factory.SmsFactory";

    private SmsDeps() {
    }

    /**
     * 以本类类加载器探测 sms4j 是否可用（装配期调用）。
     *
     * @throws cn.hg.aifei.sms.SmsException sms4j 缺失
     */
    public static void requireSms4j() {
        requireSms4j(SmsDeps.class.getClassLoader());
    }

    /**
     * 以指定类加载器探测（供测试模拟依赖缺失）。
     *
     * @param loader 类加载器
     * @throws cn.hg.aifei.sms.SmsException sms4j 缺失
     */
    static void requireSms4j(ClassLoader loader) {
        try {
            Class.forName(SMS_FACTORY_CLASS, false, loader);
        } catch (ClassNotFoundException e) {
            throw SmsErrors.sms4jMissing();
        } catch (LinkageError e) {
            throw SmsErrors.sms4jMissing();
        }
    }
}
