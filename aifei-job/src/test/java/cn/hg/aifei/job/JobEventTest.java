/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.job.support.TestLogs;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 事件快照形态用例：{@code final} 类、字段全 {@code final}、无 setter、唯一工厂 {@code of}、四阶段、
 * 触发时间为 {@code long}、每次 {@code of} 都产生新实例。
 *
 * <p>「不可变」在本模块是<b>结构属性</b>而非约定：{@code JobKit.state()} 每次返回新实例、监听器拿到的事件
 * 可被长期持有，若存在写入入口就会污染调度判据，故用反射把结构锁死。</p>
 */
public class JobEventTest {

    /**
     * 用例统一使用的任务名。
     */
    private static final String TASK = "com.acme.OrderJob#clean";

    /**
     * 用例统一使用的触发时间，epoch 毫秒。
     */
    private static final long TRIGGER_TIME = 1609430400000L;

    /**
     * 装配空日志工厂。
     */
    @BeforeClass
    public static void beforeClass() {
        TestLogs.install();
    }

    // ------------------------------------------------------------------ 不可变形态（结构断言）

    /**
     * 类是 {@code final}：不允许子类改写阶段与时间语义。
     */
    @Test
    public void classIsFinal() {
        assertTrue("JobEvent 必须是 final 类", Modifier.isFinal(JobEvent.class.getModifiers()));
    }

    /**
     * 全部字段 {@code private final}（且无静态可变字段），构造期一次赋值。
     */
    @Test
    public void allFieldsArePrivateFinal() {
        Field[] fields = JobEvent.class.getDeclaredFields();
        assertEquals("字段应为 4 个：taskName / triggerTime / phase / error", 4, fields.length);
        List<String> names = new ArrayList<>();
        for (Field field : fields) {
            names.add(field.getName());
            assertTrue("字段 " + field.getName() + " 必须是 final", Modifier.isFinal(field.getModifiers()));
            assertTrue("字段 " + field.getName() + " 必须是 private", Modifier.isPrivate(field.getModifiers()));
            assertFalse("字段 " + field.getName() + " 不得是 static", Modifier.isStatic(field.getModifiers()));
        }
        assertTrue("应含 taskName 字段", names.contains("taskName"));
        assertTrue("应含 triggerTime 字段", names.contains("triggerTime"));
        assertTrue("应含 phase 字段", names.contains("phase"));
        assertTrue("应含 error 字段", names.contains("error"));
    }

    /**
     * 无 {@code setXxx} 写入入口，也无 {@code withXxx} 一类派生写入方法。
     */
    @Test
    public void noSetterMethods() {
        for (Method method : JobEvent.class.getDeclaredMethods()) {
            String name = method.getName();
            assertFalse("不得存在 setter：" + name, name.startsWith("set"));
            assertFalse("不得存在派生写入方法：" + name, name.startsWith("with"));
        }
    }

    /**
     * 构造器为 {@code private}：只能经唯一工厂 {@link JobEvent#of} 装配。
     */
    @Test
    public void constructorIsPrivate() {
        Constructor<?>[] constructors = JobEvent.class.getDeclaredConstructors();
        assertEquals("应只有一个构造器", 1, constructors.length);
        assertTrue("构造器必须 private", Modifier.isPrivate(constructors[0].getModifiers()));
    }

    /**
     * 不实现 {@code equals} / {@code hashCode}：无值比较语义需求，且每次 {@code of} 都是新实例。
     */
    @Test
    public void equalsAndHashCodeAreNotOverridden() {
        for (Method method : JobEvent.class.getDeclaredMethods()) {
            assertFalse("不得实现 equals", "equals".equals(method.getName()));
            assertFalse("不得实现 hashCode", "hashCode".equals(method.getName()));
        }
        JobEvent one = JobEvent.of(TASK, TRIGGER_TIME, JobEvent.Phase.SUCCESS, null);
        JobEvent two = JobEvent.of(TASK, TRIGGER_TIME, JobEvent.Phase.SUCCESS, null);
        assertFalse("未实现值相等语义，两个新实例不应相等", one.equals(two));
    }

    // ------------------------------------------------------------------ 阶段与时间类型

    /**
     * 阶段恰好四值，且顺序为 {@code STARTED → SUCCESS → FAILED → SKIPPED}。
     */
    @Test
    public void phaseHasExactlyFourValuesInOrder() {
        JobEvent.Phase[] phases = JobEvent.Phase.values();
        assertEquals("阶段应为 4 个", 4, phases.length);
        assertSame("第 1 个阶段应为 STARTED", JobEvent.Phase.STARTED, phases[0]);
        assertSame("第 2 个阶段应为 SUCCESS", JobEvent.Phase.SUCCESS, phases[1]);
        assertSame("第 3 个阶段应为 FAILED", JobEvent.Phase.FAILED, phases[2]);
        assertSame("第 4 个阶段应为 SKIPPED", JobEvent.Phase.SKIPPED, phases[3]);
    }

