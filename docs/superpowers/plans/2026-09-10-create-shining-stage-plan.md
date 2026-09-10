# Create Shining Stage 工作区搭建计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在 `E:/forgeMod/Create_ShingingStage` 下生成 NeoForge 1.21.1 模组工作区，配置国内镜像，并引入 Create 6 与 Create: Aeronautics 依赖，最终通过 `./gradlew build` 验证。

**架构：** 基于 NeoForge MDK（NeoGradle）文件结构，替换示例身份为 `create_shining_stage` / `com.shiningstage`，在 `settings.gradle`、`build.gradle`、`gradle-wrapper.properties` 中配置国内镜像，按 Create Wiki 与 Modrinth Maven 添加依赖，最后运行构建。

**Tech Stack：** Java 21、Gradle 9.2.1、NeoGradle 7.1.38、NeoForge 21.1.235、Parchment 2024.11.17、Create 6.0.10、Create: Aeronautics 1.3.0。

---

## Task 1: 创建 Gradle 项目骨架与属性文件

**Files:**
- Create: `gradle.properties`
- Create: `settings.gradle`
- Create: `build.gradle`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradlew`、`gradlew.bat`、`gradle/wrapper/gradle-wrapper.jar`
- Create: `.gitattributes`、`.gitignore`

- [ ] **Step 1.1: 写入 `gradle.properties`**

```properties
# Sets default memory used for gradle commands. Can be overridden by user or command line properties.
org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true

# Parchment mappings
neogradle.subsystems.parchment.minecraftVersion=1.21.1
neogradle.subsystems.parchment.mappingsVersion=2024.11.17

# Environment Properties
minecraft_version=1.21.1
minecraft_version_range=[1.21.1]
neo_version=21.1.235
loader_version_range=[1,)

# Create dependency versions
create_version=6.0.10-280
ponder_version=1.0.82
flywheel_version=1.0.6
registrate_version=MC1.21-1.3.0+67

# Mod Properties
mod_id=create_shining_stage
mod_name=Create Shining Stage
mod_license=All Rights Reserved
mod_version=1.0.0
mod_group_id=com.shiningstage
```

- [ ] **Step 1.2: 写入 `settings.gradle`（配置插件仓库镜像）**

```groovy
pluginManagement {
    repositories {
        maven { url = 'https://maven.aliyun.com/repository/gradle-plugin' }
        gradlePluginPortal()
        maven { url = 'https://maven.neoforged.net/releases' }
    }
}

plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
}

rootProject.name = 'create_shining_stage'
```

- [ ] **Step 1.3: 写入 `build.gradle`（仓库 + 依赖 + 资源处理）**

```groovy
plugins {
    id 'java-library'
    id 'maven-publish'
    id 'net.neoforged.gradle.userdev' version '7.1.38'
}

tasks.named('wrapper', Wrapper).configure {
    distributionType = Wrapper.DistributionType.BIN
}

version = mod_version
group = mod_group_id

sourceSets.main.resources {
    srcDir('src/generated/resources')
    exclude("**/*.bbmodel")
    exclude("src/generated/**/.cache")
}

