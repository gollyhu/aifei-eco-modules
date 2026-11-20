/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.enjoy.directive;

import cn.aifei.enjoy.Directive;
import cn.aifei.enjoy.Env;
import cn.aifei.enjoy.expr.ast.Expr;
import cn.aifei.enjoy.expr.ast.ExprList;
import cn.aifei.enjoy.io.Writer;
import cn.aifei.enjoy.stat.Scope;

/**
 * {@code #lang()} / {@code #lang('xxx')} 页面语言读写指令（非承诺 API）。
 *
 * <p>无参读 = {@link DirectiveSupport#effectivePageLang()}（页面级优先，否则当前生效 locale）；
 * 单参写 = 设页面级默认语言（不写请求级 LocaleContext，也不受局部 lang= 污染）。</p>
 */
public class LangDirective extends Directive {

    private Expr[] positional;

    @Override
    public void setExprList(ExprList exprList) {
        this.positional = exprList.getExprArray().clone();
    }

    @Override
    public void exec(Env env, Scope scope, Writer writer) {
        if (positional.length == 0) {
            String lang = DirectiveSupport.effectivePageLang();
            write(writer, lang == null ? "" : lang);
            return;
        }
        if (positional.length != 1) {
            throw new IllegalArgumentException("#lang 参数过多");
        }
        String tag = DirectiveSupport.normalizeLang(positional[0].eval(scope));
        DirectivePageState.setPageLang(tag);
        // 写操作无输出
    }
}
