/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.excel;

import cn.hg.aifei.excel.support.SampleFactory;
import cn.hg.aifei.excel.support.UserRow;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 异常语义测试：
 * 两段构造器、参数 null 逐字消息、batchSize<=0、index<0 非错误、cause 保留、
 * EasyExcel 缺失可读失败（隔离 classloader 实测，禁止裸 NoClassDefFoundError）。
 */
public class ExcelExceptionTest {

    @Test
    public void constructors_keepMessageAndCause() {
        ExcelException bare = new ExcelException("m");
        assertEquals("m", bare.getMessage());
        assertNull(bare.getCause());
        IllegalStateException cause = new IllegalStateException("root");
        ExcelException wrapped = new ExcelException("m2", cause);
        assertEquals("m2", wrapped.getMessage());
        assertSame(cause, wrapped.getCause());
        assertTrue(wrapped instanceof RuntimeException);
    }

    @Test
    public void read_nullArguments_exactMessages() {
        assertEquals("excel: read argument in must not be null",
                assertThrows(ExcelException.class,
                        () -> Excel.read(null, UserRow.class, batch -> {
                        })).getMessage());
        assertEquals("excel: read argument type must not be null",
                assertThrows(ExcelException.class,
                        () -> Excel.read(new ByteArrayInputStream(new byte[0]), null, batch -> {
                        })).getMessage());
        Consumer<List<UserRow>> nullConsumer = null;
        assertEquals("excel: read argument batchConsumer must not be null",
                assertThrows(ExcelException.class,
                        () -> Excel.read(new ByteArrayInputStream(new byte[0]), UserRow.class, nullConsumer))
                        .getMessage());
    }

    @Test
    public void writer_nullArguments_exactMessages() {
        assertEquals("excel: writer argument out must not be null",
                assertThrows(ExcelException.class, () -> Excel.writer(null, UserRow.class)).getMessage());
        assertEquals("excel: writer argument type must not be null",
                assertThrows(ExcelException.class,
                        () -> Excel.writer(new ByteArrayOutputStream(), null)).getMessage());
    }

    @Test
    public void batchSize_nonPositive_exactMessages() {
        for (int n : new int[]{0, -1, -100}) {
            assertEquals("excel: batchSize must be positive, but was: " + n,
                    assertThrows(ExcelException.class,
                            () -> Excel.read(new ByteArrayInputStream(new byte[0]), UserRow.class, n,
                                    batch -> {
                                    })).getMessage());
        }
    }

    @Test
    public void negativeExplicitIndex_treatedAsUnspecified_notError() {
        List<List<Object>> sheet = new ArrayList<List<Object>>();
        sheet.add(SampleFactory.cells("Maybe"));
        sheet.add(SampleFactory.cells("V"));
        List<NegativeIndexRow> rows = ExcelKit.readAll(
                new ByteArrayInputStream(SampleFactory.xlsx(sheet)), NegativeIndexRow.class);
        assertEquals(1, rows.size());
        assertEquals("V", rows.get(0).value);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelKit.write(out, NegativeIndexRow.class, java.util.Collections.singletonList(new NegativeIndexRow()));
        assertTrue(out.size() > 0);
    }

    @Test
    public void easyExcelMissing_throwsReadableExceptionViaIsolatedClassLoader() throws Exception {
        URL classes = Paths.get("target/classes").toAbsolutePath().toUri().toURL();
        URLClassLoader isolated = new URLClassLoader(new URL[]{classes}, null) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.alibaba.") || name.startsWith("org.apache.poi.")
                        || name.startsWith("org.slf4j.")) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };
        try {
            Class<?> kit = Class.forName("cn.hg.aifei.excel.ExcelKit", true, isolated);
            Method readAll = kit.getMethod("readAll", InputStream.class, Class.class);
            InvocationTargetException ite = assertThrows(InvocationTargetException.class,
                    () -> readAll.invoke(null, new ByteArrayInputStream(new byte[0]), UserRow.class));
            Throwable cause = ite.getCause();
            assertEquals("cn.hg.aifei.excel.ExcelException", cause.getClass().getName());
            assertEquals("excel: com.alibaba:easyexcel:3.3.2 is required, add it to your dependencies",
                    cause.getMessage());
        } finally {
            isolated.close();
        }
    }

    public static class NegativeIndexRow {
        @ExcelColumn(value = "Maybe", index = -5)
        public String value;
    }
}
