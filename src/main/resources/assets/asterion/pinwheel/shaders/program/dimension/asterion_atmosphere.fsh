#version 150

uniform sampler2D DiffuseSampler0;
uniform sampler2D DiffuseDepthSampler;
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
    float depth = texture(DiffuseDepthSampler, texCoord).r;
    float distanceFog = smoothstep(0.72, 0.997, depth);
    float horizon = 1.0 - smoothstep(0.16, 0.78, abs(texCoord.y - 0.54));
    float grain = hash(floor(texCoord * vec2(420.0, 236.0) + GameTime * 19.0));
    vec3 dust = mix(vec3(0.105, 0.050, 0.025), vec3(0.19, 0.095, 0.037), grain);
    float strength = distanceFog * horizon * (0.075 + grain * 0.025);
    vec3 color = mix(scene.rgb, scene.rgb * vec3(0.965, 0.945, 0.91) + dust, strength);
    fragColor = vec4(color, scene.a);
}
