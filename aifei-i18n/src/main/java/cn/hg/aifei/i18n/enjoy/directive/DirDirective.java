/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 * 开发工程师 寇戴码（Enjoy 指令增量）
 */

package cn.hg.aifei.i18n.enjoy.directive;

import cn.aifei.enjoy.Directive;
import cn.aifei.enjoy.Env;
import cn.aifei.enjoy.expr.ast.Expr;
import cn.aifei.enjoy.expr.ast.ExprList;
import cn.aifei.enjoy.io.Writer;
import cn.aifei.enjoy.stat.Scope;
import cn.hg.aifei.i18n.internal.LocaleMeta;
import java.util.Locale;

/**
 * {@code #dir()} / {@code #dir('xxx')} 页面方向读写指令（非承诺 API）。
 *
 * <p>无参读 = 页面级覆盖优先，否则 {@link LocaleMeta#dir(Locale)}（JDK 唯一定义点，含降级 ltr + WARN）；
 * 显式写只接受 {@code ltr}/{@code rtl}，仅影响页面渲染元数据，不改 bundle 导出。</p>
 */
public class DirDirective extends Directive {

    private Expr[] positional;

    @Override
    public void setExprList(ExprList exprList) {
        this.positional = exprList.getExprArray().clone();
    }

    @Override
    public void exec(Env env, Scope scope, Writer writer) {
        if (positional.length == 0) {
            String override = DirectivePageState.pageDirOverride();
            if (override != null) {
                write(writer, override);
                return;
            }
            String lang = DirectiveSupport.effectivePageLang();
            Locale locale = Locale.forLanguageTag(lang == null ? "" : lang);
            write(writer, LocaleMeta.dir(locale));
            return;
        }
        if (positional.length != 1) {
            throw new IllegalArgumentException("#dir 参数过多");
        }
        Object raw = positional[0].eval(scope);
        String value = String.valueOf(raw);
        if (!"ltr".equals(value) && !"rtl".equals(value)) {
            throw new IllegalArgumentException("dir 非法: " + value + "（仅允许 ltr / rtl）");
        }
        DirectivePageState.setPageDirOverride(value);
        write(writer, value);
    }
}
