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
 * <p>与 {@code testvalue.CodecValue} 分开，是为了让数组形态用例自行声明本包，不必与其它用例
 * 共享放行顺序 —— 放行是进程级且只增不减的，共用同一个包会让「未声明即被拒」这类断言依赖
 * 执行顺序。</p>
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
