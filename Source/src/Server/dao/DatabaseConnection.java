package Server.dao;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Central database connection provider for DAO operations.
 * <p>
 * Connections are served from one HikariCP pool. DAO code should keep using
 * try-with-resources; closing a {@link Connection} returns it to the pool.
 * </p>
 *
 * @see UserDAO
 * @see ItemDAO
 * @see AuctionDAO
 */
public class DatabaseConnection {

    private static final Object LOCK = new Object();
    private static final int MINIMUM_IDLE = 2;
    private static final int MAXIMUM_POOL_SIZE = 8;
    private static final long CONNECTION_TIMEOUT_MS = 10_000L;
    private static final long IDLE_TIMEOUT_MS = 600_000L;
    private static final long MAX_LIFETIME_MS = 1_800_000L;

    private static String URL;
    private static String USER;
    private static String PASSWORD;
    private static HikariDataSource dataSource;

    static {
        try (java.io.InputStream input = DatabaseConnection.class.getResourceAsStream("/db.properties")) {
            Properties prop = new Properties();
            if (input == null) {
                System.err.println("Khong tim thay file db.properties!");
            } else {
                prop.load(input);
                URL = prop.getProperty("db.url");
                USER = prop.getProperty("db.user");
                PASSWORD = prop.getProperty("db.password");
            }
        } catch (java.io.IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Returns a pooled database connection.
     *
     * @return JDBC connection from the central pool
     * @throws SQLException if the pool cannot provide a connection
     */
    public static Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    public static void setConnectionParams(String url, String user, String password) {
        synchronized (LOCK) {
            URL = url;
            USER = user;
            PASSWORD = password;
            closeDataSource();
        }
    }

    public static void shutdown() {
        synchronized (LOCK) {
            closeDataSource();
        }
    }

    private static HikariDataSource getDataSource() throws SQLException {
        HikariDataSource current = dataSource;
        if (current != null && !current.isClosed()) {
            return current;
        }

        synchronized (LOCK) {
            if (dataSource == null || dataSource.isClosed()) {
                dataSource = createDataSource();
            }
            return dataSource;
        }
    }

    private static HikariDataSource createDataSource() throws SQLException {
        if (URL == null || USER == null || PASSWORD == null) {
            throw new SQLException("Database configuration has not been loaded.");
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL);
        config.setUsername(USER);
        config.setPassword(PASSWORD);
        config.setMinimumIdle(MINIMUM_IDLE);
        config.setMaximumPoolSize(MAXIMUM_POOL_SIZE);
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        config.setIdleTimeout(IDLE_TIMEOUT_MS);
        config.setMaxLifetime(MAX_LIFETIME_MS);
        config.setPoolName("AuctionDatabasePool");

        try {
            return new HikariDataSource(config);
        } catch (RuntimeException e) {
            throw new SQLException("Could not initialize database connection pool.", e);
        }
    }

    private static void closeDataSource() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }
}
