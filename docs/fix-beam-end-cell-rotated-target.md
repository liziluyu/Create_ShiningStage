# 修复计划：跨结构光束落点的回退方向在目标旋转时错误

> 对应审计问题 #3（服务端，中）。本文件仅为计划，不含代码改动。

## 问题

`SpotlightBlockEntity.beamEndCell`（约 :322-336）：

```java
BlockPos cell = hit.getType() == HitResult.Type.MISS
    ? worldPosition.relative(facing, getRange())
    : hit.getBlockPos().relative(facing.getOpposite());
```

命中分支用 `facing.getOpposite()` 回退一格——`facing` 是**本聚光灯所在空间**
（世界或本船 plot）里的朝向。Sable 的 `BlockGetter#clip` 跨空间射线命中另一
结构时，`hit.getBlockPos()` 报在**被命中结构 B 的空间**里；若 B 相对 A 有旋转
（Aeronautics 船任意姿态），A 的"朝向后退一步"在 B 的空间里并不是"命中格前
一格"，而是某个任意相邻格。

后果（有兜底，不崩）：

- 回退格落在 B 的实体方块内 → `updateBeamLight` 的 `!there.isAir()` 拒写 →
  该束光在 B 表面**不放灯**（照明缺失，玩家可感知）。
- 回退格恰好是空气但不是真正的命中前格 → 灯偏一格，且与客户端光束渲染的
  末端（渲染端用的是命中距离而非格子回退）不一致。
- 世界/未旋转结构命中时（绝大多数情况）完全正确——bug 只在"两结构相对
  旋转"时出现。

## 修复方案

原则：**回退方向取自命中本身，不取自发射方朝向。** `BlockHitResult` 自带
`getDirection()`（被命中面的外法线，表示在**命中所在空间**内），它正是
"命中格前一格"的方向——与目标结构如何旋转无关。

### `SpotlightBlockEntity.beamEndCell`

- 命中分支改为 `hit.getBlockPos().relative(hit.getDirection())`。
- MISS 分支不变（沿 facing 走到射程格，两空间刚性等距，无旋转问题——
  不命中即不跨空间）。

### 正确性核对

- 世界内命中：`hit.getDirection()` 即命中面法线，"命中格前一格"语义与
  `facing.getOpposite()` 在轴对齐命中时**一致**；对非轴对齐命中（斜面、
  楼梯、雪层等部分方块），法线回退比朝向回退**更**正确——现行代码在
  光束斜切过一个方块的棱时会把灯放进命中格侧向而非来向，修复后总是
  贴着被照亮的面。渲染端光束末端在命中点处，灯贴在命中面外侧，二者
  重新一致。
- 跨空间命中：`getDirection()` 由 B 空间内的射线求交产生，天然是 B 空间
  方向，旋转无关。灯因此放回"光束在 B 表面照亮的那一格外"，船对船任意
  姿态都正确。
- 自遮挡回归：光束紧贴自己脸上的方块（命中格 = 面朝的邻格，法线指向
  聚光灯）→ 回退格 = 聚光灯自身格 → 现有
  `cell.equals(worldPosition) ? null : cell` 护栏原样兜住，行为不变。

### 渲染端无需联动

`SpotlightRenderer` 用命中距离决定光束长度，不用格子回退，本修复只让
服务端灯格与既有渲染结果对齐，渲染代码不动。

## 验证

1. 世界回归（防退化）：平地上光束直射墙面 → 灯在墙面前一格；斜向
   （上下）命中半砖/楼梯 → 灯贴被照面外侧；贴脸遮挡 → 不放灯。
2. 旋转跨结构（修复核心）：两船，`sable paused true`，B 船装配后旋转
   90°/任意角，A 船光束打在 B 船壳上 → 灯必须出现在 B 船壳**受光面
   外侧那一格**（修复前：无灯或偏格）。移动 A 船，灯在一轮轮询内跟随。
3. 清理路径回归：断 A 船红石 → B 上的灯熄灭；`/sable remove` A 船 →
   `LIGHT_CLEANUP` 正常收回灯（跨空间记录的既有清理逻辑不受本修复影响，
   需回归确认）。
