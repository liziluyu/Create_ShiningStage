# 麦克风 + 音响（声音中继系统）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增麦克风方块与音响方块：麦克风红石开启时捕获范围内服务端声音，按距离衰减后由绑定的音响重放。

**Architecture:** 纯事件驱动——`SoundRelayHandler` 订阅 NeoForge `PlayLevelSoundEvent.AtPosition`/`.AtEntity`，遍历本维度已加载麦克风注册表，Sable 船安全距离 + 线性衰减，音响 `level.playSound` 重放（重入标记防回声）。绑定仿 Create Display Link：手持音响右键麦克风写入 `BOUND_MIC` DataComponent，放置时 `setPlacedBy` 建立双向注册。

**Tech Stack:** NeoForge 21.1.235 / MC 1.21.1(Mojmap + Parchment)、Create 6.0.10(`IBE`/`SmartBlockEntity`/`ScrollValueBehaviour`)、Sable companion(`distanceSquaredWithSubLevels(Level, Position, Position)`,common 模块，服务端安全）。

**Spec:** `docs/superpowers/specs/2026-09-11-microphone-speaker-design.md`

**测试策略：** 项目无测试设施（见 AGENTS.md)。每个任务以 `.\gradlew.bat build` 编译通过为验证；Task 6 用 `runClient` 按清单实机验证。

**已核实的 API(写代码时直接采用，不要猜）:**
- `PlayLevelSoundEvent`:`getLevel()` → `Level`;`getSound()` → `@Nullable Holder<SoundEvent>`;`getOriginalVolume()`/`getOriginalPitch()`;`AtPosition.getPosition()` → `Vec3`;`AtEntity.getEntity()` → `Entity`。
- `Sable.HELPER.distanceSquaredWithSubLevels(Level, Position, Position) → double`,`Vec3` 实现 `Position`。
- 现有模式参考：`SpotlightBlock`(DirectionalBlock+IBE+useItemOn)、`SpotlightBlockEntity`(ScrollValueBehaviour+NBT)、`SpotlightRangeValueBoxTransform`(ValueBoxTransform.Sided)。

---

### Task 1: 绑定用 DataComponent 注册

**Files:**
- Create: `src/main/java/com/shiningstage/create_shining_stage/ModDataComponents.java`
- Modify: `src/main/java/com/shiningstage/create_shining_stage/CreateShiningStage.java`

- [ ] **Step 1: 创建 ModDataComponents**

```java
package com.shiningstage.create_shining_stage;

import java.util.function.Supplier;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CreateShiningStage.MOD_ID);

    /** Microphone a speaker item was bound to by right-clicking it. Consumed by SpeakerBlock.setPlacedBy. */
    public static final Supplier<DataComponentType<GlobalPos>> BOUND_MIC =
        COMPONENTS.register("bound_mic", () -> DataComponentType.<GlobalPos>builder()
            .persistent(GlobalPos.CODEC)
            .networkSynchronized(GlobalPos.STREAM_CODEC)
            .build());
}
```

- [ ] **Step 2: 在 CreateShiningStage 构造函数注册**

在 `ModCreativeTabs.CREATIVE_TABS.register(modEventBus);` 之后加一行：

```java
        ModDataComponents.COMPONENTS.register(modEventBus);
```

- [ ] **Step 3: 编译验证**

Run: `.\gradlew.bat build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/shiningstage/create_shining_stage/ModDataComponents.java src/main/java/com/shiningstage/create_shining_stage/CreateShiningStage.java
git commit -m "feat: add BOUND_MIC data component for speaker-microphone binding"
```

---

### Task 2: 麦克风方块(方块 + BE + 值盒 + 注册 + 注册表骨架)

**Files:**
- Create: `src/main/java/com/shiningstage/create_shining_stage/MicrophoneRangeValueBoxTransform.java`
- Create: `src/main/java/com/shiningstage/create_shining_stage/MicrophoneBlockEntity.java`
- Create: `src/main/java/com/shiningstage/create_shining_stage/MicrophoneBlock.java`
- Create: `src/main/java/com/shiningstage/create_shining_stage/SoundRelayHandler.java`
- Modify: `src/main/java/com/shiningstage/create_shining_stage/ModBlocks.java`
- Modify: `src/main/java/com/shiningstage/create_shining_stage/ModBlockEntityTypes.java`
- Modify: `src/main/java/com/shiningstage/create_shining_stage/ModCreativeTabs.java`

注：本任务的麦克风不含与音响耦合的逻辑（绑定交互、拆毁清理、relay)，那些等 SpeakerBlockEntity 存在后于 Task 3 加入。

