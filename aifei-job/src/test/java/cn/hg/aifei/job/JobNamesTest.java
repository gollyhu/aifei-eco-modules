/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cn.hg.aifei.job.internal.JobNames;
import cn.hg.aifei.job.internal.JobScanner;
import cn.hg.aifei.job.support.StubCronCalculator;
import cn.hg.aifei.job.support.TestJobContainer;
import cn.hg.aifei.job.support.TestLogs;
import java.lang.reflect.Method;
import java.util.Collections;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 任务名契约用例：任务名恒为「类全名#方法名」，且与错误消息的定位口径一致。
 *
 * <p><b>期望值纪律</b>：用例把期望的任务名写成<b>硬编码字面量</b>（含嵌套类名里的 {@code $}），
 * <b>不经</b>被测的 {@link JobNames} 推导，否则断言恒真、锁不住格式。任务名是 {@code JobKit.names()} /
 * {@code JobKit.state()} 的 key、{@code JobEvent#taskName()} 的取值与全部错误消息的定位口径，
 * 因此只需在本类锁一次。</p>
 */
public class JobNamesTest {

    /**
     * 用例扫描的包名，只用于「扫不到任务」一类消息（本类不涉及）。
     */
    private static final String SCAN_PACKAGE = "cn.hg.aifei.job.names";

    /**
     * 替身认定为合法的表达式。
     */
    private static final String VALID_CRON = "0 0/5 * * * ?";

    /**
     * 装配空日志工厂。
     */
    @BeforeClass
    public static void beforeClass() {
        TestLogs.install();
    }

    // ------------------------------------------------------------------ 格式

    /**
     * 任务名 = 类全名 + {@code #} + 方法名，分隔符只有一个 {@code #}。
     */
    @Test
    public void taskNameIsClassFullNameHashMethodName() {
        String actual = JobNames.of(method(SimpleJob.class, "tick"));
        assertEquals("任务名应为「类全名#方法名」",
                "cn.hg.aifei.job.JobNamesTest$SimpleJob#tick", actual);
        assertEquals("分隔符应恰好出现一次", 1, countHash(actual));
        assertTrue("任务名应以类全名开头", actual.startsWith("cn.hg.aifei.job.JobNamesTest$SimpleJob"));
    }

    /**
     * 命名只依赖「声明类全名 + 方法名」，与调用次数、调用顺序无关。
     */
    @Test
    public void taskNameIsStableAcrossCalls() {
        Method method = method(SimpleJob.class, "tick");
        String first = JobNames.of(method);
        String second = JobNames.of(method);
        assertEquals("同一方法的任务名应恒等", first, second);
        assertEquals("任务名不得随调用变化",
                "cn.hg.aifei.job.JobNamesTest$SimpleJob#tick", JobNames.of(method));
    }

    /**
     * 同名重载方法得到相同任务名（随后由注册表按重名判失败，符合「不静默去重」口径）。
     */
    @Test
    public void overloadedMethodsShareTheSameName() {
        Method noArg = method(OverloadJob.class, "run");
        Method withEvent = method(OverloadJob.class, "run", JobEvent.class);
        assertEquals("同名重载方法应得到相同任务名",
                "cn.hg.aifei.job.JobNamesTest$OverloadJob#run", JobNames.of(noArg));
        assertEquals("同名重载方法应得到相同任务名", JobNames.of(noArg), JobNames.of(withEvent));
    }

    /**
     * 不同类的同名方法得到不同任务名（类全名参与命名）。
     */
    @Test
    public void sameMethodNameInDifferentClassesDiffers() {
        assertEquals("cn.hg.aifei.job.JobNamesTest$SimpleJob#tick",
                JobNames.of(method(SimpleJob.class, "tick")));
        assertEquals("cn.hg.aifei.job.JobNamesTest$OtherJob#tick",
                JobNames.of(method(OtherJob.class, "tick")));
    }

    /**
     * 继承而来的方法按<b>声明类</b>取名，不按取用它的子类取名（与「只扫自身声明的方法」口径一致）。
     */
    @Test
    public void inheritedMethodUsesDeclaringClass() {
        Method inherited = publicMethod(SubJob.class, "base");
        assertEquals("任务名应取方法声明类的全名",
                "cn.hg.aifei.job.JobNamesTest$BaseJob#base", JobNames.of(inherited));
        assertEquals("方法本身应来自父类", BaseJob.class, inherited.getDeclaringClass());
    }

    // ------------------------------------------------------------------ 与错误消息定位口径一致

    /**
     * 扫描期失败消息中的定位串与方法全名（即任务名）逐字一致。
     */
    @Test
    public void errorMessageUsesTheSameTaskName() {
        String expected = JobNames.of(method(StaticBadJob.class, "bad"));
        try {
            new JobScanner(new TestJobContainer(), StubCronCalculator.accepting(VALID_CRON, 1600000000000L),
                    Collections.singletonList(SCAN_PACKAGE))
                    .scan(Collections.<Class<?>>singletonList(StaticBadJob.class));
            fail("static 任务方法应抛 JobException");
        } catch (JobException e) {
            assertTrue("错误消息应含任务名 " + expected + "，实际：" + e.getMessage(),
                    e.getMessage().contains(expected));
            assertTrue("错误消息应含硬编码的类全名#方法名",
                    e.getMessage().contains("cn.hg.aifei.job.JobNamesTest$StaticBadJob#bad"));
        }
    }

    // ------------------------------------------------------------------ 辅助方法

    /**
     * @param type 类
     * @param name 方法名
     * @param parameterTypes 参数类型
     * @return 方法对象
     */
    private static Method method(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("测试用方法缺失：" + name, e);
        }
    }

    /**
     * 取可继承的 {@code public} 方法（含父类声明的方法）。
     *
     * @param type 类
     * @param name 方法名
     * @param parameterTypes 参数类型
     * @return 方法对象
     */
    private static Method publicMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("测试用方法缺失：" + name, e);
        }
    }

    /**
     * @param text 文本
     * @return {@code #} 出现的次数
     */
    private static int countHash(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '#') {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ 测试用任务类

    /**
     * 普通任务类。
     */
    private static final class SimpleJob {

        /**
         * 任务方法。
         */
        public void tick() {
        }
    }

    /**
     * 与 {@link SimpleJob} 方法同名的另一个任务类。
     */
    private static final class OtherJob {

        /**
         * 任务方法。
         */
        public void tick() {
        }
    }

    /**
     * 含同名重载方法的任务类。
     */
    private static final class OverloadJob {

        /**
         * 无参重载。
         */
        public void run() {
        }

        /**
         * 单参重载。
         *
         * @param event 事件快照
         */
        public void run(JobEvent event) {
        }
    }

    /**
     * 声明了注解方法的父类。
     */
    private static class BaseJob {

        /**
         * 父类声明的任务方法。
         */
        public void base() {
        }
    }

    /**
     * 未声明任务方法的子类。
     */
    private static final class SubJob extends BaseJob {
    }

    /**
     * {@code static} 任务方法（用于错误消息定位断言）。
     */
    private static final class StaticBadJob {

        /**
         * 违规：static。
         */
        @FixedRate(period = 1000L)
        public static void bad() {
        }
    }
}
