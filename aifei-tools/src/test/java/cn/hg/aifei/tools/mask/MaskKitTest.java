/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.mask;

import org.junit.Test;

import java.util.function.UnaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * 脱敏注册表门面测试（设计 §3.4 / §7.1 / §7.2 / §8-E / §9）。
 *
 * <p>覆盖：register/replace/use 三态、默认规则初始在册且不可删、name/fn 为 null 抛异常、
 * replace 后 MaskUtil 默认方法跟随新规则。</p>
 */
public class MaskKitTest {

    @Test
    public void register_thenUse_appliesNewRule() {
        String name = "qa_rule_register";
        UnaryOperator<String> fn = new UnaryOperator<String>() {
            public String apply(String v) {
                return "<" + v + ">";
            }
        };
        MaskKit.register(name, fn);
        assertEquals("<abc>", MaskKit.use(name).apply("abc"));
    }

    @Test
    public void register_duplicateName_throws() {
        String name = "qa_rule_dup";
        MaskKit.register(name, new UnaryOperator<String>() {
            public String apply(String v) { return v; }
        });
        try {
            MaskKit.register(name, new UnaryOperator<String>() {
                public String apply(String v) { return v; }
            });
            fail("重复 register 应抛 MaskException");
        } catch (MaskException e) {
            assertEquals("mask: rule \"" + name + "\" already registered", e.getMessage());
        }
    }

    @Test
    public void replace_missingRule_throws() {
        try {
            MaskKit.replace("qa_no_such_rule", new UnaryOperator<String>() {
                public String apply(String v) { return v; }
            });
            fail("replace 不存在的规则应抛 MaskException");
        } catch (MaskException e) {
            assertEquals("mask: rule \"qa_no_such_rule\" is not registered", e.getMessage());
        }
    }

    @Test
    public void use_missingRule_throws() {
        try {
            MaskKit.use("qa_never_registered");
            fail("use 未注册规则应抛 MaskException");
        } catch (MaskException e) {
            assertEquals("mask: rule \"qa_never_registered\" is not registered", e.getMessage());
        }
    }

    @Test
    public void defaultRules_registeredAndCannotBeReRegistered() {
        String[] defaults = {"phone", "idCard", "bankCard", "email", "name",
                "fixedPhone", "password", "address", "ip"};
        for (String name : defaults) {
            assertNotNull("默认规则须初始在册: " + name, MaskKit.use(name));
        }
        // 默认规则不可删：再次 register 同名必然冲突失败
        try {
            MaskKit.register("phone", new UnaryOperator<String>() {
                public String apply(String v) { return v; }
            });
            fail("默认规则不可重复注册（不可删）");
        } catch (MaskException e) {
            assertEquals("mask: rule \"phone\" already registered", e.getMessage());
        }
    }

    @Test
    public void nullNameOrFunction_throws() {
        UnaryOperator<String> fn = new UnaryOperator<String>() {
            public String apply(String v) { return v; }
        };
        try {
            MaskKit.register(null, fn);
            fail("name 为 null 应抛");
        } catch (MaskException e) {
            assertEquals("mask: rule name must not be null", e.getMessage());
        }
        try {
            MaskKit.register("qa_nullfn", null);
            fail("fn 为 null 应抛");
        } catch (MaskException e) {
            assertEquals("mask: mask function must not be null", e.getMessage());
        }
        try {
            MaskKit.use(null);
            fail("use(null) 应抛");
        } catch (MaskException e) {
            assertEquals("mask: rule name must not be null", e.getMessage());
        }
    }

    @Test
    public void replaceDefault_ruleMaskUtilFollowsNewImplementation() {
        final String name = "qa_follow_rule_v2";
        MaskKit.register(name, new UnaryOperator<String>() {
            public String apply(String v) { return "old:" + v; }
        });
        assertEquals("old:abc", MaskKit.use(name).apply("abc"));
        MaskKit.replace(name, new UnaryOperator<String>() {
            public String apply(String v) { return "new:" + v; }
        });
        assertEquals("new:abc", MaskKit.use(name).apply("abc"));
    }

    @Test
    public void replaceExistingDefault_maskXxxFollows() {
        UnaryOperator<String> original = MaskKit.use("phone");
        try {
            MaskKit.replace("phone", new UnaryOperator<String>() {
                public String apply(String v) { return "REPLACED:" + v; }
            });
            // 关键契约：MaskUtil.maskPhone 转发注册表，replace 后自动跟随
            assertEquals("REPLACED:13800138000", MaskUtil.maskPhone("13800138000"));
            assertEquals("REPLACED:13800138000", MaskKit.use("phone").apply("13800138000"));
        } finally {
            MaskKit.replace("phone", original); // 还原默认规则，避免测试间串扰
        }
        assertEquals("138****8000", MaskUtil.maskPhone("13800138000"));
    }
}