- [ ] **Step 1: MicrophoneRangeValueBoxTransform（滚轮盒放在顶面）**

```java
package com.shiningstage.create_shining_stage;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Places the range value box on the microphone's top face. */
public class MicrophoneRangeValueBoxTransform extends ValueBoxTransform.Sided {
    @Override
    protected Vec3 getSouthLocation() {
        return new Vec3(0.5, 0.5, 15.5 / 16.0);
    }

    @Override
    protected boolean isSideActive(BlockState state, Direction direction) {
        return direction == Direction.UP;
    }
}
```

- [ ] **Step 2: SoundRelayHandler（本任务只含注册表 + 重入标记，事件处理在 Task 4)**

```java
package com.shiningstage.create_shining_stage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Server-side registry of loaded microphones, plus the relay guard that makes speaker output uncatchable. */
public class SoundRelayHandler {
    /** True while a speaker replays a sound; the event handler skips everything to prevent echo loops. */
    public static boolean relayGuard;

    private static final Map<ResourceKey<Level>, Set<BlockPos>> MICROPHONES = new HashMap<>();

    public static void register(ResourceKey<Level> dimension, BlockPos pos) {
        MICROPHONES.computeIfAbsent(dimension, k -> new HashSet<>()).add(pos);
    }

    public static void unregister(ResourceKey<Level> dimension, BlockPos pos) {
        Set<BlockPos> set = MICROPHONES.get(dimension);
        if (set != null) {
            set.remove(pos);
            if (set.isEmpty()) {
                MICROPHONES.remove(dimension);
            }
        }
    }

    /** Live microphone positions in a dimension, or null when none. */
    public static Set<BlockPos> microphonesIn(ResourceKey<Level> dimension) {
        return MICROPHONES.get(dimension);
    }
}
```

- [ ] **Step 3: MicrophoneBlockEntity**

```java
package com.shiningstage.create_shining_stage;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

public class MicrophoneBlockEntity extends SmartBlockEntity {
    /** Adjustable listening range bounds, in blocks. */
    public static final int MIN_RANGE = 4;
    public static final int MAX_RANGE = 32;
    /** Initial listening range on placement. */
    public static final int DEFAULT_RANGE = 16;

    /** Cached on neighborChanged; read by the sound event handler so the BE never ticks. */
    private boolean redstoneOn;
    private ScrollValueBehaviour range;
    private final Set<BlockPos> boundSpeakers = new LinkedHashSet<>();

    public MicrophoneBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntityTypes.MICROPHONE.get(), pos, blockState);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        range = new ScrollValueBehaviour(
            Component.translatable("block.create_shining_stage.microphone.range"),
            this, new MicrophoneRangeValueBoxTransform())
            .between(MIN_RANGE, MAX_RANGE);
        range.value = DEFAULT_RANGE; // start at a mid value, not max
        behaviours.add(range);
    }

    /** Listening range in blocks (MIN_RANGE..MAX_RANGE), adjusted via the top value box. */
    public int getRange() {
        return range == null ? DEFAULT_RANGE : range.getValue();
    }

    public boolean isRedstoneOn() {
        return redstoneOn;
    }

    public void setRedstoneOn(boolean redstoneOn) {
        this.redstoneOn = redstoneOn;
    }

    public Set<BlockPos> getBoundSpeakers() {
        return boundSpeakers;
    }

    public void addSpeaker(BlockPos pos) {
        boundSpeakers.add(pos);
        notifyUpdate();
    }

    public void removeSpeaker(BlockPos pos) {
        boundSpeakers.remove(pos);
        notifyUpdate();
    }

    // Keep the loaded-microphone registry in sync with chunk load/unload.
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            SoundRelayHandler.register(level.dimension(), worldPosition);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (level != null && !level.isClientSide) {
            SoundRelayHandler.unregister(level.dimension(), worldPosition);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("RedstoneOn", redstoneOn);
        tag.putLongArray("BoundSpeakers", boundSpeakers.stream().mapToLong(BlockPos::asLong).toArray());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        redstoneOn = tag.getBoolean("RedstoneOn");
        boundSpeakers.clear();
        for (long packed : tag.getLongArray("BoundSpeakers")) {
            boundSpeakers.add(BlockPos.of(packed));
        }
    }
}
```

