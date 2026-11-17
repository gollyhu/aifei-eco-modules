/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.excel.internal.easyexcel;

/**
 * 可选依赖探测的唯一定义点：全模块唯一 {@code Class.forName} 点。
 *
 * <p>{@code easyexcel} 以 optional 声明，消费方未引入时首次调用公共读写 API 必须得到可读异常，
 * 不得泄漏裸 {@code NoClassDefFoundError}。探测只加载不初始化目标类，无反射实例化、无服务发现。</p>
 */
public final class ExcelDeps {

    private static final String EASYEXCEL_ENTRY = "com.alibaba.excel.EasyExcel";

    private ExcelDeps() {
    }

    /**
     * 探测 EasyExcel 内核是否可用；不可用即抛可读异常（设计 §7.5 场景 12）。
     *
     * @throws cn.hg.aifei.excel.ExcelException 依赖缺失时抛出，消息提示如何补齐依赖
     */
    public static void requireEasyExcel() {
        try {
            Class.forName(EASYEXCEL_ENTRY, false, ExcelDeps.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw ExcelErrors.missingDependency();
        } catch (NoClassDefFoundError e) {
            throw ExcelErrors.missingDependency();
        }
    }
}
