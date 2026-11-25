#version 150
uniform sampler2D Sampler0;
in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;
void main() { vec4 spark = texture(Sampler0, texCoord); fragColor = vec4(spark.rgb * vertexColor.rgb, spark.a * vertexColor.a); }
