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
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 流式读测试（设计 §5.1 / §7.3 / §12.1）：
 * 分批回调（默认与自定义批大小、末批兜底、批次数/顺序/总量）、回调异常包装、IO 包装、
 * 不关闭入参流、xls/xlsx、12000 行内存边界与批引用释放（弱引用可观测）。
 */
public class ExcelReadTest {

    @Test
    public void read_customBatchSize_deliversBatchesInOrderWithTail() {
        List<List<UserRow>> batches = new ArrayList<List<UserRow>>();
        Excel.read(new ByteArrayInputStream(userSheet(7)), UserRow.class, 3,
                batch -> batches.add(batch));

        assertEquals("7 行 / batchSize=3 应为 3 批", 3, batches.size());
        assertEquals(3, batches.get(0).size());
        assertEquals(3, batches.get(1).size());
        assertEquals("末批不足 3 应单独回调一次", 1, batches.get(2).size());
        int total = batches.get(0).size() + batches.get(1).size() + batches.get(2).size();
        assertEquals("总量须为 7", 7, total);
        assertEquals("批内顺序不得打乱", "u0", batches.get(0).get(0).getName());
        assertEquals("跨批顺序不得打乱", "u3", batches.get(1).get(0).getName());
        assertEquals("末批内容正确", "u6", batches.get(2).get(0).getName());
    }

    @Test
    public void read_defaultBatchSize_isOneThousand() {
        final List<Integer> sizes = new ArrayList<Integer>();
        Excel.read(new ByteArrayInputStream(userSheet(1005)), UserRow.class,
                batch -> sizes.add(Integer.valueOf(batch.size())));

        assertEquals("1005 行按默认 1000 应为 2 批", 2, sizes.size());
        assertEquals(Integer.valueOf(1000), sizes.get(0));
        assertEquals(Integer.valueOf(5), sizes.get(1));
    }

    @Test
    public void read_consumerThrows_wrappedWithCauseAndBatchIndex() {
        RuntimeException boom = new RuntimeException("boom");
        ExcelException ex = assertThrows(ExcelException.class,
                () -> Excel.read(new ByteArrayInputStream(userSheet(5)), UserRow.class, 2,
                        batch -> {
                            throw boom;
                        }));
        assertEquals("excel: batch consumer failed at batch 0, cause: boom", ex.getMessage());
        assertSame("cause 须为回调原异常", boom, ex.getCause());
    }

    @Test
    public void read_doesNotCloseInputStream() {
        final boolean[] closed = {false};
        InputStream tracked = new ByteArrayInputStream(userSheet(3)) {
            @Override
            public void close() {
                closed[0] = true;
            }
        };
        Excel.read(tracked, UserRow.class, 2, batch -> {
        });
        assertFalse("读侧不得关闭调用方入参流", closed[0]);
    }

    @Test
    public void read_ioFailure_wrappedAsIoError() {
        InputStream broken = new ByteArrayInputStream(userSheet(3)) {
            @Override
            public synchronized int read(byte[] b, int off, int len) {
                throw new RuntimeException("simulated io failure");
            }

            @Override
            public synchronized int read() {
                throw new RuntimeException("simulated io failure");
            }
        };
        ExcelException ex = assertThrows(ExcelException.class,
                () -> Excel.read(broken, UserRow.class, 1, batch -> {
                }));
        assertTrue(ex.getMessage(), ex.getMessage().startsWith("excel: io error during read"));
        assertTrue("cause 须保留", ex.getCause() != null);
    }

    @Test
    public void read_xls_deliversRows() {
        List<List<Object>> sheet = new ArrayList<List<Object>>();
        sheet.add(SampleFactory.cells("name", "Age", "score"));
        sheet.add(SampleFactory.cells("xls1", "11", "1.5"));
        sheet.add(SampleFactory.cells("xls2", "12", "2.5"));
        final List<String> names = new ArrayList<String>();
        Excel.read(new ByteArrayInputStream(SampleFactory.xls(sheet)), UserRow.class, 1,
                batch -> {
                    for (UserRow row : batch) {
                        names.add(row.getName());
                    }
                });
        assertEquals(2, names.size());
        assertEquals("xls1", names.get(0));
        assertEquals("xls2", names.get(1));
    }

    @Test
    public void read_twelveThousandRows_batchCountCorrect_andReleasesPastBatches() {
        final int[] seen = {0};
        final List<WeakReference<List<UserRow>>> batchRefs = new ArrayList<WeakReference<List<UserRow>>>();
        Excel.read(new ByteArrayInputStream(userSheet(12000)), UserRow.class, 1000,
                batch -> {
                    seen[0] += batch.size();
                    batchRefs.add(new WeakReference<List<UserRow>>(batch));
                });

        assertEquals("总量须为 12000", 12000, seen[0]);
        assertEquals("12000 行 / batchSize=1000 应为 12 批", 12, batchRefs.size());

        // 弱引用可观测：回调返回后模块不再持有历史批，GC 后应可回收（至少 11/12）。
        boolean released = false;
        for (int attempt = 0; attempt < 20 && !released; attempt++) {
            System.gc();
            try {
                Thread.sleep(30L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            int cleared = 0;
            for (WeakReference<List<UserRow>> ref : batchRefs) {
                if (ref.get() == null) {
                    cleared++;
                }
            }
            released = cleared >= 11;
        }
        assertTrue("回调后历史批引用应被释放（弱引用 GC 观测，至少 11/12）", released);
    }

    // ---------------------------------------------------------------- 工具

    private static byte[] userSheet(int rows) {
        List<UserRow> data = new ArrayList<UserRow>(rows);
        for (int i = 0; i < rows; i++) {
            data.add(new UserRow("u" + i, i, i * 1.0D, "secret-" + i));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelKit.write(out, UserRow.class, data);
        return out.toByteArray();
    }
}
