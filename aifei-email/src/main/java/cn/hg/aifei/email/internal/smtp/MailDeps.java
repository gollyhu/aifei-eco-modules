/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.internal.smtp;

import cn.hg.aifei.email.internal.MailErrors;

/**
 * 邮件内核依赖探测唯一 {@code Class.forName} 点（门禁锁定全模块仅此一处）。
 *
 * <p>第二参 {@code false} 表示只加载不初始化；缺失时包装为可读异常，不裸抛
 * {@code NoClassDefFoundError}。探测在装配期执行，启动即失败优于延迟到首次发送。</p>
 */
public final class MailDeps {

    private static final String MAIL_FACTORY_CLASS = "org.dromara.email.core.factory.MailFactory";

    private MailDeps() {
    }

    /**
     * 以本类类加载器探测邮件内核是否可用（装配期调用）。
     *
     * @throws cn.hg.aifei.email.MailException 内核依赖缺失
     */
    public static void requireSms4j() {
        requireSms4j(MailDeps.class.getClassLoader());
    }

    /**
     * 以指定类加载器探测（包级钩子，供测试与示例工程模拟依赖缺失，不进入公共面）。
     *
     * @param loader 类加载器
     * @throws cn.hg.aifei.email.MailException 内核依赖缺失
     */
    static void requireSms4j(ClassLoader loader) {
        try {
            Class.forName(MAIL_FACTORY_CLASS, false, loader);
        } catch (ClassNotFoundException e) {
            throw MailErrors.sms4jMissing();
        } catch (LinkageError e) {
            throw MailErrors.sms4jMissing();
        }
    }
}
