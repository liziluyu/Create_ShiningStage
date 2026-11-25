package com.shiningstage.create_shining_stage;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.shiningstage.create_shining_stage.client.beam.BeamRenderTypes;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Beam BER retained as a compatibility stub; the independent AFTER_PARTICLES pass owns drawing. */
@Mod.EventBusSubscriber(modid = CreateShiningStage.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SpotlightRenderer implements BlockEntityRenderer<SpotlightBlockEntity> {
    // Obstacle changes may stay stale for up to five normal client ticks; power and beam settings invalidate immediately.
    private static final int OCCLUSION_REFRESH_TICKS = 5;
    private static final Map<BlockPos, OcclusionEntry> FIXED_OCCLUSION = new HashMap<>();
    private static WeakReference<net.minecraft.client.multiplayer.ClientLevel> cacheLevel = new WeakReference<>(null);
    private static long clientTick;
    static final float HALF_ANGLE_TAN = 0.3f;
    static final float TOP_HALF = 0.24f;
    static final float MAX_ALPHA = 0.5f;
    static final float MIN_ALPHA = 0.3f;
    static final float END_FADE = 1.5f;
    private static final int CONTRAPTION_SIGNAL = 4;
    private static final int[][] SIDES = {{0,1},{1,2},{2,3},{3,0}};
    private static final float[][] CORNERS = {{-1,-1},{1,-1},{1,1},{-1,1}};

    public SpotlightRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(SpotlightBlockEntity be, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) { }

    @SubscribeEvent
    public static void afterParticles(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !BeamReady.ready()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        float partial = event.getPartialTick();
        Frustum frustum = event.getFrustum();
        for (SpotlightBlockEntity fixture : ClientStageLighting.trackedFixtures()) {
            if (fixture instanceof FillLightBlockEntity || fixture.isRemoved() || fixture.getLevel() != minecraft.level) continue;
            BlockPos pos = fixture.getBlockPos();
            if (!minecraft.level.hasChunkAt(pos) || minecraft.level.getBlockEntity(pos) != fixture) continue;
            boolean powered = fixture.getLevel().hasNeighborSignal(pos);
            if (!powered) {
                OcclusionEntry cached = FIXED_OCCLUSION.get(pos);
                if (cached != null && cached.fixture.get() == fixture) FIXED_OCCLUSION.remove(pos);
                continue;
            }
            draw(minecraft.level, fixture.getBlockState(), pos, Vec3.atCenterOf(pos),
                    SpotlightBlock.beamDirection(fixture.getBlockState()), fixture.getRange(),
                    fixture.getLaserColor(), fixture.getLevel().getBestNeighborSignal(pos), cameraPos,
                    event.getPoseStack(), buffers, frustum, fixture);
        }
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof AbstractContraptionEntity contraptionEntity) || entity.isRemoved()) continue;
            Contraption contraption = contraptionEntity.getContraption();
            if (contraption == null) continue;
            for (SpotlightBeams.Beam beam : ((SpotlightBeams) contraption).createShiningStage$spotlightBeams()) {
                BlockEntity be = contraption.getBlockEntityClientSide(beam.localPos());
                int range = be instanceof SpotlightBlockEntity spotlight ? spotlight.getRange() : beam.storedRange();
                int color = be instanceof SpotlightBlockEntity spotlight ? spotlight.getLaserColor() : SpotlightBlockEntity.DEFAULT_COLOR;
                Vec3 localCenter = Vec3.atCenterOf(beam.localPos());
                Vec3 origin = contraptionEntity.toGlobalVector(localCenter, partial, false);
                Vec3 normal = contraptionEntity.toGlobalVector(localCenter.add(beam.direction()), partial, false)
                        .subtract(origin).normalize();
                draw(minecraft.level, contraption.getBlocks().get(beam.localPos()).state(),
                        BlockPos.containing(localCenterToWorldCenter(contraptionEntity, localCenter, partial)), origin, normal, range, color, CONTRAPTION_SIGNAL,
                        cameraPos, event.getPoseStack(), buffers, frustum);
            }
        }
        buffers.endBatch(BeamRenderTypes.BEAM);
    }

    /** Advance only on unpaused client ticks; render events may run several times per tick. */
    public static void tickOcclusionCache() {
        Minecraft minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null) {
            clearOcclusionCache();
            return;
        }
        ensureCacheWorld(level);
        if (!minecraft.isPaused()) clientTick++;

        // Observe power even when the beam is offscreen or shaders prevent the render pass.
        Iterator<Map.Entry<BlockPos, OcclusionEntry>> entries = FIXED_OCCLUSION.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<BlockPos, OcclusionEntry> entry = entries.next();
            SpotlightBlockEntity fixture = entry.getValue().fixture.get();
            if (fixture == null || fixture.isRemoved() || fixture.getLevel() != level
                    || !level.hasChunkAt(entry.getKey())
                    || level.getBlockEntity(entry.getKey()) != fixture
                    || !level.hasNeighborSignal(entry.getKey())) entries.remove();
        }
    }

    public static void chunkUnloaded(net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.world.level.ChunkPos chunk) {
        if (cacheLevel.get() != level) return;
        FIXED_OCCLUSION.keySet().removeIf(pos -> (pos.getX() >> 4) == chunk.x && (pos.getZ() >> 4) == chunk.z);
    }

    public static void clearOcclusionCache() {
        FIXED_OCCLUSION.clear();
        cacheLevel = new WeakReference<>(null);
    }

    private static void ensureCacheWorld(net.minecraft.client.multiplayer.ClientLevel level) {
        if (cacheLevel.get() != level) {
            clearOcclusionCache();
            cacheLevel = new WeakReference<>(level);
        }
    }

    private static Vec3 localCenterToWorldCenter(AbstractContraptionEntity entity, Vec3 localCenter, float partial) {
        return entity.toGlobalVector(localCenter, partial, false);
    }

    private static void draw(net.minecraft.client.multiplayer.ClientLevel level,
            net.minecraft.world.level.block.state.BlockState state, BlockPos owner, Vec3 center,
            Vec3 direction, int range, int color, int signal, Vec3 camera, PoseStack pose,
            MultiBufferSource buffers, Frustum frustum) {
        draw(level, state, owner, center, direction, range, color, signal, camera, pose, buffers, frustum, null);
    }

    private static void draw(net.minecraft.client.multiplayer.ClientLevel level,
            net.minecraft.world.level.block.state.BlockState state, BlockPos owner, Vec3 center,
            Vec3 direction, int range, int color, int signal, Vec3 camera, PoseStack pose,
            MultiBufferSource buffers, Frustum frustum, SpotlightBlockEntity fixedFixture) {
        float control = Math.max(0, Math.min(15, signal)) / 15f;
        if (fixedFixture != null && control <= 0f) FIXED_OCCLUSION.remove(owner);
        if (control <= 0f || direction.lengthSqr() < 1.0E-8) return;
        Vec3 normal = direction.normalize();
        Vec3 origin = center.add(normal.scale(0.5));
        Vec3 end = origin.add(normal.scale(range));
        if (frustum != null && !frustum.isVisible(new AABB(origin, end).inflate(TOP_HALF + range * HALF_ANGLE_TAN))) return;
        float length = fixedFixture == null
                ? clippedLength(level, owner, origin, end, range)
                : cachedFixedLength(level, fixedFixture, owner, origin, end, normal, range);
        if (length <= 0.1f) return;

        float span = length;
        float fadeStart = Math.max(0f, span - END_FADE);
        float midHalf = TOP_HALF + fadeStart * HALF_ANGLE_TAN * (1f - control);
        float bottomHalf = TOP_HALF + span * HALF_ANGLE_TAN * (1f - control);
        float alpha = control * MAX_ALPHA;
        float tip = MIN_ALPHA / MAX_ALPHA;
        float r = ((color >> 16) & 255) / 255f, g = ((color >> 8) & 255) / 255f, b = (color & 255) / 255f;
        pose.pushPose();
        pose.translate(origin.x - camera.x, origin.y - camera.y, origin.z - camera.z);
        pose.mulPose(new Quaternionf().rotationTo(0, 0, 1, (float) normal.x, (float) normal.y, (float) normal.z));
        Matrix4f mat = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(BeamRenderTypes.BEAM);
        for (int[] side : SIDES) {
            int i = side[0], j = side[1];
            vertex(vc, mat, i, TOP_HALF, 0, alpha, 1, r, g, b);
            vertex(vc, mat, j, TOP_HALF, 0, alpha, 1, r, g, b);
            vertex(vc, mat, j, midHalf, fadeStart, alpha, tip, r, g, b);
            vertex(vc, mat, i, midHalf, fadeStart, alpha, tip, r, g, b);
            vertex(vc, mat, i, midHalf, fadeStart, alpha, tip, r, g, b);
            vertex(vc, mat, j, midHalf, fadeStart, alpha, tip, r, g, b);
            vertex(vc, mat, j, bottomHalf, span, alpha, 0, r, g, b);
            vertex(vc, mat, i, bottomHalf, span, alpha, 0, r, g, b);
        }
        pose.popPose();
    }

    private static float cachedFixedLength(net.minecraft.client.multiplayer.ClientLevel level,
            SpotlightBlockEntity fixture, BlockPos owner, Vec3 origin, Vec3 end, Vec3 direction, int range) {
        ensureCacheWorld(level);
        BlockPos key = owner.immutable();
        OcclusionEntry cached = FIXED_OCCLUSION.get(key);
        if (cached != null && (cached.fixture.get() != fixture || cached.range != range
                || !cached.direction.equals(direction))) {
            FIXED_OCCLUSION.remove(key);
            cached = null;
        }
        if (cached == null || clientTick >= cached.nextRefreshTick) {
            float length = clippedLength(level, owner, origin, end, range);
            long phase = 1 + Math.floorMod(key.hashCode(), OCCLUSION_REFRESH_TICKS);
            long next = clientTick + ticksUntilPhase(phase, clientTick);
            FIXED_OCCLUSION.put(key, new OcclusionEntry(fixture, direction, range, length, next));
            return length;
        }
        return cached.length;
    }

    private static long ticksUntilPhase(long phase, long tick) {
        long ticks = Math.floorMod(phase - Math.floorMod(tick, OCCLUSION_REFRESH_TICKS), OCCLUSION_REFRESH_TICKS);
        return ticks == 0 ? OCCLUSION_REFRESH_TICKS : ticks;
    }

    private static float clippedLength(net.minecraft.client.multiplayer.ClientLevel level,
            BlockPos owner, Vec3 origin, Vec3 end, int range) {
        BlockHitResult hit = level.clip(SpotlightBlock.beamClipContext(owner, origin, end));
        return hit.getType() == HitResult.Type.MISS ? range
                : (float) Math.max(0, Math.min(range, origin.distanceTo(hit.getLocation()) - 0.05));
    }

    private static final class OcclusionEntry {
        private final WeakReference<SpotlightBlockEntity> fixture;
        private final Vec3 direction;
        private final int range;
        private final float length;
        private final long nextRefreshTick;

        private OcclusionEntry(SpotlightBlockEntity fixture, Vec3 direction, int range,
                float length, long nextRefreshTick) {
            this.fixture = new WeakReference<>(fixture);
            this.direction = direction;
            this.range = range;
            this.length = length;
            this.nextRefreshTick = nextRefreshTick;
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, int corner, float half, float z,
            float opacity, float taper, float r, float g, float b) {
        vc.vertex(mat, CORNERS[corner][0] * half, CORNERS[corner][1] * half, z)
                .uv(opacity, taper).color(r, g, b, 1f).endVertex();
    }

    private static final class BeamReady {
        static boolean ready() { return com.shiningstage.create_shining_stage.client.beam.BeamShaders.beam() != null; }
    }
}