base {
    archivesName = mod_id
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

repositories {
    maven { url = 'https://maven.aliyun.com/repository/public' }
    maven { url = 'https://maven.neoforged.net/releases' }
    maven { url = 'https://maven.createmod.net' }
    maven { url = 'https://maven.ithundxr.dev/snapshots' }
    maven {
        name = 'Modrinth'
        url = 'https://api.modrinth.com/maven'
    }
}

configurations {
    runtimeClasspath.extendsFrom localRuntime
}

dependencies {
    implementation "net.neoforged:neoforge:${neo_version}"

    // Create dependencies
    implementation("com.simibubi.create:create-${minecraft_version}:${create_version}:slim") { transitive = false }
    implementation "net.createmod.ponder:ponder-neoforge:${ponder_version}+mc${minecraft_version}"
    compileOnly "dev.engine-room.flywheel:flywheel-neoforge-api-${minecraft_version}:${flywheel_version}"
    runtimeOnly "dev.engine-room.flywheel:flywheel-neoforge-${minecraft_version}:${flywheel_version}"
    implementation "com.tterrag.registrate:Registrate:${registrate_version}"

    // Create: Aeronautics
    implementation "maven.modrinth:create-aeronautics:1.3.0+mc1.21.1"
}

runs {
    configureEach {
        systemProperty 'forge.logging.markers', 'REGISTRIES'
        systemProperty 'forge.logging.console.level', 'debug'
        workingDirectory project.layout.projectDirectory.dir('run').dir(name)
        modSource project.sourceSets.main
    }

    client {
        systemProperty 'neoforge.enabledGameTestNamespaces', project.mod_id
    }

    server {
        systemProperty 'neoforge.enabledGameTestNamespaces', project.mod_id
        argument '--nogui'
    }

    gameTestServer {
        systemProperty 'neoforge.enabledGameTestNamespaces', project.mod_id
    }

    data {
        arguments.addAll '--mod', project.mod_id, '--all', '--output', file('src/generated/resources/').getAbsolutePath(), '--existing', file('src/main/resources/').getAbsolutePath()
    }
}

tasks.withType(ProcessResources).configureEach {
    var replaceProperties = [
            minecraft_version      : minecraft_version,
            minecraft_version_range: minecraft_version_range,
            neo_version            : neo_version,
            loader_version_range   : loader_version_range,
            mod_id                 : mod_id,
            mod_name               : mod_name,
            mod_license            : mod_license,
            mod_version            : mod_version,
    ]
    inputs.properties replaceProperties

    filesMatching(['META-INF/neoforge.mods.toml']) {
        expand replaceProperties
    }
}

publishing {
    publications {
        register('mavenJava', MavenPublication) {
            from components.java
        }
    }
    repositories {
        maven {
            url "file://${project.projectDir}/repo"
        }
    }
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}

idea {
    module {
        downloadSources = true
        downloadJavadoc = true
    }
}
```

- [ ] **Step 1.4: 写入 `gradle-wrapper.properties`（使用腾讯云 Gradle 镜像）**

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-9.2.1-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 1.5: 下载并放置 wrapper 脚本与 jar**

从 NeoForge MDK 仓库 `NeoForgeMDKs/MDK-1.21.1-NeoGradle` 的对应提交复制：
- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`

并创建 `.gitattributes` 与 `.gitignore`（与 MDK 一致）。

Run: `git add gradle.properties settings.gradle build.gradle gradle/wrapper/gradle-wrapper.properties gradlew gradlew.bat gradle/wrapper/gradle-wrapper.jar .gitattributes .gitignore`

---

## Task 2: 创建源码与资源文件

**Files:**
- Create: `src/main/java/com/shiningstage/create_shining_stage/CreateShiningStage.java`
- Create: `src/main/java/com/shiningstage/create_shining_stage/CreateShiningStageClient.java`
- Create: `src/main/java/com/shiningstage/create_shining_stage/Config.java`
- Create: `src/main/resources/META-INF/neoforge.mods.toml`
- Create: `src/main/resources/assets/create_shining_stage/lang/en_us.json`

- [ ] **Step 2.1: 写入主类 `CreateShiningStage.java`**

```java
package com.shiningstage.create_shining_stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(CreateShiningStage.MOD_ID)
public class CreateShiningStage {
    public static final String MOD_ID = "create_shining_stage";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public CreateShiningStage(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Create Shining Stage loaded");
    }
}
```

- [ ] **Step 2.2: 写入客户端类 `CreateShiningStageClient.java`**

```java
package com.shiningstage.create_shining_stage;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(value = CreateShiningStage.MOD_ID, dist = Dist.CLIENT)
public class CreateShiningStageClient {
    public CreateShiningStageClient(IEventBus modEventBus) {
    }
}
```

- [ ] **Step 2.3: 写入 `Config.java`**

```java
package com.shiningstage.create_shining_stage;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = CreateShiningStage.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec SPEC = BUILDER.build();

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
    }
}
```

- [ ] **Step 2.4: 写入 `neoforge.mods.toml`**

```toml
modLoader="javafml"
loaderVersion="${loader_version_range}"
license="${mod_license}"

[[mods]]
modId="${mod_id}"
version="${mod_version}"
displayName="${mod_name}"
description='''
A Create addon powered by Create and Create: Aeronautics.
'''

[[dependencies.${mod_id}]]
    modId="neoforge"
    type="required"
    versionRange="[${neo_version},)"
    ordering="NONE"
    side="BOTH"

[[dependencies.${mod_id}]]
    modId="minecraft"
    type="required"
    versionRange="${minecraft_version_range}"
    ordering="NONE"
    side="BOTH"

[[dependencies.${mod_id}]]
    modId="create"
    type="required"
    versionRange="[6.0.10,6.1.0)"
    ordering="NONE"
    side="BOTH"

[[dependencies.${mod_id}]]
    modId="aeronautics_bundled"
    type="required"
    versionRange="[1.3.0,1.4.0)"
    ordering="NONE"
    side="BOTH"
```

- [ ] **Step 2.5: 写入 `en_us.json`**

```json
{
  "itemGroup.create_shining_stage": "Create Shining Stage"
}
```

Run: `git add src/main/java/com/shiningstage/create_shining_stage/ src/main/resources/`

---

## Task 3: 配置国内镜像并准备运行

- [ ] **Step 3.1: 确认 wrapper 与镜像**

检查 `gradle/wrapper/gradle-wrapper.properties` 中的 `distributionUrl` 指向腾讯云镜像。

- [ ] **Step 3.2: 确认仓库顺序**

检查 `settings.gradle` 与 `build.gradle` 中阿里云镜像已前置。

---

## Task 4: 验证构建

- [ ] **Step 4.1: 运行 Gradle 构建**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL，并在 `build/libs/` 生成 `create_shining_stage-1.0.0.jar`。

- [ ] **Step 4.2: 处理潜在失败**

若 `api.modrinth.com/maven` 无法解析 Create: Aeronautics，则在 `build.gradle` 的 `repositories` 中追加 CurseMaven：

```groovy
maven { url = 'https://cursemaven.com' }
```

并将依赖改为：

```groovy
implementation "curse.maven:create-aeronautics-676721:8240058"
```

---

## Task 5: 提交

- [ ] **Step 5.1: 提交初始工作区**

```bash
git add -A
git commit -m "chore: scaffold NeoForge 1.21.1 workspace with Create 6 and Create: Aeronautics"
```
