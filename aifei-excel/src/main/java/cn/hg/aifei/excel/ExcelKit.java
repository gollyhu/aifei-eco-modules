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

/**
 * Excel 一次性便捷门面：全静态、随取随用，无 Plugin 装配期（设计 §5.2 / D15）。
 *
 * <p>{@link #readAll} 一次性把全部行读进列表返回，<b>内存 O(行数)，仅定位小文件，大文件请用
 * {@link Excel#read} 分批</b>；{@link #write} 是「writer 会话 + 一批写 + finish」的语法糖。</p>
 *
 * <p>与 {@code HttpKit}「未装配即失败」不同，本门面任何时刻都可调用；
 * 一律不关闭调用方传入的流。支持 xls / xlsx。</p>
 */
public final class ExcelKit {

    private ExcelKit() {
    }

    /**
     * 一次性全量读：返回全部数据行；无数据行时返回空列表（非 null）。
     * 内存 O(行数)，仅用于小文件；大文件请用 {@link Excel#read} 分批。不关闭入参流。
     *
     * @param in   输入流，本方法不关闭
     * @param type 行模型类型
     * @param <T>  行模型类型
     * @return 全部数据行，无数据时为空列表
     * @throws ExcelException 参数为 null、IO 失败、解析转换失败或内核依赖缺失时抛出
     */
    public static <T> List<T> readAll(InputStream in, Class<T> type) {
        if (in == null) {
            throw ExcelErrors.argument("readAll", "in");
        }
        if (type == null) {
            throw ExcelErrors.argument("readAll", "type");
        }
        ExcelDeps.requireEasyExcel();
        RowMapper<T> mapper = RowMapper.build(type);
        return ExcelReaderAdapter.readAll(in, mapper);
    }

    /**
     * 列表一次写（默认 Sheet 名），写完即结束会话；不关闭输出流。
     * rows 为 null / 空时写表头后结束，不抛异常。
     *
     * @param out  输出流，本方法不关闭
     * @param type 行模型类型
     * @param rows 数据行列表，可为空
     * @param <T>  行模型类型
     * @throws ExcelException 参数为 null、IO 失败、字段类型不支持、转换失败或内核依赖缺失时抛出
     */
    public static <T> void write(OutputStream out, Class<T> type, List<T> rows) {
        write(out, type, null, rows);
    }

    /**
     * 列表一次写（指定 Sheet 名），写完即结束会话；不关闭输出流。
     * rows 为 null / 空时写表头后结束，不抛异常。
     *
     * @param out       输出流，本方法不关闭
     * @param type      行模型类型
     * @param sheetName Sheet 名，为空时使用默认名
     * @param rows      数据行列表，可为空
     * @param <T>       行模型类型
     * @throws ExcelException 参数为 null、IO 失败、字段类型不支持、转换失败或内核依赖缺失时抛出
     */
    public static <T> void write(OutputStream out, Class<T> type, String sheetName, List<T> rows) {
        if (out == null) {
            throw ExcelErrors.argument("write", "out");
        }
        if (type == null) {
            throw ExcelErrors.argument("write", "type");
        }
        Excel session = Excel.writer(out, type, sheetName);
        session.write(rows);
        session.finish();
    }
}
