package com.wjy.mapper2sql.mcp;

import com.alibaba.druid.DbType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjy.mapper2sql.SqlUtil;
import com.wjy.mapper2sql.bo.MapperSqlInfo;
import com.wjy.mapper2sql.mcp.config.ConfigurationLoader;
import com.wjy.mapper2sql.mcp.config.JdbcConnectionConfig;
import com.wjy.mapper2sql.mcp.util.JdbcDriverLoaderUtil;
import com.wjy.mapper2sql.util.OutPutUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * MyBatis Mapper2SQL MCP 服务
 *
 * 提供 MyBatis mapper XML 文件解析功能，支持：
 * 1. 基础 SQL 提取（保留占位符）
 * 2. 带参数模拟的 SQL 提取
 * 3. 带 SQL 测试的完整提取
 *
 * 使用 Spring AI 的 @Tool 注解自动注册为 MCP 工具
 *
 * @author handsomestWei
 * @version 1.0.0
 */
@Service
public class Mapper2SqlMcpService {

    private static final Logger logger = LoggerFactory.getLogger(Mapper2SqlMcpService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 当前服务器的JDBC连接配置
    private final JdbcConnectionConfig jdbcConfig;

    public Mapper2SqlMcpService() {
        // 初始化JDBC连接配置
        this.jdbcConfig = ConfigurationLoader.loadJdbcConfig();
        if (this.jdbcConfig == null) {
            logger.warn(
                    "JDBC Config is null, JDBC connection configuration is incomplete, the server will not be able to use the function that requires database connection.");
        } else {
            logger.info("JDBC Config initialized: {}", this.jdbcConfig);
            JdbcDriverLoaderUtil.loadJdbcDriver(jdbcConfig);
            logger.info("JDBC Driver status: {}", JdbcDriverLoaderUtil.getDriverStatusInfo());
        }
    }

    /**
     * 解析 MyBatis mapper XML 文件并提取 SQL 语句（不进行参数模拟）
     *
     * @param filePath mapper XML 文件路径或目录路径
     * @return JSON 格式的解析结果
     */
    @Tool(name = "parse_mapper", description = "Parse MyBatis mapper XML files and extract SQL statements with placeholders (no parameter mocking)")
    public String parseMapper(
            @ToolParam(description = "Path to mapper XML file or directory") String filePath) {
        logger.info("Executing parse_mapper tool, parameter: filePath={}", filePath);

        try {
            String dbTypeName = (jdbcConfig != null) ? jdbcConfig.getDbType() : "mysql";
            DbType dbType = DbType.of(dbTypeName);
            if (dbType == null) {
                String errorMsg = "Database type not supported: " + dbTypeName;
                logger.error(errorMsg);
                return "Error: " + errorMsg;
            }

            // 调用核心解析功能 - 不进行参数模拟
            List<MapperSqlInfo> results = SqlUtil.parseMapper(filePath, dbType, false);

            // 将结果序列化为JSON返回
            String jsonResult = objectMapper.writeValueAsString(OutPutUtil.toLineList(results));
            logger.info("parse_mapper executed successfully, extracted {} mapper files", results.size());

            return jsonResult;

        } catch (Exception e) {
            logger.error("parse_mapper executed failed", e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * 解析 MyBatis mapper XML 文件并提取 SQL 语句（带参数模拟）
     *
     * @param filePath mapper XML 文件路径或目录路径
     * @return JSON 格式的解析结果
     */
    @Tool(name = "parse_mapper_and_mock", description = "Parse MyBatis mapper XML files and extract SQL statements and mock parameters")
    public String parseMapperAndMock(
            @ToolParam(description = "Path to mapper XML file or directory") String filePath) {
        logger.info("Executing parse_mapper_and_mock tool, parameter: filePath={}", filePath);

        try {
            String dbTypeName = (jdbcConfig != null) ? jdbcConfig.getDbType() : "mysql";
            DbType dbType = DbType.of(dbTypeName);
            if (dbType == null) {
                String errorMsg = "Database type not supported: " + dbTypeName;
                logger.error(errorMsg);
                return "Error: " + errorMsg;
            }

            List<MapperSqlInfo> results = new ArrayList<>();
            if (JdbcDriverLoaderUtil.isDriverLoaded()) {
                try (Connection conn = JdbcDriverLoaderUtil.createConnection(jdbcConfig.getJdbcUrl(),
                        jdbcConfig.getUserName(), jdbcConfig.getPassword())) {
                    results = SqlUtil.parseMapper(filePath, dbType, true, conn);
                }
            } else {
                results = SqlUtil.parseMapper(filePath, dbType, true);
            }
            String jsonResult = objectMapper.writeValueAsString(OutPutUtil.toLineList(results));
            logger.info("parse_mapper_and_mock executed successfully, extracted {} mapper files", results.size());
            return jsonResult;
        } catch (Exception e) {
            logger.error("parse_mapper_and_mock executed failed", e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * 解析 MyBatis mapper XML 文件，提取 SQL 语句并进行测试执行
     *
     * @param filePath mapper XML 文件路径或目录路径
     * @return JSON 格式的解析和测试结果
     */
    @Tool(name = "parse_mapper_and_run_test", description = "Parse MyBatis mapper XML files, extract SQL statements with parameter mocking, and test execution")
    public String parseMapperAndRunTest(
            @ToolParam(description = "Path to mapper XML file or directory") String filePath) {
        logger.info("Executing parse_mapper_and_run_test tool, parameter: filePath={}", filePath);

        try {
            // 检查JDBC配置
            if (jdbcConfig == null) {
                String errorMsg = "parse_mapper_and_run_test tool requires complete JDBC configuration, please provide database connection information through command line parameters or environment variables";
                logger.error(errorMsg);
                return "Error: " + errorMsg;
            }

            String dbTypeName = jdbcConfig.getDbType();
            DbType dbType = DbType.of(dbTypeName);
            if (dbType == null) {
                String errorMsg = "Database type not supported: " + dbTypeName;
                logger.error(errorMsg);
                return "Error: " + errorMsg;
            }

            if (!JdbcDriverLoaderUtil.isDriverLoaded()) {
                String errorMsg = "Failed to load JDBC driver: " + jdbcConfig.getJdbcDriver();
                logger.error(errorMsg);
                return "Error: " + errorMsg;
            }

            List<MapperSqlInfo> results = new ArrayList<>();
            try (Connection conn = JdbcDriverLoaderUtil.createConnection(jdbcConfig.getJdbcUrl(), jdbcConfig.getUserName(), jdbcConfig.getPassword())) {
                results = SqlUtil.parseMapperAndRunTest(filePath, dbType, conn);
            }
            String jsonResult = objectMapper.writeValueAsString(OutPutUtil.toLineList(results));
            logger.info("parse_mapper_and_run_test executed successfully, extracted and tested {} mapper files",
                    results.size());
            return jsonResult;
        } catch (Exception e) {
            logger.error("parse_mapper_and_run_test executed failed", e);
            return "Error: " + e.getMessage();
        }
    }
}
