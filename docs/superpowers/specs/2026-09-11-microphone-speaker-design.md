# 麦克风 + 音响（声音中继系统）设计

**目标：** 为 Create Shining Stage 新增麦克风方块与音响方块。麦克风在红石信号开启时检测周围一定范围内的声音事件，按距离衰减计算新音量，推送到与其绑定的音响，由音响作为新声源重放。

**方案：** 纯事件驱动架构——订阅 NeoForge `PlayLevelSoundEvent` 捕获服务端声音（携带完整 `SoundEvent`/位置/音量/音高），按平方距离衰减后通过原版 `playSound` 在音响处重放（走原版声音数据包，零自定义网络包）。绑定流程仿 Create 显示连接器/翻牌显示器：手持音响右键麦克风写入 DataComponent，放置时自动注册双向绑定。

---

## 1. 需求决策汇总

| 决策点 | 结论 |
|---|---|
| 声音检测钩子 | NeoForge `PlayLevelSoundEvent.AtPosition` / `.AtEntity`（不用 GameEvent 振动系统——它不携带 SoundEvent，无法重放） |
| 红石控制 | 有信号 = 开，无信号 = 关；信号强弱不影响音量（与 Spotlight 一致） |
| 监听范围 | 滚轮可调（`ScrollValueBehaviour`），4~32 格，默认 16 |
| 衰减模型 | 线性：新音量 = 原音量 × (1 − 距离/范围) |
| 绑定基数 | 一对多：一个麦克风可绑任意数量音响；每个音响只绑一个麦克风，换绑 = 右键另一个麦克风后重新放置 |
| 重放声音分类 | 统一映射到 `SoundSource.BLOCKS`（"方块"音量滑条统一控制） |
| 失效语义 | 静音自愈：拆麦克风 → 音响变哑且绑定注销；拆音响 → 从麦克风集合注销；重放回原位置不复活旧绑定，必须重新右键绑定 |
| 回声免疫 | 音响重放期间置 level 级重入标记，事件处理器跳过——所有麦克风均不捕获音响输出（含跨网络连锁） |
| 跨维度 | 不允许绑定（右键拒绝 + 提示）；handler 按维度隔离，天然安全 |

## 2. 架构总览

沿用项目现有模式：平面包结构（`com.shiningstage.create_shining_stage`）、DeferredRegister、Create `IBE<T>` + `SmartBlockEntity`、游戏逻辑在服务端 BE、无自定义网络包。

```
SoundRelayHandler (@SubscribeEvent, NeoForge 游戏事件总线)
  └─ PlayLevelSoundEvent ──► 遍历本维度已加载麦克风 ──► 距离衰减 ──► SpeakerBlockEntity.playRelayed()

MicrophoneBlock (DirectionalBlock + IBE)
  └─ MicrophoneBlockEntity (SmartBlockEntity)
       ├─ ScrollValueBehaviour "range" (MIN/MAX/DEFAULT = 4/32/16)
       ├─ 红石开关缓存 (neighborChanged 写入, 无 tick)
       └─ Set<BlockPos> boundSpeakers (NBT 持久化)

SpeakerBlock (Block + IBE)
  └─ SpeakerBlockEntity (SmartBlockEntity)
       ├─ GlobalPos boundMic (NBT 持久化)
       └─ playRelayed(sound, volume, pitch) — 重入标记保护下的 playSound
```

新增类：`MicrophoneBlock`、`MicrophoneBlockEntity`、`SpeakerBlock`、`SpeakerBlockEntity`、`SoundRelayHandler`、`ModDataComponents`（绑定用 DataComponent 注册）；修改 `ModBlocks`、`ModBlockEntityTypes`、`CreateShiningStage`（注册事件 handler 与 component）。

## 3. 麦克风（Microphone）

- `DirectionalBlock` + `IBE<MicrophoneBlockEntity>`，FACING 朝向；`registerDefaultState` + `codec()` + `createBlockStateDefinition`，同 Spotlight。
- **红石开关**：重写 `neighborChanged`，把 `level.getBestNeighborSignal(pos) > 0` 缓存进 BE 布尔字段。事件处理器只读缓存，无 tick、无轮询。
- **监听范围**：`ScrollValueBehaviour` "range"（4~32，默认 16），`ValueBoxTransform.Sided` 模式复用 Spotlight 的 `SpotlightRangeValueBoxTransform` 做法（新建 `MicrophoneRangeValueBoxTransform`）。
- **绑定集合**：BE 持有 `Set<BlockPos> boundSpeakers`，NBT 读写（`BlockPos` 列表序列化）。
- **拆毁**：`onRemove` 时反查每个 boundSpeaker 的 BE 清除其绑定，自身随 BE 消失。

## 4. 音响（Speaker）

