#version 330

layout(std140) uniform WorldData {
    mat4 InvViewProj;
    vec4 CameraData;
    vec4 CameraForward;
};

out vec2 texCoord;

void main() {
    vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
    texCoord = uv;
}
