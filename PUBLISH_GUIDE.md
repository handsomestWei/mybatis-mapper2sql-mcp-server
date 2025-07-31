# 发布到 mcp.so 市场指南

本指南将帮助你将 MyBatis Mapper2SQL MCP Server 发布到 [mcp.so](https://mcp.so) 市场。

## 准备工作

### 1. 构建项目
```bash
# 在 mcp-server 目录下
chmod +x build.sh
./build.sh
```

### 2. 验证构建结果
确保以下文件存在：
- `target/mapper2sql-mcp-server-1.0.0.jar`
- `mcp-registry.json`
- `README.md`

### 3. 测试MCP服务器
```bash
chmod +x test-mcp.sh
./test-mcp.sh
```

## 发布步骤

### 1. 创建 GitHub Release

1. 在 GitHub 仓库中创建新的 Release
2. 版本号：`v1.0.0`
3. 上传以下文件：
   - `mapper2sql-mcp-server-1.0.0.jar`
   - `mcp-registry.json`
   - `README.md`

### 2. 更新 mcp-registry.json

确保 `mcp-registry.json` 中的下载链接指向正确的 GitHub Release：

```json
{
  "installation": {
    "downloadUrl": "https://github.com/handsomestWei/mybatis-mapper2sql-mcp-server/releases/download/v1.0.0/mapper2sql-mcp-server-1.0.0.jar"
  }
}
```

### 3. 提交到 mcp.so

1. 访问 [mcp.so](https://mcp.so)
2. 点击 "Submit Server"
3. 填写以下信息：
   - **Name**: mapper2sql
   - **Description**: Extract SQL from MyBatis mapper XML files with parameter mocking and testing capabilities
   - **Repository**: https://github.com/handsomestWei/mybatis-mapper2sql-mcp-server
   - **Registry File**: 上传 `mcp-registry.json`
   - **Documentation**: https://github.com/handsomestWei/mybatis-mapper2sql-mcp-server/blob/main/README.md

### 4. 等待审核

mcp.so 团队会审核你的提交，通常需要 1-3 个工作日。

## 发布后验证

### 1. 在 mcp.so 上搜索
搜索 "mapper2sql" 确认服务器已成功发布。

### 2. 测试集成
使用支持 MCP 的客户端（如 Claude Desktop）测试服务器功能。

### 3. 收集反馈
关注用户反馈，及时更新和改进。

## 版本更新

### 1. 更新版本号
- `pom.xml` 中的版本号
- `mcp-registry.json` 中的版本号
- GitHub Release 标签

### 2. 更新文档
- 更新 `README.md`
- 更新 `mcp-registry.json` 中的示例和描述

### 3. 重新发布
按照上述步骤重新发布新版本。

## 常见问题

### Q: 如何确保 MCP 服务器兼容性？
A: 确保使用标准的 MCP SDK 和协议，测试所有工具的功能。

### Q: 如何处理用户反馈？
A: 在 GitHub Issues 中跟踪用户反馈，及时响应和修复问题。

### Q: 如何推广我的 MCP 服务器？
A:
- 在相关技术社区分享
- 写博客文章介绍功能
- 在 GitHub 上添加详细的使用示例
