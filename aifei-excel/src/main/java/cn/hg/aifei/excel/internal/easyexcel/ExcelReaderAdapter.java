/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.excel.internal.easyexcel;

import cn.hg.aifei.excel.ExcelException;
import com.alibaba.excel.EasyExcelFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 读装配点：{@code EasyExcelFactory.read(...)} builder 配置的唯一落点。
 *
 * <p>统一关闭底层流自动关闭开关（不关闭调用方入参流），读第 0 个 Sheet；
 * 底层失败收敛为 {@code ExcelException}，不向公共面泄漏内核异常类型。</p>
 */
public final class ExcelReaderAdapter {

    private static final int READ_ALL_BATCH = 512;

    private ExcelReaderAdapter() {
    }

    /**
     * 流式读：逐行解析并按批回调。
     *
     * @param in        输入流（本方法不关闭）
     * @param mapper    行映射定义
     * @param batchSize 批大小，须为正数（公共入口已校验）
     * @param consumer  分批回调
     * @param <T>       行模型类型
     */
    public static <T> void stream(InputStream in, RowMapper<T> mapper, int batchSize,
                                  Consumer<List<T>> consumer) {
        BatchReadListener<T> listener = new BatchReadListener<T>(mapper, batchSize, consumer);
        try {
            EasyExcelFactory.read(in)
                    .headRowNumber(0)
                    .autoCloseStream(false)
                    .registerReadListener(listener)
                    .sheet(0)
                    .doRead();
        } catch (ExcelException e) {
            throw e;
        } catch (RuntimeException e) {
            Throwable cause = findIoCause(e);
            throw ExcelErrors.io("read", cause == null ? e : cause);
        }
    }

    /**
     * 一次性全量读：内部仍逐行解析，但把所有行聚合进一个列表返回（内存 O(行数)，仅小文件）。
     *
     * @param in     输入流（本方法不关闭）
     * @param mapper 行映射定义
     * @param <T>    行模型类型
     * @return 全部数据行；无数据行时返回空列表
     */
    public static <T> List<T> readAll(InputStream in, RowMapper<T> mapper) {
        final List<T> all = new ArrayList<T>();
        stream(in, mapper, READ_ALL_BATCH, new Consumer<List<T>>() {
            @Override
            public void accept(List<T> batch) {
                all.addAll(batch);
            }
        });
        return all;
    }

    private static Throwable findIoCause(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof IOException) {
                return current;
            }
            current = current.getCause();
        }
        return null;
    }
}
