/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.demo.service;

import cn.aifei.core.Path;
import cn.aifei.util.PropKit;
import cn.hg.aifei.i18n.FormatKit;
import cn.hg.aifei.i18n.I18nKit;
import cn.hg.aifei.i18n.LanguageBundle;
import cn.hg.aifei.i18n.demo.arch.In;
import cn.hg.aifei.i18n.demo.arch.Out;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示 Service：真实 Enjoy 渲染三条指令 + 语言包下发 + 货币格式化（设计 §6.5 / §6.6）。
 *
 * <p>所有端点 HTTP 状态恒 200，语义由信封 code 承载。页面结构 / 状态卡 / P-06 优先级卡由模板指令渲染；
 * 控件文案走 bundle JSON；货币符号由 JDK / FormatKit 产出，demo 不写死符号常量。</p>
 */
@Path("/i18n/demo")
public class I18nDemoService {

    /** money 端点默认演示金额。 */
    private static final double DEFAULT_AMOUNT = 12800.0D;

    /**
     * 演示主页面：把生效 locale 与整包元数据放 model，模板顶部用 {@code #lang(lang)} 设页面级语言。
     *
     * @param in 请求输入（可选 {@code ?lang=xx}）
     * @return view，data = { lang, localeName, dir, defaultLocale }
     */
    @Path("page")
    public Out page(In in) {
        String target = resolveTarget(in.getStr("lang"));
        LanguageBundle bundle = I18nKit.bundle(target);
        Map<String, Object> model = new LinkedHashMap<String, Object>();
        model.put("lang", bundle.locale());
        model.put("localeName", bundle.localeName());
        model.put("dir", bundle.dir());
        model.put("defaultLocale", PropKit.get("i18n.defaultLocale"));
        return Out.ok().view("/i18n/index.html").data(model);
    }

    /**
     * 语言包下发：前端控件文案走此四字段 JSON（原 design §9.4 契约，原文含 {0}）。
     *
     * @param in 请求输入（可选 {@code ?locale=xx}）
     * @return data = { locale, localeName, dir, messages }
     */
    @Path("bundle")
    public Out bundle(In in) {
        String target = resolveTarget(in.getStr("locale"));
        LanguageBundle bundle = I18nKit.bundle(target);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("locale", bundle.locale());
        data.put("localeName", bundle.localeName());
        data.put("dir", bundle.dir());
        data.put("messages", bundle.messages());
        return Out.ok().data(data);
    }

    /**
     * Accept-Language 端到端：请求头经 I18nHandler 绑定上下文，页面 locale 自动切换。
     *
     * @param in 请求输入（Accept-Language 头）
     * @return data = { acceptLanguage, title, hello }，文案本身证明 locale 切换生效
     */
    @Path("header")
    public Out header(In in) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("acceptLanguage", in.getHeader("Accept-Language"));
        data.put("title", I18nKit.message("page.title"));
        data.put("hello", I18nKit.message("hello", "Bob"));
        return Out.ok().data(data);
    }

    /**
     * RTL iframe 页面：模板顶部写死 {@code #lang('ar-SA')} → {@code #i18n(key)} → {@code #dir()}（P-04）。
     *
     * @return view
     */
    @Path("rtl")
    public Out rtl() {
        return Out.ok().view("/i18n/rtl.html");
    }

    /**
     * 货币展示：demo 演示逻辑选币种（en-US→USD / ja-JP→JPY / 其它→CNY），
     * 符号与小数位一律由 JDK / FormatKit 产出，demo 不写死符号表（P-05）。
     *
     * @param in 请求输入（可选 {@code ?locale=xx&amount=yy}）
     * @return data = { locale, currencyCode, amount, formatted, symbol }
     */
    @Path("money")
    public Out money(In in) {
        String locale = resolveTarget(in.getStr("locale"));
        String currencyCode = "en-US".equals(locale) ? "USD" : "ja-JP".equals(locale) ? "JPY" : "CNY";
        Double amountParam = in.getDouble("amount");
        double amount = amountParam == null ? DEFAULT_AMOUNT : amountParam.doubleValue();
        String formatted = FormatKit.formatCurrency(amount, currencyCode, locale);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("locale", locale);
        data.put("currencyCode", currencyCode);
        data.put("amount", amount);
        data.put("formatted", formatted);
        data.put("symbol", leadingSymbol(formatted));
        return Out.ok().data(data);
    }

    /** 空 locale 落 defaultLocale；非空交由 I18nKit.bundle 统一规范化 / 校验。 */
    private static String resolveTarget(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return PropKit.get("i18n.defaultLocale");
        }
        return raw;
    }

    /** 从 FormatKit 输出中截取首个数字之前的符号片段（符号来自 JDK，不自建映射表）。 */
    private static String leadingSymbol(String formatted) {
        for (int i = 0; i < formatted.length(); i++) {
            char c = formatted.charAt(i);
            if (Character.isDigit(c)) {
                return formatted.substring(0, i).trim();
            }
        }
        return "";
    }
}
