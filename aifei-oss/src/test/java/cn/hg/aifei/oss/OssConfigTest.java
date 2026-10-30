/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss;

import static org.junit.Assert.assertEquals;

import cn.aifei.util.Prop;
import cn.hg.aifei.oss.aifei.OssConfigLoader;
import cn.hg.aifei.oss.internal.OssConfig;
import java.util.List;
import org.junit.Test;

/**
 * 配置校验与默认平台规则测试（不联网）。
 *
 * <p>固化契约：字段级校验唯一定义点在 {@link OssConfig}；平台列表由 {@code oss.<name>.type} 推导；
 * 多平台时 {@code oss.default-platform} 必填且必须指向已声明平台。</p>
 */
public class OssConfigTest {

    @Test(expected = OssException.class)
    public void blankPlatformRejected() {
        new OssConfig("  ", "local-plus", null);
    }

    @Test(expected = OssException.class)
    public void blankTypeRejected() {
        new OssConfig("local-1", " ", null);
    }

    @Test(expected = OssException.class)
    public void noPlatformConfiguredFails() {
        OssConfigLoader.load(prop());
    }

    @Test(expected = OssException.class)
    public void multiplePlatformsRequireDefault() {
        Prop prop = prop("oss.a.type", "local-plus", "oss.b.type", "local-plus");
        List<OssConfig> configs = OssConfigLoader.load(prop);
        OssConfigLoader.defaultPlatform(prop, configs);
    }

    @Test(expected = OssException.class)
    public void defaultPlatformMustBeDeclared() {
        Prop prop = prop(
                "oss.a.type", "local-plus",
                "oss.b.type", "local-plus",
                "oss.default-platform", "c");
        List<OssConfig> configs = OssConfigLoader.load(prop);
        OssConfigLoader.defaultPlatform(prop, configs);
    }

    @Test
    public void singlePlatformDefaultsToItself() {
        Prop prop = prop("oss.only.type", "local-plus");
        List<OssConfig> configs = OssConfigLoader.load(prop);
        assertEquals("only", OssConfigLoader.defaultPlatform(prop, configs));
        assertEquals("local-plus", configs.get(0).type());
    }

    @Test
    public void optionsAreKeptByKebabCase() {
        Prop prop = prop(
                "oss.a.type", "local-plus",
                "oss.a.base-path", "/tmp/files",
                "oss.a.domain", "http://localhost/files/");
        List<OssConfig> configs = OssConfigLoader.load(prop);
        assertEquals("/tmp/files", configs.get(0).options().get("base-path"));
        assertEquals("http://localhost/files/", configs.get(0).options().get("domain"));
    }

    private static Prop prop(String... keyValues) {
        Prop prop = new Prop();
        for (int i = 0; i < keyValues.length; i += 2) {
            prop.getProperties().setProperty(keyValues[i], keyValues[i + 1]);
        }
        return prop;
    }
}
