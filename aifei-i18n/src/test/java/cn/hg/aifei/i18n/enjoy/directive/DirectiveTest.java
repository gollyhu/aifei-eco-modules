/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 * 测试工程师 策市袁（T-I05：Enjoy 三条指令单测，AC-INC-01~13）
 */

package cn.hg.aifei.i18n.enjoy.directive;

import cn.aifei.enjoy.Engine;
import cn.aifei.log.LogFactory;
import cn.aifei.log.LogKit;
import cn.hg.aifei.i18n.MessageSource;
import cn.hg.aifei.i18n.internal.I18nConfig;
import cn.hg.aifei.i18n.internal.I18nEngine;
import cn.hg.aifei.i18n.internal.I18nHolder;
import cn.hg.aifei.i18n.internal.LocaleContext;
import cn.hg.aifei.i18n.internal.LocaleMeta;
import cn.hg.aifei.i18n.support.FakeMessageSource;
import cn.hg.aifei.i18n.support.RecordingLogFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Enjoy 三条指令单测（T-I05，design.md §16.6 AC-INC-01~13 ↔ 实现映射表逐条对应）。
 *
 * <p>全部用例使用<strong>真实 Enjoy Engine</strong> 渲染内存模板（{@code getTemplateByString(...).renderToString(...)}），
 * 不靠模拟函数替代；零联网、零 HTTP 依赖。语言包由 {@link FakeMessageSource} 内存提供，引擎与门面每例重装，
 * 页面级 state 与请求级 LocaleContext 每例前后清理，用例间无共享可变状态。</p>
 *
 * <pre>
 * AC-INC-01 testLangPositionIndependence      三式写法输出逐字相等
 * AC-INC-02 testLangAnyPositionKeepsArgsOrder  lang 置首/中/尾均提取，剩余参数顺序不变
 * AC-INC-03 testInterpolationAndMissingArg     {0} 插值 + 缺参占位符原样保留
 * AC-INC-04 testNoLangUsesEffectiveLocale      无 lang 走当前生效 locale
 * AC-INC-05 testPageLangGoverns                #lang('zh-CN') 后无 lang 文案跟随
 * AC-INC-06 testLocalOverrideNotPollutePage    局部覆盖不污染页面值
 * AC-INC-07 testDirReadWriteAndIllegal         #dir 读写 + 非法值 IAE
 * AC-INC-08 testDirFallback                    dir 能力不可用 → ltr + WARN，不抛
 * AC-INC-09 testIllegalIae                     lang 重复/非法/缺 key → IAE
 * AC-INC-10 testStringLiteralNotNamedArg       字符串字面量不误判为具名参数
 * AC-INC-11 testRealEngineEndToEnd             真实引擎端到端逐字比对，零联网
 * AC-INC-12 testSameThreadNoLeak               同线程连续渲染不串
 * AC-INC-13 testPriorityChainNoFallback        优先级链高优先级不回退
 * </pre>
 */
public class DirectiveTest {

    private static final String ZH_HELLO = "你好，Bob";
    private static final String EN_HELLO = "Hello, Bob";
    private static final String AR_HELLO = "مرحباً، Bob";

    private Engine engine;
    private LogFactory originalLogFactory;

    @Before
    public void setUp() {
        originalLogFactory = LogKit.get().getLogFactory();
        installFacade();
        // 独立引擎名，避免与 demo 的 "AIFEI" 主引擎及用例间相互污染
        this.engine = Engine.create("DirectiveTest-" + System.nanoTime());
        this.engine.setCacheStringTemplate(false);
        DirectiveRegistrar.register(this.engine);
        LocaleContext.clear();
        DirectivePageState.clear();
    }

    @After
    public void tearDown() {
        LogKit.get().setLogFactory(originalLogFactory);
        LocaleMeta.setDirectionResolver(null);
        LocaleContext.clear();
        DirectivePageState.clear();
        I18nHolder.clear();
    }

    /** 安装 I18nKit 门面：内存 FakeMessageSource 提供 zh-CN/en-US/ja-JP/ar-SA 文案。 */
    private static void installFacade() {
        Properties props = new Properties();
        props.setProperty(I18nConfig.KEY_DEFAULT_LOCALE, "zh-CN");
        I18nConfig config = I18nConfig.parse(props);
        List<MessageSource> sources = new ArrayList<MessageSource>();
        sources.add(new FakeMessageSource("properties")
                .put("zh-CN", "hello", "你好，{0}")
                .put("en-US", "hello", "Hello, {0}")
                .put("ja-JP", "hello", "こんにちは、{0}")
                .put("ar-SA", "hello", "مرحباً، {0}")
                .put("zh-CN", "pair", "{0}->{1}")
                .put("en-US", "pair", "{0}->{1}")
                .put("ar-SA", "pair", "{0}->{1}")
                .put("zh-CN", "noarg", "无参文案")
                .put("en-US", "noarg", "no-arg"));
        I18nHolder.install(new I18nEngine(config, sources));
    }

