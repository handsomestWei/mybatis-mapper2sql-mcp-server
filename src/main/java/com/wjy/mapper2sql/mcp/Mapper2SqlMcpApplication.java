package com.wjy.mapper2sql.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * MyBatis Mapper2SQL MCP 服务启动类
 *
 * @see <a href="https://modelcontextprotocol.io/quickstart/server#java">MCP
 *      Server Quickstart</a>
 *
 * @see <a href=
 *      "https://github.com/spring-projects/spring-ai-examples/tree/main/model-context-protocol/weather/starter-stdio-server">spring-ai
 *      starter-stdio-server
 *      demo</a>
 *
 * @author handsomestWei
 * @version 1.0.0
 */
@SpringBootApplication
public class Mapper2SqlMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(Mapper2SqlMcpApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider mapper2SqlTools(Mapper2SqlMcpService mapper2SqlMcpService) {
        return MethodToolCallbackProvider.builder().toolObjects(mapper2SqlMcpService).build();
    }
}