- [ ] **Step 4: MicrophoneBlock（本任务只有朝向 + 红石缓存）**

```java
package com.shiningstage.create_shining_stage;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public class MicrophoneBlock extends DirectionalBlock implements IBE<MicrophoneBlockEntity> {
    public static final MapCodec<MicrophoneBlock> CODEC = simpleCodec(MicrophoneBlock::new);

    public MicrophoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public Class<MicrophoneBlockEntity> getBlockEntityClass() {
        return MicrophoneBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MicrophoneBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.MICROPHONE.get();
    }

    // Cache the power state in the BE so the sound event handler reads a field instead of querying neighbors.
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MicrophoneBlockEntity be) {
            be.setRedstoneOn(level.getBestNeighborSignal(pos) > 0);
        }
    }
}
```

- [ ] **Step 5: 注册——ModBlocks 追加**

```java
    public static final Supplier<MicrophoneBlock> MICROPHONE =
        BLOCKS.register("microphone", () -> new MicrophoneBlock(BlockBehaviour.Properties.of()
            .strength(3.5f)
            .sound(SoundType.METAL)));

    public static final Supplier<BlockItem> MICROPHONE_ITEM =
        ITEMS.register("microphone", () -> new BlockItem(MICROPHONE.get(), new Item.Properties()));
```

- [ ] **Step 6: 注册——ModBlockEntityTypes 追加**

```java
    public static final Supplier<BlockEntityType<MicrophoneBlockEntity>> MICROPHONE =
        BLOCK_ENTITIES.register("microphone",
            () -> BlockEntityType.Builder.of(MicrophoneBlockEntity::new, ModBlocks.MICROPHONE.get()).build(null));
```

- [ ] **Step 7: 注册——ModCreativeTabs 的 displayItems 追加麦克风**

把 `.displayItems((params, output) -> output.accept(new ItemStack(ModBlocks.SPOTLIGHT_ITEM.get())))` 改为：

```java
            .displayItems((params, output) -> {
                output.accept(new ItemStack(ModBlocks.SPOTLIGHT_ITEM.get()));
                output.accept(new ItemStack(ModBlocks.MICROPHONE_ITEM.get()));
            })
```

- [ ] **Step 8: 编译验证**

Run: `.\gradlew.bat build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/shiningstage/create_shining_stage/
git commit -m "feat: add microphone block with redstone gate and scrollable listening range"
```

---

### Task 3: 音响方块 + 绑定交互 + 拆毁清理

**Files:**
- Create: `src/main/java/com/shiningstage/create_shining_stage/SpeakerBlockEntity.java`
- Create: `src/main/java/com/shiningstage/create_shining_stage/SpeakerBlock.java`
- Modify: `src/main/java/com/shiningstage/create_shining_stage/MicrophoneBlock.java`(useItemOn 绑定 + onRemove 清理)
- Modify: `src/main/java/com/shiningstage/create_shining_stage/MicrophoneBlockEntity.java`(relay 方法)
- Modify: `src/main/java/com/shiningstage/create_shining_stage/ModBlocks.java`
- Modify: `src/main/java/com/shiningstage/create_shining_stage/ModBlockEntityTypes.java`
- Modify: `src/main/java/com/shiningstage/create_shining_stage/ModCreativeTabs.java`

- [ ] **Step 1: SpeakerBlockEntity**

```java
package com.shiningstage.create_shining_stage;

import java.util.List;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

public class SpeakerBlockEntity extends SmartBlockEntity {
    /** Microphone this speaker is bound to, if any. Unbound speakers stay silent decoration. */
    private GlobalPos boundMic;

    public SpeakerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntityTypes.SPEAKER.get(), pos, blockState);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public GlobalPos getBoundMic() {
        return boundMic;
    }

    public void bindTo(GlobalPos boundMic) {
        this.boundMic = boundMic;
        notifyUpdate();
    }

    public void clearBinding() {
        boundMic = null;
        notifyUpdate();
    }

    /**
     * Replay a captured sound at this speaker. The relay guard makes the PlayLevelSoundEvent fired by this
     * call invisible to every microphone, so speaker output can never be captured back (no echo loops).
     */
    public void playRelayed(SoundEvent sound, float volume, float pitch) {
        if (level == null || level.isClientSide) {
            return;
        }
        SoundRelayHandler.relayGuard = true;
        try {
            level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
        } finally {
            SoundRelayHandler.relayGuard = false;
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (boundMic != null) {
            tag.put("BoundMic", GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, boundMic).getOrThrow());
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        boundMic = tag.contains("BoundMic")
            ? GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get("BoundMic")).result().orElse(null)
            : null;
    }
}
```

