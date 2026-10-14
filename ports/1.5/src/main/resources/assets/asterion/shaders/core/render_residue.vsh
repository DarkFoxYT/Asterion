#version 330 core
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV;
layout(location = 2) in vec4 Transform0;
layout(location = 3) in vec4 Transform1;
layout(location = 4) in vec4 Transform2;
layout(location = 5) in vec4 Transform3;
layout(location = 6) in vec4 Effect;
uniform mat4 ProjViewMatrix;
out vec2 vUv;
out float vHeight;
flat out vec4 vEffect;
noperspective out vec2 vScreen;
void main() {
    vec3 p = Position;
    float time = Effect.y;
    float band = floor(p.y * 14.0 + time * 1.4);
    float tear = sin(band * 13.73 + Effect.w);
    p.x += sin(p.y * 15.0 + time * 5.0 + Effect.w) * .025
        + step(.94, tear) * sin(time * 9.0) * .095;
    p.z += sin(p.y * 19.0 - time * 3.0) * .018;
    if (p.y >= 2.10) {
        float y = p.y - 2.3;
        p.y = y * cos(Effect.z) - p.z * sin(Effect.z) + 2.3;
        p.z = y * sin(Effect.z) + p.z * cos(Effect.z);
    }
    p.y += sin(time * 1.1 + Effect.w) * .025;
    mat4 model = mat4(Transform0, Transform1, Transform2, Transform3);
    gl_Position = ProjViewMatrix * model * vec4(p, 1.0);
    vScreen = gl_Position.xy / max(gl_Position.w, .001);
    vUv = UV; vHeight = Position.y; vEffect = Effect;
}
