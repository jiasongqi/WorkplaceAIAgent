@echo off
REM ============================================
REM  agent_product 专用编译脚本 (需要 JDK 21)
REM  不影响系统全局 JAVA_HOME
REM ============================================

set "JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo [INFO] 使用 JDK: %JAVA_HOME%
java -version

echo.
echo [INFO] 开始编译...
call "%~dp0mvnw.cmd" %*