- [ ] **Step 2: SpeakerBlock**

```java
package com.shiningstage.create_shining_stage;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

public class SpeakerBlock extends Block implements IBE<SpeakerBlockEntity> {
    public static final MapCodec<SpeakerBlock> CODEC = simpleCodec(SpeakerBlock::new);

    public SpeakerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public Class<SpeakerBlockEntity> getBlockEntityClass() {
        return SpeakerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SpeakerBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.SPEAKER.get();
    }

    // Binding flow (display-link style): the stack carries BOUND_MIC from right-clicking a microphone;
    // placing registers both directions. A stack without the component places an inert decoration.
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker)) {
            return;
        }
        GlobalPos mic = stack.get(ModDataComponents.BOUND_MIC.get());
        if (mic == null) {
            return;
        }
        Player player = placer instanceof Player p ? p : null;
        if (!mic.dimension().equals(level.dimension())) {
            warn(player, "block.create_shining_stage.speaker.bind_failed_dimension");
            return;
        }
        if (!(level.getBlockEntity(mic.pos()) instanceof MicrophoneBlockEntity micBe)) {
            warn(player, "block.create_shining_stage.speaker.bind_failed_unloaded");
            return;
        }
        speaker.bindTo(mic);
        micBe.addSpeaker(pos);
    }

    private static void warn(@Nullable Player player, String key) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key), true);
        }
    }

    // On break, unregister from the microphone so its bound-speaker set never leaks stale positions.
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
            && level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker && speaker.getBoundMic() != null) {
            GlobalPos mic = speaker.getBoundMic();
            if (mic.dimension().equals(level.dimension())
                && level.getBlockEntity(mic.pos()) instanceof MicrophoneBlockEntity micBe) {
                micBe.removeSpeaker(pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
```

- [ ] **Step 3: MicrophoneBlockEntity 追加 relay 方法**（放在 `removeSpeaker` 之后）

```java
    /** Push a captured sound to every bound speaker. Stale positions (unloaded/broken speakers) skip silently. */
    public void relay(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        if (level == null || level.isClientSide) {
            return;
        }
        for (BlockPos speakerPos : List.copyOf(boundSpeakers)) {
            if (level.getBlockEntity(speakerPos) instanceof SpeakerBlockEntity speaker) {
                speaker.playRelayed(sound, volume, pitch);
            }
        }
    }
```

- [ ] **Step 4: MicrophoneBlock 追加绑定交互与拆毁清理**（在 `neighborChanged` 之后追加两个方法，并补齐 import:`net.minecraft.core.GlobalPos`、`net.minecraft.network.chat.Component`、`net.minecraft.world.InteractionHand`、`net.minecraft.world.ItemInteractionResult`、`net.minecraft.world.entity.player.Player`、`net.minecraft.world.item.ItemStack`、`net.minecraft.world.phys.BlockHitResult`、`java.util.List`)

```java
    // Binding: right-click with a speaker item stores this microphone on the stack; placing the speaker
    // consumes it (see SpeakerBlock.setPlacedBy). Sneak-click bypasses this so speakers can be placed against
    // a microphone face.
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.is(ModBlocks.SPEAKER_ITEM.get())) {
            if (!level.isClientSide) {
                stack.set(ModDataComponents.BOUND_MIC.get(), GlobalPos.of(level.dimension(), pos));
                player.displayClientMessage(
                    Component.translatable("block.create_shining_stage.speaker.bound"), true);
            }
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    // On break, mute every bound speaker (binding does not revive if a new microphone is placed here later).
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
            && level.getBlockEntity(pos) instanceof MicrophoneBlockEntity be) {
            for (BlockPos speakerPos : List.copyOf(be.getBoundSpeakers())) {
                if (level.getBlockEntity(speakerPos) instanceof SpeakerBlockEntity speaker) {
                    speaker.clearBinding();
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
```

- [ ] **Step 5: 注册——ModBlocks 追加**

```java
    public static final Supplier<SpeakerBlock> SPEAKER =
        BLOCKS.register("speaker", () -> new SpeakerBlock(BlockBehaviour.Properties.of()
            .strength(3.5f)
            .sound(SoundType.WOOD)));

    public static final Supplier<BlockItem> SPEAKER_ITEM =
        ITEMS.register("speaker", () -> new BlockItem(SPEAKER.get(), new Item.Properties()));
```

