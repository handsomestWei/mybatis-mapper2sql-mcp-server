@echo off
echo 使用JDK 17运行项目...

REM 获取脚本所在目录的上级目录（项目根目录）
set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR%..
cd /d "%PROJECT_ROOT%"

echo 当前工作目录: %CD%

REM 设置JDK 17路径（请根据实际安装路径修改）
set JDK17_HOME=C:\Program Files\Java\jdk17.0.16

REM 检查JDK 17是否存在
if not exist "%JDK17_HOME%" (
    echo 错误：找不到JDK 17安装路径: %JDK17_HOME%
    echo 请修改脚本中的JDK17_HOME变量为正确的安装路径
    pause
    exit /b 1
)

echo 找到JDK 17: %JDK17_HOME%

REM 临时设置JAVA_HOME和PATH
set JAVA_HOME=%JDK17_HOME%
set PATH=%JDK17_HOME%\bin;%PATH%

echo 当前Java版本:
java -version

echo.
echo 开始运行项目...
mvn exec:java -Dexec.mainClass="com.wjy.mapper2sql.mcp.Mapper2SqlMcpStdioServer"

echo.
echo 运行完成！
pause
