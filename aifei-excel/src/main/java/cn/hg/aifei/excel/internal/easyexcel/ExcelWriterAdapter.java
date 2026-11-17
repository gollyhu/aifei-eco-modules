/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.excel.internal.easyexcel;

import cn.hg.aifei.excel.ExcelException;
import com.alibaba.excel.EasyExcelFactory;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * writer 会话实现：唯一持有 EasyExcel {@code ExcelWriter} 的类型（设计 §7.2 / §7.4）。
 *
 * <p>构建时不写表头；首次 write 写一次表头，同一会话可多次追加数据行；finish 幂等，
 * flush 并关闭底层 writer 但不关闭调用方输出流（流所有权归调用方）。</p>
 */
public final class ExcelWriterAdapter {

    /** 未指定 Sheet 名时使用的唯一默认名定义点。 */
    public static final String DEFAULT_SHEET_NAME = "Sheet1";

    private final ExcelWriter writer;
    private final WriteSheet sheet;
    private final RowMapper<?> mapper;

    private boolean headerWritten;
    private boolean closed;

    /**
     * 构建写会话，此时不向输出流写任何字节。
     *
     * @param out       输出流（本类不关闭）
     * @param mapper    行映射定义
     * @param sheetName Sheet 名，为空时使用 {@link #DEFAULT_SHEET_NAME}
     */
    public ExcelWriterAdapter(OutputStream out, RowMapper<?> mapper, String sheetName) {
        this.mapper = mapper;
        String name = sheetName == null || sheetName.length() == 0 ? DEFAULT_SHEET_NAME : sheetName;
        this.writer = EasyExcelFactory.write(out)
                .autoCloseStream(false)
                .build();
        this.sheet = EasyExcelFactory.writerSheet(Integer.valueOf(0), name)
                .needHead(false)
                .build();
    }

    /**
     * 写表头行（幂等）；已关闭会话不再写。
     */
    public void writeHeader() {
        if (headerWritten || closed) {
            return;
        }
        List<List<Object>> head = new ArrayList<List<Object>>(1);
        head.add(mapper.headerValues());
        try {
            writer.write(head, sheet);
            headerWritten = true;
        } catch (ExcelException e) {
            throw e;
        } catch (RuntimeException e) {
            throw ExcelErrors.io("write", e);
        }
    }

    /**
     * 追加一批数据行；null 或空列表按无操作处理（表头由调用方 writeHeader 保证）。
     *
     * @param rows 数据行
     */
    public void appendRows(List<?> rows) {
        if (closed) {
            throw ExcelErrors.writerFinished();
        }
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<List<Object>> data = new ArrayList<List<Object>>(rows.size());
        for (Object row : rows) {
            data.add(castMapper().rowValues(row));
        }
        try {
            writer.write(data, sheet);
        } catch (ExcelException e) {
            throw e;
        } catch (RuntimeException e) {
            throw ExcelErrors.io("write", e);
        }
    }

    /**
     * 结束会话：补写表头（若从未写）、flush 并关闭底层 writer；幂等；不关闭输出流。
     */
    public void finish() {
        if (closed) {
            return;
        }
        if (!headerWritten) {
            writeHeader();
        }
        try {
            writer.finish();
        } catch (ExcelException e) {
            throw e;
        } catch (RuntimeException e) {
            throw ExcelErrors.io("finish", e);
        } finally {
            closed = true;
        }
    }

    /**
     * 会话是否已结束。
     *
     * @return 已结束返回 true
     */
    public boolean isClosed() {
        return closed;
    }

    @SuppressWarnings("unchecked")
    private RowMapper<Object> castMapper() {
        return (RowMapper<Object>) mapper;
    }
}
