/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.trace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.junit.After;
import org.junit.Test;

import cn.hg.aifei.trace.support.RecordingLog;

/**
 * TraceLog 单元测试：覆盖设计 §7.2 场景 8–9（前缀拼装、TraceId 缺失原样透出、Supplier 打印时刻求值、
 * isXxxEnabled / getName 透传、30 个打印方法 + 5 个开关全覆盖、null delegate 拒绝）。
 */
public class TraceLogTest {

    /**
     * 测试用固定 TraceId（32 位格式，与生产契约一致）。
     */
    private static final String TID = "0123456789abcdef0123456789abcdef";

    /**
     * 测试用前缀形态（唯一拼装点在 TraceLog#prefix，此处仅复述断言期望）。
     */
    private static final String PREFIX = "[" + TID + "] ";

    private static final String[] LEVELS = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR"};

    @After
    public void cleanUp() {
        TraceKit.clear();
    }

    /**
     * 构造测试用异常。
     */
    private static RuntimeException boom() {
        return new RuntimeException("boom");
    }

    /**
     * 对指定级别调用 TraceLog 的 5 个 String 变体打印方法（覆盖 30 个打印方法中的 String 部分）。
     *
     * @param log   被测装饰器
     * @param level 级别名
     * @return 各次调用收到的异常与占位参数断言所需的辅助数据
     */
    private List<Object> invokeStringVariants(TraceLog log, String level) {
        RuntimeException t = boom();
        Object arg1 = "a1";
        Object arg2 = "a2";
        List<Object> aux = new ArrayList<>();
        aux.add(t);
        aux.add(arg1);
        aux.add(arg2);
        if ("TRACE".equals(level)) {
            log.trace("plain");
            log.trace("with-throw", t);
            log.trace("fmt-{}", arg1);
            log.trace("fmt-{}-{}", arg1, arg2);
            log.trace("fmt-var {} {}", arg1, arg2);
        } else if ("DEBUG".equals(level)) {
            log.debug("plain");
            log.debug("with-throw", t);
            log.debug("fmt-{}", arg1);
            log.debug("fmt-{}-{}", arg1, arg2);
            log.debug("fmt-var {} {}", arg1, arg2);
        } else if ("INFO".equals(level)) {
            log.info("plain");
            log.info("with-throw", t);
            log.info("fmt-{}", arg1);
            log.info("fmt-{}-{}", arg1, arg2);
            log.info("fmt-var {} {}", arg1, arg2);
        } else if ("WARN".equals(level)) {
            log.warn("plain");
            log.warn("with-throw", t);
            log.warn("fmt-{}", arg1);
            log.warn("fmt-{}-{}", arg1, arg2);
            log.warn("fmt-var {} {}", arg1, arg2);
        } else {
            log.error("plain");
            log.error("with-throw", t);
            log.error("fmt-{}", arg1);
            log.error("fmt-{}-{}", arg1, arg2);
            log.error("fmt-var {} {}", arg1, arg2);
        }
        return aux;
    }

    /**
     * 对指定级别调用 TraceLog 的 Supplier 变体打印方法（暂存于桩内，未求值）。
     *
     * @param log      被测装饰器
     * @param level    级别名
     * @param supplier 消息供给器
     */
    private void invokeSupplierVariant(TraceLog log, String level, Supplier<String> supplier) {
        if ("TRACE".equals(level)) {
            log.trace(supplier);
        } else if ("DEBUG".equals(level)) {
            log.debug(supplier);
        } else if ("INFO".equals(level)) {
            log.info(supplier);
        } else if ("WARN".equals(level)) {
            log.warn(supplier);
        } else {
            log.error(supplier);
        }
    }

    /**
     * 场景 8 反例：TraceId 已绑定时，String 变体全部拼装 "[traceId] " 前缀，级别与异常、占位参数原样透传。
     */
    @Test
    public void stringVariantsShouldPrefixWhenTraceIdBound() {
        TraceKit.init(TID);
        for (String level : LEVELS) {
            RecordingLog rec = new RecordingLog();
            TraceLog log = new TraceLog(rec);
            List<Object> aux = invokeStringVariants(log, level);

            assertEquals("[" + level + "] 应收到 5 次 String 变体打印", 5, rec.messages.size());
            assertEquals("消息应带前缀：" + level, Arrays.asList(
                    PREFIX + "plain",
                    PREFIX + "with-throw",
                    PREFIX + "fmt-{}",
                    PREFIX + "fmt-{}-{}",
                    PREFIX + "fmt-var {} {}"), rec.messages);
            for (String recordedLevel : rec.levels) {
                assertEquals("级别应透传不变：" + level, level, recordedLevel);
            }
            assertSame("异常应原样透传：" + level, aux.get(0), rec.throwables.get(1));
            assertNull("无异常处应记录 null：" + level, rec.throwables.get(0));
            assertArrayEqualsAnyOrder("占位参数应原样透传：" + level,
                    new Object[] {aux.get(1), aux.get(2)}, rec.lastArgs);
        }
    }

