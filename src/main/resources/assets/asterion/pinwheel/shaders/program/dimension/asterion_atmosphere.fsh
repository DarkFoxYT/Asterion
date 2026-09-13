#version 150

uniform sampler2D DiffuseSampler0;
uniform float GameTime;

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 value) {
    vec3 p = fract(vec3(value.xyx) * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

void main() {
    vec4 scene = texture(DiffuseSampler0, texCoord);
    float horizon = smoothstep(1.0, 0.18, abs(texCoord.y - 0.52) * 1.65);
    float grain = hash(floor(texCoord * vec2(640.0, 360.0) + GameTime * 37.0));
    vec3 dusty = vec3(0.24, 0.145, 0.085) * (0.75 + grain * 0.25);
    float strength = 0.075 * horizon;
    vec3 color = mix(scene.rgb, scene.rgb * vec3(0.94, 0.90, 0.84) + dusty, strength);
    fragColor = vec4(color, scene.a);
}
