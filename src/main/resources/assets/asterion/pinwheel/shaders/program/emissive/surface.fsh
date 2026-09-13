#version 150

#include veil:fog

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in vec2 texCoord;
in vec4 vertexColor;
in float vertexDistance;

out vec4 fragColor;

void main() {
    vec4 texel = texture(Sampler0, texCoord);
    vec4 color = texel * vertexColor * ColorModulator;
    if (color.a < 0.01) discard;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