- [ ] **Step 6: 注册——ModBlockEntityTypes 追加**

```java
    public static final Supplier<BlockEntityType<SpeakerBlockEntity>> SPEAKER =
        BLOCK_ENTITIES.register("speaker",
            () -> BlockEntityType.Builder.of(SpeakerBlockEntity::new, ModBlocks.SPEAKER.get()).build(null));
```

- [ ] **Step 7: 注册——ModCreativeTabs 的 displayItems 追加音响**（在麦克风行后加一行）

```java
                output.accept(new ItemStack(ModBlocks.SPEAKER_ITEM.get()));
```

- [ ] **Step 8: 编译验证**

Run: `.\gradlew.bat build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/shiningstage/create_shining_stage/
git commit -m "feat: add speaker block with display-link-style microphone binding"
```

---

### Task 4: 声音中继事件处理

**Files:**
- Modify: `src/main/java/com/shiningstage/create_shining_stage/SoundRelayHandler.java`
- Modify: `src/main/java/com/shiningstage/create_shining_stage/CreateShiningStage.java`

- [ ] **Step 1: SoundRelayHandler 追加事件处理**（追加 import 与三个方法；`microphonesIn` 保持不变供 handle 使用）

追加 import:

```java
import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.ryanhcode.sable.Sable;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
```

追加方法：

```java
    public static void onAtPosition(PlayLevelSoundEvent.AtPosition event) {
        handle(event.getLevel(), event.getPosition(), event.getSound(),
            event.getOriginalVolume(), event.getOriginalPitch());
    }

    public static void onAtEntity(PlayLevelSoundEvent.AtEntity event) {
        handle(event.getLevel(), event.getEntity().position(), event.getSound(),
            event.getOriginalVolume(), event.getOriginalPitch());
    }

    private static void handle(Level level, Vec3 sourcePos, @Nullable Holder<SoundEvent> sound,
                               float volume, float pitch) {
        if (relayGuard || level.isClientSide || sound == null) {
            return;
        }
        Set<BlockPos> mics = microphonesIn(level.dimension());
        if (mics == null || mics.isEmpty()) {
            return;
        }
        for (BlockPos micPos : List.copyOf(mics)) {
            if (!(level.getBlockEntity(micPos) instanceof MicrophoneBlockEntity mic) || !mic.isRedstoneOn()) {
                continue;
            }
            int range = mic.getRange();
            // Ship-safe distance: a source in an Aeronautics sub-level sits at far-away coordinates,
            // so a vanilla distance check would explode. Sable maps both points into a common space.
            double d2 = Sable.HELPER.distanceSquaredWithSubLevels(level, sourcePos, Vec3.atCenterOf(micPos));
            if (d2 > (double) range * range) {
                continue;
            }
            float newVolume = volume * (1f - (float) Math.sqrt(d2) / range);
            if (newVolume <= 0.01f) {
                continue;
            }
            mic.relay(sound.value(), newVolume, pitch);
        }
    }
```

- [ ] **Step 2: CreateShiningStage 注册事件监听**

在构造函数 `modEventBus.addListener(this::commonSetup);` 之后加：

```java
        NeoForge.EVENT_BUS.addListener(SoundRelayHandler::onAtPosition);
        NeoForge.EVENT_BUS.addListener(SoundRelayHandler::onAtEntity);
```

并加 import:`net.neoforged.neoforge.common.NeoForge`。

- [ ] **Step 3: 编译验证**

Run: `.\gradlew.bat build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/shiningstage/create_shining_stage/SoundRelayHandler.java src/main/java/com/shiningstage/create_shining_stage/CreateShiningStage.java
git commit -m "feat: relay server-side sounds from microphones to bound speakers"
```

---

### Task 5: 资源文件（blockstate/物品模型/战利品表/双语 lang)

占位美术：麦克风借用原版 `sculk_sensor` 模型（带朝向旋转），音响借用 `jukebox` 模型，后续再替换自制贴图。

**Files:**
- Create: `src/main/resources/assets/create_shining_stage/blockstates/microphone.json`
- Create: `src/main/resources/assets/create_shining_stage/blockstates/speaker.json`
- Create: `src/main/resources/assets/create_shining_stage/models/item/microphone.json`
- Create: `src/main/resources/assets/create_shining_stage/models/item/speaker.json`
- Create: `src/main/resources/data/create_shining_stage/loot_table/blocks/microphone.json`
- Create: `src/main/resources/data/create_shining_stage/loot_table/blocks/speaker.json`
- Modify: `src/main/resources/assets/create_shining_stage/lang/en_us.json`
- Modify: `src/main/resources/assets/create_shining_stage/lang/zh_cn.json`

