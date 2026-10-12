/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.demo.arch;

/** 业务状态码接口，扩展新枚举不必修改 Out */
public interface Code {
    int code();
    String msg();
}
