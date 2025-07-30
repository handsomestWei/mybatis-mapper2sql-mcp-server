package com.wjy.mapper2sql.mcp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 配置加载器
 *
 * 负责从系统属性和环境变量中读取MCP服务器的配置参数
 * 支持命令行参数（-D）和环境变量两种配置方式
 *
 * @author handsomestWei
 * @version 1.0.0
 */
public class ConfigurationLoader {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationLoader.class);

    // 系统属性名称（命令行参数 -D）
    private static final String PROP_DB_TYPE = "dbType";
    private static final String PROP_JDBC_DRIVER = "jdbcDriver";
    private static final String PROP_JDBC_DRIVER_JAR = "jdbcDriverJar";
    private static final String PROP_JDBC_URL = "jdbcUrl";
    private static final String PROP_USERNAME = "userName";
    private static final String PROP_PASSWORD = "password";

    // 环境变量名称
    private static final String ENV_DB_TYPE = "DB_TYPE";
    private static final String ENV_JDBC_DRIVER = "JDBC_DRIVER";
    private static final String ENV_JDBC_DRIVER_JAR = "JDBC_DRIVER_JAR";
    private static final String ENV_JDBC_URL = "JDBC_URL";
    private static final String ENV_USERNAME = "DB_USERNAME";
    private static final String ENV_PASSWORD = "DB_PASSWORD";

    // 默认值
    private static final String DEFAULT_DB_TYPE = "mysql";
    private static final String DEFAULT_JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";


    /**
     * 校验数据库类型，如果校验失败，则退出程序
     */
    public static void validateDbTypeBeforeStart() {
        logger.info("开始校验数据库类型配置...");

        try {
            // 从系统属性或环境变量读取数据库类型
            String rawDbType = ConfigurationLoader.getConfigValue("dbType", "DB_TYPE", null);

            // 校验数据库类型
            String validatedDbType = ConfigurationLoader.validateDbType(rawDbType);
            logger.info("数据库类型校验成功: {}", validatedDbType);

        } catch (IllegalArgumentException e) {
            logger.error("数据库类型校验失败，服务器启动终止: {}", e.getMessage());
            System.exit(1); // 退出程序
        }
    }

    /**
     * 加载JDBC连接配置
     *
     * 优先从系统属性读取，如果系统属性为空则从环境变量读取
     * 如果都为空则使用默认值（仅适用于可选参数）
     *
     * @return JDBC连接配置对象，如果必需参数缺失则返回null
     */
    public static JdbcConnectionConfig loadJdbcConfig() {
        logger.info("开始加载JDBC连接配置...");

        // 读取配置参数
        String rawDbType = getConfigValue(PROP_DB_TYPE, ENV_DB_TYPE, null);
        String jdbcDriver = getConfigValue(PROP_JDBC_DRIVER, ENV_JDBC_DRIVER, DEFAULT_JDBC_DRIVER);
        String jdbcDriverJar = getConfigValue(PROP_JDBC_DRIVER_JAR, ENV_JDBC_DRIVER_JAR, null);
        String jdbcUrl = getConfigValue(PROP_JDBC_URL, ENV_JDBC_URL, null);
        String userName = getConfigValue(PROP_USERNAME, ENV_USERNAME, null);
        String password = getConfigValue(PROP_PASSWORD, ENV_PASSWORD, null);

        // 校验数据库类型
        String dbType = validateDbType(rawDbType);

        // 创建配置对象
        JdbcConnectionConfig config = new JdbcConnectionConfig(
                dbType, jdbcDriver, jdbcDriverJar, jdbcUrl, userName, password);

        // 验证配置完整性
        if (!config.isValid()) {
            logger.warn("JDBC连接配置不完整，缺少必需参数。配置详情: {}", config);
            logger.warn("必需参数: jdbcUrl, userName, password, dbType, jdbcDriver, jdbcDriverJar");
            logger.warn("所有参数都是必需的，用于确保动态驱动加载功能正常工作");
            return null;
        }

        logger.info("JDBC连接配置加载成功: {}", config);
        return config;
    }

    /**
     * 获取配置值
     *
     * 优先从系统属性读取，如果系统属性为空则从环境变量读取
     * 如果都为空则返回默认值
     *
     * @param propertyName 系统属性名称
     * @param envName      环境变量名称
     * @param defaultValue 默认值（可以为null）
     * @return 配置值
     */
    public static String getConfigValue(String propertyName, String envName, String defaultValue) {
        // 优先从系统属性读取
        String value = System.getProperty(propertyName);
        if (value != null && !value.trim().isEmpty()) {
            logger.debug("从系统属性读取配置: {} = {}", propertyName, maskSensitiveValue(propertyName, value));
            return value.trim();
        }

        // 从环境变量读取
        value = System.getenv(envName);
        if (value != null && !value.trim().isEmpty()) {
            logger.debug("从环境变量读取配置: {} = {}", envName, maskSensitiveValue(envName, value));
            return value.trim();
        }

        // 使用默认值
        if (defaultValue != null) {
            logger.debug("使用默认配置: {} = {}", propertyName, defaultValue);
            return defaultValue;
        }

        logger.debug("配置项未设置: {} / {}", propertyName, envName);
        return null;
    }

    /**
     * 掩码敏感信息
     *
     * 在日志中隐藏密码等敏感信息
     *
     * @param key   配置键名
     * @param value 配置值
     * @return 掩码后的值
     */
    private static String maskSensitiveValue(String key, String value) {
        if (key.contains("password") || key.contains("PASSWORD")) {
            return "***";
        }
        return value;
    }

    /**
     * 获取配置来源信息
     *
     * 用于调试和日志记录，显示每个配置项的来源
     *
     * @return 配置来源信息
     */
    public static String getConfigSourceInfo() {
        StringBuilder info = new StringBuilder();
        info.append("配置来源信息:\n");

        // 检查系统属性
        info.append("系统属性:\n");
        checkAndAppendSource(info, PROP_DB_TYPE, "dbType");
        checkAndAppendSource(info, PROP_JDBC_DRIVER, "jdbcDriver");
        checkAndAppendSource(info, PROP_JDBC_DRIVER_JAR, "jdbcDriverJar");
        checkAndAppendSource(info, PROP_JDBC_URL, "jdbcUrl");
        checkAndAppendSource(info, PROP_USERNAME, "userName");
        checkAndAppendSource(info, PROP_PASSWORD, "password");

        // 检查环境变量
        info.append("环境变量:\n");
        checkAndAppendSource(info, ENV_DB_TYPE, "DB_TYPE");
        checkAndAppendSource(info, ENV_JDBC_DRIVER, "JDBC_DRIVER");
        checkAndAppendSource(info, ENV_JDBC_DRIVER_JAR, "JDBC_DRIVER_JAR");
        checkAndAppendSource(info, ENV_JDBC_URL, "JDBC_URL");
        checkAndAppendSource(info, ENV_USERNAME, "DB_USERNAME");
        checkAndAppendSource(info, ENV_PASSWORD, "DB_PASSWORD");

        return info.toString();
    }

    /**
     * 检查并添加配置来源信息
     *
     * @param info        StringBuilder对象
     * @param key         配置键
     * @param displayName 显示名称
     */
    private static void checkAndAppendSource(StringBuilder info, String key, String displayName) {
        String value = System.getProperty(key);
        if (value != null && !value.trim().isEmpty()) {
            info.append("  ").append(displayName).append(": 系统属性\n");
        } else {
            value = System.getenv(key);
            if (value != null && !value.trim().isEmpty()) {
                info.append("  ").append(displayName).append(": 环境变量\n");
            } else {
                info.append("  ").append(displayName).append(": 未设置\n");
            }
        }
    }

    /**
     * 校验数据库类型
     *
     * 如果dbType为空，则使用默认值mysql
     * 如果dbType不为空，则使用Druid的DbType.of()进行校验
     *
     * @param dbType 数据库类型
     * @return 校验后的数据库类型
     * @throws IllegalArgumentException 如果数据库类型不支持
     */
    public static String validateDbType(String dbType) {
        // 如果为空，使用默认值
        if (dbType == null || dbType.trim().isEmpty()) {
            logger.info("数据库类型未指定，使用默认值: {}", DEFAULT_DB_TYPE);
            return DEFAULT_DB_TYPE;
        }

        // 使用Druid的DbType进行校验
        com.alibaba.druid.DbType dbTypeEnum = com.alibaba.druid.DbType.of(dbType);
        if (dbTypeEnum == null) {
            String errorMsg = String.format("不支持的数据库类型: %s。请使用Druid支持的数据库类型", dbType);
            logger.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        logger.info("数据库类型校验通过: {} (Druid DbType: {})", dbType, dbTypeEnum);
        return dbType;
    }

    /**
     * 获取支持的数据库类型列表
     *
     * 注意：现在使用Druid的DbType进行校验，支持的数据库类型请参考Druid官方文档
     * 常见的数据库类型包括：mysql, postgresql, oracle, sqlserver, sqlite, h2, db2, informix等
     *
     * @return 支持的数据库类型数组（已废弃，请参考Druid文档）
     * @deprecated 使用Druid的DbType.of()方法进行数据库类型校验
     */
    @Deprecated
    public static String[] getSupportedDbTypes() {
        // 返回常见的数据库类型，但实际校验使用Druid的DbType.of()
        return new String[] { "mysql", "postgresql", "oracle", "sqlserver", "sqlite", "h2", "db2", "informix" };
    }
}
