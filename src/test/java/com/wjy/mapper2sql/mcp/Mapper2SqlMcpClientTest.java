package com.wjy.mapper2sql.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * MCP客户端测试类
 *
 * 用于测试Mapper2SqlMcpHttpServer的功能
 *
 * @author handsomestWei
 * @version 1.0.0
 */
public class Mapper2SqlMcpClientTest {

    private static final Logger logger = LoggerFactory.getLogger(Mapper2SqlMcpClientTest.class);

    // JDK目录配置
    private static String JDK17_HOME = "C:\\Program Files\\Java\\jdk17.0.16";

    private McpSyncClient mcpClient;
    private StdioClientTransport stdioTransport;

    @BeforeEach
    void setUp() {
        // 设置JDK环境变量
        setupJdkEnvironment();

        // 检查jar文件是否存在
        File jarFile = new File("target/mapper2sql-mcp-server-1.0.0.jar");
        if (!jarFile.exists()) {
            throw new RuntimeException("找不到jar文件: " + jarFile.getAbsolutePath() + "，请先运行 mvn clean package 构建项目");
        }

        // 构建服务器参数 - 使用java -jar方式启动fat jar
        var stdioParams = ServerParameters.builder("java")
                .args(
                        "-DdbType=mysql",
              "-DjdbcDriver=com.mysql.cj.jdbc.Driver",
              "-DjdbcDriverJar=D:\\apache-maven-repo\\com\\mysql\\mysql-connector-j\\8.0.33\\mysql-connector-j-8.0.33.jar",
              "-DjdbcUrl=jdbc:mysql://localhost:3306/testdb",
              "-DuserName=root",
              "-Dpassword=password",
                        "-Dlogging.pattern.console=","-jar", jarFile.getAbsolutePath())
                .build();

        // 创建stdio传输层
        stdioTransport = new StdioClientTransport(stdioParams);

        // 创建MCP客户端
        mcpClient = McpClient.sync(stdioTransport).requestTimeout(Duration.of(60, ChronoUnit.SECONDS)).build();
    }

    /**
     * 设置JDK目录，从外部传入
     */
    public static void setJdkHome(String jdkHome) {
        JDK17_HOME = jdkHome;
        logger.info("设置JDK目录: {}", JDK17_HOME);
    }

    /**
     * 设置JDK环境变量
     */
    private void setupJdkEnvironment() {
        // 设置JAVA_HOME环境变量
        System.setProperty("java.home", JDK17_HOME);

        // 设置PATH环境变量，添加JDK bin目录
        String currentPath = System.getProperty("java.library.path", "");
        String jdkBinPath = JDK17_HOME + "\\bin";

        if (!currentPath.isEmpty()) {
            System.setProperty("java.library.path", jdkBinPath + ";" + currentPath);
        } else {
            System.setProperty("java.library.path", jdkBinPath);
        }

        logger.info("设置JDK环境变量:");
        logger.info("JAVA_HOME: {}", JDK17_HOME);
        logger.info("JDK bin路径: {}", jdkBinPath);
    }

    @AfterEach
    void tearDown() {
        if (mcpClient != null) {
            mcpClient.closeGracefully();
        }
    }

    @Test
    void testInitialize() {
        logger.info("开始测试MCP客户端初始化...");

        try {
            // 初始化客户端
            mcpClient.initialize();
            logger.info("MCP客户端初始化成功");
        } catch (Exception e) {
            logger.error("MCP客户端初始化失败", e);
            throw e;
        }
    }

    @Test
    void testListTools() {
        logger.info("开始测试工具列表获取...");

        try {
            // 初始化客户端
            mcpClient.initialize();

            // 获取可用工具列表
            ListToolsResult toolsList = mcpClient.listTools();

            logger.info("获取到 {} 个工具:", toolsList.tools().size());
            toolsList.tools().forEach(tool -> {
                logger.info("工具名称: {}", tool.name());
                logger.info("工具描述: {}", tool.description());
                logger.info("工具参数: {}", tool.inputSchema());
                logger.info("---");
            });

        } catch (Exception e) {
            logger.error("获取工具列表失败", e);
            throw e;
        }
    }

    @Test
    void testParseMapper() {
        logger.info("开始测试parse_mapper工具...");

        try {
            // 初始化客户端
            mcpClient.initialize();

            // 调用parse_mapper工具
            CallToolResult result = mcpClient.callTool(
                    new CallToolRequest("parse_mapper",
                            Map.of("filePath", "src/test/resources/test-mapper.xml")));

            logger.info("parse_mapper执行结果:");
            logger.info("结果: {}", result.content());

        } catch (Exception e) {
            logger.error("parse_mapper工具调用失败", e);
            throw e;
        }
    }

    @Test
    void testParseMapperAndMock() {
        logger.info("开始测试parse_mapper_and_mock工具...");

        try {
            // 初始化客户端
            mcpClient.initialize();

            // 调用parse_mapper_with_mock工具
            CallToolResult result = mcpClient.callTool(
                    new CallToolRequest("parse_mapper_and_mock",
                            Map.of(
                                    "filePath", "src/test/resources/test-mapper.xml")));

            logger.info("parse_mapper_and_mock执行结果:");
            logger.info("结果: {}", result.content());

        } catch (Exception e) {
            logger.error("parse_mapper_and_mock工具调用失败", e);
            throw e;
        }
    }

    @Test
    void testParseMapperAndRunTest() {
        logger.info("开始测试parse_mapper_and_run_test工具...");

        try {
            // 初始化客户端
            mcpClient.initialize();

            // 调用parse_mapper_and_run_test工具
            CallToolResult result = mcpClient.callTool(
                    new CallToolRequest("parse_mapper_and_run_test",
                            Map.of("filePath", "src/test/resources/test-mapper.xml")));

            logger.info("parse_mapper_and_run_test执行结果:");
            logger.info("结果: {}", result.content());

        } catch (Exception e) {
            logger.error("parse_mapper_and_run_test工具调用失败", e);
            throw e;
        }
    }

    @Test
    void testAllTools() {
        logger.info("开始测试所有工具...");

        try {
            // 初始化客户端
            mcpClient.initialize();

            // 获取工具列表
            ListToolsResult toolsList = mcpClient.listTools();
            logger.info("可用工具数量: {}", toolsList.tools().size());

            // 测试每个工具
            for (var tool : toolsList.tools()) {
                logger.info("测试工具: {}", tool.name());

                try {
                    CallToolResult result = mcpClient.callTool(
                            new CallToolRequest(tool.name(),
                                    Map.of("filePath", "src/test/resources/test-mapper.xml")));

                    logger.info("工具 {} 执行成功: {}", tool.name(), result.content());

                } catch (Exception e) {
                    logger.error("工具 {} 执行失败: {}", tool.name(), e.getMessage());
                }

                logger.info("---");
            }

        } catch (Exception e) {
            logger.error("测试所有工具失败", e);
            throw e;
        }
    }
}
