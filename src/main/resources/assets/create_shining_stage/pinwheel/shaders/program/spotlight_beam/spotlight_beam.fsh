in vec4 vertexColor;

// x: the opacity of the beam's sides, y: the emitter-to-tip taper.
in vec2 beamData;

out vec4 fragColor;

void main() {
    // The beam is emitted light, so it takes no light from the world and casts none: the fragment's
    // own two scalars give its colour and its transparency and nothing else is consulted.
    fragColor = vec4(vertexColor.rgb, beamData.x * beamData.y);
}
