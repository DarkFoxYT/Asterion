#version 150

#include veil:fog

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;

in vec2 texCoord;
in vec4 vertexColor;
in float vertexDistance;

out vec4 fragColor;

void main() {
    vec4 texel = texture(Sampler0, texCoord);
    vec4 color = texel * vertexColor * ColorModulator;
    if (color.a < 0.01) discard;

    // HDR values give Veil's blur enough energy without bleaching the visible
    // surface pass. Alpha remains texture-authored for clean cutout edges.
    float fogFade = linear_fog_fade(vertexDistance, FogStart, FogEnd);
    fragColor = vec4(color.rgb * 1.65 * fogFade, color.a * fogFade);
}