    /**
     * 触发时间为 {@code long}（epoch 毫秒），不使用 {@code Date} / {@code Calendar} 等可变类型。
     */
    @Test
    public void triggerTimeIsLong() throws Exception {
        Method getter = JobEvent.class.getMethod("triggerTime");
        assertSame("triggerTime 的返回类型应为 long", long.class, getter.getReturnType());
        Field field = field("triggerTime");
        assertSame("triggerTime 字段类型应为 long", long.class, field.getType());
        assertEquals("触发时间应原样保留", TRIGGER_TIME,
                JobEvent.of(TASK, TRIGGER_TIME, JobEvent.Phase.SUCCESS, null).triggerTime());
    }

    // ------------------------------------------------------------------ 唯一工厂

    /**
     * {@code of} 装配的字段逐个原样保留；非 {@code FAILED} 阶段 {@code error} 为 {@code null}。
     */
    @Test
    public void ofKeepsAllValues() {
        JobEvent success = JobEvent.of(TASK, TRIGGER_TIME, JobEvent.Phase.SUCCESS, null);
        assertEquals("任务名应原样保留", TASK, success.taskName());
        assertEquals("触发时间应原样保留", TRIGGER_TIME, success.triggerTime());
        assertSame("阶段应原样保留", JobEvent.Phase.SUCCESS, success.phase());
        assertNull("非 FAILED 阶段的 error 应为 null", success.error());

        IllegalStateException cause = new IllegalStateException("boom");
        JobEvent failed = JobEvent.of(TASK, TRIGGER_TIME, JobEvent.Phase.FAILED, cause);
        assertSame("FAILED 的故障原因应原样保留", cause, failed.error());
    }

    /**
     * 每次 {@code of} 都产生<b>新实例</b>（{@code assertNotSame}），不复用、不缓存。
     */
    @Test
    public void ofReturnsNewInstanceEveryCall() {
        JobEvent first = JobEvent.of(TASK, TRIGGER_TIME, JobEvent.Phase.SUCCESS, null);
        JobEvent second = JobEvent.of(TASK, TRIGGER_TIME, JobEvent.Phase.SUCCESS, null);
        assertNotSame("同一取值两次装配应得到不同实例", first, second);
        JobEvent third = JobEvent.of(TASK, TRIGGER_TIME + 1L, JobEvent.Phase.SKIPPED, null);
        assertNotSame("不同取值更应是不同实例", first, third);
    }

    /**
     * 用已有实例的取值复制出新实例：新实例独立，原实例字段不受影响。
     */
    @Test
    public void ofCopyLeavesSourceUntouched() {
        JobEvent source = JobEvent.of(TASK, TRIGGER_TIME, JobEvent.Phase.FAILED,
                new IllegalStateException("first"));
        JobEvent copy = JobEvent.of(source.taskName(), source.triggerTime(), source.phase(), source.error());
        assertNotSame("复制应产生新实例", source, copy);
        assertEquals("复制体与源的任务名一致", source.taskName(), copy.taskName());
        assertEquals("复制体与源的触发时间一致", source.triggerTime(), copy.triggerTime());
        assertSame("复制体与源的阶段一致", source.phase(), copy.phase());
        assertSame("复制体与源的故障原因一致", source.error(), copy.error());
        assertEquals("源实例字段不得被复制过程改动", TASK, source.taskName());
        assertEquals("源实例字段不得被复制过程改动", TRIGGER_TIME, source.triggerTime());
        assertSame("源实例字段不得被复制过程改动", JobEvent.Phase.FAILED, source.phase());
    }

    // ------------------------------------------------------------------ toString

    /**
     * {@code toString} 含任务名、阶段与触发时间（故障原因可为空，不参与断言）。
     */
    @Test
    public void toStringContainsKeyFields() {
        String text = JobEvent.of(TASK, TRIGGER_TIME, JobEvent.Phase.SKIPPED, null).toString();
        assertTrue("toString 应含任务名，实际：" + text, text.contains(TASK));
        assertTrue("toString 应含阶段，实际：" + text, text.contains("SKIPPED"));
        assertTrue("toString 应含触发时间，实际：" + text, text.contains(String.valueOf(TRIGGER_TIME)));
        assertNotNull("toString 不得返回 null", text);
    }

    // ------------------------------------------------------------------ 辅助方法

    /**
     * @param name 字段名
     * @return 字段对象
     * @throws NoSuchFieldException 字段不存在
     */
    private static Field field(String name) throws NoSuchFieldException {
        return JobEvent.class.getDeclaredField(name);
    }
}
