/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n;

import cn.aifei.log.LogFactory;
import cn.aifei.log.LogKit;
import cn.hg.aifei.i18n.defaultSource.PropertiesSource;
import cn.hg.aifei.i18n.internal.I18nConfig;
import cn.hg.aifei.i18n.internal.I18nEngine;
import cn.hg.aifei.i18n.internal.I18nHolder;
import cn.hg.aifei.i18n.internal.LocaleContext;
import cn.hg.aifei.i18n.internal.LocaleMeta;
import cn.hg.aifei.i18n.support.FakeMessageSource;
import cn.hg.aifei.i18n.support.RecordingLogFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * QA 独立复验补充用例：日志分级口径（AC-02-2 / AC-09-6 / AC-14-5 / AC-15-4）。
 *
 * <p>开发现有用例只断言「返回值 / 不抛 / 跳过」等行为，未断言日志**级别**。本类注入可插拔日志工厂，
 * 直接断言：未命中=DEBUG 且无 WARN/ERROR（AC-02-2）；来源故障=WARN 含稳定名+key（AC-15-4/15-1）；
 * 不可列举来源跳过=WARN（AC-09-6）；dir 判定降级=WARN（AC-14-5）；故障 WARN 区别于未命中 DEBUG。</p>
 */
public class QaLogLevelTest {

    private LogFactory original;
    private RecordingLogFactory recorder;
    private ClassLoader originalLoader;

    @Before
    public void setUp() {
        original = LogKit.get().getLogFactory();
        originalLoader = Thread.currentThread().getContextClassLoader();
        recorder = new RecordingLogFactory();
        LogKit.get().setLogFactory(recorder);
        LocaleContext.clear();
    }

    @After
    public void tearDown() {
        LogKit.get().setLogFactory(original);
        Thread.currentThread().setContextClassLoader(originalLoader);
        LocaleMeta.setDirectionResolver(null);
        LocaleContext.clear();
        I18nHolder.clear();
    }

    private static void install(long ttl, FakeMessageSource... sources) {
        Properties props = new Properties();
        props.setProperty(I18nConfig.KEY_DEFAULT_LOCALE, "en");
        props.setProperty(I18nConfig.KEY_CACHE_TTL, String.valueOf(ttl));
        I18nConfig config = I18nConfig.parse(props);
        List<cn.hg.aifei.i18n.MessageSource> list =
                new ArrayList<cn.hg.aifei.i18n.MessageSource>(Arrays.asList(sources));
        I18nHolder.install(new I18nEngine(config, list));
    }

    /** AC-02-2：全部来源未命中 → DEBUG（含 key+locale），且无 WARN / ERROR。 */
    @Test
    public void missLogsDebugOnly() {
        install(60000, new FakeMessageSource("properties"));
        recorder.clear();
        assertEquals("no.such.key", I18nKit.local("zh-CN").message("no.such.key"));
        assertTrue("未命中应有 DEBUG 且含 key+locale，实际=" + recorder.events(),
                recorder.has("DEBUG", "no.such.key", "zh-CN"));
        assertTrue("未命中不应有任何 WARN，实际=" + recorder.events(),
                recorder.ofLevel("WARN").isEmpty());
        assertTrue("未命中不应有任何 ERROR，实际=" + recorder.events(),
                recorder.ofLevel("ERROR").isEmpty());
    }

    /** AC-15-4 / AC-15-1：单来源故障后回退成功 → WARN 含稳定名+key，且不向上抛。 */
    @Test
    public void sourceFailureLogsWarnWithNameAndKey() {
        install(60000,
                new FakeMessageSource("db").failing(true),
                new FakeMessageSource("properties").put("zh-CN", "k", "ok"));
        recorder.clear();
        assertEquals("ok", I18nKit.local("zh-CN").message("k"));
        assertTrue("故障应产生 WARN 且含稳定名 db 与 key k，实际=" + recorder.events(),
                recorder.has("WARN", "db", "k"));
    }

    /** AC-15-2 / AC-15-4：全部来源故障 → 每个故障来源各一条 WARN，返回 key，仍无 ERROR。 */
    @Test
    public void allSourcesFailLogsWarnPerSource() {
        install(60000,
                new FakeMessageSource("db").failing(true),
                new FakeMessageSource("kv").failing(true));
        recorder.clear();
        assertEquals("k", I18nKit.local("zh-CN").message("k"));
        assertTrue("db 故障应 WARN，实际=" + recorder.events(), recorder.has("WARN", "db", "k"));
        assertTrue("kv 故障应 WARN，实际=" + recorder.events(), recorder.has("WARN", "kv", "k"));
        assertTrue("运行期故障不应产生 ERROR，实际=" + recorder.events(),
                recorder.ofLevel("ERROR").isEmpty());
    }

    /** AC-09-6：导出遇不可列举来源 → 跳过 + WARN 含稳定名。 */
    @Test
    public void nonListableSourceLogsWarnOnExport() {
        install(60000, new FakeMessageSource("remote").listable(false));
        recorder.clear();
        LanguageBundle bundle = I18nKit.bundle("zh-CN");
        assertFalse("不可列举来源的 key 不应出现在整包", bundle.messages().containsKey("only"));
        assertTrue("不可列举来源跳过应 WARN 且含稳定名 remote，实际=" + recorder.events(),
                recorder.has("WARN", "remote", "整包列举"));
    }

