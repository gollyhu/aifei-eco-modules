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
import java.util.Map;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * QA 独立复验补充用例：单点取文案与整包导出的取值一致性边界（AC-10-2 / AC-10-3 / AC-10-4）。
 *
 * <p>开发现有 {@code BundleExportTest#singleAndBundleConsistent} 覆盖了基础一致性（AC-10-1），
 * 但未覆盖「缓存已存在时」与「refresh() 后」两条路径分叉风险。本类构造可列举来源在两种状态下
 * 逐 key 比对两路径；并验证仅存于不可列举来源的 key 是合法例外。</p>
 */
public class QaConsistencyTest {

    @Before
    public void setUp() {
        LocaleContext.clear();
    }

    @After
    public void tearDown() {
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

    private static void assertBundleMatchesSingleEntry(LanguageBundle bundle, String locale) {
        int compared = 0;
        for (Map.Entry<String, String> entry : bundle.messages().entrySet()) {
            assertEquals("缓存/刷新后单点与整包对 key=" + entry.getKey() + " 应一致",
                    entry.getValue(), I18nKit.message(entry.getKey(), locale));
            compared++;
        }
        assertTrue("至少比对一个 key", compared > 0);
    }

    /** AC-10-2：缓存已存在（TTL 未到期）时，两路径对可列举来源覆盖的 key 仍逐 key 一致。 */
    @Test
    public void consistentWhenCachePopulated() {
        FakeMessageSource src = new FakeMessageSource("properties")
                .put("zh-CN", "k1", "v1")
                .put("zh-CN", "k2", "v2");
        install(60000, src);
        // 先用单条路径填充缓存层
        assertEquals("v1", I18nKit.message("k1", "zh-CN"));
        assertEquals("v2", I18nKit.message("k2", "zh-CN"));
        assertBundleMatchesSingleEntry(I18nKit.bundle("zh-CN"), "zh-CN");
    }

    /** AC-10-3：refresh() 全清后，两路径对可列举来源覆盖的 key 仍逐 key 一致。 */
    @Test
    public void consistentAfterRefresh() {
        FakeMessageSource src = new FakeMessageSource("properties")
                .put("zh-CN", "k1", "v1")
                .put("zh-CN", "k2", "v2");
        install(60000, src);
        I18nKit.message("k1", "zh-CN");
        I18nKit.refresh();
        assertBundleMatchesSingleEntry(I18nKit.bundle("zh-CN"), "zh-CN");
    }

    /** AC-10-1 + AC-10-4：可列举来源 key 一致；仅存于不可列举来源的 key 单条命中、整包不含（合法例外）。 */
    @Test
    public void nonListableOnlyKeyIsLegitimateException() {
        FakeMessageSource listable = new FakeMessageSource("properties").put("zh-CN", "shared", "S");
        FakeMessageSource nonListable = new FakeMessageSource("remote").listable(false)
                .put("zh-CN", "shared", "R").put("zh-CN", "remote.only", "RO");
        install(60000, listable, nonListable);

        LanguageBundle bundle = I18nKit.bundle("zh-CN");
        // 可列举来源覆盖的 shared：两路径一致（叠加顺序 listable 在前 → "S"）
        assertEquals("S", bundle.messages().get("shared"));
        assertEquals("S", I18nKit.message("shared", "zh-CN"));
        // 仅存于不可列举来源的 key：单条命中、整包不含
        assertEquals("RO", I18nKit.message("remote.only", "zh-CN"));
        assertTrue("仅不可列举来源的 key 不应出现在整包",
                !bundle.messages().containsKey("remote.only"));
    }
}
