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
    float t=Color.r;
    float drop=float(UV1.x-UV1.y);
    vec2 tangent=vec2(-Normal.z,Normal.x);
    float across=dot(UV0,tangent);
    // Water rolls over the rim with forward momentum, then accelerates downward.
    // A second shell gives the waterfall actual thickness instead of a flat decal.
    float shell=Color.b;
    float arc=4.6*(sqrt(t+.0025)-.05);
    float envelope=sin(t*3.14159265);
    float folds=sin(across*1.8-ticks*.115+t*12.0)*.19
               +sin(across*4.6+ticks*.081-t*21.0)*.085;
    float swell=envelope*(.30+folds)+shell*(.08+envelope*.24);
    vec3 position=Position+Normal*(arc+swell);
    position.y+=envelope*sin(across*2.3-ticks*.13+t*7.0)*.10;
    gl_Position=ProjMat*ModelViewMat*vec4(position,1.0);
    cascadePosition=position;
    cascadeWorld=UV0;
    cascadeTime=ticks;
    cascadeDrop=drop;
    cascadeAlong=t;
    cascadeNormal=Normal;
}
