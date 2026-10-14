/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sentinel.demo.arch;

/** 业务状态码接口，扩展新枚举不必修改 Out */
public interface Code {
    int code();
    String msg();
}
