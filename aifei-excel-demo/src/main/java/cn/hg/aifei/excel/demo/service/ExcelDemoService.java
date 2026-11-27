/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.excel.demo.service;

import cn.aifei.core.Path;
import cn.hg.aifei.excel.Excel;
import cn.hg.aifei.excel.ExcelColumn;
import cn.hg.aifei.excel.ExcelException;
import cn.hg.aifei.excel.ExcelKit;
import cn.hg.aifei.excel.demo.arch.In;
import cn.hg.aifei.excel.demo.arch.Out;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 演示 Service：覆盖 aifei-excel 的 5 个真实端点。
 *
 * <p>所有端点 HTTP 状态恒 200，语义由信封 {@code code} 承载（{@code 0} 成功）。
 * 数据一律来自真实读写：{@code /parse} 真实解析上传文件，{@code /export-*} 真实生成可打开的 xlsx。</p>
 */
@Path("/excel/demo")
public class ExcelDemoService {

    /** xlsx 下载 Content-Type。 */
    private static final String XLSX_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** parse 端点样例行上限。 */
    private static final int SAMPLE_LIMIT = 10;

    /** 流式导出单次写入的批大小。 */
    private static final int WRITE_BATCH = 5000;

    /** 流式导出行数上限，防止演示参数把服务打满。 */
    private static final int MAX_STREAM_ROWS = 500000;

    /**
     * 演示页视图：由 Enjoy 从 {@code webapp/view/excel/index.html} 渲染。
     *
     * @return 模板名，必须以 {@code /} 开头
     */
    @Path("page")
    public String page() {
        return "/excel/index.html";
    }

    /**
     * 依赖状态：由 demo 自身尝试加载内核入口探测（demo 已声明 easyexcel，恒可用）。
     *
     * @return {@code data = { easyExcelAvailable, version }}
     */
    @Path("state")
    public Out state() {
        boolean available;
        try {
            Class.forName("com.alibaba.excel.EasyExcel", false, getClass().getClassLoader());
            available = true;
        } catch (ClassNotFoundException e) {
            available = false;
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("easyExcelAvailable", available);
        data.put("version", "3.3.2");
        return Out.ok().data(data);
    }

    /**
     * 上传解析：真实调用 {@link Excel#read} 分批回调，累计统计并取前若干行样例。
     *
     * @param in        请求输入（含 multipart 文件体与 batchSize 参数）
     * @param batchSize 批大小，缺省 1000
     * @return {@code data = { fileName, rows, batches, headers, sample }}
     */
    @Path("parse")
    public Out parse(In in, Integer batchSize) {
        byte[] bytes = in.getUploadBytes();
        String fileName = in.getUploadFileName();
        if (bytes == null || bytes.length == 0) {
            return Out.fail("未收到上传文件（multipart 字段名 file）");
        }
        int size = batchSize == null || batchSize.intValue() <= 0 ? 1000 : batchSize.intValue();

        final int[] rowCount = new int[]{0};
        final int[] batchCount = new int[]{0};
        final List<List<String>> sample = new ArrayList<List<String>>();
        final List<String> headers = new ArrayList<String>();

        try {
            Excel.read(new ByteArrayInputStream(bytes), UploadRow.class, size,
                    new Consumer<List<UploadRow>>() {
                        @Override
                        public void accept(List<UploadRow> batch) {
                            if (batchCount[0] == 0) {
                                // 第一批时填表头（由映射元数据的表头名构成）
                                for (int c = 0; c < HEADERS.length; c++) {
                                    headers.add(HEADERS[c]);
                                }
                            }
                            batchCount[0]++;
                            rowCount[0] += batch.size();
                            for (UploadRow row : batch) {
                                if (sample.size() < SAMPLE_LIMIT) {
                                    List<String> line = new ArrayList<String>();
                                    line.add(String.valueOf(row.getA()));
                                    line.add(String.valueOf(row.getB()));
                                    line.add(String.valueOf(row.getC()));
                                    sample.add(line);
                                }
                            }
                        }
                    });
        } catch (ExcelException e) {
            return Out.fail("解析失败: " + e.getMessage());
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("fileName", fileName);
        data.put("rows", rowCount[0]);
        data.put("batches", batchCount[0]);
        data.put("headers", headers);
        data.put("sample", sample);
        return Out.ok().data(data);
    }

    /**
     * 一次性导出：真实调用 {@link ExcelKit#write} 写若干示例行。
     *
     * @return 二进制 xlsx 下载
     */
    @Path("export-once")
    public Out exportOnce() {
        List<OrderRow> rows = sampleRows(12);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelKit.write(out, OrderRow.class, rows);
        return Out.binary(out.toByteArray(), "aifei-excel-once.xlsx", XLSX_TYPE);
    }

    /**
     * 流式导出：真实调用 {@link Excel#writer} 会话，分批多次 {@code write} 后 {@code finish}，
     * 演示同流多次追加与内存边界（内存不随行数线性增长）。
     *
     * @param rows 导出行数，缺省 50000
     * @return 二进制 xlsx 下载
     */
    @Path("export-stream")
    public Out exportStream(Integer rows) {
        int total = rows == null || rows.intValue() <= 0 ? 50000 : rows.intValue();
        total = Math.min(total, MAX_STREAM_ROWS);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Excel writer = Excel.writer(out, OrderRow.class, "流式明细");
        try {
            int written = 0;
            while (written < total) {
                int size = Math.min(WRITE_BATCH, total - written);
                List<OrderRow> batch = new ArrayList<OrderRow>(size);
                for (int i = 0; i < size; i++) {
                    int no = written + i + 1;
                    batch.add(new OrderRow(no, "订单-" + no, no * 1.5D));
                }
                writer.write(batch);
                written += size;
            }
        } finally {
            writer.finish();
        }
        return Out.binary(out.toByteArray(), "aifei-excel-stream.xlsx", XLSX_TYPE);
    }

    // ===== 内部工具 =====================================================================

    /** 上传解析用的行模型：按列序定位（index 优先），可解析任意三列 Excel。 */
    public static class UploadRow {
        @ExcelColumn(index = 0)
        private String a;
        @ExcelColumn(index = 1)
        private String b;
        @ExcelColumn(index = 2)
        private String c;

        public UploadRow() {
        }

        public String getA() {
            return a;
        }

        public String getB() {
            return b;
        }

        public String getC() {
            return c;
        }
    }

    /** 导出行模型：列序 + 表头名 + 三类型字段，演示映射规则。 */
    public static class OrderRow {
        @ExcelColumn(value = "序号", index = 0)
        private int no;
        @ExcelColumn(value = "名称", index = 1)
        private String name;
        @ExcelColumn(value = "金额", index = 2)
        private double amount;

        public OrderRow() {
        }

        public OrderRow(int no, String name, double amount) {
            this.no = no;
            this.name = name;
            this.amount = amount;
        }
    }

    /** 按列序解析时的展示表头（与上传文件真实表头无关）。 */
    private static final String[] HEADERS = {"第1列", "第2列", "第3列"};

    private static List<OrderRow> sampleRows(int count) {
        List<OrderRow> rows = new ArrayList<OrderRow>(count);
        for (int i = 1; i <= count; i++) {
            rows.add(new OrderRow(i, "订单-" + i, i * 9.9D));
        }
        return rows;
    }
}
