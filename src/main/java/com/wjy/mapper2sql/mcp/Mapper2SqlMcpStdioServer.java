package com.wjy.mapper2sql.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import com.wjy.mapper2sql.SqlUtil;
import com.wjy.mapper2sql.bo.JdbcConnProperties;
import com.wjy.mapper2sql.bo.MapperSqlInfo;
import com.alibaba.druid.DbType;
import com.wjy.mapper2sql.mcp.config.ConfigurationLoader;
import com.wjy.mapper2sql.mcp.config.JdbcConnectionConfig;
import com.wjy.mapper2sql.mcp.util.JdbcDriverLoaderUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * MyBatis Mapper2SQL MCP Stdio服务器
 *
 * 这是一个Model Context Protocol (MCP)服务器实现，提供MyBatis mapper XML文件解析功能。
 * 基于stdio（stdin/stdout）模式（本地 Server），通过标准输入输出，进行进程间通信，适用于命令行工具集成。
 *
 * 服务器支持以下功能：
 * 1. 工具(Tools)：解析mapper XML文件，提取SQL语句（数据库类型从配置获取）
 * 2. 资源(Resources)：提供帮助文档和版本信息
 * 3. 提示(Prompts)：提供SQL提取和优化建议模板
 * 4. 自动完成(Completions)：为参数提供建议值
 * 5. 日志(Logging)：结构化日志记录
 *
 * 开发实践-关于mcp服务端所需的配置参数读取和传递使用
 * 1、每个AI IDEA都有各自的mcp客户端，都有各自的mcp.json配置文件，里面包含mcp服务端需要的一些配置参数
 * 2、mcp客户端会读取各自的配置文件
 * 3、mcp服务端不应该直接读取mcp.json配置文件参数，而是通过mcp客户端传递
 * 4、参数传递的方式，建议使用命令行或者环境变量
 * 5、同一个mcp客户端，支持配置多组mcp服务端连接，每组配置都是独立的一个mcp服务进程，基本不存在同一个服务端对接多个客户端的情况
 *
 * @author handsomestWei
 * @version 1.0.0
 */
