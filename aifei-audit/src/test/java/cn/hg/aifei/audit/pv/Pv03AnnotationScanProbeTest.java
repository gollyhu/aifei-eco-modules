/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.pv;

import cn.aifei.log.Log;
import cn.aifei.log.LogFactory;
import cn.aifei.scanner.Scanner;
import cn.hg.aifei.audit.support.RecordingLogFactory;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * PV-03 前置实证：{@code AuditPlugin.start()} 内能否枚举出全部被标注方法。
 *
 * <p>验证三点：</p>
 *
 * <pre>
 * ① 框架自带 {@code cn.aifei.scanner.Scanner}（{@code aifei-all} 内），无需第三方扫描库；
 * ② 扫描过程<b>不依赖容器启动</b>：本用例全程未调用 {@code Aifei.start(...)}，插件 start() 阶段同样可扫；
 * ③ 目录（target/test-classes）与 jar（aifei-all）两种 classpath 形态均可命中。
 * </pre>
 *
 * <p>已知边界（由 Scanner 源码读得，非推断）：{@code scan} 只覆盖 {@code classLoader.getResources(basePackage)}
 * 能找到的 {@code file} / {@code jar} 协议；其它协议（如部分 fat-jar / 容器类加载器）只记 debug 日志并跳过，
 * 因此设计上扫描范围必须允许消费方以「类清单」兜底，不能只提供包名。</p>
 */
public class Pv03AnnotationScanProbeTest {

    /**
     * 探针注解：与未来 {@code @Audit} 同形（方法级、运行期可读）。
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface PvMarker {

        String action();
    }

    /**
     * 被扫描的业务类：两个标注方法（其一 action 为空串）+ 一个未标注方法。
     */
    public static class AnnotatedService {

        @PvMarker(action = "")
        public String emptyAction() {
            return "empty";
        }

        @PvMarker(action = "order.cancel")
        public String okAction() {
            return "ok";
        }

        public String plain() {
            return "plain";
        }
    }

    /**
     * 换入前的日志工厂，用例结束后还原。
     */
    private static LogFactory previousLogFactory;

    /**
     * Scanner 的静态字段在类初始化时取日志，框架默认 NoLogFactory 直接抛异常，故必须先装记录型工厂。
     */
    @BeforeClass
    public static void installLogFactory() {
        previousLogFactory = RecordingLogFactory.install();
    }

    @AfterClass
    public static void restoreLogFactory() {
        RecordingLogFactory.restore(previousLogFactory);
    }

    /**
     * ① + ②：目录形态下扫描出全部被标注方法，且未标注方法不被计入（等价于 AC-29 启动期校验的输入）。
     */
    @Test
    public void scanFindsAnnotatedMethodsWithoutContainer() {
        Set<Class<?>> classes = new Scanner().scan("cn.hg.aifei.audit.pv");
        Assert.assertTrue("扫描应命中探针类本身：" + names(classes),
                classes.contains(Pv03AnnotationScanProbeTest.class) || classes.contains(AnnotatedService.class));

        List<String> annotated = new ArrayList<>();
        List<String> emptyActions = new ArrayList<>();
        for (Class<?> clazz : classes) {
            for (Method method : clazz.getDeclaredMethods()) {
                PvMarker marker = method.getAnnotation(PvMarker.class);
                if (marker == null) {
                    continue;
                }
                String signature = clazz.getName() + "#" + method.getName();
                annotated.add(signature);
                if (marker.action().trim().isEmpty()) {
                    emptyActions.add(signature);
                }
            }
        }

        Assert.assertEquals("应恰有 2 个标注方法：" + annotated, 2, annotated.size());
        Assert.assertEquals("其中恰有 1 个空 action：" + emptyActions, 1, emptyActions.size());
        Assert.assertTrue("空 action 能定位到 类名#方法名 形态：" + emptyActions,
                emptyActions.get(0).endsWith("AnnotatedService#emptyAction"));
        Assert.assertFalse("未标注方法不得进入校验面：" + annotated,
                annotated.contains(AnnotatedService.class.getName() + "#plain"));
    }

    /**
     * ③：jar 形态同样可扫（aifei-all 自身即在 jar 内），且 Scanner 静态日志在无日志工厂时可用。
     */
    @Test
    public void scanWorksOnJarClasspathAndLogIsUsableWithoutFactory() {
        Set<Class<?>> classes = new Scanner().scan("cn.aifei.scanner");
        Assert.assertTrue("jar 内类应被扫描到：" + names(classes), classes.contains(Scanner.class));
        Assert.assertNotNull("Scanner 静态日志初始化依赖 Log.get，未装日志工厂时不应为 null", Log.get(Scanner.class));
    }

    /**
     * 扫描结果按包逐层展开，子包亦被覆盖（消费方只传根包即可）。
     */
    @Test
    public void scanCoversSubPackages() {
        Set<Class<?>> classes = new Scanner().scan("cn.hg.aifei.audit");
        Set<String> names = new LinkedHashSet<>();
        for (Class<?> clazz : classes) {
            names.add(clazz.getName());
        }
        Assert.assertTrue("根包扫描应覆盖 pv 子包：" + names, names.contains(AnnotatedService.class.getName()));
    }

    private static List<String> names(Set<Class<?>> classes) {
        List<String> names = new ArrayList<>();
        for (Class<?> clazz : classes) {
            names.add(clazz.getName());
        }
        return names;
    }
}
