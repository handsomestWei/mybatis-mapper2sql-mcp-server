FROM openjdk:11-jre-slim

# 设置工作目录
WORKDIR /app

# 复制jar文件
COPY target/mapper2sql-mcp-server-1.0.0.jar app.jar

# 创建非root用户
RUN addgroup --system appgroup && \
    adduser --system --ingroup appgroup appuser

# 设置权限
RUN chown -R appuser:appgroup /app
USER appuser

# 暴露端口（如果需要HTTP接口）
EXPOSE 8080

# 设置JVM参数
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
