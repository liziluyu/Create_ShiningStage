# Create Shining Stage Forge 1.20.1 功能移植方案

将 `1.21.1neoforge` 分支的舞台设备功能移植到 `1.20.1forge` 分支。目标环境为 Minecraft 1.20.1、Forge 47.4.0、Java 17 和 Create 6.0.8。保留 Create 原版运动结构上的灯光与音频功能，移除 Sable、Aeronautics 及相关物理结构兼容代码；光柱和冷焰火改用 Forge 原生着色器，客户端照明沿用独立虚拟光照层。

方案日期：2026 年 11 月 25 日。

## 移植基线与当前进度

源代码基线为 `origin/1.21.1neoforge` 的提交 [`1e7f9178d10bccd295997532c3500c255b664966`](https://github.com/liziluyu/Create_ShiningStage/tree/1e7f9178d10bccd295997532c3500c255b664966)。源工程使用 NeoForge 21.1.235、Java 21 和 Create 6.0.10-280。

目标工作目录为 `E:\forgeMod\Create_ShingingStage-1.20.1`，分支为 `1.20.1forge`，模组 ID 保持 `create_shining_stage`。

| 项目 | 当前状态 |
| --- | --- |
| Forge 基础工程、模组入口与 Gradle Wrapper | 已建立 |
| Forge 编译基线与最低依赖 | 已设为 47.4.0，依赖范围为 `[47.4.0,48)` |
| Create 及配套库 | 已配置并完成依赖解析 |
| 可选集成与开发运行时模组 | 已配置，已核对 Forge 构件及客户端隔离 |
| IntelliJ IDEA 运行配置 | 已按当前依赖生成 |
| 源分支游戏功能 | 已落地 Forge 适配代码，运行表现待游戏内验收 |
| 移植后的编译、打包与游戏内验证 | `compileJava`、`classes` 已通过，含资源处理与 refmap 生成；打包与游戏内验证另行安排 |

依赖配置记录在根目录的 `gradle.properties`、`build.gradle` 和 `src/main/resources/META-INF/mods.toml`。依赖解析成功表示所选构件可获取，不等于功能或游戏兼容性已经验证。

## 功能范围

| 功能 | 保留的行为 | 主要实现位置 |
| --- | --- | --- |
| 聚光灯 | 安装方向与模型、染色、滚轮长度、红石控制光柱、照射落点照明 | `SpotlightBlock`、`SpotlightBlockEntity`、`SpotlightRenderer` |
| 补光灯 | 通电控制、服务器决定照射范围、灯体与落点的客户端照明 | `FillLightBlock`、`FillLightBlockEntity`、`StageLightingSettings` |
| 三脚架、杆与桁架 | 支撑与连接形态、舞台红石传导 | `TripodBlock`、`PoleBlock`、`TrussBlock`、`StageRedstoneNetwork`、`StageSignalSolver` |
| 桁架包覆动力部件 | 包覆传动杆、齿轮和大齿轮，保留动力与扳手拆壳 | `ModBlocks`、`TrussBlock` |
| 麦克风与音响 | 物品绑定、监听范围、通电播放、音响聚合、绑定提示 | `MicrophoneBlockEntity`、`SpeakerBlockEntity`、`SoundRelayHandler`、`SpeakerBindingOutliner` |
| 冷焰火机 | 单槽燃料、侧向自动输入、GUI、喷射高度、每个红石上升沿消耗一次燃料并喷射 | `ColdSparkMachineBlockEntity`、菜单、屏幕与渲染器 |
| 原点位 | 物品存取、展示与服务器白名单同步 | `PositionZeroBlockEntity`、`PositionZeroRenderer`、`PositionZeroItemPolicy` |
| Create 原版运动结构 | 灯具姿态与照明跟随，麦克风与音响在组装、移动和拆解时正确转接 | `SpotlightBeams`、运动行为、结构坐标转换 |
| AE2 可选集成 | 使用桁架直接包覆线缆，保留面板的模型方向处理 | `Ae2TrussFacade`、`TrussBlockItem` |
| 资源与配方 | 模型、纹理、中英文翻译、掉落、普通合成与机械合成 | 资源目录、`PoleModelProvider`、`ShiningStageRecipeProvider` |

不引入 Sable、Aeronautics、Sable Companion 或 Veil，不保留船体 plot 坐标、跨子世界射线及船体删除观察器。本次功能移植也不包含将 Minecraft 1.21.1 存档降级为 1.20.1 存档。

## 依赖与开发运行时

| 依赖 | 目标版本 | Gradle 用途 |
| --- | --- | --- |
| Forge | 47.4.0 | 编译与运行基线 |
| Create | `6.0.8-291:slim` | `implementation`；模组元数据要求 `[6.0.8,6.1.0)` |
| Ponder | 1.0.91 | `implementation`，Forge 构件 |
| Registrate | MC1.20-1.3.3 | `implementation` |
| Flywheel | 1.0.6-beta-266 | API 为 `compileOnly`，Forge 实现为 `runtimeOnly` |
| MixinExtras | 0.4.1 | `implementation` |
| AE2 | 15.4.12 | `compileOnly` 与开发专用 `localRuntime` |
| GuideME | 20.1.15 | 开发专用 `localRuntime`，满足 AE2 的运行依赖 |
| Oculus | 1.8.0 | `compileOnly` 与客户端专用 `clientRuntime` |
| Embeddium | 0.3.31 | 客户端专用 `clientRuntime` |
| JEI | 15.20.0.106 | 客户端专用 `clientRuntime`，用于配方检查 |
| Jade | 11.13.3 | 客户端专用 `clientRuntime`，用于方块信息检查 |

Create 的配套库版本与其完整构件的内嵌依赖一致。开发环境使用 `slim` 并显式提供这些库；本模组不重复打包它们。用户端使用 Create 完整构件提供配套库。AE2、GuideME 和客户端工具不会因此成为本模组的强制依赖。

Modrinth 上相同版本号可能同时对应 Fabric 和 Forge 构件。AE2 的 `15.4.12` 坐标曾解析到 Fabric 包，因此采用精确版本 ID 锁定：

| 模组 | Modrinth 版本 ID |
| --- | --- |
| AE2 15.4.12 Forge | `QRpVsqDI` |
| GuideME 20.1.15 Forge | `i7Tp1AHw` |
| Oculus 1.8.0 Forge | `iQ1SwGc3` |
| Embeddium 0.3.31 Forge | `UTbfe5d1` |
| Jade 11.13.3 Forge | `xJQHCmWJ` |

`localRuntime` 加入公共开发运行 classpath，因此客户端、服务端与数据生成均可使用 AE2 和 GuideME。`clientRuntime` 只通过客户端运行配置的 classpath token 加载，服务端、数据生成和 GameTest 配置不加载 Oculus、Embeddium、JEI 与 Jade。

`enable_client_mods=false` 可关闭客户端专用模组，供无光影基线验证使用；AE2 和 GuideME 仍在公共开发运行时中。验证 AE2 缺席场景时，还需使用不含这两项开发依赖的运行配置。运行时开关变化后应重新生成 IDE 运行配置。

## 平台与 API 适配

| 部分 | 1.21.1 NeoForge 实现 | 1.20.1 Forge 处理 |
| --- | --- | --- |
| 加载、事件与配置 | NeoForge 事件总线、客户端入口、配置注册 | 使用 Forge 对应接口，区分模组事件总线与游戏事件总线，保持客户端类隔离 |
| 注册 | NeoForge `DeferredRegister` 与注册对象 | 改为 Forge 注册接口，保留方块、物品和 BE 的注册 ID |
| 方块交互 | `useItemOn`、`useWithoutItem`、`ItemInteractionResult` | 合并到 `use`，重新检查物品交互返回值与执行顺序 |
| 方块编解码 | 方块 `MapCodec` 与 `codec()` | 按 1.20.1 基类接口移除或调整对应实现 |
| BE 序列化 | 带 `HolderLookup.Provider` 的读写 | 改用 1.20.1 NBT API；Create BE 使用 `read/write(CompoundTag, boolean)` |
| 物品序列化 | `ItemStack` 的注册表感知读写 | 改为 1.20.1 的 `save` 与 `ItemStack.of` 等 NBT 接口 |
| 音响绑定数据 | `DataComponentType<GlobalPos>` | 用物品 NBT 保存麦克风的维度和位置，统一读写入口 |
| 网络 | `CustomPacketPayload`、`StreamCodec`、`RegistryFriendlyByteBuf` | 改为 `SimpleChannel` 与 `FriendlyByteBuf`，客户端处理通过主线程执行 |
| 库存能力 | `RegisterCapabilitiesEvent`、NeoForge 能力接口 | 使用 `ForgeCapabilities.ITEM_HANDLER`、`LazyOptional` 与失效生命周期 |
| 菜单与屏幕 | 新版缓冲区、菜单注册及屏幕注册事件 | 适配 1.20.1 菜单工厂、Forge 打开菜单流程和客户端屏幕注册 |
| 渲染顶点 | `addVertex().setColor().setUv()` | 改为 `vertex().color().uv().endVertex()` |
| 光柱包围盒 | 渲染器上的 BE 包围盒接口 | 使用 Forge/Create 的方块实体包围盒接口，或由独立光柱绘制路径裁剪 |
| 数据生成 | `RecipeOutput` 与新版 provider 构造参数 | 改为 `Consumer<FinishedRecipe>` 等 1.20.1 接口 |
| 数据目录 | `recipe`、`loot_table`、`advancement` | 改为 `recipes`、`loot_tables`、`advancements` |
| 通用材料标签 | 新版 `c:` 标签 | 按材料语义映射到对应 `forge:` 标签，检查实际成员 |
| Mixin 发布 | Java 21，NeoForge 不使用相同的生产混淆流程 | Java 17，配置 Forge 的 refmap 生成、注册与生产重映射 |

方块交互需保持原有优先级：扳手继续执行扳手操作，持方块时允许正常放置，空手操作才按设备规则打开菜单或取物。冷焰火机的库存能力仍只向水平侧面开放，顶部漏斗不应获得燃料槽。

`StageLightingSettings` 和 `PositionZeroItemPolicy` 的网络协议分别保留服务器补光范围及原点位白名单同步。登录时发送当前值，配置重载时通知在线客户端，退出服务器时清除客户端缓存。

桁架包覆目前借用 Create 的动力 BE 类型。源分支通过 NeoForge 的 `BlockEntityTypeAddBlocksEvent` 加入有效方块；目标端需要在合适的注册生命周期，通过范围受限的 Mixin 或访问适配扩展有效方块集合。仅注册包覆方块不足以保证动力 BE 能创建。

AE2 15.4.12 的 `FacadePart` 构造参数为 `ItemStack` 与面向，源分支使用 `BlockState` 与面向。需要单独设计面板状态和模型选择的适配，保留桁架面板去除背侧重复框架的效果，并验证状态能够保存与同步。所有 AE2 类继续留在独立兼容类中，只在检测到 AE2 后进入。

## 光柱与冷焰火渲染

参考 `E:\forgeMod\AutoIllumination-1.20.1` 中以下实现，移植底层机制并保留闪耀舞台自己的几何、颜色和设备参数：

- `client/beam/BeamShaders.java`：Forge 着色器注册与资源重载生命周期。
- `client/beam/BeamRenderTypes.java`：透明度、深度测试、关闭深度写入与输出目标。
- `client/beam/BeamShaderInstance.java`、`OculusCompat.java`、`OculusIntegration.java`：可选 Oculus 帧缓冲路由与状态恢复。
- `client/beam/BeamRenderer.java`、`client/AutomobileHeadlights.java`：在 `AFTER_PARTICLES` 绘制并提交批次。

聚光灯保留彩色四棱台、红石强度控制扩散和透明度、滚轮范围及末端渐隐。透明度与渐隐参数继续通过浮点数据传给着色器，避免低信号时在八位顶点透明度中量化消失。

采用深度测试、关闭深度写入与双面透明绘制，保持墙体遮挡和交叉光柱效果。Veil 的 pinwheel 程序与渲染类型定义改为 Forge 原生着色器资源和代码中的 `RenderType`。

建议建立独立光柱绘制路径：从灯具注册表和结构缓存获取光源，在每帧将灯具姿态转换到世界空间，于 `AFTER_PARTICLES` 集中绘制，并按光柱自身包围盒裁剪。光源发现不依赖某个 BER 本帧是否被调用，防止结构本体离开视野时仍在屏幕内的光柱消失。若采用此方案，源分支的实体裁剪 Mixin 可随对应光柱路径一并替换；其他设备的包围盒仍按各自渲染方式适配。

静态灯与结构灯共用射线计算规则，保证光柱长度和虚拟照明落点一致。结构灯先由局部坐标得到世界位置与方向，再按目标运行环境的方块遮挡规则计算命中；不保留 Sable 跨空间射线。

冷焰火保留现有喷射轨迹、出生分布、帧动画、喷射持续时间和同步时间计算，单独适配带纹理的原生着色器及渲染类型，不改为逐个火花的客户端粒子模拟。

Oculus 1.8.0 的桥接涉及内部接口，必须保持可选类隔离、失败回退、帧缓冲恢复和资源关闭。复用代码不能代替目标模组在具体光影包中的画面验证。

## 客户端虚拟光照

保留源分支 `ClientStageLighting` 与 `StageLightLayerMerger` 的独立光照层：由单独的原版 `BlockLightEngine` 传播虚拟光，渲染读取亮度和整段 `DataLayer` 时取真实光与虚拟光的最大值。真实方块负责透光和遮挡，虚拟光不写回服务端光照数组，也不放置 `minecraft:light`。

AutoIllumination 的 `ClientBeamLights` 与光照 Mixin 可用于核对 Forge 1.20.1 的注入签名、空 section 保留、移动光源更新及光照包处理。但它向客户端主引擎注入光源，与独立虚拟层不同，不直接整套替换源分支设计。

移植约束如下：

- 静态灯在客户端 BE tick 入口注册，避免公共 tick 提前返回导致漏注册。
- 结构读取时缓存灯具局部位置、方向和范围；更新时遍历灯具缓存，不反复扫描结构全部方块。
- 光柱绘制使用逐帧插值姿态，虚拟光照先沿用每 5 tick 更新光源集合的节奏。
- 同格光源合并，移走一盏灯不撤掉其他灯的贡献；只对变化的位置触发光照传播。
- 保留空 section 的存储分配与回收、方块遮挡变化、邻近区块变化和重新标记渲染的处理。
- 读取缺失区块不请求加载，卸载、切换世界和退出时丢弃对应光源、合并缓存与引擎。
- 整段 `DataLayer` 的合并不修改输入快照，支持 Embeddium 工作线程读取。
- 灯光仅影响客户端画面，照射落点仍为原有方块，不改变服务端刷怪或作物亮度判定；落点为原版白光，彩色效果保留在光柱中。

AutoIllumination 也注入 `BlockLightEngine.getEmission`，共存时必须区分主引擎与本模组独立引擎，避免外部车灯源被混入虚拟层或被覆盖。需要在实现阶段确定隔离方式，验证开启和关闭两套光源后的叠加与撤销，不能依赖 Mixin 执行顺序。

## Create 原版运动结构

所选 Create 6.0.8 构件保留 `MovementBehaviour.REGISTRY`、`Contraption.readNBT`、`getBlockEntityClientSide` 和 `AbstractContraptionEntity.toGlobalVector`，可继续使用现有运动行为与结构缓存设计。

| 设备 | 保持的结构内行为 |
| --- | --- |
| 聚光灯 | 保持开启，按源实现固定使用红石强度 4；光柱及光照随结构移动与旋转 |
| 补光灯 | 沿用结构保存的 `POWERED` 状态，范围采用服务器同步值 |
| 麦克风 | 通过运动行为持续注册，监听位置由结构实时变换得到 |
| 音响 | 通过运动行为追踪实时发声位置，保留源实现的结构内播放规则 |

静态放置的音响仍要求通电才能播放。组装、拆解、卸载和重读 NBT 时，静态设备与运动设备的登记必须正确转接，避免重复播放、失去绑定或继续从组装前的位置发声。音响聚合继续按实际位置计算。

## 分阶段实施

| 阶段 | 主要工作 | 完成标准 | 状态 |
| --- | --- | --- | --- |
| 依赖与开发环境 | Forge 47.4.0、Create 配套库、可选集成编译依赖、开发运行时与 IDE 配置 | 构件解析成功，Forge 包选择正确，客户端模组不进入非客户端配置 | 已完成 |
| 平台骨架与基础内容 | 事件、注册、配置、NBT、网络、基础方块、模型和资源目录 | 注册 ID 齐全，目标 API 适配完成；后续构建可检查公共与客户端类隔离 | 代码已实现，编译通过；运行验收待安排 |
| 设备玩法与红石 | 舞台红石网络、包覆动力部件、冷焰火库存与菜单、原点位 | 保留交互顺序、燃料与红石规则，动力 BE 能正确创建 | 代码已实现，编译通过；运行验收待安排 |
| 原生渲染 | 聚光灯、冷焰火、资源重载与可选 Oculus 桥接 | 无光影基线正确，光柱遮挡和透明度正确，光影环境无状态泄漏 | 代码已实现，编译通过；画面验收待安排 |
| 虚拟光照与结构灯 | 独立引擎、光照合并、结构灯具缓存与世界坐标转换 | 移动、断电、重叠、卸载后光照正确，结构本体出视野仍可见光柱 | 代码已实现，编译通过；运行验收待安排 |
| 音频与可选兼容 | 音响 NBT 绑定、运动行为、声音转播与聚合、AE2 面板适配 | 静态与运动结构声音正确；AE2 有无均可运行 | 代码已实现，编译通过；运行验收待安排 |
| 配方与验证 | 数据生成适配、回归检查、发布重映射和游戏内验收 | 配方可用，开发与发布环境一致，关键功能逐项通过 | Provider 与现成 JSON 已适配，refmap 已生成；其他验证未运行 |

将可独立检查的功能分批落地，避免一次性复制源树后同时修复所有子系统。保留源分支作为对照，不将 1.21.1 构建脚本、Jar 或旧依赖目录直接覆盖到目标工程。

## 验证安排

本次实现遵循用户的最新约束：只运行编译测试，不运行回归程序、GameTest、数据生成器、客户端、服务端或其他测试。下表保留后续验收清单，不表示这些项目已经执行或通过。

构建和游戏启动根据后续具体指令安排。功能完成后先做不启动游戏的检查，再由用户启动客户端或服务端验收；数据生成使用独立运行目录。

| 验证项 | 重点 |
| --- | --- |
| 依赖与元数据 | Forge 47.4.0、Create 范围、可选依赖隔离、无 Fabric 包、无 Sable/Veil 残留 |
| 编译与发布 Jar | Java 17、Mixin refmap、发布重映射、资源打包与客户端类隔离；不能仅依赖开发环境运行结果 |
| 信号网络回归 | 移植 `StageSignalSolverRegression`，覆盖信号传播与网络更新 |
| 光照合并回归 | 移植 `StageLightLayerMergerRegression`，覆盖全部亮度配对、输入快照、撤销及并发读取 |
| 光源注册回归 | 适配 `StageLightingHookRegression`，检查客户端提前返回前已登记灯具 |
| 基础玩法 | 安装与连接形态、扳手、染色、滚轮、库存与 GUI、原点位存取、配方与掉落 |
| 静态光照 | 通断电、转向、调距、重叠光源、遮挡、跨区块、普通火把更新、退出重进和换维度 |
| 运动结构灯光 | 旋转与平移、视野裁剪、组装与拆解、结构重载；灯光落点不留下实体光方块 |
| 音频 | 绑定、通断电、服务器范围配置、音响聚合、结构移动后的监听与发声位置、拆解无双重播放 |
| 冷焰火 | 每个上升沿一次喷射、一次燃料、持续高电平不连发、水平面输入和顶部拒绝输入 |
| 光影与渲染器 | 无光影、Embeddium、Oculus 开关光影、资源重载、交叉光柱、冷焰火透明度与遮挡 |
| 可选模组 | AE2 有无均可加载，面板无重复框架；AutoIllumination 共存时光照叠加与撤销正确 |
| 多人环境 | 客户端采用服务器补光范围及原点位白名单，重载配置对已放置设备生效，服务端亮度不被虚拟光改变 |

源分支的 `LegacyLightCleanupRegression` 及旧光方块清理队列属于历史存档迁移机制。本项目当前从全新 1.20.1 工程起步，首版不直接引入这套清理及旧方块调色板迁移 Mixin；如后续明确需要兼容已有 1.20.1 历史数据，再按实际旧数据格式补充。不得通过扫描所有原版光方块来猜测旧灯光归属。

## 参考实现与接口资料

- [源分支固定提交](https://github.com/liziluyu/Create_ShiningStage/tree/1e7f9178d10bccd295997532c3500c255b664966)：功能、资源与行为基线。
- 源提交的 `docs/client-stage-lighting.md`：独立虚拟光照层、结构灯具与清理边界。
- 本机 `E:\forgeMod\AutoIllumination-1.20.1`：Forge 1.20.1 光柱、Oculus 桥接与移动光源参考；该工作区包含未提交修改，实际移植时按具体代码和许可证保留必要署名。
- [Create 6.0.8-291 官方构件](https://maven.createmod.net/com/simibubi/create/create-1.20.1/6.0.8-291/)：目标 Create API 与配套库版本。
- [Forge 1.20.1 SimpleImpl](https://docs.minecraftforge.net/en/1.20.1/networking/simpleimpl/)：网络消息注册与线程处理。
- [Forge 1.20.1 Capabilities](https://docs.minecraftforge.net/en/1.20.1/datastorage/capabilities/)：物品能力暴露与生命周期。
- [ForgeGradle 6 运行配置](https://docs.minecraftforge.net/en/fg-6.x/configuration/runs/)：运行配置与 classpath token。
