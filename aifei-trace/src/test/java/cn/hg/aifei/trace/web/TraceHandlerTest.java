/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.trace.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import cn.aifei.core.Handler;
import cn.aifei.core.Input;
import cn.aifei.core.Output;
import cn.hg.aifei.trace.TraceKit;

/**
 * TraceHandler 单元测试：覆盖纯生成、提取绑定、空白回退、提取异常吞掉回退、
 * 下游异常原样传播且 finally 清理、next == null 安全结束）。
 */
public class TraceHandlerTest {

    /**
     * 测试桩 Input：携带 header 读取能力（模拟业务侧自定义 Input 暴露 header 后提取 X-Trace-Id 的用法）。
     */
    private static class TestInput implements Input {

        /**
         * 模拟请求头集合，测试用 extractor 直接读取。
         */
        public final Map<String, String> headers = new LinkedHashMap<>();

        /**
         * 模拟的 header 读取方法（cn.aifei.core.Input 无 header 能力，此方法属测试桩自有）。
         *
         * @param name header 名
         * @return header 值，缺失时为 null
         */
        public String getHeader(String name) {
            return headers.get(name);
        }

        @Override
        public boolean has(String name) {
            return false;
        }

        @Override
        public boolean has(int index) {
            return false;
        }

        @Override
        public void pathPara(String pathPara) {
            // 测试桩无需实现
        }

        @Override
        public <T> T getBean(String name, Class<T> type) {
            return null;
        }

        @Override
        public <T> java.util.List<T> getList(String name, Class<T> type) {
            return null;
        }

        @Override
        public <T> T[] getArray(String name, Class<T> type) {
            return null;
        }

        @Override
        public Map<String, Object> getMap(String name) {
            return null;
        }

        @Override
        public String getStr(String name) {
            return null;
        }

        @Override
        public String getStr(int index) {
            return null;
        }

        @Override
        public Integer getInt(String name) {
            return null;
        }

        @Override
        public Integer getInt(int index) {
            return null;
        }

        @Override
        public Long getLong(String name) {
            return null;
        }

        @Override
        public Long getLong(int index) {
            return null;
        }

        @Override
        public Double getDouble(String name) {
            return null;
        }

        @Override
        public Double getDouble(int index) {
            return null;
        }

        @Override
        public java.math.BigDecimal getBigDecimal(String name) {
            return null;
        }

        @Override
        public java.math.BigDecimal getBigDecimal(int index) {
            return null;
        }

        @Override
        public Boolean getBoolean(String name) {
            return null;
        }

        @Override
        public Boolean getBoolean(int index) {
            return null;
        }

        @Override
        public java.util.Date getDate(String name) {
            return null;
        }

        @Override
        public java.time.LocalDate getLocalDate(String name) {
            return null;
        }

        @Override
        public java.time.LocalTime getLocalTime(String name) {
            return null;
        }

        @Override
        public java.time.LocalDateTime getLocalDateTime(String name) {
            return null;
        }
    }

    /**
     * 测试桩 Output：空实现即可。
     */
    private static class TestOutput implements Output {
    }

    /**
     * 测试桩下游 Handler：记录 handle 时刻的链路上下文，可配置抛出异常。
     */
    private static class RecordingHandler extends Handler<TestInput, TestOutput> {

        int callCount;
        String capturedTraceId;
        String capturedSpanId;
        RuntimeException toThrow;

        @Override
        public void handle(String path, TestInput input, TestOutput output) {
            callCount++;
            capturedTraceId = TraceKit.getTraceId();
            capturedSpanId = TraceKit.getSpanId();
            if (toThrow != null) {
                throw toThrow;
            }
        }

        /**
         * 串联下游 Handler（访问继承自框架 Handler 的 next 字段）。
         *
         * @param downstream 下游 Handler
         */
        void link(Handler<TestInput, TestOutput> downstream) {
            this.next = downstream;
        }
    }

    /**
     * 测试用 TraceHandler 子类：暴露 next 字段的链接能力（TraceHandler 与测试类不同包，test 侧经子类访问）。
     */
    private static class LinkableTraceHandler extends TraceHandler<TestInput, TestOutput> {

        LinkableTraceHandler() {
            super();
        }

        LinkableTraceHandler(TraceIdExtractor<TestInput> extractor) {
            super(extractor);
        }

        /**
         * 串联下游 Handler。
         *
         * @param downstream 下游 Handler
         */
        void link(Handler<TestInput, TestOutput> downstream) {
            this.next = downstream;
        }
    }

    /**
     * 构造 header 提取器（业务典型用法：input -> input.getHeader("X-Trace-Id")）。
     */
    private static TraceIdExtractor<TestInput> headerExtractor() {
        return new TraceIdExtractor<TestInput>() {
            @Override
            public String extract(TestInput input) {
                return input.getHeader("X-Trace-Id");
            }
        };
    }

    /**
     * 断言为 32 位 [0-9a-f]（默认生成器产出契约）。
     *
     * @param id 待断言值
     */
    private static void assertGeneratedId(String id) {
        assertNotNull("回退生成的 TraceId 不应为 null", id);
        assertTrue("回退生成的 TraceId 应为 32 位 [0-9a-f]：" + id, id.matches("[0-9a-f]{32}"));
    }

