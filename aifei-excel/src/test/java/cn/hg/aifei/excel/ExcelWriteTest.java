/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.excel;

import cn.hg.aifei.excel.support.UserRow;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 流式写测试（设计 §5.1 / §7.4 / §12.1）：
 * 同会话多次追加且顺序不变、表头只写一次、finish 前补写表头、finish/close 幂等、
 * 不关闭输出流、finished 后再写报错（场景 3）、flush 失败包装（场景 13）。
 * 说明：写格式由内核按 OutputStream 默认 xlsx；模块公共 API 无 ExcelType 入参，
 * 「写 xls」不在本期契约（xls 读已由 ExcelReadTest / AnnotationMappingTest 覆盖）。
 */
public class ExcelWriteTest {

    @Test
    public void writer_multipleWriteAppendInOrder_headerWrittenOnce() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Excel writer = Excel.writer(out, UserRow.class);
        writer.write(Arrays.asList(row("a", 1), row("b", 2)));
        writer.write(Arrays.asList(row("c", 3)));
        writer.finish();

        List<UserRow> back = ExcelKit.readAll(new ByteArrayInputStream(out.toByteArray()), UserRow.class);
        assertEquals("三行须全部追加", 3, back.size());
        assertEquals("a", back.get(0).getName());
        assertEquals("b", back.get(1).getName());
        assertEquals("c", back.get(2).getName());
        assertEquals(3, back.get(2).getAge());
    }

    @Test
    public void writer_finishWithoutWrite_stillEmitsHeaderOnly() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Excel writer = Excel.writer(out, UserRow.class);
        writer.finish();
        assertTrue("仅表头也应产出非空文件", out.size() > 0);
        assertEquals("无数据行时读回空列表", 0,
                ExcelKit.readAll(new ByteArrayInputStream(out.toByteArray()), UserRow.class).size());
    }

    @Test
    public void writer_nullOrEmptyRows_areNoOp() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Excel writer = Excel.writer(out, UserRow.class);
        writer.write(null);
        writer.write(new ArrayList<UserRow>());
        writer.finish();
        assertEquals(0, ExcelKit.readAll(new ByteArrayInputStream(out.toByteArray()), UserRow.class).size());
    }

    @Test
    public void writer_finishAndClose_areIdempotent() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Excel writer = Excel.writer(out, UserRow.class);
        writer.write(Arrays.asList(row("a", 1)));
        writer.finish();
        writer.finish();
        writer.close();
        assertEquals(1, ExcelKit.readAll(new ByteArrayInputStream(out.toByteArray()), UserRow.class).size());
    }

    @Test
    public void writer_doesNotCloseOutputStream() {
        final boolean[] closed = {false};
        OutputStream tracked = new ByteArrayOutputStream() {
            @Override
            public void close() {
                closed[0] = true;
            }
        };
        Excel writer = Excel.writer(tracked, UserRow.class);
        writer.write(Arrays.asList(row("a", 1)));
        writer.finish();
        assertFalse("写侧不得关闭调用方输出流", closed[0]);
    }

    @Test
    public void writer_writeAfterFinish_throwsWriterFinished() {
        Excel writer = Excel.writer(new ByteArrayOutputStream(), UserRow.class);
        writer.finish();
        ExcelException ex = assertThrows(ExcelException.class,
                () -> writer.write(Arrays.asList(row("late", 9))));
        assertEquals("excel: writer session already finished, create a new writer", ex.getMessage());
    }

    @Test
    public void writer_flushFailure_wrappedAsFinishIoError() {
        OutputStream broken = new ByteArrayOutputStream() {
            @Override
            public void flush() throws IOException {
                throw new IOException("flush failed");
            }
        };
        Excel writer = Excel.writer(broken, UserRow.class);
        writer.write(Arrays.asList(row("a", 1)));
        ExcelException ex = assertThrows(ExcelException.class, writer::finish);
        assertTrue(ex.getMessage(), ex.getMessage().startsWith("excel: io error during finish"));
        assertTrue("cause 须保留", ex.getCause() != null);
    }

    private static UserRow row(String name, int age) {
        return new UserRow(name, age, age * 1.0D, "ignore-me");
    }
}
