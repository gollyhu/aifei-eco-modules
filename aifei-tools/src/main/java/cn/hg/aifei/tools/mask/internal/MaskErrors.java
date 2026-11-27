/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.mask.internal;

import cn.hg.aifei.tools.mask.MaskException;

/**
 * mask 异常消息与构造的唯一定义点（措辞唯一来源）。
 */
public final class MaskErrors {

    private MaskErrors() {
    }

    /**
     * 规则名重复。
     *
     * @param name 规则名
     * @return 统一异常
     */
    public static MaskException alreadyRegistered(String name) {
        return new MaskException("mask: rule \"" + name + "\" already registered");
    }

    /**
     * 规则未注册。
     *
     * @param name 规则名
     * @return 统一异常
     */
    public static MaskException notRegistered(String name) {
        return new MaskException("mask: rule \"" + name + "\" is not registered");
    }

    /**
     * 规则名为 null。
     *
     * @return 统一异常
     */
    public static MaskException nullName() {
        return new MaskException("mask: rule name must not be null");
    }

    /**
     * 脱敏函数为 null。
     *
     * @return 统一异常
     */
    public static MaskException nullFunction() {
        return new MaskException("mask: mask function must not be null");
    }

    /**
     * 保留位数为负。
     *
     * @return 统一异常
     */
    public static MaskException negativeKeep() {
        return new MaskException("mask: keepLeft and keepRight must not be negative");
    }
}
