package com.wjy.mapper2sql.mcp.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.util.Properties;

import com.wjy.mapper2sql.mcp.config.JdbcConnectionConfig;

/**
 * JDBC驱动加载器
 *
 * 负责动态加载用户指定的JDBC驱动JAR文件
 * 支持从文件系统路径加载驱动类，并缓存已加载的驱动避免重复加载
 *
 * @author handsomestWei
 * @version 1.0.0
 */
public class JdbcDriverLoaderUtil {

    private static final Logger logger = LoggerFactory.getLogger(JdbcDriverLoaderUtil.class);

    // 已加载的JDBC驱动类，避免重复加载
    private static Class<?> loadedDriver = null;

    // 动态加载的类加载器
    private static URLClassLoader dynamicClassLoader = null;

    /**
     * 加载JDBC驱动
     *
     * 从外部文件系统加载JDBC驱动JAR文件
     *
     * @param config JDBC连接配置
     * @return 加载的驱动类，如果加载失败则返回null
     */
    public static Class<?> loadJdbcDriver(JdbcConnectionConfig config) {
        try {
            // 检查配置是否支持动态加载
            if (!config.supportsDynamicLoading()) {
                logger.warn(
                        "JDBC configuration does not support dynamic loading, missing driver class name or JAR file path");
                logger.warn("Configuration details: {}", config);
                return null;
            }

            String driverClassName = config.getJdbcDriver();
            String driverJarPath = config.getJdbcDriverJar();

            // 检查是否已经加载过
            if (loadedDriver != null) {
                logger.debug("JDBC driver already loaded: {}", driverClassName);
                return loadedDriver;
            }

            // 从外部文件系统加载
            if (driverJarPath != null && !driverJarPath.trim().isEmpty()) {
                Class<?> driverClass = loadFromExternalJar(driverJarPath, driverClassName);
                if (driverClass != null) {
                    logger.info("Successfully loaded JDBC driver: {} from {}", driverClassName, driverJarPath);
                    loadedDriver = driverClass;
                    return driverClass;
                }
            }

            logger.error("Driver loading failed: {}", driverClassName);
            return null;

        } catch (Exception e) {
            logger.error("Failed to load JDBC driver: {}", config.getJdbcDriver(), e);
            return null;
        }
    }

    /**
     * 从外部JAR文件加载驱动
     *
     * @param jarPath         JAR文件路径
     * @param driverClassName 驱动类名
     * @return 加载的驱动类，如果失败则返回null
     */
    private static Class<?> loadFromExternalJar(String jarPath, String driverClassName) {
        try {
            // 验证JAR文件
            if (!validateJarFile(jarPath)) {
                logger.warn("External JAR file validation failed: {}", jarPath);
                return null;
            }

            // 创建URLClassLoader加载外部JAR
            File jarFile = new File(jarPath);
            URL jarUrl = jarFile.toURI().toURL();

            URLClassLoader externalLoader = new URLClassLoader(new URL[] { jarUrl },
                    JdbcDriverLoaderUtil.class.getClassLoader());

            try {
                Class<?> driverClass = externalLoader.loadClass(driverClassName);
                return driverClass;
            } catch (ClassNotFoundException e) {
                logger.warn("Driver class not found in external JAR: {} in {}", driverClassName, jarPath);
                return null;
            }

        } catch (Exception e) {
            logger.warn("Failed to load driver from external JAR: {} from {}", driverClassName, jarPath, e);
            return null;
        }
    }

    /**
     * 使用已加载的驱动创建JDBC连接
     * 动态加载驱动的场景，不在依赖包mapper2sql的内部加载驱动：
     * 1）在DriverManager.getConnection()内部，isDriverAllowed方法会隔离类加载器，会导致取不到连接对象
     * 2）所以在调用的上层创建连接对象并传入
     *
     * @param jdbcUrl  JDBC URL
     * @param userName 用户名
     * @param password 密码
     * @return JDBC连接，如果失败则返回null
     */
    public static Connection createConnection(String jdbcUrl, String userName, String password) {
        if (loadedDriver == null) {
            logger.warn("No JDBC driver loaded, cannot create connection");
            return null;
        }

        try {
            // 使用已加载的驱动类创建连接
            Driver driver = (Driver) loadedDriver.getDeclaredConstructor().newInstance();
            Properties props = new Properties();
            props.setProperty("user", userName);
            props.setProperty("password", password);

            Connection connection = driver.connect(jdbcUrl, props);
            if (connection != null) {
                logger.info("Successfully created JDBC connection to: {}", jdbcUrl);
                return connection;
            } else {
                logger.warn("Driver returned null connection for URL: {}", jdbcUrl);
                return null;
            }

        } catch (Exception e) {
            logger.error("Failed to create JDBC connection: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 检查驱动是否已加载
     *
     * @return true表示已加载，false表示未加载
     */
    public static boolean isDriverLoaded() {
        return loadedDriver != null;
    }

    /**
     * 获取已加载的驱动类
     *
     * @return 已加载的驱动类，如果未加载则返回null
     */
    public static Class<?> getLoadedDriver() {
        return loadedDriver;
    }

    /**
     * 清除已加载的驱动
     *
     * 主要用于测试或重新加载驱动
     */
    public static void clearLoadedDriver() {
        loadedDriver = null;

        // 清理动态类加载器
        if (dynamicClassLoader != null) {
            try {
                dynamicClassLoader.close();
            } catch (IOException e) {
                logger.warn("Failed to close dynamic class loader", e);
            }
            dynamicClassLoader = null;
        }

        logger.debug("Cleared cached JDBC driver and class loader");
    }

    /**
     * 验证驱动JAR文件
     *
     * 检查JAR文件是否存在且可读
     *
     * @param jarPath JAR文件路径
     * @return true表示文件有效，false表示文件无效
     */
    private static boolean validateJarFile(String jarPath) {
        if (jarPath == null || jarPath.trim().isEmpty()) {
            logger.warn("JAR file path is empty");
            return false;
        }

        File jarFile = new File(jarPath);

        // 检查文件是否存在
        if (!jarFile.exists()) {
            logger.warn("JAR file does not exist: {}", jarPath);
            return false;
        }

        // 检查是否为文件
        if (!jarFile.isFile()) {
            logger.warn("Specified path is not a file: {}", jarPath);
            return false;
        }

        // 检查是否可读
        if (!jarFile.canRead()) {
            logger.warn("JAR file is not readable: {}", jarPath);
            return false;
        }

        // 检查文件扩展名
        if (!jarFile.getName().toLowerCase().endsWith(".jar")) {
            logger.warn("File extension is not .jar: {}", jarPath);
            return false;
        }

        logger.debug("JAR file validation passed: {}", jarPath);
        return true;
    }

    /**
     * 获取驱动加载状态信息
     *
     * @return 驱动加载状态信息
     */
    public static String getDriverStatusInfo() {
        StringBuilder info = new StringBuilder();
        if (loadedDriver == null) {
            info.append("JDBC driver not loaded");
        } else {
            info.append(String.format("JDBC driver loaded: %s", loadedDriver.getName()));
            info.append(String.format(" (loader: %s)", loadedDriver.getClassLoader().getClass().getSimpleName()));
        }
        if (dynamicClassLoader != null) {
            info.append(", dynamic class loader created");
        }
        return info.toString();
    }
}
