/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.elementvalue;

import java.io.Serializable;

/**
 * 数组元素用的业务值类型样本，位于默认不被放行的包内。
 *
 */
public class ElementValue implements Serializable {

    private static final long serialVersionUID = 1L;

    public String name;

    public int amount;

    public ElementValue() {
    }

    public ElementValue(String name, int amount) {
        this.name = name;
        this.amount = amount;
    }
}
