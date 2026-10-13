/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.satoken.internal;

import cn.aifei.util.Prop;
import cn.dev33.satoken.config.SaTokenConfig;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 配置装载：取值映射、缺省回退与非法值 fail-fast */
public class SaTokenConfigLoaderTest {

    @Test
    public void mapsConfiguredKeysToConfig() {
        SaTokenConfig config = SaTokenConfigLoader.load(new Prop("sa-token-test.txt"));

        Assert.assertEquals("demo-token", config.getTokenName());
        Assert.assertEquals(86400L, config.getTimeout());
        Assert.assertEquals(1800L, config.getActiveTimeout());
        Assert.assertEquals(Boolean.FALSE, config.getIsConcurrent());
        Assert.assertEquals(Boolean.TRUE, config.getIsShare());
        Assert.assertEquals(3, config.getMaxLoginCount());
        Assert.assertEquals(Boolean.FALSE, config.getIsReadBody());
        Assert.assertEquals(Boolean.TRUE, config.getIsReadHeader());
        Assert.assertEquals(Boolean.FALSE, config.getIsReadCookie());
        Assert.assertEquals(Boolean.TRUE, config.getIsWriteHeader());
        Assert.assertEquals("uuid", config.getTokenStyle());
        Assert.assertEquals("Bearer", config.getTokenPrefix());
        Assert.assertEquals(Boolean.FALSE, config.getAutoRenew());
        Assert.assertEquals(60, config.getDataRefreshPeriod());
        Assert.assertEquals(Boolean.FALSE, config.getIsPrint());
        Assert.assertEquals(Boolean.TRUE, config.getIsLog());
        Assert.assertEquals("info", config.getLogLevel());
        Assert.assertEquals("http://localhost:18080", config.getCurrDomain());
    }

    @Test
    public void keepsSaTokenDefaultsWhenKeysAbsent() {
        SaTokenConfig config = SaTokenConfigLoader.load(new Prop());

        Assert.assertEquals("satoken", config.getTokenName());
        Assert.assertEquals(60L * 60 * 24 * 30, config.getTimeout());
        Assert.assertEquals(-1L, config.getActiveTimeout());
        Assert.assertEquals(Boolean.TRUE, config.getIsConcurrent());
        Assert.assertEquals(12, config.getMaxLoginCount());
    }

    @Test
    public void failsFastOnInvalidNumber() throws IOException {
        Path temp = Files.createTempFile("sa-token-invalid", ".txt");
        try {
            Files.write(temp, "sa-token.timeout = not-a-number".getBytes(StandardCharsets.UTF_8));
            SaTokenConfigLoader.load(new Prop(temp.toFile()));
            Assert.fail("非法数值应抛出 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue("异常信息应指明出错配置项",
                    expected.getMessage().contains("sa-token.timeout"));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Test
    public void failsFastOnInvalidBoolean() throws IOException {
        Path temp = Files.createTempFile("sa-token-invalid-bool", ".txt");
        try {
            Files.write(temp, "sa-token.is-concurrent = maybe".getBytes(StandardCharsets.UTF_8));
            SaTokenConfigLoader.load(new Prop(temp.toFile()));
            Assert.fail("非法布尔值应抛出 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("sa-token.is-concurrent"));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullProp() {
        SaTokenConfigLoader.load(null);
    }
}
