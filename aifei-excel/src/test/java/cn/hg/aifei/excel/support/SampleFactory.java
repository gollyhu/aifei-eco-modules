/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.excel.support;

import com.alibaba.excel.EasyExcelFactory;
import com.alibaba.excel.support.ExcelTypeEnum;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 测试样本工厂：用 EasyExcel 内核在内存中生成「表头行 + 数据行」的 xls / xlsx 字节，
 * 供读侧测试构造自定义表头、重复表头、非法值等场景。样本不落盘、不提交二进制。
 */
public final class SampleFactory {

    private SampleFactory() {
    }

    /** 生成一行单元格值。 */
    public static List<Object> cells(Object... values) {
        return new ArrayList<Object>(Arrays.asList(values));
    }

    /** 用指定 Excel 类型生成工作簿字节（rows 的第一行会被视为表头行）。 */
    public static byte[] workbook(ExcelTypeEnum type, List<List<Object>> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcelFactory.write(out).excelType(type).sheet(0).doWrite(rows);
        return out.toByteArray();
    }

    /** 生成 xlsx 字节。 */
    public static byte[] xlsx(List<List<Object>> rows) {
        return workbook(ExcelTypeEnum.XLSX, rows);
    }

    /** 生成 xls 字节。 */
    public static byte[] xls(List<List<Object>> rows) {
        return workbook(ExcelTypeEnum.XLS, rows);
    }
}
