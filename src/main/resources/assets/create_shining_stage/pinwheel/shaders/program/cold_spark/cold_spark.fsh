uniform sampler2D Sampler0;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 spark = texture(Sampler0, texCoord);
    // The sparks are added to the frame, in the sprite's own shape and dimmed by the per-spark
    // brightness the vertex carries. Nothing from the world is consulted: a cold spark is light it
    // makes itself, so it takes no light from the level and ignores the lightmap it was handed.
    //
    // The alpha here is load-bearing: the render type must blend with SRC_ALPHA/ONE (Veil's
    // LIGHTNING), not ADDITIVE, which vanilla implements as ONE/ONE and which would discard this
    // alpha entirely — painting each spark as a flat white square the size of its quad.
    fragColor = vec4(spark.rgb * vertexColor.rgb, spark.a * vertexColor.a);
}
