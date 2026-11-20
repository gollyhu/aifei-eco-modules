/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.mask;

import java.util.function.UnaryOperator;

import cn.hg.aifei.tools.mask.internal.MaskRegistry;

/**
 * 脱敏规则注册表门面：新增、覆盖与按名取用规则。
 *
 * <p>九条默认规则初始即在册、不可删除但可覆盖；覆盖后所有 {@link MaskUtil}
 * 的默认规则方法自动跟随新实现。</p>
 */
public final class MaskKit {

    private MaskKit() {
    }

    /**
     * 注册新规则，同名已存在时失败。
     *
     * @param name 规则名
     * @param fn 脱敏函数
     * @throws MaskException 参数为空或同名已存在
     */
    public static void register(String name, UnaryOperator<String> fn) {
        MaskRegistry.register(name, fn);
    }

    /**
     * 覆盖已有规则，未注册时失败。
     *
     * @param name 规则名
     * @param fn 脱敏函数
     * @throws MaskException 参数为空或规则未注册
     */
    public static void replace(String name, UnaryOperator<String> fn) {
        MaskRegistry.replace(name, fn);
    }

    /**
     * 按名取规则。
     *
     * @param name 规则名
     * @return 脱敏函数
     * @throws MaskException 参数为空或规则未注册
     */
    public static UnaryOperator<String> use(String name) {
        return MaskRegistry.use(name);
    }
}