    /**
     * 场景 8：TraceId 未绑定时，消息原样透出（空前缀），级别与参数照常透传。
     */
    @Test
    public void missingTraceIdShouldPassThroughRaw() {
        TraceKit.clear();
        for (String level : LEVELS) {
            RecordingLog rec = new RecordingLog();
            TraceLog log = new TraceLog(rec);
            invokeStringVariants(log, level);
            assertEquals("TraceId 缺失时消息应原样透出：" + level, Arrays.asList(
                    "plain", "with-throw", "fmt-{}", "fmt-{}-{}", "fmt-var {} {}"), rec.messages);
        }
    }

    /**
     * Supplier 变体懒求值——五个级别的 Supplier 变体均在 flush（打印时刻）才求值，且带前缀。
     */
    @Test
    public void supplierVariantsShouldEvaluateAtPrintTimeForAllLevels() {
        TraceKit.init(TID);
        for (String level : LEVELS) {
            RecordingLog rec = new RecordingLog();
            TraceLog log = new TraceLog(rec);
            invokeSupplierVariant(log, level, new Supplier<String>() {
                @Override
                public String get() {
                    return "lazy-msg";
                }
            });
            assertTrue("调用时刻不应记录 Supplier 消息：" + level, rec.messages.isEmpty());
            rec.flushPendingSupplier();
            assertEquals("打印时刻求值并带前缀：" + level, Arrays.asList(PREFIX + "lazy-msg"), rec.messages);
            assertEquals("级别应透传：" + level, Arrays.asList(level), rec.levels);
        }
    }

    /**
     * 懒求值时序证明——先调用后绑定，打印时刻取到的是打印线程当下的 TraceId（调用时刻未绑定也不影响）。
     */
    @Test
    public void supplierShouldReadContextAtFlushNotAtCall() {
        TraceKit.clear();
        final int[] counter = {0};
        RecordingLog rec = new RecordingLog();
        TraceLog log = new TraceLog(rec);
        log.info(new Supplier<String>() {
            @Override
            public String get() {
                counter[0]++;
                return "deferred";
            }
        });
        assertEquals("调用时刻不得求值 supplier", 0, counter[0]);

        TraceKit.init(TID);
        rec.flushPendingSupplier();
        assertEquals("flush（打印时刻）才求值", 1, counter[0]);
        assertEquals("打印时刻的 TraceId 应被拼入前缀", Arrays.asList(PREFIX + "deferred"), rec.messages);
    }

    /**
     * 反向时序：调用时已绑定、打印（flush）时已清理——前缀应缺失，证明前缀读取发生在打印时刻。
     */
    @Test
    public void supplierShouldNotPrefixWhenTraceIdClearedBeforeFlush() {
        TraceKit.init(TID);
        RecordingLog rec = new RecordingLog();
        TraceLog log = new TraceLog(rec);
        log.info(new Supplier<String>() {
            @Override
            public String get() {
                return "no-prefix";
            }
        });
        TraceKit.clear();
        rec.flushPendingSupplier();
        assertEquals("打印时刻 TraceId 已清理，消息应原样透出", Arrays.asList("no-prefix"), rec.messages);
    }

    /**
     * isXxxEnabled 与 getName 纯透传 delegate，不拼前缀。
     */
    @Test
    public void enabledFlagsAndNameShouldPassThrough() {
        RecordingLog rec = new RecordingLog();
        rec.traceEnabled = false;
        rec.infoEnabled = false;
        rec.name = "biz-log";
        TraceLog log = new TraceLog(rec);
        assertFalse("isTraceEnabled 应透传", log.isTraceEnabled());
        assertTrue("isDebugEnabled 应透传", log.isDebugEnabled());
        assertFalse("isInfoEnabled 应透传", log.isInfoEnabled());
        assertTrue("isWarnEnabled 应透传", log.isWarnEnabled());
        assertTrue("isErrorEnabled 应透传", log.isErrorEnabled());
        assertEquals("getName 应透传", "biz-log", log.getName());
    }

    /**
     * 场景 9：new TraceLog(null) 拒绝，消息逐字对齐 §7.3。
     */
    @Test
    public void nullDelegateShouldReject() {
        try {
            new TraceLog(null);
            fail("new TraceLog(null) 应抛出 IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("delegate can not be null.", e.getMessage());
        }
    }

    /**
     * 占位参数数组的宽松相等断言（数组元素逐一比对）。
     *
     * @param message  断言消息
     * @param expected 期望数组
     * @param actual   实际数组
     */
    private static void assertArrayEqualsAnyOrder(String message, Object[] expected, Object[] actual) {
        assertEquals(message, expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(message, expected[i], actual[i]);
        }
    }
}
