/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 * 开发工程师 寇戴码（Enjoy 指令增量）
 */

package cn.hg.aifei.i18n.internal.directive;

import cn.aifei.enjoy.Directive;
import cn.aifei.enjoy.Env;
import cn.aifei.enjoy.expr.ast.Assign;
import cn.aifei.enjoy.expr.ast.Expr;
import cn.aifei.enjoy.expr.ast.ExprList;
import cn.aifei.enjoy.io.Writer;
import cn.aifei.enjoy.stat.Scope;
import cn.hg.aifei.i18n.I18nKit;
import java.util.Arrays;

/**
 * {@code #i18n(...)} 取文案指令（非承诺 API）。
 *
 * <p>位置无关抽取唯一的具名参数 {@code lang=}，其余按原顺序切为 key + args，再分发到
 * {@link I18nKit} 既有重载；回退链 / 规范化 / 插值全部由 I18nKit 内部唯一定义点完成，本类零复制。</p>
 *
 * <pre>
 * #i18n('hello', lang='zh-CN', 'Bob')
 * #i18n(lang='zh-CN', 'hello', 'Bob')   → key=hello, lang=zh-CN, args=[Bob]
 * #i18n('hello', 'Bob', lang='zh-CN')
 * </pre>
 */
public class I18nDirective extends Directive {

    private Assign langAssign;
    private Expr[] positional;

    /**
     * 解析期只做结构化切分，不求值（lang 具名参数重复在此抛出）。
     */
    @Override
    public void setExprList(ExprList exprList) {
        this.langAssign = DirectiveSupport.findLangAssign(exprList);
        this.positional = DirectiveSupport.positionalOf(exprList, this.langAssign);
    }

    @Override
    public void exec(Env env, Scope scope, Writer writer) {
        // ① 局部 lang（具名参数右值读值，禁用 Assign.eval 以免污染 scope）
        String langVal = null;
        if (langAssign != null) {
            langVal = DirectiveSupport.normalizeLang(langAssign.getRight().eval(scope));
        }

        // ② 位置参数按序求值：第一个 = key，其余 = args
        if (positional.length == 0) {
            throw new IllegalArgumentException("key 不能为空");
        }
        Object[] pos = new Object[positional.length];
        for (int i = 0; i < positional.length; i++) {
            pos[i] = positional[i].eval(scope);
        }
        String key = String.valueOf(pos[0]);
        Object[] args = positional.length > 1
                ? Arrays.copyOfRange(pos, 1, pos.length) : new Object[0];

        // ③ 选语言层：局部 lang > 页面级 #lang > 当前生效 locale（唯一落点）
        String effLang = langVal != null ? langVal : DirectiveSupport.effectivePageLang();

        // ④ 薄封装分发到 I18nKit 既有重载
        String out;
        if (effLang != null && args.length > 0) {
            out = I18nKit.message(key, effLang, args);
        } else if (effLang != null) {
            out = I18nKit.message(key, effLang);
        } else if (args.length > 0) {
            out = I18nKit.message(key, null, args);
        } else {
            out = I18nKit.message(key);
        }
        write(writer, out);
    }
}
