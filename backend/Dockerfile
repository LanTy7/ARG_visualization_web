# 使用 Eclipse Temurin OpenJDK 17 作为基础镜像，这是一个稳定且广泛使用的版本
FROM eclipse-temurin:17-jdk-jammy

# 设置容器的工作目录
WORKDIR /app

# 将 Maven 构建出的 JAR 文件复制到容器中，并重命名为 app.jar
# ARG 指令定义了一个变量，可以在 docker build 命令中使用 --build-arg 来传递
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

# 暴露 Spring Boot 应用的默认端口 8080
EXPOSE 8080

# 容器启动时执行的命令
# 使用 exec 格式可以使得 java 应用作为 PID 1 运行，能更好地处理信号
ENTRYPOINT ["java", "-jar", "/app.jar"]