- 普通 `Block` + `IBE<SpeakerBlockEntity>`，无朝向。
- BE 存 `GlobalPos boundMic`（NBT 持久化）。
- **重放**：`playRelayed(SoundEvent, float volume, float pitch)` → `level.playSound(null, pos, event, SoundSource.BLOCKS, volume, pitch)`，调用前后置/清 level 级重入标记。
- **拆毁**：反查 boundMic 对应 BE，从其 boundSpeakers 注销自身。
- 未绑定（直接放置、物品无 component）= 纯装饰静默方块。

## 5. 绑定流程（仿 Display Link）

1. 手持音响方块物品右键麦克风 → 麦克风 `GlobalPos` 写入物品自定义 DataComponent，actionbar 提示已绑定。对已绑定物品重复右键 = 换绑（覆盖 component）。
2. 右键不同维度的麦克风 → 拒绝写入，提示失败。
3. 放置音响 → `setPlacedBy` 读取 component → BE 存 boundMic → 反查麦克风 BE 将自身坐标加入其 boundSpeakers。
4. 双向存储，两端在 `onLoad`/拆毁时各自注册/注销，区块卸载重载后自愈，无幽灵绑定。

## 6. 声音中继（核心路径）

`SoundRelayHandler` 订阅两个事件子类，逻辑共用：

```
if (重入标记) return;
Set<BlockPos> mics = MIC_REGISTRY.get(level.dimension());
if (mics == null || mics.isEmpty()) return;
for (micPos : mics):
    MicrophoneBlockEntity mic = BE 反查; if (mic == null) continue;
    if (!mic.redstoneOn) continue;
    d2 = Sable.HELPER.distanceSquaredWithSubLevels(声源位置, micPos)   // 船安全距离
    range = mic.range 值
    if (d2 > range * range) continue;
    newVolume = 原volume × (1 − sqrt(d2)/range)
    if (newVolume <= 0.01f) continue;
    for (speakerPos : mic.boundSpeakers):
        SpeakerBlockEntity spk = BE 反查; if (spk != null) spk.playRelayed(sound, newVolume, pitch);
```

- **麦克风注册表**：静态 `Map<ResourceKey<Level>, Set<BlockPos>>`，BE `onLoad` 加入、`setRemoved` 移除（仅服务端）。
- **回声免疫**：`playRelayed` 在 `playSound` 前后置/清静态重入标记（`playSound` 同步触发事件，单线程服务端安全）；事件处理器见标记直接返回。任何麦克风（含其他玩家的网络）都捕获不到音响输出，杜绝连锁自激。
- **维度隔离**：注册表按 `ResourceKey<Level>` 分桶，只查本维度。

## 7. 性能

- 空闲成本为零：无 BE tick、无定时扫描、无客户端逻辑（音响无渲染器需求，麦克风滚轮 ValueBox 走 Create 现有机制）。
- 每次 `playSound`：本维度麦克风数 × 一次平方距离比较；命中后每个绑定音响一次原版 `playSound`。
- 重放走原版 `SClientSoundPacket` 数据包，无自定义网络。

## 8. 资源与配套

- blockstate / 方块模型 / 物品模型 / loot_table / lang（en_us + zh_cn 双语，键名规范沿用 `block.create_shining_stage.<name>`，ValueBox 标签 `block.create_shining_stage.microphone.range`）。
- 模型：麦克风为带朝向的简单方块模型，音响为纯方块模型；先程序化贴图占位，美术后换。
- 两方块加入现有 `ModCreativeTabs` 创造物品栏。
- 合成配方：本期不实现，后续用已接通的 `runData` datagen 或手写 JSON 补充。
- 自定义 DataComponent：注册到 NeoForge `DeferredRegister<DataComponentType<?>>`，codec 序列化 `GlobalPos`。

## 9. 已知取舍

- **只能捕获走服务端 `playSound` 的声音**（实体动作、方块交互、物品使用等绝大多数游戏内声音）；客户端环境音、直调客户端音效引擎的 mod（如语音聊天类）捕获不到。这是原版架构限制。
- 音响重放分类统一为 BLOCKS，损失原声音的分类归属（玩家用"方块"滑条统一控制音响音量）。

## 10. 验证

`.\gradlew.bat build` 编译通过后 `runClient` 实机验证：

1. 放置麦克风，红石开/关，确认仅开启时中继。
2. 麦克风旁射箭 / 触发音符盒 / 放置破坏方块 → 绑定音响重放对应声音。
3. 同一声源、不同距离放置两台麦克风 → 各音响音量随距离衰减；滚轮调 range 生效。
4. 音响输出旁再放一台开启的麦克风 + 音响 → 验证不发生二次中继（回声免疫）。
5. 拆麦克风 → 音响静音；拆音响 → 麦克风集合注销；重新放置需重新绑定。
6. 创造物品栏、破坏掉落、lang 双语显示正常。
