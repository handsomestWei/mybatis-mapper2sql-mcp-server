# MyBatis Mapper2SQL MCP Server

这是一个基于Model Context Protocol (MCP)的MyBatis Mapper XML SQL提取服务。

## 概述

本项目将原有的 [MyBatis Mapper2SQL](https://github.com/handsomestWei/mybatis-mapper2sql) 工具，转型为 MCP (Model Context Protocol) 服务，使其能够在 AI 时代继续发挥价值，并与 AI 模型协作提供更强大的 SQL 提取和分析能力。

基于spring-ai, 提供stdio（stdin/stdout）模式，通过标准输入输出，进行进程间通信，适用于命令行工具集成。

## 功能特性

- **SQL提取**: 从MyBatis mapper XML文件中提取SQL语句
- **参数Mock**: 自动生成SQL参数，支持基于resultMap和JDBC连接的类型推断
- **SQL测试**: 连接数据库执行SQL并记录执行结果

## 核心优势

1. **确定性解析**: 基于 MyBatis 官方解析引擎，结果稳定可靠。
2. **AI 协作**: 作为 AI 的"专家工具"，提供精确的 SQL 解析能力。
3. **服务化**: 通过 MCP 协议提供标准化接口。
4. **扩展性**: 支持多种数据库类型。

## 可用工具
本项目提供3个专业工具，满足不同场景的SQL提取需求：

#### 1. parse_mapper
- **功能**: 基础 SQL 提取，保留占位符（不进行参数模拟）
- **适用场景**: 快速查看 SQL 结构，无需参数 mock
- **参数:**
  - `filePath` (string): mapper XML文件或目录路径

#### 2. parse_mapper_and_mock
- **功能**: SQL 提取 + 参数自动 mock
- **适用场景**: 需要可执行 SQL 进行测试或分析
- **参数:**
  - `filePath` (string): mapper XML文件或目录路径

#### 3. parse_mapper_and_run_test
- **功能**: SQL 提取 + 参数 mock + 执行测试
- **适用场景**: 验证 SQL 在真实数据库中的执行情况
- **参数:**
  - `filePath` (string): mapper XML文件或目录路径

## 使用说明

### 编译运行
[参考](/scripts/)

### MCP客户端配置
[参考](/mcp-config-example.json)

## 使用示例
参考[本地使用Trae MCP客户端调试说明](/doc/本地使用Trae%20MCP客户端调试说明.md)

### 基本SQL提取
```json
{
  "name": "parse_mapper",
  "arguments": {
    "filePath": "/path/to/mapper.xml"
  }
}
```

### 带参数Mock的SQL提取
```json
{
  "name": "parse_mapper_and_mock",
  "arguments": {
    "filePath": "/path/to/mapper.xml"
  }
}
```

### SQL测试
```json
{
  "name": "parse_mapper_and_run_test",
  "arguments": {
    "filePath": "/path/to/mapper.xml"
  }
}
```

## 参考
+ [spring-ai weather examples](https://github.com/spring-projects/spring-ai-examples/blob/main/model-context-protocol/weather/starter-stdio-server/README.md)
+ [modelcontextprotocol quickstart java server](https://modelcontextprotocol.io/quickstart/server#java)
