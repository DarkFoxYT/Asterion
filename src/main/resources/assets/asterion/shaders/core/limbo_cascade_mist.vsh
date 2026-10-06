#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;
out vec3 mistPosition;
out vec2 mistWorld;
out float mistTime;
out float mistHeight;
out float mistStrength;
void main() {
    float ticks=float(UV2.x & 65535)+float(UV2.y & 65535)*65536.0+Color.g;
    vec3 position=Position+Normal*Color.r*(.85+.25*sin(ticks*.024+dot(UV0,vec2(.13,.17))));
    gl_Position=ProjMat*ModelViewMat*vec4(position,1.0);
    mistPosition=position;
    mistWorld=UV0;
    mistTime=ticks;
    mistHeight=Color.r;
    mistStrength=Color.b;
}
