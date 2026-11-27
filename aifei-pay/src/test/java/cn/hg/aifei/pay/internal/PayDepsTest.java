/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.pay.PayException;
import cn.hg.aifei.pay.PayNotifyUtil;
import cn.hg.aifei.pay.support.PayTestEnv;
import cn.hg.aifei.pay.support.PayTestProps;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 可选依赖缺失的可读异常：任一内核依赖不在 classpath 时，首次调用抛 {@link PayException}
 * 并给出缺失坐标，不裸抛 {@code NoClassDefFoundError} / {@code ClassNotFoundException}。
 *
 * <p><b>手法</b>：不改 pom、不启新进程，而是把线程上下文类加载器换成「对指定包前缀不可见」的加载器——
 * {@link PayDeps} 的探测点以 TCCL 优先，因此这等价于「消费方漏声明依赖」的观测结果。</p>
 */
public class PayDepsTest {

    /**
     * 被测应用编号。
     */
    private static final String APP = PayTestProps.APP_MAIN;

    /**
     * 原始线程上下文类加载器，用例结束后还原。
     */
    private ClassLoader originalTccl;

    /**
     * 记录并保留原始 TCCL。
     */
    @Before
    public void rememberTccl() {
        originalTccl = Thread.currentThread().getContextClassLoader();
    }

    /**
     * 还原 TCCL 与门面持有器。
     */
    @After
    public void restoreTccl() {
        Thread.currentThread().setContextClassLoader(originalTccl);
        PayTestEnv.clear();
    }

    /**
     * 依赖齐备时探测通过（对照组，证明其余用例的失败来自「缺失」而不是探测恒抛）。
     */
    @Test
    public void shouldPassWhenDependenciesArePresent() {
        PayDeps.requireKernel();
        PayDeps.requireBouncyCastle();
    }

    /**
     * IJPay 不可见 → {@link PayException}，消息含缺失坐标，cause 为原始加载失败。
     */
    @Test
    public void shouldReportMissingIjpayWithCoordinates() {
        Thread.currentThread().setContextClassLoader(hiding("com.ijpay."));

        PayException e = assertThrows(PayException.class, PayDeps::requireKernel);

        assertTrue("消息应含构件名：" + e.getMessage(), e.getMessage().contains("IJPay-AliPay-V3"));
        assertTrue("消息应含 groupId：" + e.getMessage(), e.getMessage().contains("com.github.javen205"));
        assertTrue("消息应含版本提示：" + e.getMessage(), e.getMessage().contains("3.0.0"));
        assertTrue("cause 应为类加载失败", e.getCause() instanceof ClassNotFoundException);
        assertEquals("依赖缺失不属于内核业务失败，不应带错误码", null, e.code());
    }

    /**
     * 支付宝 SDK 不可见（IJPay 仍在）→ 消息指出 SDK 坐标。
     */
    @Test
    public void shouldReportMissingSdkWithCoordinates() {
        Thread.currentThread().setContextClassLoader(hiding("com.alipay."));

        PayException e = assertThrows(PayException.class, PayDeps::requireKernel);

        assertTrue("消息应含构件名：" + e.getMessage(), e.getMessage().contains("alipay-sdk-java-v3"));
        assertTrue("消息应含 groupId：" + e.getMessage(), e.getMessage().contains("com.alipay.sdk"));
    }

    /**
     * 证书模式缺 BouncyCastle → 探测期即失败，消息指出 bcprov 坐标。
     */
    @Test
    public void shouldReportMissingBouncyCastle() {
        Thread.currentThread().setContextClassLoader(hiding("org.bouncycastle."));

        PayException e = assertThrows(PayException.class, PayDeps::requireBouncyCastle);

        assertTrue("消息应含构件名：" + e.getMessage(), e.getMessage().contains("bcprov-jdk18on"));
        assertTrue("消息应含版本提示：" + e.getMessage(), e.getMessage().contains("1.86"));
    }

    /**
     * 交易动作入口把依赖缺失转成可读异常，调用方看不到 {@code NoClassDefFoundError}。
     */
    @Test
    public void shouldSurfaceMissingDependencyFromTradeCall() {
        DefaultPayClient client = new DefaultPayClient(PayConfig.parse(PayTestProps.keyMode(APP)),
                new PayKernelStub());
        Thread.currentThread().setContextClassLoader(hiding("com.ijpay."));

        PayException e = assertThrows(PayException.class,
                () -> client.precreate(APP, "T2026010100001", 1L, "测试订单", PayTestProps.NOTIFY_URL));

        assertTrue("消息应是依赖缺失提示：" + e.getMessage(), e.getMessage().contains("缺少支付宝依赖"));
    }

    /**
     * 验签入口同样给出可读异常（而非裸抛加载错误）。
     */
    @Test
    public void shouldSurfaceMissingDependencyFromVerifyCall() {
        PayTestEnv.install(PayTestProps.keyMode(APP));
        Map<String, String> params = new LinkedHashMap<>();
        params.put("out_trade_no", "T2026010100001");
        params.put("sign", "any");
        Thread.currentThread().setContextClassLoader(hiding("com.alipay."));

        PayException e = assertThrows(PayException.class,
                () -> PayNotifyUtil.verify(APP, params));

        assertTrue("消息应是依赖缺失提示：" + e.getMessage(), e.getMessage().contains("缺少支付宝依赖"));
    }

    /**
     * 构造一个对指定包前缀不可见的类加载器（其余类型仍可加载）。
     *
     * @param hiddenPrefix 不可见的包前缀
     * @return 类加载器
     */
    private static ClassLoader hiding(final String hiddenPrefix) {
        return new ClassLoader(PayDepsTest.class.getClassLoader()) {

            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith(hiddenPrefix)) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };
    }
}
