/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 测试用隔离类加载器：子优先加载本模块 class，并隐藏指定 optional 依赖前缀，
 * 用于在依赖真实存在于测试类路径时模拟「依赖缺失」链路（全离线、无临时进程）。
 *
 * <p>本模块 class 由本加载器自行 define，故其中对被隐藏内核类的符号引用（含
 * {@code Class.forName} 与直接 {@code Xxx.class} 引用）都经由本加载器解析，
 * 从而命中隐藏逻辑得到 {@code ClassNotFoundException}/{@code NoClassDefFoundError}，
 * 交由被测 *Deps 收敛为可读模块异常。</p>
 */
public final class HiddenClassClassLoader extends ClassLoader {

    private final String hiddenPrefix;
    private final Path classesDir;

    /**
     * 构造隔离加载器。
     *
     * @param hiddenPrefix 要隐藏的依赖包名前缀（含尾点）
     */
    public HiddenClassClassLoader(String hiddenPrefix) {
        super(HiddenClassClassLoader.class.getClassLoader());
        this.hiddenPrefix = hiddenPrefix;
        this.classesDir = Paths.get("target", "classes");
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name.startsWith(hiddenPrefix)) {
            throw new ClassNotFoundException(name);
        }
        if (name.startsWith("cn.hg.aifei.tools.")) {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    Path file = classesDir.resolve(name.replace('.', '/') + ".class");
                    if (Files.exists(file)) {
                        byte[] bytes;
                        try {
                            bytes = Files.readAllBytes(file);
                        } catch (IOException e) {
                            throw new ClassNotFoundException(name, e);
                        }
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } else {
                        loaded = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
        return super.loadClass(name, resolve);
    }
}
