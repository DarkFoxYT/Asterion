#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV;
layout(location = 2) in vec3 Center;
layout(location = 3) in float Size;
layout(location = 4) in vec4 InstColor;
layout(location = 5) in vec4 UvRect;

uniform mat4 ProjectionMatrix;
uniform mat4 ViewMatrix;

out vec2 planeUV;
out vec4 fogColor;
out float cameraDistance;
out vec2 textureMotion;

void main() {
    vec4 center = ViewMatrix * vec4(Center, 1.0);
    gl_Position = ProjectionMatrix * vec4(center.xy + Position.xy * Size, center.zw);
    planeUV = UV;
    fogColor = InstColor;
    cameraDistance = length(Center);
    textureMotion = UvRect.xy;
}
