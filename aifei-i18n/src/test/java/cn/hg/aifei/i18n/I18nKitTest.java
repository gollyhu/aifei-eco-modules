/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n;

import cn.hg.aifei.i18n.internal.I18nConfig;
import cn.hg.aifei.i18n.internal.I18nEngine;
import cn.hg.aifei.i18n.internal.I18nHolder;
import cn.hg.aifei.i18n.internal.LocaleContext;
import cn.hg.aifei.i18n.support.FakeMessageSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 取文案核心路径单测（服务层与模板渲染端到端用例）。
 */
public class I18nKitTest {

    @After
    public void tearDown() {
        I18nHolder.clear();
        LocaleContext.clear();
    }

    private static void install(String defaultLocale, long ttl, FakeMessageSource... sources) {
        Properties props = new Properties();
        props.setProperty(I18nConfig.KEY_DEFAULT_LOCALE, defaultLocale);
        props.setProperty(I18nConfig.KEY_CACHE_TTL, String.valueOf(ttl));
        I18nConfig config = I18nConfig.parse(props);
        List<MessageSource> list = new ArrayList<MessageSource>(Arrays.asList(sources));
        I18nHolder.install(new I18nEngine(config, list));
    }

    @Test
    public void messageHitWithLocale() {
        install("en", 60000, new FakeMessageSource("properties").put("zh-CN", "login.title", "登录"));
        assertEquals("登录", I18nKit.local("zh-CN").message("login.title"));
    }

    @Test
    public void messagePlaceholderMissingArgKeptAsIs() {
        install("en", 60000, new FakeMessageSource("properties").put("en", "hello", "hello {0}"));
        assertEquals("hello Bob", I18nKit.local("en").message("hello", "Bob"));
        assertEquals("hello {0}", I18nKit.local("en").message("hello"));
    }

    @Test
    public void messageMissReturnsKeyNoThrow() {
        install("en", 60000, new FakeMessageSource("properties"));
        assertEquals("no.such.key", I18nKit.local("zh-CN").message("no.such.key"));
    }

    @Test
    public void emptyStringIsHit() {
        install("en", 60000, new FakeMessageSource("properties").put("zh-CN", "empty", ""));
        assertEquals("", I18nKit.local("zh-CN").message("empty"));
    }

    @Test
    public void fallbackToDefaultLocaleAcrossSources() {
        install("en", 60000,
                new FakeMessageSource("db"),
                new FakeMessageSource("properties").put("en", "k", "default-value"));
        assertEquals("default-value", I18nKit.local("zh-CN").message("k"));
    }

    @Test
    public void overlayOrderFirstHitWins() {
        install("en", 60000,
                new FakeMessageSource("first").put("zh-CN", "k", "first-value"),
                new FakeMessageSource("second").put("zh-CN", "k", "second-value"));
        assertEquals("first-value", I18nKit.local("zh-CN").message("k"));
    }

    @Test
    public void cacheHitWithinTtl() {
        FakeMessageSource source = new FakeMessageSource("properties").put("zh-CN", "k", "v");
        install("en", 60000, source);
        I18nKit.local("zh-CN").message("k");
        I18nKit.local("zh-CN").message("k");
        assertEquals("TTL 内来源只被调用一次", 1, source.getCalls());
    }

    @Test
    public void ttlZeroBypassesCache() {
        FakeMessageSource source = new FakeMessageSource("properties").put("zh-CN", "k", "v");
        install("en", 0, source);
        I18nKit.local("zh-CN").message("k");
        I18nKit.local("zh-CN").message("k");
        assertEquals("TTL≤0 每次回源", 2, source.getCalls());
    }

    @Test
    public void ttlExpiryReloads() {
        // TTL 到期由时间自然推进触发，用例不使用 Thread.sleep。
        // 采用极小 TTL + 有界轮询（nanoTime 截止时间），时间推进后即触发回源。
        FakeMessageSource source = new FakeMessageSource("properties").put("zh-CN", "k", "v");
        install("en", 1L, source);
        I18nKit.local("zh-CN").message("k"); // 首次加载并写入缓存
        long deadlineNanos = System.nanoTime() + 2_000_000_000L; // 2 秒上限，防止死循环
        while (source.getCalls() < 2 && System.nanoTime() < deadlineNanos) {
            I18nKit.local("zh-CN").message("k");
        }
        assertEquals("TTL 到期后回源", 2, source.getCalls());
    }

    @Test
    public void refreshClearsCache() {
        FakeMessageSource source = new FakeMessageSource("properties").put("zh-CN", "k", "v");
        install("en", 60000, source);
        assertEquals("v", I18nKit.local("zh-CN").message("k"));
        I18nKit.refresh();
        assertEquals("v", I18nKit.local("zh-CN").message("k"));
        assertEquals("refresh 后重新回源", 2, source.getCalls());
    }

