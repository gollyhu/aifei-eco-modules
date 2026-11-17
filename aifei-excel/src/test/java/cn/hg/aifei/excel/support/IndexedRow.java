/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.excel.support;

import cn.hg.aifei.excel.ExcelColumn;

/**
 * 列序测试行模型：显式 index 字段固定列位，未注解字段按声明顺序填入未占用空位。
 * 参与列：seq(列0,"SEQ") / note(列1,"note") / code(列2,"CODE")。
 */
public class IndexedRow {

    @ExcelColumn(value = "SEQ", index = 0)
    private int seq;

    private String note;

    @ExcelColumn(value = "CODE", index = 2)
    private String code;

    public IndexedRow() {
    }

    public IndexedRow(int seq, String note, String code) {
        this.seq = seq;
        this.note = note;
        this.code = code;
    }

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