    /** 真实引擎渲染内存模板（零联网、零 HTTP）。 */
    private String render(String template) {
        return engine.getTemplateByString(template).renderToString(new HashMap<String, Object>());
    }

    /** 断言模板渲染抛 IllegalArgumentException，且消息含全部给定片段（消息可定位）。 */
    private void assertIae(String template, String... fragments) {
        try {
            render(template);
            fail("期望抛出 IllegalArgumentException，模板: " + template);
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            for (String fragment : fragments) {
                assertTrue("异常消息应含 [" + fragment + "]，实际: " + message,
                        message != null && message.contains(fragment));
            }
        }
    }

    /** AC-INC-01：§3.1 三式写法（lang 置中/置首/置尾）输出逐字相等。 */
    @Test
    public void testLangPositionIndependence() {
        String middle = render("#i18n('hello', lang='zh-CN', 'Bob')");
        String first = render("#i18n(lang='zh-CN', 'hello', 'Bob')");
        String last = render("#i18n('hello', 'Bob', lang='zh-CN')");
        assertEquals(ZH_HELLO, middle);
        assertEquals("lang 置中应与置首逐字相等", middle, first);
        assertEquals("lang 置中应与置尾逐字相等", middle, last);
    }

    /** AC-INC-02：lang 置首/中/尾均提取，剩余位置参数顺序不变（双参数 {0}->{1}）。 */
    @Test
    public void testLangAnyPositionKeepsArgsOrder() {
        String middle = render("#i18n('pair', lang='zh-CN', 'A', 'B')");
        String first = render("#i18n(lang='zh-CN', 'pair', 'A', 'B')");
        String last = render("#i18n('pair', 'A', 'B', lang='zh-CN')");
        assertEquals("剩余参数须按原顺序 A->B", "A->B", middle);
        assertEquals("lang 置首不应打乱 args 顺序", middle, first);
        assertEquals("lang 置尾不应打乱 args 顺序", middle, last);
    }

    /** {0} 插值生效；缺参时占位符原样保留。 */
    @Test
    public void testInterpolationAndMissingArg() {
        assertEquals("有参应插值", ZH_HELLO, render("#i18n('hello', lang='zh-CN', 'Bob')"));
        assertEquals("缺参应原样保留占位符", "你好，{0}", render("#i18n('hello', lang='zh-CN')"));
    }

    /** AC-INC-04：无 lang 时走当前生效 locale（请求级 LocaleContext）。 */
    @Test
    public void testNoLangUsesEffectiveLocale() {
        LocaleContext.set("en-US");
        assertEquals("无 lang 应走请求级 en-US", EN_HELLO + "|en-US",
                render("#i18n('hello','Bob')|#lang()"));
    }

    /** AC-INC-05：#lang('xxx') 设置页面级语言后，无 lang 的 #i18n 跟随该值。 */
    @Test
    public void testPageLangGoverns() {
        assertEquals("页面级 zh-CN 应生效", ZH_HELLO, render("#lang('zh-CN')#i18n('hello','Bob')"));
        DirectivePageState.clear();
        assertEquals("页面级 en-US 应生效", EN_HELLO, render("#lang('en-US')#i18n('hello','Bob')"));
    }

    /** AC-INC-06：局部 ar-SA 取阿文，#lang() 仍读页面级 zh-CN（局部覆盖不污染页面值）。 */
    @Test
    public void testLocalOverrideNotPollutePage() {
        assertEquals("局部阿文且页面值不被污染", AR_HELLO + "|zh-CN",
                render("#lang('zh-CN')#i18n('hello', lang='ar-SA', 'Bob')|#lang()"));
        DirectivePageState.clear();
        assertEquals("局部条为阿文、其后无 lang 条仍跟随页面级 zh-CN", AR_HELLO + ZH_HELLO,
                render("#lang('zh-CN')#i18n('hello', lang='ar-SA','Bob')#i18n('hello','Bob')"));
    }

    /** AC-INC-07：#dir('rtl') 后 #dir()=rtl；合法 locale 走 JDK 判定；非法值抛 IAE。 */
    @Test
    public void testDirReadWriteAndIllegal() {
        assertEquals("显式写 rtl 后读回 rtl", "rtl|rtl", render("#dir('rtl')|#dir()"));
        DirectivePageState.clear();
        assertEquals("ar-SA 复用 LocaleMeta/JDK 判定为 rtl", "rtl", render("#lang('ar-SA')#dir()"));
        DirectivePageState.clear();
        assertEquals("zh-CN 判定为 ltr", "ltr", render("#lang('zh-CN')#dir()"));
        assertIae("#dir('bad')", "dir 非法", "bad", "ltr / rtl");
    }

