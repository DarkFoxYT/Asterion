#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;
out vec3 cascadePosition;
out vec2 cascadeWorld;
out float cascadeTime;
out float cascadeDrop;
out float cascadeAlong;
out vec3 cascadeNormal;
void main() {
    float ticks=float(UV2.x & 65535)+float(UV2.y & 65535)*65536.0+Color.g;
    float along=Color.r;
    // Pin both ends to the cached sea surfaces. Interior motion has no CPU cost.
    float bulge=sin(along*3.14159265)*(.13+.065*sin(dot(UV0,vec2(1.73,.91))-ticks*.19));
    vec3 position=Position+Normal*bulge;
    gl_Position=ProjMat*ModelViewMat*vec4(position,1.0);
    cascadePosition=position;
    cascadeWorld=UV0;
    cascadeTime=ticks;
    cascadeDrop=float(UV1.x-UV1.y);
    cascadeAlong=along;
    cascadeNormal=Normal;
}
