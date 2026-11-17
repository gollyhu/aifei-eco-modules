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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

/**
 * 一次性便捷门面测试（设计 §5.2 / §7.5 场景 1）：
 * readAll 空结果空 List / 小文件全量 / write 语法糖读回一致 / 指定 sheetName / null 参数 / 不关流。
 */
public class ExcelKitTest {

    @Test
    public void readAll_emptyFile_returnsEmptyList() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelKit.write(out, UserRow.class, new ArrayList<UserRow>());
        List<UserRow> rows = ExcelKit.readAll(new ByteArrayInputStream(out.toByteArray()), UserRow.class);
        assertNotNull("空结果不得为 null", rows);
        assertEquals(0, rows.size());
    }

    @Test
    public void readAll_smallFile_returnsAllRowsInOrder() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelKit.write(out, UserRow.class, Arrays.asList(
                new UserRow("u0", 0, 0D, "x"),
                new UserRow("u1", 1, 1D, "x"),
                new UserRow("u2", 2, 2D, "x")));
        List<UserRow> rows = ExcelKit.readAll(new ByteArrayInputStream(out.toByteArray()), UserRow.class);
        assertEquals(3, rows.size());
        for (int i = 0; i < 3; i++) {
            assertEquals("u" + i, rows.get(i).getName());
            assertEquals(i, rows.get(i).getAge());
        }
    }

    @Test
    public void write_syntaxSugar_roundTripsWithSheetName() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelKit.write(out, UserRow.class, "DemoSheet",
                Arrays.asList(new UserRow("a", 10, 1.5D, "x"), new UserRow("b", 20, 2.5D, "x")));
        List<UserRow> back = ExcelKit.readAll(new ByteArrayInputStream(out.toByteArray()), UserRow.class);
        assertEquals(2, back.size());
        assertEquals("a", back.get(0).getName());
        assertEquals(10, back.get(0).getAge());
        assertEquals(20, back.get(1).getAge());
    }

    @Test
    public void readAll_andWrite_doNotCloseCallerStreams() {
        final boolean[] inClosed = {false};
        final boolean[] outClosed = {false};
        ByteArrayOutputStream source = new ByteArrayOutputStream();
        ExcelKit.write(source, UserRow.class, Arrays.asList(new UserRow("a", 1, 1D, "x")));

        InputStream in = new ByteArrayInputStream(source.toByteArray()) {
            @Override
            public void close() {
                inClosed[0] = true;
            }
        };
        OutputStream out = new ByteArrayOutputStream() {
            @Override
            public void close() {
                outClosed[0] = true;
            }
        };
        ExcelKit.readAll(in, UserRow.class);
        ExcelKit.write(out, UserRow.class, Arrays.asList(new UserRow("b", 2, 2D, "x")));
        assertFalse("readAll 不得关闭入参流", inClosed[0]);
        assertFalse("write 不得关闭出参流", outClosed[0]);
    }

    @Test
    public void readAll_nullArguments_throwArgumentError() {
        assertEquals("excel: readAll argument in must not be null",
                assertThrows(ExcelException.class,
                        () -> ExcelKit.readAll(null, UserRow.class)).getMessage());
        assertEquals("excel: readAll argument type must not be null",
                assertThrows(ExcelException.class,
                        () -> ExcelKit.readAll(new ByteArrayInputStream(new byte[0]), null)).getMessage());
    }

    @Test
    public void write_nullArguments_throwArgumentError() {
        assertEquals("excel: write argument out must not be null",
                assertThrows(ExcelException.class,
                        () -> ExcelKit.write(null, UserRow.class, new ArrayList<UserRow>())).getMessage());
        assertEquals("excel: write argument type must not be null",
                assertThrows(ExcelException.class,
                        () -> ExcelKit.write(new ByteArrayOutputStream(), null, new ArrayList<UserRow>())).getMessage());
    }
}