    @Test
    public void sourceFailureFallsBackNoThrow() {
        install("en", 60000,
                new FakeMessageSource("db").failing(true),
                new FakeMessageSource("properties").put("zh-CN", "k", "ok"));
        assertEquals("ok", I18nKit.local("zh-CN").message("k"));
    }

    @Test
    public void allSourcesFailReturnsKey() {
        install("en", 60000,
                new FakeMessageSource("db").failing(true),
                new FakeMessageSource("kv").failing(true));
        assertEquals("k", I18nKit.local("zh-CN").message("k"));
    }

    @Test
    public void sourceFailureTriggersDefaultLocaleFallback() {
        install("en", 60000,
                new FakeMessageSource("db").failing(true),
                new FakeMessageSource("properties").put("en", "k", "en-value"));
        assertEquals("en-value", I18nKit.local("zh-CN").message("k"));
    }

    @Test
    public void normalizedLocaleHit() {
        install("en", 60000, new FakeMessageSource("properties").put("zh-CN", "k", "规范值"));
        assertEquals("规范值", I18nKit.local("zh_CN").message("k"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void explicitIllegalLocaleThrows() {
        install("en", 60000, new FakeMessageSource("properties"));
        I18nKit.local("zh_CN_").message("k");
    }

    @Test(expected = IllegalArgumentException.class)
    public void blankKeyThrows() {
        install("en", 60000, new FakeMessageSource("properties"));
        I18nKit.message("  ");
    }

    @Test
    public void concurrentLocalesDoNotBleed() throws InterruptedException {
        install("en", 60000,
                new FakeMessageSource("properties")
                        .put("zh-CN", "k", "中文").put("en", "k", "English"));
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> zhResult = new AtomicReference<>();
        final AtomicReference<String> enResult = new AtomicReference<>();
        Thread zh = new Thread(new Runnable() {
            @Override
            public void run() {
                await(latch);
                zhResult.set(I18nKit.local("zh-CN").message("k"));
            }
        });
        Thread en = new Thread(new Runnable() {
            @Override
            public void run() {
                await(latch);
                enResult.set(I18nKit.local("en").message("k"));
            }
        });
        zh.start();
        en.start();
        latch.countDown();
        zh.join();
        en.join();
        assertEquals("中文", zhResult.get());
        assertEquals("English", enResult.get());
    }

    @Test
    public void serviceAndTemplateRenderShareSameEntry() {
        install("en", 60000, new FakeMessageSource("properties").put("zh-CN", "login.title", "登录"));
        String serviceValue = I18nKit.local("zh-CN").message("login.title");
        // 模拟消费方模板函数的一行调用（与服务端同一入口、同一回退语义）
        java.util.function.Function<String, String> template = key -> I18nKit.local("zh-CN").message(key);
        assertEquals(serviceValue, template.apply("login.title"));
        assertTrue("模板渲染应逐字等价服务层", "登录".equals(template.apply("login.title")));
    }

    @Test
    public void localReturnsReusableCarrier() {
        install("en", 60000, new FakeMessageSource("properties")
                .put("zh-CN", "hello", "你好，{0}").put("zh-CN", "plain", "纯文案"));
        Localizer zh = I18nKit.local("zh_CN");
        assertEquals("你好，Bob", zh.message("hello", "Bob"));
        assertEquals("纯文案", zh.message("plain"));
        assertEquals("载体复用不变", "你好，Ann", zh.message("hello", "Ann"));
    }

    @Test
    public void defaultLocalMessageWithSingleStringArgIsInterpolation() {
        install("en", 60000, new FakeMessageSource("properties").put("en", "hello", "hello {0}"));
        assertEquals("第二 String 参是 {0} 插值参数，非 locale", "hello Bob", I18nKit.message("hello", "Bob"));
        assertEquals("无参保留占位符", "hello {0}", I18nKit.message("hello"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void localIllegalTagThrowsAtLocalCall() {
        // 不安装引擎：若校验推迟到 message()，将抛 I18nException 或不抛，本例即失败
        I18nKit.local("zh_CN_");
    }

    @Test
    public void defaultLocalLocaleLikeSecondArgIsStillInterpolation() {
        install("en", 60000, new FakeMessageSource("properties")
                .put("en", "hello", "hello {0}")
                .put("zh-CN", "hello", "你好，{0}"));
        assertEquals("合法 locale 串作第二参仍是插值，不切换 locale", "hello zh-CN", I18nKit.message("hello", "zh-CN"));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
