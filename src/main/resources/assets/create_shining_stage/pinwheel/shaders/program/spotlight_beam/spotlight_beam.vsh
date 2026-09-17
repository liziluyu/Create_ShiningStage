layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV;
layout(location = 2) in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;

// x: the opacity of the beam's sides, y: the emitter-to-tip taper.
// These ride in UV rather than in the vertex colour because the alpha of a vertex colour is only
// eight bits wide, and a spotlight dimmed by a low redstone signal needs steps finer than that.
out vec2 beamData;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color;
    beamData = UV;
}