    /**
     * 场景 4 前置：提取值非空白时原样绑定（模块不校验格式，按原值绑定）。
     */
    @Test
    public void extractedValueShouldBeBound() throws Exception {
        LinkableTraceHandler handler = new LinkableTraceHandler(headerExtractor());
        RecordingHandler downstream = new RecordingHandler();
        handler.link(downstream);
        TestInput input = new TestInput();
        input.headers.put("X-Trace-Id", "upstream-trace-id");

        handler.handle("/biz", input, new TestOutput());

        assertEquals("下游应看到提取绑定的 TraceId", "upstream-trace-id", downstream.capturedTraceId);
        assertNull("仅绑定 TraceId 不得影响 spanId", downstream.capturedSpanId);
        assertEquals(1, downstream.callCount);
        assertNull("请求结束 finally 应清理 TraceId", TraceKit.getTraceId());
        assertNull("请求结束 finally 应清理 SpanId", TraceKit.getSpanId());
    }

    /**
     * 场景 4：extractor 返回 null / 空白时回退生成新 TraceId。
     */
    @Test
    public void blankExtractionShouldFallbackToGenerate() throws Exception {
        for (String blank : new String[] {null, "", "   "}) {
            LinkableTraceHandler handler = new LinkableTraceHandler(headerExtractor());
            RecordingHandler downstream = new RecordingHandler();
            handler.link(downstream);
            TestInput input = new TestInput();
            if (blank != null) {
                input.headers.put("X-Trace-Id", blank);
            }

            handler.handle("/biz", input, new TestOutput());

            assertGeneratedId(downstream.capturedTraceId);
            assertNull("请求结束 finally 应清理", TraceKit.getTraceId());
        }
    }

    /**
     * 场景 4 前置（无 extractor）：纯生成模式直接生成新 TraceId。
     */
    @Test
    public void pureGenerationModeShouldGenerate() throws Exception {
        LinkableTraceHandler handler = new LinkableTraceHandler();
        RecordingHandler downstream = new RecordingHandler();
        handler.link(downstream);

        handler.handle("/biz", new TestInput(), new TestOutput());

        assertGeneratedId(downstream.capturedTraceId);
        assertNull("请求结束 finally 应清理", TraceKit.getTraceId());
    }

    /**
     * 场景 5：extractor 抛异常被吞掉并回退生成，业务请求不受影响。
     */
    @Test
    public void extractionExceptionShouldBeSwallowedAndFallback() throws Exception {
        TraceIdExtractor<TestInput> broken = new TraceIdExtractor<TestInput>() {
            @Override
            public String extract(TestInput input) {
                throw new RuntimeException("extract boom");
            }
        };
        LinkableTraceHandler handler = new LinkableTraceHandler(broken);
        RecordingHandler downstream = new RecordingHandler();
        handler.link(downstream);

        handler.handle("/biz", new TestInput(), new TestOutput());

        assertGeneratedId(downstream.capturedTraceId);
        assertEquals("提取异常不得打断业务请求", 1, downstream.callCount);
        assertNull("请求结束 finally 应清理", TraceKit.getTraceId());
    }

    /**
     * 场景 6：下游异常原样向上传播（同一实例、不包装），finally 照常清理。
     */
    @Test
    public void downstreamExceptionShouldPropagateAndFinallyClear() throws Exception {
        LinkableTraceHandler handler = new LinkableTraceHandler(headerExtractor());
        RecordingHandler downstream = new RecordingHandler();
        IllegalStateException boom = new IllegalStateException("biz boom");
        downstream.toThrow = boom;
        handler.link(downstream);
        TestInput input = new TestInput();
        input.headers.put("X-Trace-Id", "upstream-trace-id");

        try {
            handler.handle("/biz", input, new TestOutput());
            fail("下游异常应原样向上传播");
        } catch (Exception e) {
            assertSame("传播的必须是原异常实例（不捕获不包装）", boom, e);
        }

        assertNull("下游异常路径 finally 也应清理 TraceId", TraceKit.getTraceId());
        assertNull("下游异常路径 finally 也应清理 SpanId", TraceKit.getSpanId());
    }

    /**
     * 场景 12：next == null 安全结束（无下游可传），finally 清理照常执行。
     */
    @Test
    public void nextNullShouldEndSafely() throws Exception {
        TraceHandler<TestInput, TestOutput> handler = new TraceHandler<TestInput, TestOutput>();

        handler.handle("/biz", new TestInput(), new TestOutput());

        assertNull("请求结束 finally 应清理", TraceKit.getTraceId());
    }

    /**
     * setExtractor 返回自身支持链式；传 null 等价纯生成模式。
     */
    @Test
    public void setExtractorShouldChainAndNullMeansPureGeneration() throws Exception {
        LinkableTraceHandler handler = new LinkableTraceHandler();
        TraceIdExtractor<TestInput> extractor = headerExtractor();
        assertSame("setExtractor 应返回 this 支持链式", handler, handler.setExtractor(extractor));
        handler.setExtractor(null);

        RecordingHandler downstream = new RecordingHandler();
        handler.link(downstream);
        handler.handle("/biz", new TestInput(), new TestOutput());
        assertGeneratedId(downstream.capturedTraceId);
        assertNull("请求结束 finally 应清理", TraceKit.getTraceId());
    }
}
