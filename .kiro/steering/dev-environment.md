---
inclusion: auto
---

# 开发环境信息

## Java 环境

- JDK 路径：`C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot`
- 启动后端时必须设置 JAVA_HOME 为上述路径
- 启动命令：`$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot"; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"; mvn spring-boot:run -DskipTests`

## 前端环境

- 前端目录：`yu-ai-agent-frontend`
- 启动命令：`npm run dev`
- 访问地址：http://localhost:3000/

## 后端环境

- 后端端口：8123
- context-path：/api
- 启动后完整地址：http://localhost:8123/api
