# Create Shining Stage 模组工作区设计

**目标：** 在 `E:/forgeMod/Create_ShingingStage` 目录下搭建一个可用于 NeoForge 1.21.1 的模组开发工作区，并正确引入 Create 6 与 Create: Aeronautics 作为依赖。

**方案：** 基于 NeoForge 官方 MDK（NeoGradle 7.1.38）生成项目骨架，替换示例 ID/组名，配置国内镜像加速 Gradle 与 Maven 下载，再按 Create 官方文档与 Modrinth Maven 添加两个依赖。

**技术栈：** Java 21、Gradle 9.2.1、NeoGradle 7.1.38、NeoForge 21.1.235、Parchment 映射。

---

## 1. 项目身份

| 项 | 值 |
|---|---|
| mod_id | `create_shining_stage` |
| Maven group | `com.shiningstage` |
| 显示名称 | `Create Shining Stage` |
| 初始版本 | `1.0.0` |
| 协议 | `All Rights Reserved`（保留示例，后续可改） |

## 2. 版本选择

| 组件 | 版本 | 来源 |
|---|---|---|
| Minecraft | 1.21.1 | NeoForge MDK |
| NeoForge | 21.1.235 | MDK 默认，对应 1.21.1 的稳定版本 |
| NeoGradle | 7.1.38 | MDK 默认 |
| Gradle Wrapper | 9.2.1 | MDK 默认 |
| Java Toolchain | 21 | NeoForge 1.21.1 要求 |
| Parchment | 2024.11.17 | MDK 默认 |
| Create | 6.0.10-280 | Create Maven (`maven.createmod.net`)，对应发布版 6.0.10 |
| Ponder | 1.0.82 | Create 依赖 |
| Flywheel | 1.0.6 | Create 依赖 |
| Registrate | MC1.21-1.3.0+67 | Create 依赖 |
| Create: Aeronautics | 1.3.0+mc1.21.1 | Modrinth Maven (`api.modrinth.com/maven`) |

## 3. 仓库与镜像策略

由于网络环境在国内，采用以下策略：

- **Gradle 发行版**：使用腾讯云镜像 `https://mirrors.cloud.tencent.com/gradle/gradle-9.2.1-bin.zip`，避免 `services.gradle.org` 下载缓慢或失败。
- **Gradle 插件仓库**：在 `settings.gradle` 的 `pluginManagement` 中保留 `gradlePluginPortal()`，并前置阿里云 Gradle 插件镜像 `https://maven.aliyun.com/repository/gradle-plugin` 与 NeoForge 插件仓库 `https://maven.neoforged.net/releases`。
- **项目依赖仓库**：在 `build.gradle` 的 `repositories` 中前置阿里云公共镜像 `https://maven.aliyun.com/repository/public`，再依次加入：
  - `https://maven.neoforged.net/releases`（NeoForge 本体）
  - `https://maven.createmod.net`（Create、Ponder、Flywheel）
  - `https://maven.ithundxr.dev/snapshots`（Registrate）
  - `https://api.modrinth.com/maven`（Create: Aeronautics）
- 若 Modrinth 解析失败，可回退到 CurseMaven `https://cursemaven.com` 的 `curse.maven:create-aeronautics-676721:8240058`。

## 4. 依赖配置

### 4.1 Create 开发依赖

按 Create Wiki 的 NeoForge 1.21.1 指引配置：

```groovy
implementation("com.simibubi.create:create-${minecraft_version}:${create_version}:slim") { transitive = false }
implementation("net.createmod.ponder:ponder-neoforge:${ponder_version}+mc${minecraft_version}")
compileOnly("dev.engine-room.flywheel:flywheel-neoforge-api-${minecraft_version}:${flywheel_version}")
runtimeOnly("dev.engine-room.flywheel:flywheel-neoforge-${minecraft_version}:${flywheel_version}")
implementation("com.tterrag.registrate:Registrate:${registrate_version}")
```

### 4.2 Create 生产依赖

在 `neoforge.mods.toml` 中声明：

```toml
[[dependencies.create_shining_stage]]
    modId="create"
    type="required"
    versionRange="[6.0.10,6.1.0)"
    ordering="NONE"
    side="BOTH"
```

### 4.3 Create: Aeronautics 依赖

```groovy
implementation("maven.modrinth:create-aeronautics:1.3.0+mc1.21.1")
```

并在 `neoforge.mods.toml` 中声明：

```toml
[[dependencies.create_shining_stage]]
    modId="aeronautics_bundled"
    type="required"
    versionRange="[1.3.0,1.4.0)"
    ordering="NONE"
    side="BOTH"
```

> 注：Create: Aeronautics 的 bundled 版本 modId 为 `aeronautics_bundled`，见已发布的 jar 与社区日志。

## 5. 目录结构

```
Create_ShingingStage/
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── src/
│   ├── main/
│   │   ├── java/com/shiningstage/
│   │   │   └── create_shining_stage/
│   │   │       ├── Config.java
│   │   │       ├── CreateShiningStage.java
│   │   │       └── CreateShiningStageClient.java
│   │   └── resources/
│   │       ├── META-INF/neoforge.mods.toml
│   │       └── assets/create_shining_stage/lang/en_us.json
│   └── generated/resources/ (数据生成输出，初始为空)
├── build.gradle
├── gradle.properties
├── settings.gradle
├── gradlew
├── gradlew.bat
├── .gitattributes
└── .gitignore
```

## 6. 验证

完成文件创建后，执行：

```bash
./gradlew build
```

预期结果：Gradle 能成功从国内镜像与外部 Maven 下载依赖，编译无错误，并在 `build/libs/` 生成 `create_shining_stage-1.0.0.jar`。

## 7. 风险与回退

- **CurseMaven / Modrinth 在国内不稳定**：若下载 Create: Aeronautics 失败，可改为在 `libs/` 目录放置手动下载的 jar，并使用 `implementation files("libs/...")`。
- **NeoForge Maven 不稳定**：若 `maven.neoforged.net` 无法访问，可考虑使用 BMCLAPI 等国内 Minecraft 资源镜像，或临时通过 VPN/代理。
- **Create 子依赖版本冲突**：严格按 Create Wiki 的 `gradle.properties` 版本号填写，避免混用不同构建号。
