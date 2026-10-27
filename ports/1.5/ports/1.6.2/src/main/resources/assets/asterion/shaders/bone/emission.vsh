#version 330 core
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in mat4 Pose;
layout(location = 6) in vec4 Color;
layout(location = 7) in vec4 UvScale;
uniform mat4 ProjViewMatrix;
out vec2 texCoord;
out vec4 tint;
void main() {
    gl_Position = ProjViewMatrix * Pose * vec4(Position, 1.0);
    texCoord = UV0 * UvScale.xy;
    tint = Color;
}
