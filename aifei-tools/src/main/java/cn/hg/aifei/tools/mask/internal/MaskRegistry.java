/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.mask.internal;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * 脱敏规则注册表（全模块唯一 ConcurrentHashMap 持有者）。
 *
 * <p>类初始化时由 {@link DefaultMasks} 装入九条默认规则；默认规则可被替换但不可删除。</p>
 */
public final class MaskRegistry {

    private static final ConcurrentHashMap<String, UnaryOperator<String>> RULES =
            new ConcurrentHashMap<String, UnaryOperator<String>>();

    static {
        DefaultMasks.install();
    }

    private MaskRegistry() {
    }

    /**
     * 注册新规则；同名已存在则失败。
     *
     * @param name 规则名
     * @param fn 脱敏函数
     * @throws cn.hg.aifei.tools.mask.MaskException 参数为空或同名已存在
     */
    public static void register(String name, UnaryOperator<String> fn) {
        requireName(name);
        requireFunction(fn);
        if (RULES.putIfAbsent(name, fn) != null) {
            throw MaskErrors.alreadyRegistered(name);
        }
    }

    /**
     * 覆盖已有规则；不存在则失败。
     *
     * @param name 规则名
     * @param fn 脱敏函数
     * @throws cn.hg.aifei.tools.mask.MaskException 参数为空或规则未注册
     */
    public static void replace(String name, UnaryOperator<String> fn) {
        requireName(name);
        requireFunction(fn);
        if (!RULES.containsKey(name)) {
            throw MaskErrors.notRegistered(name);
        }
        RULES.put(name, fn);
    }

    /**
     * 按名取规则。
     *
     * @param name 规则名
     * @return 脱敏函数
     * @throws cn.hg.aifei.tools.mask.MaskException 参数为空或规则未注册
     */
    public static UnaryOperator<String> use(String name) {
        requireName(name);
        UnaryOperator<String> fn = RULES.get(name);
        if (fn == null) {
            throw MaskErrors.notRegistered(name);
        }
        return fn;
    }

    private static void requireName(String name) {
        if (name == null) {
            throw MaskErrors.nullName();
        }
    }

    private static void requireFunction(UnaryOperator<String> fn) {
        if (fn == null) {
            throw MaskErrors.nullFunction();
        }
    }
}
