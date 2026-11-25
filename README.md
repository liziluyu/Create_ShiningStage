# Create Shining Stage · Forge 1.20.1

Create 舞台设备扩展的 Forge 1.20.1 移植工程，包含聚光灯、补光灯、支架与桁架、麦克风与音响、冷焰火机和原点位。保留 Create 运动结构适配，使用原生着色器与客户端独立虚拟光照层，支持可选 AE2 桁架面板和 Oculus 渲染桥接。

功能范围、API 差异、光照设计和分阶段实施安排见[Forge 1.20.1 功能移植方案](docs/forge-1.20.1-porting-plan.md)。

2026-11-25：移植代码已通过 `compileJava` 和 `classes`（含资源处理与 Mixin refmap 生成）。本次按要求仅进行编译，未运行其他测试或启动游戏；运行行为、光影与可选集成尚待游戏内验收。

## 环境

- Minecraft：1.20.1
- Forge：47.4.0，最低依赖范围 `[47.4.0,48)`
- Java：JDK 17
- Gradle：8.8，通过项目自带的 Wrapper 使用，无需全局安装
- 模组 ID：`create_shining_stage`
- Java 包：`com.shiningstage.create_shining_stage`

`gradle.properties` 中已为本机设置 `org.gradle.java.home=D:/Program Files/Java/jdk-17`。换电脑时修改或删除此项，并将 `JAVA_HOME` 和 IDE 的 Gradle JVM 设置为 JDK 17。

## 依赖与开发运行时

- 必需依赖：Create 6.0.8（开发构件 `6.0.8-291:slim`）。
- 配套库：Ponder 1.0.91、Flywheel 1.0.6-beta-266、Registrate MC1.20-1.3.3、MixinExtras 0.4.1，与 Create 完整构件内嵌版本一致。
- 可选集成编译依赖：AE2 15.4.12、Oculus 1.8.0。
- 客户端、服务端及数据生成的开发运行时：AE2 15.4.12、GuideME 20.1.15。
- 仅开发客户端加载：JEI 15.20.0.106、Jade 11.13.3、Oculus 1.8.0、Embeddium 0.3.31。

以上库和模组均由 Gradle 管理，无需手动复制到 `run/mods`。本模组不打包这些依赖，AE2、GuideME、Oculus、Embeddium、JEI 和 Jade 不会成为本模组的强制依赖。用户端的 Create 完整构件自带其配套库。工程不依赖 Sable、Aeronautics 或 Veil。

客户端专用模组通过 ForgeGradle 的客户端 classpath token 加载，不进入服务端或数据生成运行配置。用 `.\gradlew.bat runClient -Penable_client_mods=false` 可启动不带这组客户端模组的基线环境；AE2 和 GuideME 仍会加载。修改此开关后，IDE 运行配置需重新生成。

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

- `src/main/java/com/shiningstage/create_shining_stage/`：Java 源代码
- `src/main/resources/META-INF/mods.toml`：模组元数据与依赖声明
- `src/main/resources/assets/create_shining_stage/`：客户端模型、纹理、翻译与着色器
- `src/main/resources/data/create_shining_stage/`：掉落表等服务端数据
- `src/generated/resources/`：数据生成输出
- `gradle.properties`：版本、名称、作者等配置

Forge 下载：https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html

Forge 文档：https://docs.minecraftforge.net/en/1.20.1/gettingstarted/
