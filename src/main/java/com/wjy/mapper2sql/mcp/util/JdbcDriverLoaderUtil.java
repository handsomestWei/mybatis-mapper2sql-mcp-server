package com.wjy.mapper2sql.mcp.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;

import com.wjy.mapper2sql.mcp.config.JdbcConnectionConfig;

/**
 * JDBC驱动加载器
 *
 * 负责动态加载用户指定的JDBC驱动JAR文件
 * 支持从文件系统路径加载驱动类，并缓存已加载的驱动避免重复加载
 * 通过修改系统类路径，确保Class.forName能够找到驱动类
 *
 * @author handsomestWei
 * @version 1.0.0
 */
public class JdbcDriverLoaderUtil {

    private static final Logger logger = LoggerFactory.getLogger(JdbcDriverLoaderUtil.class);

    // 已加载的JDBC驱动类，避免重复加载
    private static Class<?> loadedDriver = null;

    // 记录是否已经修改了系统类路径
    private static boolean systemClassPathModified = false;

    /**
     * 加载JDBC驱动
     *
     * 使用URLClassLoader动态加载用户指定的JDBC驱动JAR文件
     * 支持从文件系统路径加载驱动类
     * 通过修改系统类路径，确保Class.forName能够找到驱动类
     *
     * @param config JDBC连接配置
     * @return 加载的驱动类，如果加载失败则返回null
     */
    public static Class<?> loadJdbcDriver(JdbcConnectionConfig config) {
        try {
            // 检查配置是否支持动态加载
            if (!config.supportsDynamicLoading()) {
                logger.warn("JDBC配置不支持动态加载，缺少驱动类名或JAR文件路径");
                logger.warn("配置详情: {}", config);
                return null;
            }

            String driverClassName = config.getJdbcDriver();
            String driverJarPath = config.getJdbcDriverJar();

            // 检查是否已经加载过
            if (loadedDriver != null) {
                logger.debug("JDBC驱动已加载: {}", driverClassName);
                return loadedDriver;
            }

            // 验证JAR文件
            if (!validateJarFile(driverJarPath)) {
                logger.error("JDBC驱动JAR文件验证失败: {}", driverJarPath);
                return null;
            }

            // 将JAR文件添加到系统类路径
            if (!addJarToSystemClassPath(driverJarPath)) {
                logger.error("无法将JAR文件添加到系统类路径: {}", driverJarPath);
                return null;
            }

            // 使用Class.forName加载驱动类（现在应该能找到）
            try {
                Class<?> driverClass = Class.forName(driverClassName);
                logger.info("成功加载JDBC驱动: {} from {}", driverClassName, driverJarPath);

                // 缓存已加载的驱动类
                loadedDriver = driverClass;
                return driverClass;

            } catch (ClassNotFoundException e) {
                logger.error("Class.forName无法找到驱动类: {}", driverClassName, e);
                return null;
            }

        } catch (Exception e) {
            logger.error("加载JDBC驱动失败: {}", config.getJdbcDriver(), e);
            return null;
        }
    }

    /**
     * 将JAR文件添加到系统类路径
     *
     * 通过反射修改系统类加载器的URL数组，将JAR文件路径添加进去
     * 这样Class.forName就能找到驱动类了
     *
     * @param jarPath JAR文件路径
     * @return true表示成功，false表示失败
     */
    private static boolean addJarToSystemClassPath(String jarPath) {
        try {
            // 如果已经修改过系统类路径，直接返回成功
            if (systemClassPathModified) {
                logger.debug("系统类路径已经修改过，跳过重复操作");
                return true;
            }

            File jarFile = new File(jarPath);
            URL jarUrl = jarFile.toURI().toURL();

            // 获取系统类加载器
            ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();

            if (systemClassLoader instanceof URLClassLoader) {
                URLClassLoader urlClassLoader = (URLClassLoader) systemClassLoader;

                // 通过反射获取URL数组
                Field ucpField = URLClassLoader.class.getDeclaredField("ucp");
                ucpField.setAccessible(true);
                Object ucp = ucpField.get(urlClassLoader);

                // 获取URL数组
                Field pathField = ucp.getClass().getDeclaredField("path");
                pathField.setAccessible(true);
                URL[] path = (URL[]) pathField.get(ucp);

                // 检查JAR是否已经在类路径中
                for (URL url : path) {
                    if (url.equals(jarUrl)) {
                        logger.debug("JAR文件已在系统类路径中: {}", jarPath);
                        systemClassPathModified = true;
                        return true;
                    }
                }

                // 添加新的URL到类路径
                Field urlsField = ucp.getClass().getDeclaredField("urls");
                urlsField.setAccessible(true);
                URL[] urls = (URL[]) urlsField.get(ucp);

                URL[] newUrls = new URL[urls.length + 1];
                System.arraycopy(urls, 0, newUrls, 0, urls.length);
                newUrls[urls.length] = jarUrl;

                urlsField.set(ucp, newUrls);

                logger.info("成功将JAR文件添加到系统类路径: {}", jarPath);
                systemClassPathModified = true;
                return true;

            } else {
                logger.warn("系统类加载器不是URLClassLoader，无法修改类路径: {}", systemClassLoader.getClass().getName());
                return false;
            }

        } catch (Exception e) {
            logger.error("修改系统类路径失败: {}", jarPath, e);
            return false;
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
        systemClassPathModified = false;
        logger.debug("已清除缓存的JDBC驱动和类路径修改状态");
    }

    /**
     * 验证驱动JAR文件
     *
     * 检查JAR文件是否存在且可读
     *
     * @param jarPath JAR文件路径
     * @return true表示文件有效，false表示文件无效
     */
    public static boolean validateJarFile(String jarPath) {
        if (jarPath == null || jarPath.trim().isEmpty()) {
            logger.warn("JAR文件路径为空");
            return false;
        }

        File jarFile = new File(jarPath);

        // 检查文件是否存在
        if (!jarFile.exists()) {
            logger.warn("JAR文件不存在: {}", jarPath);
            return false;
        }

        // 检查是否为文件
        if (!jarFile.isFile()) {
            logger.warn("指定路径不是文件: {}", jarPath);
            return false;
        }

        // 检查是否可读
        if (!jarFile.canRead()) {
            logger.warn("JAR文件不可读: {}", jarPath);
            return false;
        }

        // 检查文件扩展名
        if (!jarFile.getName().toLowerCase().endsWith(".jar")) {
            logger.warn("文件扩展名不是.jar: {}", jarPath);
            return false;
        }

        logger.debug("JAR文件验证通过: {}", jarPath);
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
            info.append("JDBC驱动未加载");
        } else {
            info.append(String.format("JDBC驱动已加载: %s", loadedDriver.getName()));
        }

        if (systemClassPathModified) {
            info.append(", 系统类路径已修改");
        } else {
            info.append(", 系统类路径未修改");
        }

        return info.toString();
    }
}
