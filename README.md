# Create Shining Stage · Forge 1.20.1

基础模组开发工程，使用官方 Forge MDK。当前只包含模组入口，尚未移植其他版本的功能，也未添加 Create 等扩展依赖。

## 环境

- Minecraft：1.20.1
- Forge：47.4.28
- Java：JDK 17
- Gradle：8.8，通过项目自带的 Wrapper 使用，无需全局安装
- 模组 ID：`create_shining_stage`
- Java 包：`com.shiningstage`

`gradle.properties` 中已为本机设置 `org.gradle.java.home=D:/Program Files/Java/jdk-17`。换电脑时修改或删除此项，并将 `JAVA_HOME` 和 IDE 的 Gradle JVM 设置为 JDK 17。

## 常用命令

在项目根目录打开 PowerShell：

```powershell
# 构建，产物位于 build/libs/
.\gradlew.bat build

# 启动开发客户端
.\gradlew.bat runClient

# 启动开发服务端，首次运行需自行阅读并接受 Minecraft EULA
.\gradlew.bat runServer

# 运行数据生成器
.\gradlew.bat runData

# 生成 IntelliJ IDEA 运行配置
.\gradlew.bat genIntellijRuns

# 生成 Eclipse 运行配置
.\gradlew.bat genEclipseRuns
```

使用 IntelliJ IDEA 打开本目录，以 Gradle 工程导入，将 Gradle JVM 设置为 JDK 17。生成运行配置后可选择 `runClient` 调试。

## 目录

- `src/main/java/com/shiningstage/`：Java 源代码
- `src/main/resources/META-INF/mods.toml`：模组元数据与依赖声明
- `src/main/resources/assets/create_shining_stage/`：后续添加客户端资源的位置
- `src/main/resources/data/create_shining_stage/`：后续添加配方、标签等数据的位置
- `src/generated/resources/`：数据生成输出
- `gradle.properties`：版本、名称、作者等配置

Forge 下载：https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html

Forge 文档：https://docs.minecraftforge.net/en/1.20.1/gettingstarted/
