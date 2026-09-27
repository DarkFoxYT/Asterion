#version 330 core
uniform sampler2D TextureSampler;
in vec2 texCoord;
in vec4 tint;
out vec4 FragColor;
void main() {
    vec4 color = texture(TextureSampler, texCoord) * tint;
    if (color.a < 0.1) discard;
    FragColor = color;
}