    /** AC-15-5：导出期间可列举来源抛异常 → 跳过 + WARN 含稳定名，导出仍成功。 */
    @Test
    public void listFailureLogsWarnOnExport() {
        install(60000, new FakeMessageSource("db").listFailing(true));
        recorder.clear();
        LanguageBundle bundle = I18nKit.bundle("zh-CN");
        assertEquals("列举故障不影响导出成功", 0, bundle.messages().size());
        assertTrue("列举故障应 WARN 且含稳定名 db，实际=" + recorder.events(),
                recorder.has("WARN", "db", "整包列举失败"));
    }

    /** AC-14-5：dir 判定能力不可用 → 降级 ltr + WARN，导出仍成功。 */
    @Test
    public void dirDegradationLogsWarn() {
        install(60000, new FakeMessageSource("properties").put("zh-CN", "k", "v"));
        LocaleMeta.setDirectionResolver(locale -> {
            throw new LinkageError("模拟 java.desktop 缺失");
        });
        recorder.clear();
        LanguageBundle bundle = I18nKit.bundle("zh-CN");
        assertEquals("降级为 ltr", "ltr", bundle.dir());
        assertEquals("导出仍成功", "v", bundle.messages().get("k"));
        // AC-14-5 只要求 WARN 含 locale；LocaleMeta 打印的是 Locale.toString()（zh_CN），
        // 而非规范化 tag（zh-CN），故断言 "locale=" 字段存在即可，不写死具体格式。
        assertTrue("dir 降级应 WARN 且含 locale，实际=" + recorder.events(),
                recorder.has("WARN", "dir", "ltr", "locale="));
    }

    /** 日志分级交叉验证：未命中路径全程无 WARN，故障路径全程无 DEBUG（含故障后命中其他来源场景）。 */
    @Test
    public void debugAndWarnLevelsAreDistinct() {
        // 场景 A：无故障的纯未命中
        install(60000, new FakeMessageSource("properties"));
        recorder.clear();
        I18nKit.local("zh-CN").message("miss.only");
        assertEquals("纯未命中：DEBUG>=1、WARN=0", 0, recorder.ofLevel("WARN").size());
        assertTrue("纯未命中应有 DEBUG", recorder.ofLevel("DEBUG").size() >= 1);

        // 场景 B：故障来源 + 命中来源（命中后不会再打未命中 DEBUG）
        install(60000,
                new FakeMessageSource("db").failing(true),
                new FakeMessageSource("properties").put("zh-CN", "k", "ok"));
        recorder.clear();
        I18nKit.local("zh-CN").message("k");
        assertEquals("故障后命中：WARN>=1、DEBUG=0", 0, recorder.ofLevel("DEBUG").size());
        assertTrue("故障后命中应有 WARN", recorder.ofLevel("WARN").size() >= 1);
    }

    /** 本轮优化：来源 refresh 抛 RuntimeException → WARN 含稳定名，不中断其它来源，未命中仍 DEBUG。 */
    @Test
    public void refreshFailureLogsWarnAndContinuesOtherSources() {
        FakeMessageSource broken = new FakeMessageSource("db").refreshFailing(true);
        FakeMessageSource healthy = new FakeMessageSource("properties").put("zh-CN", "k", "v");
        install(60000, broken, healthy);
        broken.resetCalls();
        healthy.resetCalls();
        recorder.clear();

        I18nKit.refresh();

        assertEquals("故障来源 refresh 应被调用 1 次", 1, broken.refreshCalls());
        assertEquals("健康来源 refresh 不被中断，仍应调用 1 次", 1, healthy.refreshCalls());
        assertTrue("refresh 故障应 WARN 且含稳定名 db，实际=" + recorder.events(),
                recorder.has("WARN", "db", "刷新失败"));
        assertTrue("refresh 故障不应产生 ERROR，实际=" + recorder.events(),
                recorder.ofLevel("ERROR").isEmpty());

        recorder.clear();
        assertEquals("未命中返回 key", "missing.key", I18nKit.local("zh-CN").message("missing.key"));
        assertTrue("未命中仍应 DEBUG 含 key+locale，实际=" + recorder.events(),
                recorder.has("DEBUG", "missing.key", "zh-CN"));
        assertTrue("未命中不应产生 WARN，实际=" + recorder.events(),
                recorder.ofLevel("WARN").isEmpty());
    }

    /** 本轮优化：properties 资源读取 IOException → WARN 含资源名，返回空来源，不启动失败、不抛。 */
    @Test
    public void propertiesReadFailureLogsWarnAndYieldsEmptySource() {
        final String resource = "qa-broken-i18n.properties";
        Thread.currentThread().setContextClassLoader(new ClassLoader(originalLoader) {
            @Override
            public InputStream getResourceAsStream(String name) {
                if (!resource.equals(name)) {
                    return super.getResourceAsStream(name);
                }
                return new InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new IOException("模拟资源读取故障");
                    }

                    @Override
                    public int read(byte[] b, int off, int len) throws IOException {
                        throw new IOException("模拟资源读取故障");
                    }
                };
            }
        });
        recorder.clear();

        PropertiesSource source = new PropertiesSource(resource);

        assertEquals("properties", source.name());
        assertNull("读取失败应为空来源：get 返回 null", source.get("k", "zh-CN"));
        assertTrue("读取失败应为空来源：list 返回空", source.list("zh-CN").isEmpty());
        assertTrue("读取失败应 WARN 且含资源名，实际=" + recorder.events(),
                recorder.has("WARN", resource, "资源读取失败"));
        assertTrue("读取失败不应产生 ERROR，实际=" + recorder.events(),
                recorder.ofLevel("ERROR").isEmpty());
    }
}
