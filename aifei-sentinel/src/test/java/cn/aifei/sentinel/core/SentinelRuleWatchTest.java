/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.sentinel.core;

import cn.aifei.aop.Interceptor;
import cn.aifei.aop.Invocation;
import cn.aifei.proxy.Callback;
import cn.aifei.sentinel.aifei.SentinelInterceptor;
import com.alibaba.csp.sentinel.datasource.FileRefreshableDataSource;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 热生效测试：watch 规则文件后，修改文件内容应自动刷新规则，无需重启。
 */
public class SentinelRuleWatchTest {

    public static class Svc {
        public String run() {
            return "ok";
        }
    }

    private static final String RESOURCE = Svc.class.getName() + "#run";
    private static final SentinelInterceptor INTERCEPTOR = new SentinelInterceptor(e -> "blocked");

    @Test
    public void testRuleHotReload() throws Exception {
        Path file = Files.createTempFile("sentinel-rules", ".json");
        write(file, 0);   // 初始 QPS=0：拒绝

        List<FileRefreshableDataSource<?>> sources = SentinelRuleLoader.watch(file, 1000);
        try {
            Assert.assertEquals("初始规则应命中限流", "blocked", invoke());

            write(file, 1000);      // 改为 QPS=1000：放行
            Thread.sleep(2500);     // 等待热刷新

            Assert.assertEquals("规则热生效后应放行", "ok", invoke());
        } finally {
            SentinelRuleLoader.closeQuietly(sources);
            Files.deleteIfExists(file);
        }
    }

    private static void write(Path file, double count) throws Exception {
        String json = "{\"flowRules\":[{\"resource\":\"" + RESOURCE + "\",\"grade\":1,\"count\":" + count + "}]}";
        Files.write(file, json.getBytes(StandardCharsets.UTF_8));
    }

    private static Object invoke() throws Exception {
        Svc target = new Svc();
        Method m = Svc.class.getMethod("run");
        Callback callback = args -> {
            try {
                return m.invoke(target, args);
            } catch (InvocationTargetException e) {
                Throwable t = e.getTargetException();
                throw t instanceof Exception ? (Exception) t : new RuntimeException(t);
            }
        };
        Invocation inv = new Invocation(target, m, new Object[]{}, new Interceptor[]{INTERCEPTOR}, callback);
        inv.invoke();
        return inv.getReturnValue();
    }
}