- [ ] **Step 1: blockstates/microphone.json**

```json
{
  "variants": {
    "facing=up":    { "model": "minecraft:block/sculk_sensor" },
    "facing=down":  { "model": "minecraft:block/sculk_sensor", "x": 180 },
    "facing=north": { "model": "minecraft:block/sculk_sensor", "x": 90 },
    "facing=south": { "model": "minecraft:block/sculk_sensor", "x": 90, "y": 180 },
    "facing=west":  { "model": "minecraft:block/sculk_sensor", "x": 90, "y": 270 },
    "facing=east":  { "model": "minecraft:block/sculk_sensor", "x": 90, "y": 90 }
  }
}
```

- [ ] **Step 2: blockstates/speaker.json**

```json
{
  "variants": {
    "": { "model": "minecraft:block/jukebox" }
  }
}
```

- [ ] **Step 3: models/item/microphone.json**

```json
{
  "parent": "minecraft:block/sculk_sensor"
}
```

- [ ] **Step 4: models/item/speaker.json**

```json
{
  "parent": "minecraft:block/jukebox"
}
```

- [ ] **Step 5: 两个 loot_table**（内容同 spotlight.json，仅 `name` 分别改为 `create_shining_stage:microphone` / `create_shining_stage:speaker`)

```json
{
  "type": "minecraft:block",
  "pools": [
    {
      "rolls": 1.0,
      "bonus_rolls": 0.0,
      "conditions": [
        {
          "condition": "minecraft:survives_explosion"
        }
      ],
      "entries": [
        {
          "type": "minecraft:item",
          "name": "create_shining_stage:microphone"
        }
      ]
    }
  ]
}
```

- [ ] **Step 6: en_us.json 追加键**

```json
  "block.create_shining_stage.microphone": "Microphone",
  "block.create_shining_stage.microphone.range": "Listening Range",
  "block.create_shining_stage.speaker": "Speaker",
  "block.create_shining_stage.speaker.bound": "Speaker bound to microphone",
  "block.create_shining_stage.speaker.bind_failed_dimension": "Binding failed: microphone is in another dimension",
  "block.create_shining_stage.speaker.bind_failed_unloaded": "Binding failed: microphone is not loaded",
```

- [ ] **Step 7: zh_cn.json 追加键**

```json
  "block.create_shining_stage.microphone": "麦克风",
  "block.create_shining_stage.microphone.range": "监听范围",
  "block.create_shining_stage.speaker": "音响",
  "block.create_shining_stage.speaker.bound": "已绑定到麦克风",
  "block.create_shining_stage.speaker.bind_failed_dimension": "绑定失败：麦克风在其他维度",
  "block.create_shining_stage.speaker.bind_failed_unloaded": "绑定失败：麦克风所在区块未加载",
```

- [ ] **Step 8: 编译验证**

Run: `.\gradlew.bat build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/
git commit -m "feat: add microphone/speaker blockstates, models, loot tables, and lang"
```

---

### Task 6: 实机验证

- [ ] **Step 1: 启动开发客户端**

Run: `.\gradlew.bat runClient`（由执行者人工操作游戏）

- [ ] **Step 2: 按清单逐项验证（对应 spec §10)**

1. 放置麦克风 + 拉杆：仅红石开启时中继生效。
2. 麦克风旁放音符盒发声 / 射箭落地 / 放置破坏方块 → 绑定音响重放相同声音，分类为"方块"（音量滑条可验证）。
3. 同一声源、不同距离两台麦克风各绑一个音响 → 近者音量大、远者小；滚轮调 range 后生效范围变化。
4. 音响旁再放一台开启的麦克风 + 第二音响 → 音响输出不被捕获，无二次中继（回声免疫）。
5. 拆麦克风 → 音响静音；拆音响 → 重新右键绑定流程可重新建立绑定。
6. 手持音响右键麦克风出 actionbar 提示；跨维度绑定（下界麦克风带回主世界放置）提示失败。
7. 创造物品栏两个物品可见；破坏掉落自身；中英文 lang 正常。

- [ ] **Step 3: 全部通过后，若有修复则提交**

```bash
git commit -m "fix: issues found during in-game verification"  # 如有改动
```