public class Mapper2SqlMcpStdioServer {
    private static final Logger logger = LoggerFactory.getLogger(Mapper2SqlMcpStdioServer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 当前服务器的JDBC连接配置
    private static JdbcConnectionConfig jdbcConfig;

    private static McpSyncServer server;

    /**
     * MCP服务器主入口方法
     *
     * 负责：
     * 1. 创建传输提供者（使用标准输入输出流）
     * 2. 配置服务器能力和功能
     * 3. 注册各种工具、资源和提示
     * 4. 启动服务器并保持运行
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        logger.info("正在启动MyBatis Mapper2SQL MCP服务器...");

        try {
            // 启动前校验数据库类型
            ConfigurationLoader.validateDbTypeBeforeStart();

            // 初始化JDBC连接配置
            jdbcConfig = ConfigurationLoader.loadJdbcConfig();
            if (jdbcConfig == null) {
                logger.warn("JDBC连接配置不完整，服务器将无法使用需要数据库连接的功能。");
            } else {
                logger.info("JDBC连接配置初始化完成: {}", jdbcConfig);
            }

            // 创建传输提供者 - 使用标准输入输出流进行通信
            // 这是MCP协议的标准传输方式，适合进程间通信
            StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(objectMapper);

            // 创建同步MCP服务器，直接在链式调用中注册工具
            server = McpServer.sync(transportProvider)
                    .serverInfo("mapper2sql", "1.0.0") // 设置服务器名称和版本
                    .capabilities(McpSchema.ServerCapabilities.builder()
                            .resources(true, true) // 启用资源支持，包括列表变更通知
                            .tools(true) // 启用工具支持，包括列表变更通知
                            .prompts(true) // 启用提示支持，包括列表变更通知
                            .logging() // 启用日志支持
                            .completions() // 启用自动完成支持
                            .build())
                    .completions(createCompletions()) // 注册自动完成规范
                    // 直接注册工具，按照demo方式

                    .tool(new McpSchema.Tool("parse_mapper",
                            "Parse MyBatis mapper XML files and extract SQL statements with placeholders (no parameter mocking)",
                            "{\"type\":\"object\",\"properties\":{\"file_path\":{\"type\":\"string\",\"description\":\"Path to mapper XML file or directory\"}},\"required\":[\"file_path\"]}"),
                            (exchange, arguments) -> {
                                logger.info("正在执行parse_mapper工具，参数: {}", arguments);
                                try {
                                    String filePath = (String) arguments.get("file_path");
                                    String dbTypeName = (jdbcConfig != null) ? jdbcConfig.getDbType() : "mysql";
                                    DbType dbType = DbType.of(dbTypeName);
                                    if (dbType == null) {
                                        return new McpSchema.CallToolResult("Error: 配置中的数据库类型不支持: " + dbTypeName,
                                                false);
                                    }
                                    List<MapperSqlInfo> results = SqlUtil.parseMapper(filePath, dbType, false);
                                    String jsonResult = objectMapper.writeValueAsString(results);
                                    logger.info("parse_mapper执行成功，提取了{}个mapper文件", results.size());
                                    return new McpSchema.CallToolResult(jsonResult, false);
                                } catch (Exception e) {
                                    logger.error("parse_mapper执行出错", e);
                                    return new McpSchema.CallToolResult("Error: " + e.getMessage(), false);
                                }
                            })
                    .tool(new McpSchema.Tool("parse_mapper_with_mock",
                            "Parse MyBatis mapper XML files and extract SQL statements with parameter mocking",
                            "{\"type\":\"object\",\"properties\":{\"file_path\":{\"type\":\"string\",\"description\":\"Path to mapper XML file or directory\"},\"use_jdbc_connection\":{\"type\":\"boolean\",\"description\":\"Whether to use JDBC connection for type inference (default: false)\"}},\"required\":[\"file_path\"]}"),
                            (exchange, arguments) -> {
                                logger.info("正在执行parse_mapper_with_mock工具，参数: {}", arguments);
                                try {
                                    String filePath = (String) arguments.get("file_path");
                                    Boolean useJdbcConnection = (Boolean) arguments.getOrDefault("use_jdbc_connection",
                                            false);
                                    String dbTypeName = (jdbcConfig != null) ? jdbcConfig.getDbType() : "mysql";
                                    DbType dbType = DbType.of(dbTypeName);
                                    if (dbType == null) {
                                        return new McpSchema.CallToolResult("Error: 配置中的数据库类型不支持: " + dbTypeName,
                                                false);
                                    }

                                    List<MapperSqlInfo> results;
                                    if (useJdbcConnection) {
                                        if (jdbcConfig == null) {
                                            return new McpSchema.CallToolResult("Error: 使用JDBC连接需要完整的JDBC配置", false);
                                        }
                                        Class<?> driverClass = JdbcDriverLoaderUtil.loadJdbcDriver(jdbcConfig);
                                        if (driverClass == null) {
                                            return new McpSchema.CallToolResult(
                                                    "Error: 无法加载JDBC驱动: " + jdbcConfig.getJdbcDriver(), false);
                                        }
                                        JdbcConnProperties connProps = new JdbcConnProperties(
                                                jdbcConfig.getJdbcDriver(),
                                                jdbcConfig.getJdbcUrl(), jdbcConfig.getUserName(),
                                                jdbcConfig.getPassword());
                                        results = SqlUtil.parseMapper(filePath, dbType, true, connProps);
                                    } else {
                                        results = SqlUtil.parseMapper(filePath, dbType, true);
                                    }

                                    String jsonResult = objectMapper.writeValueAsString(results);
                                    logger.info("parse_mapper_with_mock执行成功，提取了{}个mapper文件", results.size());
                                    return new McpSchema.CallToolResult(jsonResult, false);
                                } catch (Exception e) {
                                    logger.error("parse_mapper_with_mock执行出错", e);
                                    return new McpSchema.CallToolResult("Error: " + e.getMessage(), false);
                                }
                            })
                    .tool(new McpSchema.Tool("parse_mapper_with_test",
                            "Parse MyBatis mapper XML files, extract SQL statements with parameter mocking, and test execution",
                            "{\"type\":\"object\",\"properties\":{\"file_path\":{\"type\":\"string\",\"description\":\"Path to mapper XML file or directory\"}},\"required\":[\"file_path\"]}"),
                            (exchange, arguments) -> {
                                logger.info("正在执行parse_mapper_with_test工具，参数: {}", arguments);
                                try {
                                    String filePath = (String) arguments.get("file_path");
                                    if (jdbcConfig == null) {
                                        return new McpSchema.CallToolResult(
                                                "Error: parse_mapper_with_test工具需要完整的JDBC配置", false);
                                    }
                                    String dbTypeName = jdbcConfig.getDbType();
                                    DbType dbType = DbType.of(dbTypeName);
                                    if (dbType == null) {
                                        return new McpSchema.CallToolResult("Error: 配置中的数据库类型不支持: " + dbTypeName,
                                                false);
                                    }

                                    Class<?> driverClass = JdbcDriverLoaderUtil.loadJdbcDriver(jdbcConfig);
                                    if (driverClass == null) {
                                        return new McpSchema.CallToolResult(
                                                "Error: 无法加载JDBC驱动: " + jdbcConfig.getJdbcDriver(), false);
                                    }

                                    JdbcConnProperties connProps = new JdbcConnProperties(jdbcConfig.getJdbcDriver(),
                                            jdbcConfig.getJdbcUrl(), jdbcConfig.getUserName(),
                                            jdbcConfig.getPassword());
                                    List<MapperSqlInfo> results = SqlUtil.parseMapperAndRunTest(filePath, dbType,
                                            connProps);

                                    String jsonResult = objectMapper.writeValueAsString(results);
                                    logger.info("parse_mapper_with_test执行成功，提取并测试了{}个mapper文件", results.size());
                                    return new McpSchema.CallToolResult(jsonResult, false);
                                } catch (Exception e) {
                                    logger.error("parse_mapper_with_test执行出错", e);
                                    return new McpSchema.CallToolResult("Error: " + e.getMessage(), false);
                                }
                            })
                    // 注册资源
                    .resources(
                            new McpServerFeatures.SyncResourceSpecification(
                                    new McpSchema.Resource("mapper2sql://help", "help",
                                            "MyBatis Mapper2SQL MCP Server Help Documentation", "text/markdown", null),
                                    (exchange, request) -> {
                                        String helpContent = """
                                                # MyBatis Mapper2SQL MCP Server

                                                ## Overview
                                                This MCP server provides tools for extracting SQL from MyBatis mapper XML files,
                                                with support for parameter mocking and SQL testing.

                                                ## Available Tools

                                                ### 1. parse_mapper
                                                Parse MyBatis mapper XML files and extract SQL statements with placeholders (no parameter mocking).

                                                **Parameters:**
                                                - `file_path`: Path to mapper XML file or directory

                                                **Note:** Database type is obtained from server configuration.

                                                ### 2. parse_mapper_with_mock
                                                Parse MyBatis mapper XML files and extract SQL statements with parameter mocking.

                                                **Parameters:**
                                                - `file_path`: Path to mapper XML file or directory
                                                - `use_jdbc_connection`: Whether to use JDBC connection for type inference (default: false)

                                                **Note:** Database type is obtained from server configuration. When `use_jdbc_connection` is true, the server will use the JDBC configuration provided via command line arguments or environment variables.

                                                ### 3. parse_mapper_with_test
                                                Parse MyBatis mapper XML files, extract SQL statements with parameter mocking, and test execution.

                                                **Parameters:**
                                                - `file_path`: Path to mapper XML file or directory

                                                **Note:** Database type is obtained from server configuration. This tool requires JDBC configuration to be provided via command line arguments or environment variables.

                                                ## Supported Database Types
                                                - mysql
                                                - postgresql
                                                - oracle
                                                - sqlserver
                                                - db2
                                                - h2
                                                - derby
                                                - sqlite
                                                - 等等

                                                **注意：** 数据库类型校验使用Druid的DbType，支持更多数据库类型，具体请参考Druid官方文档。

                                                ## Author
                                                [handsomestWei](https://github.com/handsomestWei/)
                                                """;
                                        return new McpSchema.ReadResourceResult(List.of(
                                                new McpSchema.TextResourceContents("mapper2sql://help", "text/markdown",
                                                        helpContent)));
                                    }),
                            new McpServerFeatures.SyncResourceSpecification(
                                    new McpSchema.Resource("mapper2sql://version", "version",
                                            "MyBatis Mapper2SQL MCP Server Version Information", "application/json",
                                            null),
                                    (exchange, request) -> {
                                        String versionInfo = """
                                                {
                                                  "name": "mapper2sql",
                                                  "version": "1.0.0",
                                                  "description": "MyBatis Mapper2SQL MCP Server",
                                                  "author": "handsomestWei",
                                                  "repository": "https://github.com/handsomestWei/mybatis-mapper2sql-mcp-server",
                                                  "features": [
                                                    "SQL extraction from MyBatis mapper XML files",
                                                    "Parameter mocking with type inference",
                                                    "SQL execution testing",
                                                    "Multiple database type support"
                                                  ]
                                                }
                                                """;
                                        return new McpSchema.ReadResourceResult(List.of(
                                                new McpSchema.TextResourceContents("mapper2sql://version",
                                                        "application/json", versionInfo)));
                                    }))
                    // 注册提示模板
                    .prompts(
                            new McpServerFeatures.SyncPromptSpecification(
                                    new McpSchema.Prompt("sql_extraction_guide",
                                            "Guide for extracting and understanding SQL from MyBatis mapper XML files",
                                            List.of(
                                                    new McpSchema.PromptArgument("mapper_file",
                                                            "Path to the MyBatis mapper XML file", true),
                                                    new McpSchema.PromptArgument("tool_name",
                                                            "Tool name: 'parse_mapper', 'parse_mapper_with_mock', or 'parse_mapper_with_test'",
                                                            false))),
                                    (exchange, request) -> {
                                        String mapperFile = (String) request.arguments().get("mapper_file");
                                        String toolName = (String) request.arguments().getOrDefault("tool_name",
                                                "parse_mapper");
                                        String systemPrompt = """
                                                你是MyBatis 专家，专门帮助用户从mapper XML文件中提取和理解SQL语句。

                                                文件路径: %s
                                                选择工具: %s

                                                可用工具说明：
                                                - parse_mapper：基础SQL提取，保留占位符
                                                - parse_mapper_with_mock：带参数模拟的SQL提取
                                                - parse_mapper_with_test：带SQL测试的完整提取

                                                请提供以下指导：
                                                1. 如何正确使用mapper2sql工具提取SQL
                                                2. 不同工具的区别和适用场景
                                                3. 提取结果的解读方法
                                                4. 常见问题和解决方案
                                                5. 最佳实践建议
                                                """
                                                .formatted(mapperFile, toolName);
                                        List<McpSchema.PromptMessage> messages = Arrays.asList(
                                                new McpSchema.PromptMessage(McpSchema.Role.ASSISTANT,
                                                        new McpSchema.TextContent(systemPrompt)),
                                                new McpSchema.PromptMessage(McpSchema.Role.USER,
                                                        new McpSchema.TextContent(
                                                                "请帮我从这个mapper文件中提取SQL语句，并提供详细的使用指导。")));
                                        return new McpSchema.GetPromptResult("SQL Extraction Guide", messages);
                                    }))
                    .build();

            logger.info("MyBatis Mapper2SQL MCP服务器启动成功");
            // 保持服务器运行
            logger.info("服务器开始等待客户端连接...");
            // 添加一个简单的测试，确保服务器正在运行
            try {
                while (true) {
                    Thread.sleep(30000); // 每30秒打印一次心跳
                    logger.info("服务器心跳 - 仍在运行");
                }
            } catch (InterruptedException e) {
                logger.info("服务器被中断");
                if (server != null) {
                    server.close();
                }
                logger.info("服务器已停止");
            }
        } catch (Throwable e) {
            logger.error("启动MCP服务器失败", e);
            if (server != null) {
                server.close();
            }
            System.exit(1);
        }
    }

    /**
     * 注册MCP工具
     *
     * 工具是MCP的核心功能，允许AI客户端调用服务器提供的功能。
     * 这里注册了三个工具：
     * 1. parse_mapper：基本解析，不进行参数模拟
     * 2. parse_mapper_with_mock：带参数模拟的解析
     * 3. parse_mapper_with_test：解析并执行SQL测试
     *
     * @param serverBuilder MCP同步服务器构建器
     */
    private static void registerTools(McpServer.SyncSpecification serverBuilder) {
        logger.info("开始注册工具...");

        // 注册工具1：parse_mapper - 基本解析（不进行参数模拟）
        var parseMapperSchema = """
                {
                  "type": "object",
                  "properties": {
                    "file_path": {
                      "type": "string",
                      "description": "Path to mapper XML file or directory"
                    }
                  },
                  "required": ["file_path"]
                }
                """;

        serverBuilder.tool(
                new McpSchema.Tool("parse_mapper",
                        "Parse MyBatis mapper XML files and extract SQL statements with placeholders (no parameter mocking)",
                        parseMapperSchema),
                (exchange, arguments) -> {
                    logger.info("正在执行parse_mapper工具，参数: {}", arguments);

                    try {
                        // 从参数中提取文件路径
                        String filePath = (String) arguments.get("file_path");

                        // 从配置中获取数据库类型
                        String dbTypeName;
                        if (jdbcConfig != null) {
                            dbTypeName = jdbcConfig.getDbType();
                        } else {
                            // 如果没有JDBC配置，使用默认的mysql类型
                            dbTypeName = "mysql";
                            logger.info("未找到JDBC连接配置，使用默认数据库类型: {}", dbTypeName);
                        }

                        DbType dbType = DbType.of(dbTypeName);
                        if (dbType == null) {
                            String errorMsg = "配置中的数据库类型不支持: " + dbTypeName;
                            logger.error(errorMsg);
                            return new McpSchema.CallToolResult("Error: " + errorMsg, false);
                        }

                        // 调用核心解析功能 - 不进行参数模拟
                        List<MapperSqlInfo> results = SqlUtil.parseMapper(filePath, dbType, false);

                        // 将结果序列化为JSON返回
                        String jsonResult = objectMapper.writeValueAsString(results);
                        logger.info("parse_mapper执行成功，提取了{}个mapper文件", results.size());

                        return new McpSchema.CallToolResult(jsonResult, false);

                    } catch (Exception e) {
                        logger.error("parse_mapper执行出错", e);
                        return new McpSchema.CallToolResult("Error: " + e.getMessage(), false);
                    }
                });
        logger.info("已注册工具: parse_mapper");

        // 注册工具2：parse_mapper_with_mock - 带参数模拟的解析
        var parseMapperWithMockSchema = """
                {
                  "type": "object",
                  "properties": {
                    "file_path": {
                      "type": "string",
                      "description": "Path to mapper XML file or directory"
                    },
                    "use_jdbc_connection": {
                      "type": "boolean",
                      "description": "Whether to use JDBC connection for type inference (default: false)"
                    }
                  },
                  "required": ["file_path"]
                }
                """;

        serverBuilder.tool(
                new McpSchema.Tool("parse_mapper_with_mock",
                        "Parse MyBatis mapper XML files and extract SQL statements with parameter mocking",
                        parseMapperWithMockSchema),
                (exchange, arguments) -> {
                    logger.info("正在执行parse_mapper_with_mock工具，参数: {}", arguments);

                    try {
                        // 提取所有参数
                        String filePath = (String) arguments.get("file_path");
                        Boolean useJdbcConnection = (Boolean) arguments.getOrDefault("use_jdbc_connection", false);

                        // 从配置中获取数据库类型
                        String dbTypeName;
                        if (jdbcConfig != null) {
                            dbTypeName = jdbcConfig.getDbType();
                        } else {
                            // 如果没有JDBC配置，使用默认的mysql类型
                            dbTypeName = "mysql";
                            logger.info("未找到JDBC连接配置，使用默认数据库类型: {}", dbTypeName);
                        }

                        DbType dbType = DbType.of(dbTypeName);
                        if (dbType == null) {
                            String errorMsg = "配置中的数据库类型不支持: " + dbTypeName;
                            logger.error(errorMsg);
                            return new McpSchema.CallToolResult("Error: " + errorMsg, false);
                        }

                        // 根据是否使用JDBC连接选择不同的解析方式
                        List<MapperSqlInfo> results;
                        if (useJdbcConnection) {
                            if (jdbcConfig == null) {
                                String errorMsg = "使用JDBC连接需要完整的JDBC配置，请通过命令行参数或环境变量提供数据库连接信息";
                                logger.error(errorMsg);
                                return new McpSchema.CallToolResult("Error: " + errorMsg, false);
                            }

                            // 动态加载JDBC驱动
                            Class<?> driverClass = JdbcDriverLoaderUtil.loadJdbcDriver(jdbcConfig);
                            if (driverClass == null) {
                                String errorMsg = "无法加载JDBC驱动: " + jdbcConfig.getJdbcDriver();
                                logger.error(errorMsg);
                                return new McpSchema.CallToolResult("Error: " + errorMsg, false);
                            }

                            JdbcConnProperties connProps = new JdbcConnProperties(jdbcConfig.getJdbcDriver(),
                                    jdbcConfig.getJdbcUrl(),
                                    jdbcConfig.getUserName(),
                                    jdbcConfig.getPassword());
                            results = SqlUtil.parseMapper(filePath, dbType, true, connProps);
                        } else {
                            // 无数据库连接 - 仅使用XML中的类型信息
                            results = SqlUtil.parseMapper(filePath, dbType, true);
                        }

                        String jsonResult = objectMapper.writeValueAsString(results);
                        logger.info("parse_mapper_with_mock执行成功，提取了{}个mapper文件",
                                results.size());

                        return new McpSchema.CallToolResult(jsonResult, false);

                    } catch (Exception e) {
                        logger.error("parse_mapper_with_mock执行出错", e);
                        return new McpSchema.CallToolResult("Error: " + e.getMessage(), false);
                    }
                });
        logger.info("已注册工具: parse_mapper_with_mock");

        // 注册工具3：parse_mapper_with_test - 解析并执行SQL测试
        var parseMapperWithTestSchema = """
                {
                  "type": "object",
                  "properties": {
                    "file_path": {
                      "type": "string",
                      "description": "Path to mapper XML file or directory"
                    }
                  },
                  "required": ["file_path"]
                }
                """;

        serverBuilder.tool(
                new McpSchema.Tool("parse_mapper_with_test",
                        "Parse MyBatis mapper XML files, extract SQL statements with parameter mocking, and test execution",
                        parseMapperWithTestSchema),
                (exchange, arguments) -> {
                    logger.info("正在执行parse_mapper_with_test工具，参数: {}", arguments);

                    try {
                        // 提取所有必需的参数
                        String filePath = (String) arguments.get("file_path");

                        // 从配置中获取数据库类型
                        if (jdbcConfig == null) {
                            String errorMsg = "parse_mapper_with_test工具需要完整的JDBC配置，请通过命令行参数或环境变量提供数据库连接信息";
                            logger.error(errorMsg);
                            return new McpSchema.CallToolResult("Error: " + errorMsg, false);
                        }

                        String dbTypeName = jdbcConfig.getDbType();
                        DbType dbType = DbType.of(dbTypeName);
                        if (dbType == null) {
                            String errorMsg = "配置中的数据库类型不支持: " + dbTypeName;
                            logger.error(errorMsg);
                            return new McpSchema.CallToolResult("Error: " + errorMsg, false);
                        }

                        // 动态加载JDBC驱动
                        Class<?> driverClass = JdbcDriverLoaderUtil.loadJdbcDriver(jdbcConfig);
                        if (driverClass == null) {
                            String errorMsg = "无法加载JDBC驱动: " + jdbcConfig.getJdbcDriver();
                            logger.error(errorMsg);
                            return new McpSchema.CallToolResult("Error: " + errorMsg, false);
                        }

                        // 创建数据库连接属性
                        JdbcConnProperties connProps = new JdbcConnProperties(jdbcConfig.getJdbcDriver(),
                                jdbcConfig.getJdbcUrl(),
                                jdbcConfig.getUserName(),
                                jdbcConfig.getPassword());
                        List<MapperSqlInfo> results = SqlUtil.parseMapperAndRunTest(filePath, dbType, connProps);

                        String jsonResult = objectMapper.writeValueAsString(results);
                        logger.info(
                                "parse_mapper_with_test执行成功，提取并测试了{}个mapper文件",
                                results.size());

                        return new McpSchema.CallToolResult(jsonResult, false);

                    } catch (Exception e) {
                        logger.error("parse_mapper_with_test执行出错", e);
                        return new McpSchema.CallToolResult("Error: " + e.getMessage(), false);
                    }
                });
        logger.info("已注册工具: parse_mapper_with_test");

        logger.info("所有工具注册完成: parse_mapper, parse_mapper_with_mock, parse_mapper_with_test");
    }

    /**
     * 注册MCP资源
     *
     * 资源提供静态内容给AI客户端，如帮助文档、版本信息等。
     * 资源通过URI访问，类似于Web资源。
     *
     * 使用场景：
     * 用户查询帮助 → AI调用资源接口 → 获取帮助文档 → 返回给用户
     * 用户需要版本信息 → AI调用资源接口 → 获取版本详情 → 返回给用户
     *
     * 示例：
     * 用户: "mapper2sql工具有哪些功能？"
     * AI: 调用mapper2sql://help资源，获取工具介绍和功能说明
     * 返回: "mapper2sql提供3个核心功能：parse_mapper（基础SQL提取）、
     * parse_mapper_with_mock（带参数模拟）、parse_mapper_with_test（带SQL测试）"
     *
     * 资源类型：
     * - 帮助文档：提供工具使用说明、参数说明、示例等
     * - 版本信息：提供版本号、功能特性、作者信息等
     * - 配置模板：提供标准配置文件格式
     * - 使用示例：提供各种场景的示例代码
     *
     * @param serverBuilder MCP同步服务器构建器
     */
    private static void registerResources(McpServer.SyncSpecification serverBuilder) {
        logger.info("开始注册资源...");

        // 注册资源1：mapper2sql://help - 帮助文档
        var helpResource = new McpServerFeatures.SyncResourceSpecification(
                new McpSchema.Resource("mapper2sql://help", "help",
                        "MyBatis Mapper2SQL MCP Server Help Documentation", "text/markdown", null),
                (exchange, request) -> {
                    // 返回Markdown格式的帮助文档
                    String helpContent = """
                            # MyBatis Mapper2SQL MCP Server

                            ## Overview
                            This MCP server provides tools for extracting SQL from MyBatis mapper XML files,
                            with support for parameter mocking and SQL testing.

                            ## Available Tools

                            ### 1. parse_mapper
                            Parse MyBatis mapper XML files and extract SQL statements with placeholders (no parameter mocking).

                            **Parameters:**
                            - `file_path`: Path to mapper XML file or directory

                            **Note:** Database type is obtained from server configuration.

                            ### 2. parse_mapper_with_mock
                            Parse MyBatis mapper XML files and extract SQL statements with parameter mocking.

                            **Parameters:**
                            - `file_path`: Path to mapper XML file or directory
                            - `use_jdbc_connection`: Whether to use JDBC connection for type inference (default: false)

                            **Note:** Database type is obtained from server configuration. When `use_jdbc_connection` is true, the server will use the JDBC configuration provided via command line arguments or environment variables.

                            ### 3. parse_mapper_with_test
                            Parse MyBatis mapper XML files, extract SQL statements with parameter mocking, and test execution.

                            **Parameters:**
                            - `file_path`: Path to mapper XML file or directory

                            **Note:** Database type is obtained from server configuration. This tool requires JDBC configuration to be provided via command line arguments or environment variables.

                            ## Supported Database Types
                            - mysql
                            - postgresql
                            - oracle
                            - sqlserver
                            - db2
                            - h2
                            - derby
                            - sqlite
                            - 等等

                            **注意：** 数据库类型校验使用Druid的DbType，支持更多数据库类型，具体请参考Druid官方文档。

                            ## Author
                            [handsomestWei](https://github.com/handsomestWei/)
                            """;

                    // 返回文本资源内容
                    return new McpSchema.ReadResourceResult(List.of(
                            new McpSchema.TextResourceContents("mapper2sql://help", "text/markdown", helpContent)));
                });

        // 注册资源2：mapper2sql://version - 版本信息
        var versionResource = new McpServerFeatures.SyncResourceSpecification(
                new McpSchema.Resource("mapper2sql://version", "version",
                        "MyBatis Mapper2SQL MCP Server Version Information", "application/json", null),
                (exchange, request) -> {
                    // 返回JSON格式的版本信息
                    String versionInfo = """
                            {
                              "name": "mapper2sql",
                              "version": "1.0.0",
                              "description": "MyBatis Mapper2SQL MCP Server",
                              "author": "handsomestWei",
                              "repository": "https://github.com/handsomestWei/mybatis-mapper2sql-mcp-server",
                              "features": [
                                "SQL extraction from MyBatis mapper XML files",
                                "Parameter mocking with type inference",
                                "SQL execution testing",
                                "Multiple database type support"
                              ]
                            }
                            """;

                    // 返回JSON资源内容
                    return new McpSchema.ReadResourceResult(List.of(
                            new McpSchema.TextResourceContents("mapper2sql://version", "application/json",
                                    versionInfo)));
                });

        // 将资源添加到服务器构建器
        serverBuilder.resources(helpResource, versionResource);
        logger.info("已注册资源: mapper2sql://help, mapper2sql://version");

        logger.info("所有资源注册完成: mapper2sql://help, mapper2sql://version");
    }

    /**
     * 注册MCP提示模板
     *
     * 提示模板为AI客户端提供结构化的交互模板，帮助AI更好地理解和处理特定任务。
     *
     * 使用流程：
     * 用户输入 → AI识别意图 → 调用提示模板 → 获取预置结构 → AI格式化输出 → 返回结果
     *
     * 关键特点：
     * - 模板预置：MCP服务器提前定义各种场景的提示模板
     * - AI执行：AI根据模板指导生成具体内容
     * - 结构化：确保AI输出符合特定格式和要求
     * - 参数化：用户需求转换为模板参数
     *
     * 示例：
     * 用户: "帮我从mapper文件提取SQL，并告诉我怎么使用这个工具"
     * AI: 调用sql_extraction_guide提示模板，传入文件路径和工具名称
     * 返回: "作为MyBatis 专家，我将为您提供详细指导：
     * 1. 使用parse_mapper工具提取基础SQL
     * 2. 如需参数模拟，使用parse_mapper_with_mock
     * 3. 如需SQL测试，使用parse_mapper_with_test..."
     *
     * 目前提供了sql_extraction_guide提示模板，帮助用户更好地使用SQL提取功能。
     *
     * @param serverBuilder MCP同步服务器构建器
     */
    private static void registerPrompts(McpServer.SyncSpecification serverBuilder) {
        logger.info("开始注册提示模板...");

        // 注册提示模板：sql_extraction_guide - SQL提取指导模板
        var sqlExtractionPrompt = new McpServerFeatures.SyncPromptSpecification(
                new McpSchema.Prompt("sql_extraction_guide",
                        "Guide for extracting and understanding SQL from MyBatis mapper XML files",
                        List.of(
                                new McpSchema.PromptArgument("mapper_file",
                                        "Path to the MyBatis mapper XML file", true),
                                new McpSchema.PromptArgument("tool_name",
                                        "Tool name: 'parse_mapper', 'parse_mapper_with_mock', or 'parse_mapper_with_test'",
                                        false))),
                (exchange, request) -> {
                    // 从请求中提取参数
                    String mapperFile = (String) request.arguments().get("mapper_file");
                    String toolName = (String) request.arguments().getOrDefault("tool_name", "parse_mapper");

                    // 构建系统提示信息
                    String systemPrompt = """
                            你是MyBatis 专家，专门帮助用户从mapper XML文件中提取和理解SQL语句。

                            文件路径: %s
                            选择工具: %s

                            可用工具说明：
                            - parse_mapper：基础SQL提取，保留占位符
                            - parse_mapper_with_mock：带参数模拟的SQL提取
                            - parse_mapper_with_test：带SQL测试的完整提取

                            请提供以下指导：
                            1. 如何正确使用mapper2sql工具提取SQL
                            2. 不同工具的区别和适用场景
                            3. 提取结果的解读方法
                            4. 常见问题和解决方案
                            5. 最佳实践建议
                            """
                            .formatted(mapperFile, toolName);

                    // 构建消息列表：系统提示 + 用户输入
                    List<McpSchema.PromptMessage> messages = Arrays.asList(
                            new McpSchema.PromptMessage(McpSchema.Role.ASSISTANT,
                                    new McpSchema.TextContent(systemPrompt)),
                            new McpSchema.PromptMessage(McpSchema.Role.USER,
                                    new McpSchema.TextContent(
                                            "请帮我从这个mapper文件中提取SQL语句，并提供详细的使用指导。")));

                    // 返回格式化的提示结果
                    return new McpSchema.GetPromptResult("SQL Extraction Guide", messages);
                });

        // 将提示模板添加到服务器构建器
        serverBuilder.prompts(sqlExtractionPrompt);
        logger.info("已注册提示模板: sql_extraction_guide");
        logger.info("所有提示模板注册完成: sql_extraction_guide");
    }

    /**
     * 创建MCP自动完成规范
     *
     * 自动完成功能为AI客户端提供参数建议，提高用户体验。
     * 当用户输入参数时，服务器可以提供预定义的建议值。
     *
     * 使用场景：
     * 用户输入参数 → AI调用自动完成接口 → 获取建议值列表 → 展示给用户选择
     *
     * 自动完成类型：
     * - 工具名称：parse_mapper, parse_mapper_with_mock, parse_mapper_with_test
     *
     * 示例：
     * 用户: "我想提取SQL，使用哪个工具..."
     * AI: 调用自动完成接口，获取工具名称列表
     * 返回: "可选的工具：parse_mapper（基础提取）, parse_mapper_with_mock（带参数模拟）,
     * parse_mapper_with_test（带SQL测试）"
     *
     * @return 自动完成规范列表
     */
    private static List<McpServerFeatures.SyncCompletionSpecification> createCompletions() {
        // 自动完成1：工具名称建议 - 用于提示模板的参数
        var toolNameCompletions = new McpServerFeatures.SyncCompletionSpecification(
                new McpSchema.PromptReference("sql_extraction_guide", "tool_name"),
                (exchange, request) -> {
                    // 返回可用的工具名称
                    List<String> toolNames = List.of(
                            "parse_mapper", "parse_mapper_with_mock", "parse_mapper_with_test");

                    return new McpSchema.CompleteResult(
                            new McpSchema.CompleteResult.CompleteCompletion(
                                    toolNames,
                                    toolNames.size(),
                                    false));
                });

        // 返回所有自动完成规范
        return List.of(toolNameCompletions);
    }
}
