#ifndef ASTERION_SCENE_DEPTH_GLSL
#define ASTERION_SCENE_DEPTH_GLSL
// Minecraft 26.2+ clears reversed scene depth to zero.
bool asterionSkyDepth(float depth) {
#ifdef ASTERION_REVERSED_DEPTH
    return depth <= 0.0000001;
#else
    return depth >= 0.9999;
#endif
}
float asterionClosestDepth(float a, float b) {
#ifdef ASTERION_REVERSED_DEPTH
    return max(a, b);
#else
    return min(a, b);
#endif
}
#endif
