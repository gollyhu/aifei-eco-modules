/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.demo.db;

import cn.aifei.db.core.DbConfig;
import cn.aifei.db.core.DbKit;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import org.h2.jdbcx.JdbcDataSource;

/**
 * H2 内存库初始化：建 i18n_message 表并把 classpath i18n.properties 迁为初始译文。
 *
 * <p>必须在 I18nPlugin.start() 前调用，保证 DbSource 取得到 DbKit 主配置；重启即复位。</p>
 */
public final class I18nDbSeed {

    private static final String JDBC_URL =
            "jdbc:h2:mem:i18n_demo;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    private I18nDbSeed() {
    }

    /** 建表 + 清空 + 迁移；任何失败直接抛出，装配中止而不是留半初始化状态。 */
    public static void init() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(JDBC_URL);
        dataSource.setUser("sa");
        dataSource.setPassword("");
        DbKit.addConfig(new DbConfig(DbKit.MAIN_CONFIG_ID, dataSource));

        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            createTable(connection);
            seed(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("i18n-demo H2 初始化失败", e);
        } finally {
            closeQuietly(connection);
        }
    }

    private static void createTable(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        try {
            statement.execute("CREATE TABLE IF NOT EXISTS i18n_message ("
                    + "locale VARCHAR(32) NOT NULL, "
                    + "msg_key VARCHAR(255) NOT NULL, "
                    + "content VARCHAR(4096) NOT NULL, "
                    + "PRIMARY KEY (locale, msg_key))");
            statement.execute("DELETE FROM i18n_message");
        } finally {
            statement.close();
        }
    }

    private static void seed(Connection connection) throws SQLException {
        Properties properties = new Properties();
        InputStream in = I18nDbSeed.class.getClassLoader().getResourceAsStream("i18n.properties");
        if (in == null) {
            throw new IllegalStateException("classpath 缺少 i18n.properties");
        }
        try {
            properties.load(new InputStreamReader(in, "UTF-8"));
        } catch (IOException e) {
            throw new IllegalStateException("读取 i18n.properties 失败", e);
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // 内容已读入，关闭失败不影响迁移
            }
        }

        PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO i18n_message(locale, msg_key, content) VALUES (?, ?, ?)");
        try {
            for (String name : properties.stringPropertyNames()) {
                int bar = name.indexOf('|');
                if (bar <= 0 || bar == name.length() - 1) {
                    continue;
                }
                ps.setString(1, name.substring(0, bar));
                ps.setString(2, name.substring(bar + 1));
                ps.setString(3, properties.getProperty(name));
                ps.addBatch();
            }
            ps.executeBatch();
        } finally {
            ps.close();
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // 建表与迁移已在 close 前完成
        }
    }
}
