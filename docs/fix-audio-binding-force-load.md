# 修复计划：音频绑定解析路径强制加载远区块

> 对应审计问题 #1（服务端，高）。本文件仅为计划，不含代码改动。

## 问题

绑定坐标（麦克风 ↔ 音响）距离无上限，但解析路径直接对绑定坐标调用
`Level#getBlockEntity` / `getBestNeighborSignal`。1.21.1 中 `Level#getBlockEntity` 走
`getChunkAt` → `getChunk(..., ChunkStatus.FULL, requireChunk = true)`：区块不在内存就
**同步加载**，未生成则**现场生成**。

波及点：

| 位置 | 调用 | 触发频率 |
|---|---|---|
| `SoundRelayHandler.speakerPosition`（约 :291） | `getBlockEntity(speakerPos)` + `getBestNeighborSignal(speakerPos)` | 每次发声 × 每个绑定音响 |
| `SpeakerBlock.setPlacedBy`（约 :112） | `getBlockEntity(mic.pos())` | 每次放置已绑定音响 |
| `SpeakerBlock.onRemove`（约 :134） | `getBlockEntity(mic.pos())` | 每次破坏音响 |
| `MicrophoneBlock.onRemove`（约 :144） | `getBlockEntity(speakerPos)`（循环） | 每次破坏麦克风 |

最危险的是第一行：把音响绑好后带去几千格外（或未生成地形）放下，麦克风附近
**每次发声**都会同步装载一个远处区块，服务器上可被恶意用于卡服。

附带 bug：`SpeakerBlock.setPlacedBy` 的 `bind_failed_unloaded` 警告是死代码——
`getBlockEntity` 自己会把区块拉起来，"未加载"分支永远走不到；玩家看到的不是
警告，而是一次隐藏的远程区块加载后绑定"成功"。

对照：聚光灯的光束灯轮询用 `Level#isLoaded`（`hasChunk`，`requireChunk = false`）
先行护栏（`SpotlightBlockEntity.beamPathLoaded` / `cellPresent`），本修复把同一
约定推广到音频链路。

## 修复方案

原则：**绑定解析一律先 `level.isLoaded(pos)`，未加载即按"该端当前不可达"处理，
语义与现状保持一致（沉默/跳过），绝不读区块。**

### 1. `SoundRelayHandler.speakerPosition`

- 入口处加 `if (!level.isLoaded(speakerPos))` → 直接返回 mounted 分支的查找结果
  （mounted 音响不依赖区块，仍可播放）。
- 即：placed 分支整体以 `isLoaded` 为前置；`getBestNeighborSignal` 在
  `getBlockEntity` 命中后调用，此时区块必然已加载，无需二次护栏。
- 语义变化：绑定音响所在区块未加载时不再"把它拉起来播放"，而是本轮沉默。
  这正是玩家直觉（区块都没加载，哪来的电）。

### 2. `SpeakerBlock.setPlacedBy`

- `level.getBlockEntity(mic.pos())` 之前加 `!level.isLoaded(mic.pos())` 判断，
  未加载 → 走现有 `bind_failed_unloaded` 警告并返回。
- 效果：`bind_failed_unloaded` 从死代码变成真实路径，文案（"麦克风所在区块未
  加载"）本就为这一语义写好，无需改动。

### 3. `SpeakerBlock.onRemove` / `MicrophoneBlock.onRemove` 的解绑循环

- 对端坐标加 `level.isLoaded(...)` 护栏，未加载则跳过解绑。
- 跳过的后果已在现有设计中自洽：
  - 音响被破坏时对端麦克风未加载 → 麦克风的 `boundSpeakers` 残留一个坐标；
    该坐标日后再被 `speakerPosition` 解析时 `getBlockEntity` 不是音响即沉默，
    与"音响已不存在"语义一致（现有代码本就容忍失效绑定坐标）。
  - 麦克风被破坏时对端音响未加载 → 音响的 `boundMic` 残留；其作用仅是
    日后破坏音响时反查解绑（反查不到即跳过）与手持描边（描边检查
    `state.getBlock() instanceof MicrophoneBlock`，空气即不画），均自洽。

### 4. 顺带核对（只读，不改动）

- `SoundRelayHandler.handle` 中 placed 麦克风的 `getBlockEntity(micPos)`：
  注册表由 `onLoad`/`invalidate` 与区块加载同步，坐标必然已加载，**不需要**
  护栏。修复时在代码注释里写明这一不变式，防止后人误加或误删。

## 不做什么

- 不改变"绑定可跨任意距离"的玩法语义；未加载只是暂时沉默，不是解绑。
- 不引入定期清理失效绑定坐标的机制（现有容忍逻辑已正确，属于减重）。
- 不动 `MicrophoneBlockEntity` 的注册/注销时机。

## 验证

1. 服务器控制台（rcon 或 runServer 控制台）：
   - 麦克风放 A 点，音响绑好后放到 5000 格外（未生成区域），在麦克风旁发声
     （`/playsound` 或踩压力板）：修复前可观察到远区区块被加载/生成
     （日志 `Loading chunk` 或磁盘新区块文件），修复后无任何区块加载，
     且 `/execute if loaded` 确认远端仍未加载。
2. 手持绑定音响在麦克风区块未加载时放置：必须收到
   `bind_failed_unloaded` 提示（修复前收不到），且世界日志无远程区块加载。
3. 麦克风旁正常场景回归：绑定的音响在红石供能下正常回放、失能即停；
   聚类（两组邻近音响合并为一源）行为不变。
4. 破坏麦克风时其绑定音响处于未加载区块：不加载该区块；日后再加载该音响、
   发声，确认沉默而非报错。
