package com.wjy.mapper2sql.mcp.config;

/**
 * JDBC连接配置类
 *
 * 用于存储MCP服务器的JDBC连接参数
 * 字段名称与JdbcConnProperties保持一致，便于与核心逻辑集成
 *
 * @author handsomestWei
 * @version 1.0.0
 */
public class JdbcConnectionConfig {

    private final String dbType;
    private final String jdbcDriver;
    private final String jdbcDriverJar;
    private final String jdbcUrl;
    private final String userName;
    private final String password;

    /**
     * 构造函数
     *
     * @param dbType        数据库类型（mysql、postgresql、oracle、sqlserver等）
     * @param jdbcDriver    JDBC驱动类名
     * @param jdbcDriverJar JDBC驱动JAR文件路径
     * @param jdbcUrl       完整的JDBC连接URL
     * @param userName      数据库用户名
     * @param password      数据库密码
     */
    public JdbcConnectionConfig(String dbType, String jdbcDriver, String jdbcDriverJar,
            String jdbcUrl, String userName, String password) {
        this.dbType = dbType;
        this.jdbcDriver = jdbcDriver;
        this.jdbcDriverJar = jdbcDriverJar;
        this.jdbcUrl = jdbcUrl;
        this.userName = userName;
        this.password = password;
    }

    /**
     * 获取数据库类型
     *
     * @return 数据库类型
     */
    public String getDbType() {
        return dbType;
    }

    /**
     * 获取JDBC驱动类名
     *
     * @return JDBC驱动类名
     */
    public String getJdbcDriver() {
        return jdbcDriver;
    }

    /**
     * 获取JDBC驱动JAR文件路径
     *
     * @return JDBC驱动JAR文件路径
     */
    public String getJdbcDriverJar() {
        return jdbcDriverJar;
    }

    /**
     * 获取JDBC连接URL
     *
     * @return JDBC连接URL
     */
    public String getJdbcUrl() {
        return jdbcUrl;
    }

    /**
     * 获取数据库用户名
     *
     * @return 数据库用户名
     */
    public String getUserName() {
        return userName;
    }

    /**
     * 获取数据库密码
     *
     * @return 数据库密码
     */
    public String getPassword() {
        return password;
    }

    /**
     * 检查配置是否完整
     *
     * 必需参数：jdbcUrl、userName、password、dbType、jdbcDriver、jdbcDriverJar
     * 所有参数都是必需的，用于确保动态驱动加载功能正常工作
     *
     * @return true表示配置完整，false表示配置不完整
     */
    public boolean isValid() {
        return jdbcUrl != null && !jdbcUrl.trim().isEmpty() &&
                userName != null && !userName.trim().isEmpty() &&
                password != null && !password.trim().isEmpty() &&
                dbType != null && !dbType.trim().isEmpty() &&
                jdbcDriver != null && !jdbcDriver.trim().isEmpty() &&
                jdbcDriverJar != null && !jdbcDriverJar.trim().isEmpty();
    }

    /**
     * 检查是否支持动态驱动加载
     *
     * @return true表示支持动态加载，false表示不支持
     */
    public boolean supportsDynamicLoading() {
        return jdbcDriver != null && !jdbcDriver.trim().isEmpty() &&
                jdbcDriverJar != null && !jdbcDriverJar.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "JdbcConnectionConfig{" +
                "dbType='" + dbType + '\'' +
                ", jdbcDriver='" + jdbcDriver + '\'' +
                ", jdbcDriverJar='" + jdbcDriverJar + '\'' +
                ", jdbcUrl='" + jdbcUrl + '\'' +
                ", userName='" + userName + '\'' +
                ", password='***'" + // 隐藏密码
                '}';
    }
}
