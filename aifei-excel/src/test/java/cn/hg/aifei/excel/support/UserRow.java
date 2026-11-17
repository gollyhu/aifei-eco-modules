/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.excel.support;

import cn.hg.aifei.excel.ExcelColumn;
import cn.hg.aifei.excel.ExcelIgnore;

/**
 * 业务视角的测试行模型：未注解字段（name / score）、value 表头字段（age）、排除字段（internalNote）。
 * 参与列顺序：name(列0,"name") / age(列1,"Age") / score(列2,"score")。
 */
public class UserRow {

    private String name;

    @ExcelColumn("Age")
    private int age;

    private double score;

    @ExcelIgnore
    private String internalNote;

    public UserRow() {
    }

    public UserRow(String name, int age, double score, String internalNote) {
        this.name = name;
        this.age = age;
        this.score = score;
        this.internalNote = internalNote;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getInternalNote() {
        return internalNote;
    }

    public void setInternalNote(String internalNote) {
        this.internalNote = internalNote;
    }
}
