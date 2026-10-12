/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.testvalue;

import java.io.Serializable;

/**
 * 业务值类型样本，位于默认不被放行的包内，用于验证 Fury 反序列化白名单。
 */
public class CodecValue implements Serializable {

    private static final long serialVersionUID = 1L;

    public String name;

    public int amount;

    public CodecValue() {
    }

    public CodecValue(String name, int amount) {
        this.name = name;
        this.amount = amount;
    }
}
