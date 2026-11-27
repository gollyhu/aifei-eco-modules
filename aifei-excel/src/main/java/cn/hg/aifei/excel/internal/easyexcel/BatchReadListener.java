/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.excel.internal.easyexcel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 分批读回调实现：唯一 {@code invoke} 落点。
 *
 * <p>只持有当前一批（上限 batchSize），每读满一批同步回调一次并立即丢弃该批引用；
 * 表头行为第 0 行，仅用于定位列、不进入批次；末批不足 batchSize 在分析结束时兜底回调。
 * 回调自身抛异常时包装为 {@code ExcelException}（cause 为原异常）并中止读。</p>
 *
 * @param <T> 行模型类型
 */
public final class BatchReadListener<T> implements ReadListener<Map<Integer, String>> {

    private final RowMapper<T> mapper;
    private final int batchSize;
    private final Consumer<List<T>> batchConsumer;

    private List<T> currentBatch;
    private int batchIndex;
    private boolean headerResolved;

    /**
     * 构造分批监听器。
     *
     * @param mapper        行映射定义
     * @param batchSize     批大小，须为正数（由公共入口校验）
     * @param batchConsumer 分批回调
     */
    public BatchReadListener(RowMapper<T> mapper, int batchSize, Consumer<List<T>> batchConsumer) {
        this.mapper = mapper;
        this.batchSize = batchSize;
        this.batchConsumer = batchConsumer;
        this.currentBatch = new ArrayList<T>(batchSize);
    }

    @Override
    public void invoke(Map<Integer, String> data, AnalysisContext context) {
        int row = context.readRowHolder().getRowIndex();
        if (!headerResolved) {
            mapper.resolveHeader(data);
            headerResolved = true;
            return;
        }
        currentBatch.add(mapper.mapRow(data, row));
        if (currentBatch.size() == batchSize) {
            deliver();
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        if (headerResolved && !currentBatch.isEmpty()) {
            deliver();
        }
    }

    private void deliver() {
        List<T> batch = currentBatch;
        currentBatch = new ArrayList<T>(batchSize);
        try {
            batchConsumer.accept(batch);
        } catch (RuntimeException e) {
            throw ExcelErrors.consumerFailed(batchIndex, e);
        } catch (Error e) {
            throw ExcelErrors.consumerFailed(batchIndex, e);
        }
        batchIndex++;
    }
}
