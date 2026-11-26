/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.support;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;

/**
 * 测试用隔离类加载器工厂：以真实类加载行为模拟「服务资源缺失」与「内核依赖缺失」两类环境。
 *
 * <p>不修改任何全局状态、不替换线程上下文加载器，只在被测代码显式传入时生效。</p>
 */
public final class IsolatedClassLoaders {

    private static final String SERVICES_RESOURCE = "META-INF/services/cn.hg.aifei.email.spi.MailProvider";
    private static final String CORE_PACKAGE_PREFIX = "org.dromara.email.";

    private IsolatedClassLoaders() {
    }

    /**
     * 屏蔽通道 Provider 服务声明资源的类加载器（模拟资源文件缺失 / 无实现）。
     *
     * @return 类加载器
     */
    public static ClassLoader hidingMailProviderServices() {
        return new ClassLoader(IsolatedClassLoaders.class.getClassLoader()) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                if (SERVICES_RESOURCE.equals(name)) {
                    return Collections.emptyEnumeration();
                }
                return super.getResources(name);
            }

            @Override
            public URL getResource(String name) {
                if (SERVICES_RESOURCE.equals(name)) {
                    return null;
                }
                return super.getResource(name);
            }

            @Override
            public InputStream getResourceAsStream(String name) {
                if (SERVICES_RESOURCE.equals(name)) {
                    return null;
                }
                return super.getResourceAsStream(name);
            }
        };
    }

    /**
     * 屏蔽邮件内核包（{@code org.dromara.email.*}）的类加载器（模拟未引入内核依赖）。
     *
     * @return 类加载器
     */
    public static ClassLoader blockingMailCore() {
        return new ClassLoader(IsolatedClassLoaders.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith(CORE_PACKAGE_PREFIX)) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };
    }
}
