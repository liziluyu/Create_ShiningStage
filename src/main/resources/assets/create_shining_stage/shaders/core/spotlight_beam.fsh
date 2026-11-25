#version 150
in vec4 vertexColor;
in vec2 beamData;
out vec4 fragColor;
void main() { fragColor = vec4(vertexColor.rgb, beamData.x * beamData.y); }
