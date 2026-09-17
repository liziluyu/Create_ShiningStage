package com.shiningstage.create_shining_stage;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.contraptions.Contraption;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/**
 * The spotlights carried by a {@link Contraption}, captured when its block data is read so that
 * {@link SpotlightRenderer} can widen the contraption's culling box without rescanning every block
 * of the structure on every culling query.
 *
 * <p>A contraption's block data only changes when it is (re)read from NBT — nothing can add, remove
 * or rotate blocks at runtime. Runtime changes are block <em>states</em> (levers, doors, controls),
 * which never move a spotlight, so captured positions and facings stay valid for the contraption's
 * lifetime. Beam lengths can still change, so those are read from the live client block entity
 * instead of being cached.
 */
public interface SpotlightBeams {
    /** Captured spotlights, in contraption-local space; empty if the contraption carries none. */
    List<Beam> createShiningStage$spotlightBeams();

    /** One spotlight of a contraption, in contraption-local space. */
    record Beam(BlockPos localPos, Direction facing, int storedRange) {
    }

    static List<Beam> scan(Contraption contraption) {
        List<Beam> beams = new ArrayList<>();
        for (StructureBlockInfo info : contraption.getBlocks().values()) {
            BlockState state = info.state();
            if (state.getBlock() instanceof SpotlightBlock) {
                beams.add(new Beam(info.pos(), state.getValue(DirectionalBlock.FACING), storedRange(info)));
            }
        }
        return List.copyOf(beams);
    }

    /** Beam length stored in the contraption's block data, clamped to the value box's bounds. */
    private static int storedRange(StructureBlockInfo info) {
        CompoundTag nbt = info.nbt();
        // Legacy contraptions can lack the behaviour tag; assume the longest beam so that a
        // longer-than-assumed beam is never culled away.
        if (nbt == null || !nbt.contains("ScrollValue")) {
            return SpotlightBlockEntity.MAX_RANGE;
        }
        return Mth.clamp(nbt.getInt("ScrollValue"), SpotlightBlockEntity.MIN_RANGE, SpotlightBlockEntity.MAX_RANGE);
    }
}
