package com.shiningstage.create_shining_stage;

import java.awt.Color;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.util.SableDistUtil;
import foundry.veil.api.client.render.VeilRenderBridge;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public class SpotlightRenderer implements BlockEntityRenderer<SpotlightBlockEntity> {
    /** Divergence of the frustum; bottom half-size = top + span * HALF_ANGLE_TAN. */
    static final float HALF_ANGLE_TAN = 0.3f;
    /** Half-size of the top (emitter) face; matches the laser pointer's 0.48 cross-section. */
    static final float TOP_HALF = 0.24f;
    /** Alpha of a single side at the emitter end (most opaque), scaled by the redstone control. */
    static final float MAX_ALPHA = 0.5f;
    /** Alpha of a single side at the far end (most transparent), scaled by the redstone control. */
    static final float MIN_ALPHA = 0.3f;
    /** Length of the transparent fade-out at the beam tip, in blocks (laser pointer uses 0.5; we use a longer, softer fade). */
    static final float END_FADE = 1.5f;
    /** Alpha of a side at the fade start, as a share of the emitter end. */
    private static final float TIP_SHARE = MIN_ALPHA / MAX_ALPHA;
    /**
     * The beam's shader program, from {@code assets/create_shining_stage/pinwheel/shaders/program/}.
     * See {@link #BEAM} for why the beam is drawn by this mod's own program rather than a vanilla
     * one.
     */
    private static final ResourceLocation BEAM_PROGRAM =
        ResourceLocation.fromNamespaceAndPath(CreateShiningStage.MOD_ID, "spotlight_beam/spotlight_beam");
    /**
     * Signal level the beam is pinned to inside a Create contraption. A contraption's block
     * entities are rebuilt in the contraption's own world, which is cut off from the redstone of
     * the level the contraption travels through, so a mounted spotlight would otherwise go dark.
     * This keeps it glowing at a fixed dim level instead.
     */
    static final int CONTRAPTION_SIGNAL = 4;

    /**
     * The beam, drawn by this mod's own shader program ({@link #BEAM_PROGRAM}).
     *
     * <p>A shader pack adapts to vanilla by replacing its shader getters, so a beam drawn with any
     * vanilla program is shaded by the pack as though it were a lit surface — measured against the
     * pack's own shadow map, with no light of its own and no say over its transparency. Veil's
     * programs are not reached by that substitution, so the beam is emitted light under every pack
     * and with no pack installed alike, and needs no pack-specific path. Drawing it here also keeps
     * its two scalars — opacity and taper — in float: the alpha of a vertex colour is only eight
     * bits wide, and a beam dimmed by a low signal rounds away to nothing inside it.
     */
    private static final RenderType BEAM = RenderType.create(
        "create_shining_stage:spotlight_beam",
        DefaultVertexFormat.POSITION_TEX_COLOR,
        VertexFormat.Mode.QUADS,
        1536, false, true,
        RenderType.CompositeState.builder()
            .setShaderState(VeilRenderBridge.shaderState(BEAM_PROGRAM))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setCullState(RenderStateShard.NO_CULL)
            // Never write depth: crossing translucent beams would depth-cull each other.
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(false));

    /** The frustum's four sides, as pairs of cross-section corner indices. */
    private static final int[][] SLABS = { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 0 } };

    public SpotlightRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(SpotlightBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(SpotlightBlockEntity be) {
        Vec3i normal = be.getBlockState().getValue(DirectionalBlock.FACING).getNormal();
        return new AABB(be.getBlockPos())
            .expandTowards(Vec3.atLowerCornerOf(normal).scale(SpotlightBlockEntity.MAX_RANGE))
            .inflate(TOP_HALF + SpotlightBlockEntity.MAX_RANGE * HALF_ANGLE_TAN);
    }

    @Override
    public void render(SpotlightBlockEntity be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                       int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }

        BlockPos pos = be.getBlockPos();
        Direction dir = be.getBlockState().getValue(DirectionalBlock.FACING);

        // Redstone can't be read per-face; a single signal drives both convergence and
        // opacity together. 0 = widest frustum + transparent, 15 = prism + full opacity.
        int signal = isInContraption(be) ? CONTRAPTION_SIGNAL : level.getBestNeighborSignal(pos);
        float control = signal / 15f;
        if (control <= 0f) {
            return;
        }

        Vec3i normal = dir.getNormal();
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 start = center.add(Vec3.atLowerCornerOf(normal).scale(0.5));
        Vec3 end = start.add(Vec3.atLowerCornerOf(normal).scale(be.getRange()));
        BlockHitResult hit = level.clip(new ClipContext(
            start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));

        // A hit in another physical space (ship/sub-level) sits at far-away coordinates, so a
        // vanilla distanceTo explodes and the beam looks infinite. Sable maps both points into a
        // common space for the true distance, exactly like the laser pointer does.
        double dist = hit.getType() == HitResult.Type.MISS
            ? end.distanceTo(center)
            : Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(
                SableDistUtil.getClientLevel(), center, hit.getLocation()));
        float beamLength = Math.min((float) (dist - 0.05), be.getRange());
        if (beamLength <= 0.1f) {
            return;
        }

        Color color = new Color(be.getLaserColor());
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;

        float span = beamLength - 0.5f;
        float bottomHalf = TOP_HALF + span * HALF_ANGLE_TAN * (1f - control);
        // Fade the last END_FADE blocks out to fully transparent, like the laser pointer's tip.
        float fadeStart = Math.max(0f, span - END_FADE);
        float midHalf = TOP_HALF + fadeStart * HALF_ANGLE_TAN * (1f - control);

        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(dir.getRotation());
        ms.mulPose(Axis.XP.rotationDegrees(-90f));
        // Local +Z now points along `dir`; origin at block center.
        ms.translate(0, 0, 0.5); // top face sits at the block face

        float[][] top = { {-TOP_HALF, -TOP_HALF}, {TOP_HALF, -TOP_HALF}, {TOP_HALF, TOP_HALF}, {-TOP_HALF, TOP_HALF} };
        float[][] mid = { {-midHalf, -midHalf}, {midHalf, -midHalf}, {midHalf, midHalf}, {-midHalf, midHalf} };
        float[][] bottom = { {-bottomHalf, -bottomHalf}, {bottomHalf, -bottomHalf}, {bottomHalf, bottomHalf}, {-bottomHalf, bottomHalf} };

        float sideAlpha = control * MAX_ALPHA;

        Matrix4f mat = ms.last().pose();
        VertexConsumer vc = buffer.getBuffer(BEAM);
        for (int[] slab : SLABS) {
            int i = slab[0];
            int j = slab[1];
            // Main segment: emitter -> fade start, at the redstone-scaled alpha.
            vertex(vc, mat, top[i][0], top[i][1], 0, sideAlpha, 1f, r, g, b);
            vertex(vc, mat, top[j][0], top[j][1], 0, sideAlpha, 1f, r, g, b);
            vertex(vc, mat, mid[j][0], mid[j][1], fadeStart, sideAlpha, TIP_SHARE, r, g, b);
            vertex(vc, mat, mid[i][0], mid[i][1], fadeStart, sideAlpha, TIP_SHARE, r, g, b);
            // Taper segment: fade start -> tip, fading out to transparent.
            vertex(vc, mat, mid[i][0], mid[i][1], fadeStart, sideAlpha, TIP_SHARE, r, g, b);
            vertex(vc, mat, mid[j][0], mid[j][1], fadeStart, sideAlpha, TIP_SHARE, r, g, b);
            vertex(vc, mat, bottom[j][0], bottom[j][1], span, sideAlpha, 0f, r, g, b);
            vertex(vc, mat, bottom[i][0], bottom[i][1], span, sideAlpha, 0f, r, g, b);
        }
        ms.popPose();
    }

    /**
     * True while this spotlight is part of a Create contraption rather than the static world.
     * A contraption's block entities are rendered against a {@link VirtualRenderWorld} built by
     * {@code ClientContraption}, and are rebuilt inside the contraption's own world, which is cut
     * off from the redstone of the level the contraption travels through. Create instantiates
     * that world for contraptions only, so this never matches a static block.
     */
    private static boolean isInContraption(SpotlightBlockEntity be) {
        return be.getLevel() instanceof VirtualRenderWorld;
    }

    /**
     * World-space culling box of a contraption, widened to cover the beams of any spotlights it
     * carries. Used by {@link EntityCullingMixin}: without it a beam leaves the view together with
     * the hull, since Create renders a contraption's block entities only while the entity passes
     * culling.
     *
     * <p>The spotlight positions and facings come from {@link SpotlightBeams}, captured once when the
     * contraption's block data was read — a contraption's blocks and their facings never change at
     * runtime, so this runs in constant time per query (an empty list, the common case, exits
     * immediately). Beam <em>lengths</em> do change, so each one is read from the live client block
     * entity, falling back to the length captured at read time when it is not available.
     */
    public static AABB contraptionCullingBox(AbstractContraptionEntity entity, AABB fallback) {
        Contraption contraption = entity.getContraption();
        if (contraption == null) {
            return fallback;
        }

        List<SpotlightBeams.Beam> beams = ((SpotlightBeams) contraption).createShiningStage$spotlightBeams();
        if (beams.isEmpty()) {
            return fallback;
        }

        AABB box = fallback;
        for (SpotlightBeams.Beam beam : beams) {
            Vec3i normal = beam.facing().getNormal();
            int range = beamRange(contraption, beam);

            Vec3 beamStart = Vec3.atCenterOf(beam.localPos()).add(Vec3.atLowerCornerOf(normal).scale(0.5));
            Vec3 beamEnd = beamStart.add(Vec3.atLowerCornerOf(normal).scale(range));
            // Hull of the drawn frustum: the emitter box plus the tip box, which is wider by the
            // beam's divergence over its length (widest when the redstone control is 0).
            AABB local = new AABB(beam.localPos())
                .inflate(TOP_HALF)
                .minmax(new AABB(beamEnd, beamEnd).inflate(TOP_HALF + (range - 0.5f) * HALF_ANGLE_TAN));

            box = box.minmax(worldBounds(local, entity));
        }
        return box;
    }

    /** Live beam length of a contraption spotlight, falling back to the value captured on read. */
    private static int beamRange(Contraption contraption, SpotlightBeams.Beam beam) {
        BlockEntity be = contraption.getBlockEntityClientSide(beam.localPos());
        return be instanceof SpotlightBlockEntity spotlight ? spotlight.getRange() : beam.storedRange();
    }

    /**
     * Maps a contraption-local box into world space. The contraption can be rotated arbitrarily, so
     * the box is rebuilt from its transformed corners. Both ends of the current tick are mapped —
     * the render pose is interpolated between them, and rotations in between stay inside the union.
     */
    private static AABB worldBounds(AABB local, AbstractContraptionEntity entity) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int pose = 0; pose < 2; pose++) {
            float partialTicks = pose == 0 ? 0f : 1f;
            boolean prevAnchor = pose == 0;
            for (int corner = 0; corner < 8; corner++) {
                Vec3 world = entity.toGlobalVector(new Vec3(
                    (corner & 1) == 0 ? local.minX : local.maxX,
                    (corner & 2) == 0 ? local.minY : local.maxY,
                    (corner & 4) == 0 ? local.minZ : local.maxZ), partialTicks, prevAnchor);
                minX = Math.min(minX, world.x);
                minY = Math.min(minY, world.y);
                minZ = Math.min(minZ, world.z);
                maxX = Math.max(maxX, world.x);
                maxY = Math.max(maxY, world.y);
                maxZ = Math.max(maxZ, world.z);
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Emits one beam vertex. The two scalars the beam needs — its opacity and its taper — ride in UV
     * rather than the vertex colour: a colour's alpha is eight bits wide, so a beam dimmed by a low
     * redstone signal rounds away to nothing inside it, while a UV is a float.
     *
     * @param opacity the opacity of this side, held constant along the beam
     * @param taper   emitter-to-tip fade, 1 at the emitter down to 0 at the tip
     */
    private static void vertex(VertexConsumer vc, Matrix4f mat, float x, float y, float z,
                               float opacity, float taper, float r, float g, float b) {
        vc.addVertex(mat, x, y, z)
            .setColor(r, g, b, 1f)
            .setUv(opacity, taper);
    }
}
