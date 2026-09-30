# 修复计划：聚光灯渲染包围盒违反 Sable 对称不变式（船上光束不可见）

> 对应审计问题 #2（客户端，高）。本文件仅为计划，不含代码改动。

## 问题

已用字节码核实的完整因果链：

1. NeoForge 21.1.235 的 `LevelRenderer` 补丁（`patches/.../LevelRenderer.java.patch`
   第 84 行）对**每个**可渲染 BE 无条件调用
   `ClientHooks.isBlockEntityRendererVisible`——`shouldRenderOffScreen() == true`
   **并不绕过**该检查（1.21.1 的这条路径上它实际不起作用）。
2. Sable 2.0.5 重定向该方法
   （`dev.ryanhcode.sable.neoforge.mixin.block_entity_visible.LevelRendererMixin`），
   取 `getRenderBoundingBox(be).getCenter()` 调
   `Sable.HELPER.getContainingClient(center)`：
   - 中心落在某 plot 内 → 包围盒经该结构 logicalPose 变换后做视锥测试（正确）；
   - 中心不在任何 plot 内 → 用**未变换的 plot 空间包围盒**直接对（世界空间）
     视锥测试。plot 坐标离摄像机数百万格，必裁剪 → **永不渲染**。

`SpotlightRenderer.getRenderBoundingBox`（:109-114）：

```java
return new AABB(be.getBlockPos())
    .expandTowards(Vec3.atLowerCornerOf(normal).scale(MAX_RANGE))
    .inflate(TOP_HALF + MAX_RANGE * HALF_ANGLE_TAN);
```

只沿 FACING **单向**扩展最多 32 格，盒心偏离方块最远约 16 格（+ inflate 半径）。
船在光束方向不足 16 格、或光束指向船舷之外时，盒心落在 plot 外 → 走上面第 2
条的失败分支 → 船上的聚光灯整束光永久不可见。

这正是 `ColdSparkMachineRenderer.getRenderBoundingBox`（:198-209）用**对称**膨胀
规避、并在注释里写明不变式的同一故障模式（"a box whose centre has floated off
the block resolves to no sub-level … culled forever"）——聚光灯漏掉了同样处理。

## 修复方案

原则：**盒心必须钉在方块上**——沿 FACING 方向改为对称扩展，代价仅是略微过绘
（off-screen 多画几帧），换来任何船型/朝向下中心恒在 plot 内。

### `SpotlightRenderer.getRenderBoundingBox`

- 以 `new AABB(be.getBlockPos())` 为基，沿光束轴**双向**扩展
  `MAX_RANGE`（正方向覆盖实际光束，反方向为对称冗余），再
  `inflate(TOP_HALF + MAX_RANGE * HALF_ANGLE_TAN)` 保持侧向不变。
- 实现上不宜手写两个角点（易错）；直接
  `new AABB(pos).inflate(MAX_RANGE)` 再做侧向 inflate，或
  `expandTowards(normal*MAX).expandTowards(normal*-MAX)`。
  盒心因此恒为方块中心，与该方块所在 plot 的关系不再依赖朝向和射程。
- 横向 inflate 公式（`TOP_HALF + MAX_RANGE * HALF_ANGLE_TAN ≈ 9.84`）不变。

### 性能影响核算

- 包围盒变大（约 65×52×65 → 最坏约 84 边长量级的轴对齐盒），但成本只是每次
  视锥测试多框住一些 section——**绘制本身不变**：`render()` 内仍按真实
  `getRange()` + raycast 画光束，且信号为 0 时早退。
- 视锥测试是常数时间（6 个平面 × AABB），盒大不影响单次成本。
- `shouldRender`（距离 256）不变。

### 顺带确认（只读）

- `ColdSparkMachineRenderer` 已对称，无需动。
- `contraptionCullingBox`（Create contraption 路径）走 `EntityCullingMixin`，
  与 Sable 该重定向无关，不受本修复影响。
- 修复后 `shouldRenderOffScreen() == true` 可保留（对 Create 自身的渲染路径
  仍有意义），但应在注释中写明：NeoForge 的逐 BE 剔除路径**不**读它，
  真正保命的是对称包围盒——避免后人再踩"off-screen=true 就安全"的误解。

## 验证

1. 复现（修复前确认 bug 存在）：
   - 造一艘小船（数格宽），装聚光灯朝舷外打，`sable paused true`；
     站在能看到光束角度的位置 → 光束消失（盒心已在 plot 外）。
   - 同船把光束转向船体纵深方向（盒心留在 plot 内）→ 光束可见。
2. 修复后：两种朝向光束均可见；转动船（装配后旋转）、改变射程
   （value box 2↔32）全程光束不闪灭。
3. 世界内回归：地面聚光灯在光束贴脸、转身、最大射程下无异常裁剪
   （修复前后均应正常，确认盒变大未引入新裁剪错误）。
4. Create contraption 回归：装配进轴承结构，转头使船体出视锥，
   光束须保持可见（`contraptionCullingBox` 路径未动，预期不变）。
