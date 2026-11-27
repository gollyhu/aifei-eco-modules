/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.excel;

import cn.hg.aifei.excel.internal.easyexcel.ExcelDeps;
import cn.hg.aifei.excel.internal.easyexcel.ExcelErrors;
import cn.hg.aifei.excel.internal.easyexcel.ExcelReaderAdapter;
import cn.hg.aifei.excel.internal.easyexcel.ExcelWriterAdapter;
import cn.hg.aifei.excel.internal.easyexcel.RowMapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Consumer;

/**
 * Excel 流式门面：静态分批读 + 有状态 writer 会话。
 *
 * <p><b>流式读</b>：{@link #read} 按行 SAX 解析，累积满批大小回调一次，末批不足亦回调；
 * 峰值内存 O(batchSize × 单对象大小)，与文件总行数无关；回调后本模块不再持有该批引用。</p>
 *
 * <p><b>流式写</b>：{@link #writer} 创建会话，{@link #write} 可对同一会话多次追加，行按调用顺序写入同一 Sheet；
 * {@link #finish} / {@link #close} 幂等收尾。writer 会话非线程安全，须单线程写入；静态读无共享状态，可并发。</p>
 *
 * <p><b>流所有权</b>：本门面一律不关闭调用方传入的 {@link InputStream} / {@link OutputStream}；
 * 流的开关与生命周期由调用方负责。支持 xls / xlsx，由内核按文件签名自动识别。</p>
 */
public final class Excel implements AutoCloseable {

    /** 两参 read 重载的默认批大小。 */
    private static final int DEFAULT_BATCH_SIZE = 1000;

    private final ExcelWriterAdapter adapter;

    private Excel(OutputStream out, Class<?> type, String sheetName) {
        ExcelDeps.requireEasyExcel();
        RowMapper<?> mapper = RowMapper.build(type);
        this.adapter = new ExcelWriterAdapter(out, mapper, sheetName);
    }

    /**
     * 流式读（默认批大小 1000），逐批回调；不关闭入参流。
     *
     * @param in            输入流，本方法不关闭
     * @param type          行模型类型
     * @param batchConsumer 每读满一批触发的回调，末批不足亦触发一次
     * @param <T>           行模型类型
     * @throws ExcelException 参数为 null、IO 失败、解析转换失败、回调自身抛异常或内核依赖缺失时抛出
     */
    public static <T> void read(InputStream in, Class<T> type, Consumer<List<T>> batchConsumer) {
        read(in, type, DEFAULT_BATCH_SIZE, batchConsumer);
    }

    /**
     * 流式读（指定批大小），逐批回调；不关闭入参流。
     *
     * @param in            输入流，本方法不关闭
     * @param type          行模型类型
     * @param batchSize     批大小，须大于 0
     * @param batchConsumer 每读满一批触发的回调，末批不足亦触发一次
     * @param <T>           行模型类型
     * @throws ExcelException 参数为 null 或 batchSize 非正、IO 失败、解析转换失败、回调自身抛异常或内核依赖缺失时抛出
     */
    public static <T> void read(InputStream in, Class<T> type, int batchSize,
                                Consumer<List<T>> batchConsumer) {
        if (in == null) {
            throw ExcelErrors.argument("read", "in");
        }
        if (type == null) {
            throw ExcelErrors.argument("read", "type");
        }
        if (batchConsumer == null) {
            throw ExcelErrors.argument("read", "batchConsumer");
        }
        if (batchSize <= 0) {
            throw ExcelErrors.positiveBatchSize(batchSize);
        }
        ExcelDeps.requireEasyExcel();
        RowMapper<T> mapper = RowMapper.build(type);
        ExcelReaderAdapter.stream(in, mapper, batchSize, batchConsumer);
    }

    /**
     * 创建 writer 会话（默认 Sheet 名），此时不向输出流写任何字节。
     *
     * @param out  输出流，本会话不关闭
     * @param type 行模型类型
     * @param <T>  行模型类型
     * @return writer 会话
     * @throws ExcelException 参数为 null、内核依赖缺失或构建 writer 失败时抛出
     */
    public static <T> Excel writer(OutputStream out, Class<T> type) {
        return writer(out, type, null);
    }

    /**
     * 创建 writer 会话（指定 Sheet 名），此时不向输出流写任何字节。
     *
     * @param out       输出流，本会话不关闭
     * @param type      行模型类型
     * @param sheetName Sheet 名，为空时使用默认名
     * @param <T>       行模型类型
     * @return writer 会话
     * @throws ExcelException 参数为 null、内核依赖缺失或构建 writer 失败时抛出
     */
    public static <T> Excel writer(OutputStream out, Class<T> type, String sheetName) {
        if (out == null) {
            throw ExcelErrors.argument("writer", "out");
        }
        if (type == null) {
            throw ExcelErrors.argument("writer", "type");
        }
        return new Excel(out, type, sheetName);
    }

    /**
     * 向同一会话追加一批数据行；可多次调用，行按调用顺序写入；null / 空列表仅确保表头后无操作。
     * 会话非线程安全，须单线程调用。
     *
     * @param rows 数据行列表，可为空
     * @param <T>  行模型类型
     * @throws ExcelException 会话已结束、字段类型不支持、转换失败或 IO 失败时抛出
     */
    public <T> void write(List<T> rows) {
        if (adapter.isClosed()) {
            throw ExcelErrors.writerFinished();
        }
        adapter.writeHeader();
        adapter.appendRows(rows);
    }

    /**
     * 结束会话：补写表头（若尚未写）、flush 并关闭底层 writer；幂等；不关闭调用方输出流。
     *
     * @throws ExcelException 底层 flush / close 失败时抛出
     */
    public void finish() {
        adapter.finish();
    }

    /**
     * 等价于 {@link #finish()}，便于 try-with-resources；不关闭调用方输出流。
     *
     * @throws ExcelException 底层 flush / close 失败时抛出
     */
    @Override
    public void close() {
        finish();
    }
}
