package com.shiningstage.create_shining_stage;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import foundry.veil.api.client.render.rendertype.VeilRenderType;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the cold spark fountain: {@link #SPARK_COUNT} camera-facing quads per machine, thrown along a
 * parabola and added to the frame.
 *
 * <p>This is not a particle system. There is no per-spark object, no client tick and no simulation —
 * every spark's whole life is a closed-form function of "how long ago did this shot start", so the
 * frame cost is one pass over a fixed count, and nothing has to be kept alive between frames. Two
 * consequences are worth the trade:
 *
 * <ul>
 * <li><b>Nothing drifts.</b> A spark is placed by its index, not by accumulating velocity, so a
 * dropped frame, a re-entered view or a client that joins mid-shot all produce the identical
 * fountain. The randomness is a hash of (machine position, shot serial, spark index), never a
 * {@code RandomSource} — a sequence can only be walked forward, so it cannot answer "spark number
 * 137" without having drawn the 136 before it, and a client that missed the start of the sequence
 * would draw a different pattern than its neighbour.
 * <li><b>Nothing is budgeted against it.</b> These quads never enter {@code ParticleEngine}, so the
 * vanilla particle limit cannot thin a fountain out; the only cost is the geometry itself.
 * </ul>
 *
 * <p>Cameras and contraptions come out right for free: the quads are emitted in block-local space
 * through whatever pose the caller set up, so a machine riding a contraption carries its spray along
 * with no transform of our own — only the billboard basis is pulled into that same local space.
 *
 * <p>Each sprite is rolled to point the way its spark was fired, which is what the launch direction is
 * for: a spark that leaves the nozzle leaned over stays leaned over, so the spray reads as a cone
 * thrown outwards rather than as a column of identically upright dots. Because the cone is deliberately
 * narrow — {@link #LATERAL_SPREAD} is a few percent of the launch speed — that tilt is only a few
 * degrees, so it takes an elongated sprite to see it at all; a round one hides it completely.
 *
 * <p>The sprite also ages with the spark: it is a strip of {@link #SPRITE_FRAMES} cells and the spark's
 * own age picks the cell, so a jet of them shows all of the cells at once, each on the sparks at the
 * matching point in their flight. That is the opposite of an animated sprite sheet, whose frame is
 * driven by a clock shared across the whole texture — every spark would switch cell on the same tick
 * and the spray would flicker between shapes instead of each spark burning out on its own.
 *
 * <p>The one thing missing is a world: no spark is stopped by a block above it. Sparks die on their
 * own parabola, {@link #FALL_DISTANCE} blocks past the apex, which is what a fountain built to shoot
 * into open air looks like.
 */
public class ColdSparkMachineRenderer implements BlockEntityRenderer<ColdSparkMachineBlockEntity> {
    /** Sparks per shot. One quad each; 200 quads is 800 triangles, one draw call, no allocations. */
    static final int SPARK_COUNT = 200;
    /**
     * Downward acceleration, in blocks per tick squared. With a shot's height H the launch speed
     * follows as sqrt(2*GRAVITY*H), so reaching the apex takes sqrt(2H/GRAVITY) ticks — about one second
     * for an 8-block spray, and about two for the tallest. A spark does not then ride the parabola back
     * down: it dies {@link #FALL_DISTANCE} blocks past the apex.
     */
    static final float GRAVITY = 0.036f;
    /**
     * How far past its apex a spark falls before it dies, in blocks. The whole descent is spent inside
     * this distance, so the fade-out is compressed into it and the sparks leave the air near the top of
     * the fountain instead of raining back down through it — a dropped spark would otherwise be drawn
     * falling for as long as it rose, which reads as an arc rather than as a spray.
     */
    static final float FALL_DISTANCE = 0.75f;
    /**
     * Ticks spent between the apex and the death point: the time to fall {@link #FALL_DISTANCE} blocks.
     * Independent of the launch speed, since every spark falls at the same rate.
     */
    private static final float FALL_TICKS = Mth.sqrt(2f * FALL_DISTANCE / GRAVITY);
    /**
     * Half-extent of a spark quad, in blocks, before the per-spark size variation — so a spark spans
     * about {@code 2*SPARK_SIZE} blocks, roughly a third of a block on average. Smaller than this and
     * the sparks stop reading as sparks at normal viewing distance and just sparkle; much larger and
     * they read as blobs rather than as sparks.
     */
    static final float SPARK_SIZE = 0.16f;
    /**
     * Cells in the spray sprite, stacked top to bottom, each as tall as it is wide (so three cells
     * make a 16x48 image). A spark is drawn from one cell at a time — the strip is not looped by the
     * atlas, the spark's own age picks the cell — which is what gives it a beginning, a middle and an
     * end rather than a per-frame flicker across the whole spray.
     */
    static final int SPRITE_FRAMES = 3;
    /**
     * Sideways launch speed as a share of the upward one, i.e. the half-angle of the spray cone. The
     * spread widens with height on its own (displacement works out at 4*LATERAL_SPREAD*H blocks), so
     * this stays small: 0.06 gives a 16-block fountain a ~3.8-block-wide top.
     */
    static final float LATERAL_SPREAD = 0.06f;
    /**
     * Radius of the disc the sparks are launched from, in blocks. Every spark starts somewhere inside
     * it rather than all of them at one point, so the spray comes out of an aperture the size of a
     * nozzle instead of from a mathematical point — at close range the base of the fountain otherwise
     * reads as a single bright dot. Two sixteenths is a little narrower than one spark quad, so this
     * fattens the mouth without scattering the spray.
     *
     * <p>A spark leaves from the side it is about to drift towards, so this widens the mouth without
     * deciding *where* the spray goes: it and the cone push the same way, and the spray simply starts
     * a couple of sixteenths closer to the direction it was always going to take.
     */
    static final float BIRTH_RADIUS = 2f / 16f;
    /**
     * Per-spark multipliers on the launch speed and on the quad's size, min..max. The speed one stays
     * small on purpose: a spark's apex goes with the square of its speed, so ±{@value} already spreads a
     * nominal 8-block spray over roughly 5.5 to 11 blocks. Wider than that and the spray-height setting
     * stops describing the height you get.
     */
    static final float SPEED_VARIATION = 0.18f;
    static final float SIZE_VARIATION = 0.4f;
    /** Brightness a spark is added at, held constant until the descent begins. */
    static final float BRIGHTNESS = 0.85f;
    /** Colour range the sparks are drawn from: a blue-white cold end and a near-white hot one. */
    static final float COOL_R = 0.72f, COOL_G = 0.86f, COOL_B = 1f;
    static final float HOT_R = 1f, HOT_G = 0.98f, HOT_B = 0.92f;

    /** The spray, from {@code assets/create_shining_stage/pinwheel/rendertypes/}. */
    private static final ResourceLocation SPRAY_RENDER_TYPE =
        ResourceLocation.fromNamespaceAndPath(CreateShiningStage.MOD_ID, "cold_spark");

    /**
     * Resolved on first draw rather than in the constructor: block entity renderers are built while the
     * resource reload that carries Veil's render types is still in flight, so at construction time the
     * definition may not have been read yet. Per-instance because the renderer is rebuilt on every
     * reload, which is exactly when a re-resolve is wanted.
     */
    private RenderType spray;
    private boolean sprayMissing;

    // Scratch, reused across machines: the renderer is rebuilt each resource reload, runs on the render
    // thread only and is never re-entered, so a fountain costs no allocation at all.
    private final Matrix4f inversePose = new Matrix4f();
    private final Vector3f localRight = new Vector3f();
    private final Vector3f localUp = new Vector3f();
    // World-space camera axes, kept alongside their local-space twins: the launch direction is
    // projected onto these to find each sprite's roll.
    private final Vector3f worldRight = new Vector3f();
    private final Vector3f worldUp = new Vector3f();
    private final Vector3f sparkVelocity = new Vector3f();

    public ColdSparkMachineRenderer(BlockEntityRendererProvider.Context context) {
    }

    /**
     * The spray reaches up to 16 blocks above the block it comes from, so the block's own section can
     * leave the frustum while a spray it is feeding is still on screen. Off-screen rendering keeps the
     * two together; the distance check stays in {@link #shouldRender}.
     */
    @Override
    public boolean shouldRenderOffScreen(ColdSparkMachineBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    /**
     * An idle machine costs nothing beyond this call: a fountain is only worth drawing while it has
     * sparks in the air, which is the shot itself plus the last spark's flight after it.
     */
    @Override
    public boolean shouldRender(ColdSparkMachineBlockEntity be, Vec3 cameraPos) {
        float elapsed = shotElapsed(be, 0f);
        return elapsed >= 0f && elapsed < sprayWindow(be)
            && BlockEntityRenderer.super.shouldRender(be, cameraPos);
    }

    @Override
    public AABB getRenderBoundingBox(ColdSparkMachineBlockEntity be) {
        // Sideways reach of the fastest spark: its lateral speed times its flight time, which works out
        // at 6*LATERAL_SPREAD*H — see LATERAL_SPREAD — plus the spark's own half-width and the radius
        // of the aperture it was launched from.
        float fastest = 1f + SPEED_VARIATION;
        double reach = 6.0 * LATERAL_SPREAD * fastest * fastest * be.getSprayHeight() + SPARK_SIZE
            + BIRTH_RADIUS;
        // The spray only leaves through the top, but the box is grown *symmetrically* so its centre
        // stays on the block. Sable decides which physics structure a block entity belongs to from this
        // box's centre (it redirects ClientHooks.isBlockEntityRendererVisible and resolves the
        // containing sub-level from there), so a box whose centre rode up with the fountain would stop
        // being found as soon as the spray reached past the edge of the ship — and the whole spray
        // would then be culled, since the untransformed plot-space box is nowhere near the camera.
        // Costing a little overdraw to keep the centre put is what makes the machine work on a ship.
        return new AABB(be.getBlockPos()).inflate(reach, be.getSprayHeight() + SPARK_SIZE, reach);
    }

    @Override
    public void render(ColdSparkMachineBlockEntity be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                       int light, int overlay) {
        float elapsed = shotElapsed(be, partialTick);
        if (elapsed < 0f || elapsed >= sprayWindow(be)) {
            return;
        }
        // The render type carries the shader and the texture, so this only ever has to be resolved once.
        // A missing definition is reported once and then drawn as nothing, rather than crashing the
        // frame: a renderer that throws here takes the whole game down with it.
        if (spray == null) {
            if (sprayMissing) {
                return;
            }
            try {
                spray = VeilRenderType.get(SPRAY_RENDER_TYPE);
            } catch (RuntimeException e) {
                CreateShiningStage.LOGGER.error("Cold spark spray render type '{}' failed to load", SPRAY_RENDER_TYPE, e);
            }
            if (spray == null) {
                sprayMissing = true;
                return;
            }
        }

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Matrix4f pose = ms.last().pose();
        // The billboard basis has to live in the same space the quads are emitted in, which is the
        // caller's pose — block-local in the world, contraption-local inside a contraption. Inverting
        // the pose maps the camera's world-space right/up into it, so one inversion per machine covers
        // every spark, and the pose's own rotation and scale come out right without being undone.
        inversePose.set(pose).invert();
        worldRight.set(camera.getLeftVector()).negate();
        worldUp.set(camera.getUpVector());
        localRight.set(worldRight);
        localUp.set(worldUp);
        inversePose.transformDirection(localRight);
        inversePose.transformDirection(localUp);

        float launchSpeed = launchSpeed(be.getSprayHeight());
        int serial = be.getSpraySerial();
        // Sparks are given out of one hash of the machine, the shot and the index, so the pattern is a
        // pure function of the three and identical on every client that sees the shot.
        int seed = be.getBlockPos().hashCode() * 0x9E3779B9 + serial * 0x85EBCA6B;

        VertexConsumer vc = buffer.getBuffer(spray);
        for (int i = 0; i < SPARK_COUNT; i++) {
            int h1 = mix(seed + i * 0x27D4EB2F);
            int h2 = mix(h1 ^ 0x165667B1);
            float birth = ColdSparkMachineBlockEntity.SPRAY_DURATION * (float) i / SPARK_COUNT;
            float age = elapsed - birth;
            if (age < 0f) {
                break; // emission is in index order, so nothing later has been born either
            }

            float speed = launchSpeed * (1f + SPEED_VARIATION * (unit(h1) * 2f - 1f));
            // A spark's rise lasts exactly speed/GRAVITY, which the sprite's own aging is keyed to —
            // see spriteCell. Named rather than folded into the life so both readers use one fact.
            float riseTicks = speed / GRAVITY;
            // Rise to the apex, then the fixed fall distance. Nothing is drawn past the death point,
            // so the spark leaves the air just below the top of the fountain rather than sinking all
            // the way back to the nozzle.
            float life = riseTicks + FALL_TICKS;
            if (age >= life) {
                continue;
            }

            float azimuth = unit(h1 >>> 16) * Mth.TWO_PI;
            float lateral = speed * LATERAL_SPREAD * (0.5f + unit(h2));
            float dirX = Mth.cos(azimuth);
            float dirZ = Mth.sin(azimuth);
            // The spark leaves from the side of the aperture it is about to drift towards, so the
            // launch offset and the deflection reinforce each other rather than being two unrelated
            // random directions that mostly cancel out. That is also what a nozzle does: its flow
            // diverges away from the centre, so what comes out on the left is thrown further left.
            // Uniform over the *disc* rather than over the radius — a radius taken linearly would
            // bunch the sparks around the centre and leave the rim bare, since a thin ring's area
            // shrinks with its radius.
            float birthRadius = BIRTH_RADIUS * Mth.sqrt(unit(h2 >>> 16));
            float birthX = dirX * birthRadius;
            float birthZ = dirZ * birthRadius;
            // The drift grows with age; the launch offset does not — it is where the spark started,
            // not how far it has travelled.
            float x = birthX + dirX * lateral * age;
            float z = birthZ + dirZ * lateral * age;
            // The parabola the launch speed and gravity imply: v0*t - g*t^2/2, apex at exactly the
            // configured spray height.
            float y = speed * age - 0.5f * GRAVITY * age * age;

            // Roll the sprite so its top edge points the way this spark was fired. The launch
            // direction is fixed at birth, so a spark keeps the orientation it left the nozzle at
            // instead of tumbling as gravity turns its velocity. The pose carries that direction into
            // the world — which is what makes it come out right on a contraption — and there it is
            // projected onto the camera's screen axes. Normalising the projection rather than taking
            // its angle keeps this to a handful of multiplies per spark, with no trigonometry.
            sparkVelocity.set(dirX * lateral, speed, dirZ * lateral);
            pose.transformDirection(sparkVelocity);
            float screenX = sparkVelocity.dot(worldRight);
            float screenY = sparkVelocity.dot(worldUp);
            float screenLength = Mth.sqrt(screenX * screenX + screenY * screenY);
            // Fired straight at or away from the camera there is no on-screen direction to point
            // along; the sprite stays upright rather than rolling on noise.
            float cos = 1f;
            float sin = 0f;
            if (screenLength > 1.0E-4f) {
                cos = screenY / screenLength;
                sin = screenX / screenLength;
            }

            // Full brightness from the moment it leaves the nozzle until the apex: the only thing that
            // dims a spark is its own descent, so the rise is a field of solid points rather than a
            // gradient. A fade-in here would be visible on *every* frame, because sparks are born
            // continuously through the shot, so there would always be some of them still fading up.
            float fadeOut = Mth.clamp((life - age) / FALL_TICKS, 0f, 1f);
            float alpha = BRIGHTNESS * fadeOut;
            if (alpha <= 0.004f) {
                continue;
            }

            float warmth = unit(h2 >>> 8);
            float r = Mth.lerp(warmth, COOL_R, HOT_R);
            float g = Mth.lerp(warmth, COOL_G, HOT_G);
            float b = Mth.lerp(warmth, COOL_B, HOT_B);
            float size = SPARK_SIZE * (1f + SIZE_VARIATION * (unit(h2 >>> 24) * 2f - 1f));

            quad(vc, pose, 0.5f + x, 1f + y, 0.5f + z, size, r, g, b, alpha, cos, sin,
                spriteCell(age, riseTicks));
        }
    }

    /**
     * Which cell of the sprite strip a spark shows, from its age. The rise is shared between every cell
     * but the last and the descent takes that last one — rather than slicing the whole life into equal
     * parts. The fall is a fixed {@link #FALL_TICKS} long however fast the spark was thrown, so on a
     * tall spray equal parts would run the shortest cell out well before the apex and the spark would
     * look burnt out halfway up. Keying the last cell to {@code riseTicks} instead puts it exactly at
     * the apex, which is where the art's shortest cell belongs.
     */
    private static int spriteCell(float age, float riseTicks) {
        if (age >= riseTicks) {
            return SPRITE_FRAMES - 1;
        }
        // The rise runs over the remaining cells, so three frames give it two. Clamped defensively:
        // the division is already inside [0, 1) here.
        return Mth.clamp((int) (age / riseTicks * (SPRITE_FRAMES - 1)), 0, SPRITE_FRAMES - 2);
    }

    /** World time since the current shot began, or -1 if this machine has never fired. */
    private static float shotElapsed(ColdSparkMachineBlockEntity be, float partialTick) {
        if (be.getSpraySerial() == 0 || be.getLevel() == null) {
            return -1f;
        }
        return be.getLevel().getGameTime() - be.getSprayStartTick() + partialTick;
    }

    /**
     * How long the spray lasts in total: the shot itself, plus the life of the sparks emitted last —
     * their rise plus the fixed fall. The renderer stops there, so an idle machine is skipped before any
     * geometry is built.
     */
    private static float sprayWindow(ColdSparkMachineBlockEntity be) {
        return ColdSparkMachineBlockEntity.SPRAY_DURATION + launchSpeed(be.getSprayHeight()) / GRAVITY
            + FALL_TICKS;
    }

    /** Upward launch speed that makes a spark's apex exactly {@code height} blocks above the nozzle. */
    private static float launchSpeed(int height) {
        return Mth.sqrt(2f * GRAVITY * height);
    }

    /** Emits one camera-facing spark quad, centred at the local point, sampling one sprite cell. */
    private void quad(VertexConsumer vc, Matrix4f pose, float cx, float cy, float cz, float size,
                      float r, float g, float b, float alpha, float cos, float sin, int cell) {
        // {@code cos}/{@code sin} roll the sprite about the view axis — the only rotation a
        // camera-facing quad has. Rotating the two basis vectors together keeps the quad in the screen
        // plane (their cross product is unchanged) and mirrors nothing, so the corners below still
        // wind the same way and the UVs still land on the right edges.
        float rightX = localRight.x, rightY = localRight.y, rightZ = localRight.z;
        float upX = localUp.x, upY = localUp.y, upZ = localUp.z;
        float rx = (rightX * cos - upX * sin) * size;
        float ry = (rightY * cos - upY * sin) * size;
        float rz = (rightZ * cos - upZ * sin) * size;
        float ux = (upX * cos + rightX * sin) * size;
        float uy = (upY * cos + rightY * sin) * size;
        float uz = (upZ * cos + rightZ * sin) * size;
        // Two triangles wound around the centre; nothing is culled and additive blending ignores the
        // order, so the corners only have to describe the quad.
        //
        // V spans the whole sprite and runs 0 at its *top* row, so cell {@code c} of the strip occupies
        // [c, c+1) / SPRITE_FRAMES and the camera-up corners take the cell's top edge, the camera-down
        // ones its bottom. Getting the pair backwards renders every spark upside down, which a
        // symmetric sprite hides — the placeholder used to be one, so the flip only showed on real art.
        // The cells carry transparent padding above and below their art precisely so these edges, which
        // land on texel boundaries under GL_NEAREST, never sample a lit texel of the neighbouring cell.
        float v0 = (float) cell / SPRITE_FRAMES;
        float v1 = (float) (cell + 1) / SPRITE_FRAMES;
        vertex(vc, pose, cx - rx - ux, cy - ry - uy, cz - rz - uz, 0f, v1, r, g, b, alpha);
        vertex(vc, pose, cx + rx - ux, cy + ry - uy, cz + rz - uz, 1f, v1, r, g, b, alpha);
        vertex(vc, pose, cx + rx + ux, cy + ry + uy, cz + rz + uz, 1f, v0, r, g, b, alpha);
        vertex(vc, pose, cx - rx + ux, cy - ry + uy, cz - rz + uz, 0f, v0, r, g, b, alpha);
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, float x, float y, float z,
                               float u, float v, float r, float g, float b, float alpha) {
        vc.addVertex(pose, x, y, z)
            .setColor(r, g, b, alpha)
            .setUv(u, v);
    }

    /** One byte of a hash as a 0..1 fraction. */
    private static float unit(int hash) {
        return (hash & 0xFF) / 255f;
    }

    /**
     * Integer hash (the "lowbias32" finalizer). Deterministic, allocation-free, and — unlike a
     * {@code RandomSource} — answerable out of order, which is what lets spark 137 be placed without
     * first walking 136 others.
     */
    private static int mix(int x) {
        x ^= x >>> 16;
        x *= 0x7FEB352D;
        x ^= x >>> 15;
        x *= 0x846CA68B;
        x ^= x >>> 16;
        return x;
    }
}
