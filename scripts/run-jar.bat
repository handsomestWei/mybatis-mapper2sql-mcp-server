@echo off
setlocal enabledelayedexpansion
echo 使用JDK 17运行fat jar文件（支持JDBC配置）...

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

REM 检查jar文件是否存在
if not exist "target\mapper2sql-mcp-server-1.0.0.jar" (
    echo 错误：找不到jar文件，请先构建项目
    echo 期望的jar文件路径: target\mapper2sql-mcp-server-1.0.0.jar
    echo.
    echo 请运行以下命令构建项目：
    echo scripts\build.bat
    pause
    exit /b 1
)

echo.
echo 找到jar文件: target\mapper2sql-mcp-server-1.0.0.jar

REM 构建Java命令参数
set JAVA_OPTS=

REM 检查命令行参数并添加到JAVA_OPTS
if not "%1"=="" (
    echo 检测到命令行参数，将作为系统属性传递给Java
    echo 参数: %*
    set JAVA_OPTS=%*
)

REM 如果没有命令行参数，检查环境变量
if "%JAVA_OPTS%"=="" (
    echo 未提供命令行参数，检查环境变量...

    REM 检查环境变量并构建参数
    if not "%DB_TYPE%"=="" (
        set JAVA_OPTS=-DdbType=%DB_TYPE%
    )
    if not "%JDBC_DRIVER%"=="" (
        if not "%JAVA_OPTS%"=="" (
            set JAVA_OPTS=%JAVA_OPTS% -DjdbcDriverClass=%JDBC_DRIVER%
        ) else (
            set JAVA_OPTS=-DjdbcDriverClass=%JDBC_DRIVER%
        )
    )
    if not "%JDBC_DRIVER_JAR%"=="" (
        if not "%JAVA_OPTS%"=="" (
            set JAVA_OPTS=%JAVA_OPTS% -DjdbcDriverJar=%JDBC_DRIVER_JAR%
        ) else (
            set JAVA_OPTS=-DjdbcDriverJar=%JDBC_DRIVER_JAR%
        )
    )
    if not "%JDBC_URL%"=="" (
        if not "%JAVA_OPTS%"=="" (
            set JAVA_OPTS=%JAVA_OPTS% -DjdbcUrl=%JDBC_URL%
        ) else (
            set JAVA_OPTS=-DjdbcUrl=%JDBC_URL%
        )
    )
    if not "%DB_USERNAME%"=="" (
        if not "%JAVA_OPTS%"=="" (
            set JAVA_OPTS=%JAVA_OPTS% -DuserName=%DB_USERNAME%
        ) else (
            set JAVA_OPTS=-DuserName=%DB_USERNAME%
        )
    )
    if not "%DB_PASSWORD%"=="" (
        if not "%JAVA_OPTS%"=="" (
            set JAVA_OPTS=%JAVA_OPTS% -Dpassword=%DB_PASSWORD%
        ) else (
            set JAVA_OPTS=-Dpassword=%DB_PASSWORD%
        )
    )
)

REM 显示配置信息
if not "%JAVA_OPTS%"=="" (
    echo.
    echo 使用以下配置启动服务器：
    echo %JAVA_OPTS%
) else (
    echo.
    echo 警告：未提供JDBC配置参数，服务器将以基础模式运行
    echo.
    echo 使用方法：
    echo 1. 命令行参数方式：
    echo    scripts\run-jar.bat -DdbType=mysql -DjdbcDriver=com.mysql.cj.jdbc.Driver -DjdbcDriverJar=D:\\apache-maven-repo\\com\\mysql\\mysql-connector-j\\8.0.33\\mysql-connector-j-8.0.33.jar -DjdbcUrl=jdbc:mysql://localhost:3306/testdb -DuserName=root -Dpassword=password
    echo.
    echo 2. 环境变量方式：
    echo    set DB_TYPE=mysql
    echo    set JDBC_DRIVER=com.mysql.cj.jdbc.Driver
    echo    set JDBC_DRIVER_JAR=D:\\apache-maven-repo\\com\\mysql\\mysql-connector-j\\8.0.33\\mysql-connector-j-8.0.33.jar
    echo    set JDBC_URL=jdbc:mysql://localhost:3306/testdb
    echo    set DB_USERNAME=root
    echo    set DB_PASSWORD=password
    echo    scripts\run-jar.bat
    echo.
)

echo.
echo 开始运行MCP服务器...

REM 运行fat jar文件（使用-jar参数）
if not "%JAVA_OPTS%"=="" (
    java %JAVA_OPTS% -jar target\mapper2sql-mcp-server-1.0.0.jar
) else (
    java -jar target\mapper2sql-mcp-server-1.0.0.jar
)

echo.
echo MCP服务器已停止
pause
