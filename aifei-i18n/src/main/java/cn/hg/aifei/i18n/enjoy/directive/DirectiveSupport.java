/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.enjoy.directive;

import cn.aifei.enjoy.expr.ast.Assign;
import cn.aifei.enjoy.expr.ast.Expr;
import cn.aifei.enjoy.expr.ast.ExprList;
import cn.hg.aifei.i18n.internal.LocaleNormalizer;
import cn.hg.aifei.i18n.internal.LocaleResolver;
import java.util.ArrayList;
import java.util.List;

/**
 * 三条指令的共享工具（非承诺 API，非规则定义点）。
 *
 * <p>本类只做「选语言层 + 参数切分 + 取值」，规则本身（规范化、回退、插值、dir 判定）一律委派
 * 唯一定义点 {@link LocaleNormalizer} / {@link LocaleResolver} / {@code I18nKit} / {@code LocaleMeta}，
 * 零复制。语言取值优先级链（局部 &gt; 页面级 &gt; 当前生效 locale）在 {@link #effectivePageLang()} 单点落地。</p>
 */
public final class DirectiveSupport {

    private static final String LANG_PARAM = "lang";

    private DirectiveSupport() {
    }

    /**
     * 语言取值唯一优先级链的后两级：页面级 {@code #lang('xxx')} &gt; 当前生效 locale。
     *
     * <p>局部 {@code lang=} 由 I18nDirective 在调用前先判，不进入本方法，从而构成完整三级链且不互相污染。</p>
     *
     * @return 页面级 lang（若已设）否则 {@code LocaleResolver.resolve(null)}（四级优先级唯一定义点）
     */
    public static String effectivePageLang() {
        String page = DirectivePageState.pageLang();
        if (page != null) {
            return page;
        }
        return LocaleResolver.resolve(null);
    }

    /**
     * 把具名参数 eval 结果转为规范化 locale tag；非法走 {@link LocaleNormalizer} 的 IAE（逐字消息）。
     *
     * @param raw 具名参数右值 eval 结果，可 null
     * @return 规范化 BCP 47 tag
     */
    public static String normalizeLang(Object raw) {
        String tag = raw == null ? null : String.valueOf(raw);
        return LocaleNormalizer.normalize(tag);
    }

    /**
     * 扫描表达式列表，抽出唯一的具名参数 {@code lang=}，只记录节点不求值；重复即 IAE。
     *
     * <p>字符串字面量（字符串常量表达式）天然不是 {@link Assign}，不会被误判为具名参数。</p>
     *
     * @param exprList 指令实参表达式列表
     * @return lang 赋值节点，未给返回 null
     * @throws IllegalArgumentException lang 具名参数重复
     */
    public static Assign findLangAssign(ExprList exprList) {
        Assign lang = null;
        for (Expr expr : exprList.getExprArray()) {
            if (expr instanceof Assign && LANG_PARAM.equals(((Assign) expr).getId())) {
                if (lang != null) {
                    throw new IllegalArgumentException("i18n 指令具名参数 lang 重复");
                }
                lang = (Assign) expr;
            }
        }
        return lang;
    }

    /**
     * 按原书写顺序返回位置参数（剔除 lang 具名参数节点）。
     *
     * @param exprList 指令实参表达式列表
     * @param langAssign {@link #findLangAssign(ExprList)} 的结果，可为 null
     * @return 位置参数表达式数组（保序，可变副本）
     */
    public static Expr[] positionalOf(ExprList exprList, Assign langAssign) {
        Expr[] all = exprList.getExprArray();
        if (langAssign == null) {
            return all.clone();
        }
        List<Expr> positional = new ArrayList<Expr>(all.length);
        for (Expr expr : all) {
            if (expr != langAssign) {
                positional.add(expr);
            }
        }
        return positional.toArray(new Expr[positional.size()]);
    }
}