    /** AC-INC-08：dir 判定能力不可用 → ltr + WARN，不抛（注入判定器替身隔离，断言输出 + 日志级别）。 */
    @Test
    public void testDirFallback() {
        final RecordingLogFactory recorder = new RecordingLogFactory();
        LogKit.get().setLogFactory(recorder);
        LocaleMeta.setDirectionResolver(new LocaleMeta.DirectionResolver() {
            @Override
            public String resolve(Locale locale) {
                throw new LinkageError("模拟 java.desktop 缺失");
            }
        });
        recorder.clear();
        assertEquals("dir 降级为 ltr 且不抛", "你好，Bob|ltr",
                render("#lang('zh-CN')#i18n('hello','Bob')|#dir()"));
        assertTrue("dir 降级应 WARN 且含 dir/ltr/locale，实际=" + recorder.events(),
                recorder.has("WARN", "dir", "ltr", "locale="));
    }

    /** AC-INC-09：lang 具名参数重复 / 非法 BCP47 / 缺 key → IAE，消息可定位。 */
    @Test
    public void testIllegalIae() {
        assertIae("#i18n(lang='zh-CN', lang='en-US', 'hello')", "lang 重复");
        assertIae("#i18n('hello', lang='zh_CN_', 'Bob')", "locale 非法", "zh_CN_");
        assertIae("#i18n(lang='zh-CN')", "key 不能为空");
    }

    /** AC-INC-10：args 中字符串字面量 "lang='x'" 视为普通参数，不被当作具名参数。 */
    @Test
    public void testStringLiteralNotNamedArg() {
        assertEquals("字面量应作为普通 {0} 参数", "你好，lang='x'",
                render("#i18n('hello', \"lang='x'\", 'Bob')"));
        assertEquals("真 lang 优先，字面量仍只作普通参数", "Hello, lang='x'",
                render("#i18n('hello', lang='en-US', \"lang='x'\", 'Bob')"));
    }

    /** AC-INC-11：真实 Engine 渲染 §4 场景，输出逐字相等，零联网。 */
    @Test
    public void testRealEngineEndToEnd() {
        assertEquals("中文页嵌阿文 + 页面读值 + 方向：逐字相等",
                ZH_HELLO + "|" + AR_HELLO + "|zh-CN|ltr",
                render("#lang('zh-CN')#i18n('hello','Bob')|#i18n('hello', lang='ar-SA','Bob')|#lang()|#dir()"));
        DirectivePageState.clear();
        assertEquals("插值/字面量 + dir 显式覆盖：逐字相等",
                "你好，lang='x'|rtl|rtl",
                render("#i18n('hello', \"lang='x'\", 'Bob')|#dir('rtl')|#dir()"));
    }

    /** AC-INC-12：同线程连续两次不同语言渲染不串（渲染前后 clear 隔离）。 */
    @Test
    public void testSameThreadNoLeak() {
        // 第一次渲染：页面级 zh-CN（模拟 IoHandler：render 前 clear）
        DirectivePageState.clear();
        String first = render("#lang('zh-CN')#i18n('hello','Bob')|#lang()");
        assertEquals(ZH_HELLO + "|zh-CN", first);

        // 第二次渲染：先 clear（finally 已清 + 渲染前再清），不设页面级，请求级 en-US
        DirectivePageState.clear();
        LocaleContext.set("en-US");
        String second = render("#i18n('hello','Bob')|#lang()");
        assertEquals("不得继承第一次的 zh-CN", EN_HELLO + "|en-US", second);

        // 第三次渲染：再 clear，请求级也清空 → 回落 defaultLocale，不继承上一次的 en-US
        DirectivePageState.clear();
        LocaleContext.clear();
        assertEquals("回落 defaultLocale，不串上一次请求级 en-US", ZH_HELLO + "|zh-CN",
                render("#i18n('hello','Bob')|#lang()"));
    }

    /** AC-INC-13：请求头 en-US + 页面 zh-CN + 局部 ar-SA；高优先级存在时不向下回退。 */
    @Test
    public void testPriorityChainNoFallback() {
        LocaleContext.set("en-US");
        assertEquals("局部 ar-SA / 无 lang 与 #lang() 均 zh-CN（高优先级不回退）",
                AR_HELLO + "|" + ZH_HELLO + "|zh-CN",
                render("#lang('zh-CN')#i18n('hello', lang='ar-SA','Bob')|#i18n('hello','Bob')|#lang()"));
    }
}
