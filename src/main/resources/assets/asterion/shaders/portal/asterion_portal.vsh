#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 InstTransform0;
layout(location = 2) in vec4 InstTransform1;
layout(location = 3) in vec4 InstTransform2;
layout(location = 4) in vec4 InstTransform3;
layout(location = 5) in vec4 InstPortal;
layout(location = 6) in vec4 InstEffect;

uniform mat4 ProjViewMatrix;

out vec2 vUv;
flat out vec4 vPortal;
flat out vec4 vEffect;

void main() {
    mat4 model = mat4(InstTransform0, InstTransform1, InstTransform2, InstTransform3);
    gl_Position = ProjViewMatrix * model * vec4(Position, 1.0);
    vUv = Position.xz * 0.5 + 0.5;
    vPortal = InstPortal;
    vEffect = InstEffect;
}
