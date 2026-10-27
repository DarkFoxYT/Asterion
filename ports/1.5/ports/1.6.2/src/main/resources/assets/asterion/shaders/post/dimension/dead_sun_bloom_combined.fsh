#version 330
uniform sampler2D InSampler;
layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; };
layout(std140) uniform AsterionQuality { float Quality; };
in vec2 texCoord;
out vec4 fragColor;
void main() {
    // Bilinear 2D kernel: one quarter-resolution draw, no horizontal intermediate.
    vec2 stepUv = vec2(Quality >= 1.5 ? 3.2 : 1.4) / max(OutSize,vec2(1.0));
    vec3 glow=texture(InSampler,texCoord).rgb*0.25;
    glow+=(texture(InSampler,texCoord+vec2(stepUv.x,0)).rgb
          +texture(InSampler,texCoord-vec2(stepUv.x,0)).rgb
          +texture(InSampler,texCoord+vec2(0,stepUv.y)).rgb
          +texture(InSampler,texCoord-vec2(0,stepUv.y)).rgb)*0.125;
    glow+=(texture(InSampler,texCoord+stepUv).rgb
          +texture(InSampler,texCoord-stepUv).rgb
          +texture(InSampler,texCoord+vec2(stepUv.x,-stepUv.y)).rgb
          +texture(InSampler,texCoord+vec2(-stepUv.x,stepUv.y)).rgb)*0.0625;
    fragColor=vec4(glow,1.0);
}
