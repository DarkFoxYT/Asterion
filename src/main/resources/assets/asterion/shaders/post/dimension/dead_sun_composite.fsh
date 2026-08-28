#version 330

uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;
uniform sampler2D SunSampler;
uniform sampler2D BloomSampler;
layout(std140) uniform EclipseData { float Eclipse; };
layout(std140) uniform WorldDarkness { float Darkness; };

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 scene = texture(SceneSampler, texCoord);
    float sceneDepth = texture(DepthSampler, texCoord).r;
    vec4 sun = texture(SunSampler, texCoord);
    vec3 bloom = texture(BloomSampler, texCoord).rgb;
    float eclipse = clamp(Eclipse, 0.0, 1.0);
    float darkness = clamp(Darkness, 0.0, 1.0);
    // Pull down both exposure and saturation so the whole rendered world—not only fog and the
    // sun—visibly falls into shadow during the Eclipse while retaining enough detail to navigate.
    vec3 darkScene = scene.rgb * mix(1.0, 0.30, darkness);
    float luminance = dot(darkScene, vec3(0.2126, 0.7152, 0.0722));
    darkScene = mix(darkScene, vec3(luminance), darkness * 0.42);
    // Keep the world dark, but allow the Eclipse's pink-red corona to remain aggressively
    // emissive and bloom against it. Normal Dead Sun bloom is also slightly stronger.
    // Bloom is intentionally excluded from the opaque Eclipse silhouette. Otherwise the blur
    // pass smears the red corona back over the center after the sun shader made it black.
    float eclipseMask = clamp(sun.a * eclipse, 0.0, 1.0);
    float outerEdgeLeak = smoothstep(0.10, 0.58, eclipseMask)
            * (1.0 - smoothstep(0.72, 0.98, eclipseMask));
    float bloomTransmission = 1.0 - eclipseMask + outerEdgeLeak * 0.16;
    // The blurred buffer extends beyond the raw sun silhouette, so depth-test it again here.
    // A soft threshold avoids a harsh one-pixel seam along walls and the ground horizon.
    float skyVisibility = smoothstep(0.9975, 0.9999, sceneDepth);
    vec3 color = darkScene * (1.0 - sun.a) + sun.rgb
            + bloom * mix(0.86, 1.08, eclipse) * bloomTransmission * skyVisibility;
    // World-space fracture bolts are submitted with the shared lightning renderer before this
    // post pass. Preserve their red-hot current over the Sun volume; no crack geometry is
    // authored here, this only prevents the volumetric composite from painting over it.
    float sceneLuma = max(scene.r, max(scene.g, scene.b));
    float redCurrent = scene.r - max(scene.g * 1.35, scene.b * 1.05);
    float whiteCore = sceneLuma - 0.72;
    float renderedCurrent = smoothstep(0.08, 0.62, max(redCurrent, whiteCore));
    color = mix(color, scene.rgb * 1.85 + vec3(0.32, 0.015, 0.01),
            renderedCurrent * sun.a * 0.94);
    fragColor = vec4(color, scene.a);
}
