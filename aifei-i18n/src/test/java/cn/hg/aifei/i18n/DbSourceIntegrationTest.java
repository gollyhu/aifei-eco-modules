/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n;

import cn.aifei.db.core.DbConfig;
import cn.aifei.db.core.DbKit;
import cn.aifei.log.LogFactory;
import cn.aifei.log.LogKit;
import cn.aifei.util.Prop;
import cn.hg.aifei.i18n.aifei.I18nPlugin;
import cn.hg.aifei.i18n.support.RecordingLogFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * db 来源真实集成测试，默认关闭；用 {@code -Ddb.integration=true} 开启。
 *
 * <p>采用 H2 内存库（本地 Driver，零外网、零外部服务）：开启后建表 / 写入走真实 JDBC，
 * 异常不吞即失败。断言真实建表 + 写入 + 单条取命中 + 整包列举 + refresh 后缓存失效。
 * 表结构与 {@code DbSource} 查询契约一致，仅测试可见。</p>
 */
public class DbSourceIntegrationTest {

    private static final boolean ENABLED = Boolean.getBoolean("db.integration");

    private static final String JDBC_URL = System.getProperty("db.jdbcUrl", "jdbc:h2:mem:i18n_it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");

    private static final String KEY = "it.title";

    private I18nPlugin plugin;

    private LogFactory originalLogFactory;

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue("db integration tests: enable -Ddb.integration=true", ENABLED);
        originalLogFactory = LogKit.get().getLogFactory();
        LogKit.get().setLogFactory(new RecordingLogFactory());
        Class.forName("org.h2.Driver");
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(JDBC_URL);
        dataSource.setUser("sa");
        dataSource.setPassword("");
        DbKit.addConfig(new DbConfig(DbKit.MAIN_CONFIG_ID, dataSource));
        createAndSeed();
        Prop prop = new Prop();
        prop.getProperties().setProperty("i18n.defaultLocale", "en-US");
        prop.getProperties().setProperty("i18n.sources", "db");
        plugin = new I18nPlugin(prop);
        plugin.start();
    }

    @After
    public void tearDown() {
        if (!ENABLED) {
            return;
        }
        if (plugin != null) {
            plugin.stop();
        }
        DbKit.removeConfig(DbKit.MAIN_CONFIG_ID);
        LogKit.get().setLogFactory(originalLogFactory);
    }

    @Test
    public void singleHitAndBundleList() {
        assertEquals("标题-zh", I18nKit.local("zh-CN").message(KEY));
        LanguageBundle bundle = I18nKit.bundle("zh-CN");
        assertEquals("标题-zh", bundle.messages().get(KEY));
        assertEquals(2, bundle.messages().size());
    }

    @Test
    public void refreshInvalidatesCache() throws SQLException {
        assertEquals("标题-zh", I18nKit.local("zh-CN").message(KEY));
        execute("UPDATE i18n_message SET content='标题-zh-v2' WHERE locale='zh-CN' AND msg_key='" + KEY + "'");
        assertEquals("TTL 内仍读缓存", "标题-zh", I18nKit.local("zh-CN").message(KEY));
        I18nKit.refresh();
        assertEquals("refresh 后重新回源", "标题-zh-v2", I18nKit.local("zh-CN").message(KEY));
    }

    private static void createAndSeed() throws SQLException {
        execute("CREATE TABLE IF NOT EXISTS i18n_message ("
                + "locale VARCHAR(32) NOT NULL, "
                + "msg_key VARCHAR(255) NOT NULL, "
                + "content VARCHAR(4096) NOT NULL, "
                + "PRIMARY KEY (locale, msg_key))");
        execute("DELETE FROM i18n_message");
        execute("INSERT INTO i18n_message(locale, msg_key, content) VALUES ('zh-CN', '" + KEY + "', '标题-zh')");
        execute("INSERT INTO i18n_message(locale, msg_key, content) VALUES ('zh-CN', 'it.other', '其它-zh')");
    }

    private static void execute(String sql) throws SQLException {
        Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
        try {
            Statement statement = connection.createStatement();
            try {
                statement.execute(sql);
            } finally {
                statement.close();
            }
        } finally {
            connection.close();
        }
    }
}
