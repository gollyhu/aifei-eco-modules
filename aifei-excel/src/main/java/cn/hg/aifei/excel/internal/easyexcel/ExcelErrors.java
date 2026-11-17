/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.excel.internal.easyexcel;

import cn.hg.aifei.excel.ExcelException;

/**
 * {@link ExcelException} 的唯一构造点与全部异常消息措辞的唯一定义点。
 *
 * <p>本类为非承诺实现层组件，仅供适配层内部调用；所有消息统一带 {@code excel: } 前缀，
 * 逐字对齐设计文档 §7.5 异常语义表。需要异常时使用本类工厂方法，不得在别处自拼消息。</p>
 */
public final class ExcelErrors {

    private ExcelErrors() {
    }

    /**
     * 参数为 null 的异常（场景 1）。
     *
     * @param method 公共方法名
     * @param name   为 null 的参数名
     * @return 已构造的参数错误异常
     */
    public static ExcelException argument(String method, String name) {
        return new ExcelException("excel: " + method + " argument " + name + " must not be null");
    }

    /**
     * 批大小非正的异常（场景 2）。
     *
     * @param n 实际传入的批大小
     * @return 已构造的参数错误异常
     */
    public static ExcelException positiveBatchSize(int n) {
        return new ExcelException("excel: batchSize must be positive, but was: " + n);
    }

    /**
     * 已结束的 writer 会话再次写入的异常（场景 3）。
     *
     * @return 已构造的参数错误异常
     */
    public static ExcelException writerFinished() {
        return new ExcelException("excel: writer session already finished, create a new writer");
    }

    /**
     * 分批回调自身抛异常的包装（场景 5）。
     *
     * @param batchIndex 批次序号（从 0 起）
     * @param cause      回调抛出的原始异常
     * @return 已构造的解析转换类异常，cause 为原异常
     */
    public static ExcelException consumerFailed(int batchIndex, Throwable cause) {
        return new ExcelException("excel: batch consumer failed at batch " + batchIndex
                + ", cause: " + causeMessage(cause), cause);
    }

    /**
     * 底层 IO 失败的包装（场景 6 / 13）。
     *
     * @param operation 操作名：read / write / finish
     * @param cause     底层 IO 异常
     * @return 已构造的 IO 类异常，cause 为原异常
     */
    public static ExcelException io(String operation, Throwable cause) {
        return new ExcelException("excel: io error during " + operation
                + ", cause: " + causeMessage(cause), cause);
    }

    /**
     * 单元格值转换为字段类型失败的包装（场景 7）。
     *
     * @param row   行号（从 0 起，表头为第 0 行）
     * @param col   列号（从 0 起）
     * @param field 字段名
     * @param type  目标 Java 类型
     * @param cause 转换失败原始异常
     * @return 已构造的解析转换类异常，cause 为原异常
     */
    public static ExcelException convert(int row, int col, String field, Class<?> type, Throwable cause) {
        return new ExcelException("excel: convert failed at row " + row + ", column " + col
                + ", field " + field + ", type " + type.getName()
                + ", cause: " + causeMessage(cause), cause);
    }

    /**
     * 表头出现重复名称的异常（场景 8）。
     *
     * @param header 重复的表头名
     * @param col1   首次出现的列号
     * @param col2   再次出现的列号
     * @return 已构造的解析转换类异常
     */
    public static ExcelException duplicatedHeader(String header, int col1, int col2) {
        return new ExcelException("excel: duplicated header \"" + header
                + "\" at column " + col1 + " and " + col2);
    }

    /**
     * 两个字段声明相同列序的异常（场景 9）。
     *
     * @param index 重复的列序
     * @param f1    首次声明该列序的字段名
     * @param f2    再次声明该列序的字段名
     * @return 已构造的参数错误异常
     */
    public static ExcelException duplicatedIndex(int index, String f1, String f2) {
        return new ExcelException("excel: duplicated column index " + index
                + " on fields " + f1 + " and " + f2);
    }

    /**
     * 字段类型不在内置支持范围的异常（场景 10，写侧）。
     *
     * @param type  不支持的字段类型
     * @param field 字段名
     * @return 已构造的参数错误异常
     */
    public static ExcelException unsupportedType(Class<?> type, String field) {
        return new ExcelException("excel: unsupported field type " + type.getName()
                + " on field " + field);
    }

    /**
     * 可选依赖缺失的异常（场景 12）。
     *
     * @return 已构造的可读异常，禁止泄漏裸链接错误
     */
    public static ExcelException missingDependency() {
        return new ExcelException("excel: com.alibaba:easyexcel:3.3.2 is required, add it to your dependencies");
    }

    /**
     * 行模型类型无法实例化的异常（无访问到的无参构造器）。
     *
     * @param type 行模型类型
     * @return 已构造的参数错误异常
     */
    public static ExcelException noDefaultConstructor(Class<?> type) {
        return new ExcelException("excel: row type " + type.getName()
                + " must have an accessible no-arg constructor");
    }

    private static String causeMessage(Throwable cause) {
        if (cause == null) {
            return "";
        }
        String msg = cause.getMessage();
        return msg == null ? cause.getClass().getName() : msg;
    }
}
