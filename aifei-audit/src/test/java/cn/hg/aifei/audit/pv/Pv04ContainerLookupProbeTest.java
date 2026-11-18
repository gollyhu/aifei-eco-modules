/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.pv;

import cn.aifei.aop.Aop;
import cn.aifei.aop.AopKit;
import org.junit.Assert;
import org.junit.Test;

/**
 * PV-04（设计关自加实证，非需求指定的三项之一）：SPI 实现「从容器按类型取」的实际语义。
 *
 * <p>需求 Q14 的默认假设是「从容器按类型取（与 {@code MqPlugin} / {@code JobPlugin} 同通道），同一类型多实现视为
 * 配置错误并启动失败」。框架只有 {@code AopKit.addSingletonObject(type, obj)} 一个注册入口，本用例确认：</p>
 *
 * <pre>
 * ① 未注册时 {@code Aop.get(接口)} 的行为（决定「无 sink」如何判定）；
 * ② 注册后取到的是同一个实例；
 * ③ 同类型二次注册直接抛 {@code RuntimeException("Singleton object already exists for type : ...")} ——
 *    「同一时刻只有一个生效 sink」由框架保证，且运行期无法换实现（决定了 sink 切换只能在消费方用委托实现做）。
 * </pre>
 */
public class Pv04ContainerLookupProbeTest {

    /**
     * 探针 SPI 接口：模拟 {@code AuditSink} 这类「消费方实现、模块按类型取」的契约。
     */
    public interface ProbeSpi {

        String id();
    }

    /**
     * 实现 A。
     */
    public static class SpiA implements ProbeSpi {

        @Override
        public String id() {
            return "A";
        }
    }

    /**
     * 实现 B。
     */
    public static class SpiB implements ProbeSpi {

        @Override
        public String id() {
            return "B";
        }
    }

    /**
     * ① 未注册：接口无法实例化，{@code Aop.get} 抛 {@link RuntimeException}（不是返回 {@code null}）。
     */
    @Test
    public void unregisteredInterfaceThrows() {
        RuntimeException failure = null;
        try {
            Aop.get(ProbeSpi.class);
        } catch (RuntimeException e) {
            failure = e;
        }
        Assert.assertNotNull("未注册的接口类型应抛 RuntimeException，不能返回 null", failure);
    }

    /**
     * ② 注册后取到同一实例；③ 再次注册同一类型被框架拒绝（不覆盖、不并存），原实例继续生效。
     */
    @Test
    public void registeredSingletonIsReturnedAndSecondRegistrationRejected() {
        SpiA first = new SpiA();
        AopKit.get().addSingletonObject(ProbeSpi.class, first);
        Assert.assertSame("按类型取到注册的同一实例", first, Aop.get(ProbeSpi.class));

        RuntimeException failure = null;
        try {
            AopKit.get().addSingletonObject(ProbeSpi.class, new SpiB());
        } catch (RuntimeException e) {
            failure = e;
        }
        Assert.assertNotNull("同类型二次注册应被拒绝", failure);
        Assert.assertTrue("拒绝消息应指明类型已存在：" + failure.getMessage(),
                failure.getMessage().contains("already exists"));
        Assert.assertSame("拒绝后原实例继续生效（框架不可能在运行期换实现）", first, Aop.get(ProbeSpi.class));
        Assert.assertEquals("A", Aop.get(ProbeSpi.class).id());
    }
}
