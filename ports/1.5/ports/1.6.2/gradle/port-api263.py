"""26.3 API changes, applied only to generated sources of the 1.6 release."""
import re

def remove_method(text, name):
    """Remove a named obsolete override, preserving all neighbouring methods."""
    pattern = r'\s*@Override\s+(?:public|protected)[^{};]*?\b' + name + r'\([^{}]*?\)\s*\{'
    while match := re.search(pattern, text):
        end, depth = match.end(), 1
        while depth:
            if text[end] == '{': depth += 1
            elif text[end] == '}': depth -= 1
            end += 1
        text = text[:match.start()] + '\n' + text[end:]
    return text

for path in root.rglob('*.java'):
    if platform in {'forge', 'neoforge'} and path.name in {'ForgeLevelRendererMixin.java', 'NativeRenderEvents.java', 'FuelValueEvents.java'}:
        path.unlink()
        continue
    if path.name == 'PositionCenters.java':
        continue
    text = path.read_text(encoding='utf-8')
    if path.name == 'MinotaurGeoRenderer.java':
        text = re.sub(r'(shouldRender\(MinotaurEntity boss,\s*net\.minecraft\.client\.renderer\.culling\.Frustum frustum,\s*double x, double y, double z)\)',
                      r'\1, float partialTick)', text)
    if path.name == 'LevelRenderContext.java':
        text = text.replace('import net.minecraft.client.renderer.MultiBufferSource;', '')
        text = text.replace('MultiBufferSource.BufferSource bufferSource();',
            'net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector();')
    if path.name != 'PoseTransforms.java':
        text = re.sub(r'\b(poses|poseStack|modelPose)\.mulPose\(',
            r'net.krodark.asterion.client.render.PoseTransforms.apply(\1, ', text)
    text = re.sub(r'([\w.]+(?:\([^()]*\))?)\.getCenter\(\)',
        lambda m: m.group() if m[1] == 'region' else 'net.krodark.asterion.compat.PositionCenters.center(' + m[1] + ')', text)
    text = text.replace('EntityType.MARKER', 'net.minecraft.world.entity.EntityTypes.MARKER')
    interpolation = re.search(r'private final InterpolationHandler interpolation = ([^;]+);', text)
    if interpolation:
        text = text[:interpolation.start()] + text[interpolation.end():]
        text = text.replace('public InterpolationHandler getInterpolation() { return interpolation; }',
            'protected InterpolationHandler createInterpolationHandler() { return ' + interpolation[1] + '; }')
        text = re.sub(r'\binterpolation\.', 'getInterpolation().', text)
    text = re.sub(r'(\b(?:player|target|entity|client\.player))\.invulnerableTime\s*=\s*([^;]+);', r'\1.setInvulnerableTime(\2);', text)
    text = text.replace('((LivingEntity)(Object)this).invulnerableTime = 0;', '((LivingEntity)(Object)this).setInvulnerableTime(0);')
    text = re.sub(r'\b([\w.]+)\.swing\(([^,;]+)\);', r'net.krodark.asterion.entity.EntityVersionCompatibility.swing(\1, \2, false);', text)
    text = re.sub(r'(?<![\w.])swing\(([^,;]+)\);', r'net.krodark.asterion.entity.EntityVersionCompatibility.swing(this, \1, false);', text)
    text = re.sub(r'player.drop\((reward|bloom|stack|weapon), false\)',
        r'net.krodark.asterion.entity.EntityVersionCompatibility.drop(player, \1, false)', text)
    # HUD visibility moved from Options into Gui; keep the same restoration lifecycle.
    text = re.sub(r'([\w.()]+)\.options\.hideGui\s*=\s*([^;]+);', r'net.krodark.asterion.client.render.HudVisibility.hidden(\1, \2);', text)
    text = re.sub(r'([\w.()]+)\.options\.hideGui\b', r'net.krodark.asterion.client.render.HudVisibility.hidden(\1)', text)
    text = text.replace('client.gui.setOverlayMessage(', 'client.gui.hud.setOverlayMessage(')
    if '/block/' in path.as_posix() or path.name == 'TidalWaterBlock.java':
        text = remove_method(text, 'codec')
        text = re.sub(r'\s*public static final MapCodec<(TidalWaterBlock|PassionBloomBlock)> CODEC = simpleCodec\(\1::new\);', '', text)
    if path.name == 'ShaleSpikeBlock.java':
        text = text.replace('super(properties)', 'super(net.minecraft.world.level.block.Blocks.POINTED_DRIPSTONE.defaultBlockState(), properties)')
    if path.name == 'Asterion.java':
        text = re.sub(r'Registry.register\(\s*BuiltInRegistries.FEATURE,\s*',
            'net.krodark.asterion.worldgen.FeatureRegistration.register(', text)
        text = text.replace('net.fabricmc.fabric.api.registry.FuelValueEvents.BUILD.register((builder, context) ->\n                builder.add(DEADWOOD_STICK, context.baseSmeltTime() / 2));', '')
        text = text.replace('new Item(new Item.Properties().setId(key)));\n    }\n\n    private static Item registerForgedComponentItem',
            'new Item(name.equals("deadwood_stick") ? new Item.Properties().setId(key)\n'
            '                .component(net.minecraft.core.component.DataComponents.COOKING_FUEL,\n'
            '                    new net.minecraft.world.item.component.CookingFuel(\n'
            '                        new net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt.Constant(100),\n'
            '                        new net.minecraft.world.level.storage.loot.providers.number.floats.ResolvableFloat.Constant(1F)))\n'
            '                : new Item.Properties().setId(key)));\n    }\n\n    private static Item registerForgedComponentItem')
    if path.name in {'AuthoredForge.java', 'ForgeDepths.java'}:
        text = re.sub(r'(\b\w+)\.info\(\)\.pos\(\)', r'\1.pos()', text)
        text = re.sub(r'(\b\w+)\.info\(\)\.state\(\)', r'\1.state()', text)
        text = text.replace('.findFirst().orElseThrow().info().pos()', '.findFirst().orElseThrow().pos()')
    if path.name == 'AsterionEmissiveBuffer.java':
        text = text.replace('.withVertexFormat(source.getVertexFormat(),source.getVertexFormatMode())',
            '.withVertexBinding(0, source.getVertexFormatBinding(0)).withPrimitiveTopology(source.getPrimitiveTopology())')
        text = text.replace('for(var sampler:source.getSamplers())builder.withSampler(sampler);',
            'for(var layout:source.getBindGroupLayouts())builder.withBindGroupLayout(layout);')
        text = text.replace('for(var uniform:source.getUniforms())builder.withUniform(uniform.name(),uniform.type());', '')
    if path.name == 'StaticVineMesh.java' and 'setUv3(' not in text:
        text = text.replace('implements VertexConsumer {', 'implements VertexConsumer {\n    @Override public VertexConsumer setUv3(float u, float v) { return this; }')
    if path.name == 'GeckoAnimationCacheMixin.java':
        text = re.sub(r'writeToTexture\(texture, cached,\s*0, 0, 0, 0, cached.getWidth\(\), cached.getHeight\(\), 0, 0\)',
            'writeToTexture(texture, cached)', text)
    if path.name == 'PortClientSmoke.java':
        text = text.replace('client.screen', 'client.gui.screen()').replace('client.setScreen(', 'client.gui.setScreen(')
        text = text.replace('client.getOverlay()', 'client.gui.overlay()')
        text = text.replace('client.getWindow().updateVsync(false);', '')
        text = text.replace('client.getMainRenderTarget()', 'client.gameRenderer.mainRenderTarget()')
    if path.name == 'HeavyWaterTripwireMixin.java':
        text = text.replace('setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z',
            'setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z')
        text = text.replace('BlockState next, int flags, Operation<Boolean>', 'BlockState next, Operation<Boolean>')
        text = text.replace('original.call(level, pos, next, flags)', 'original.call(level, pos, next)')
    if platform in {'forge', 'neoforge'} and path.name == 'HeavyWaterBucketMixin.java':
        text = text.replace('Inject(method = "emptyContents",', 'Inject(method = EMPTY_CONTENTS_STACK_AWARE,')
        text = text.replace('BlockHitResult hit,', 'BlockHitResult hit, net.minecraft.world.item.ItemStack stack,')
    if path.name == 'ForgePlayerRendererMixin.java':
        text = text.replace('double x, double y, double z,', 'double x, double y, double z, float partial,')
    if path.name == 'RenderRemnantCaptureFrameMixin.java':
        text = text.replace('Lcom/mojang/blaze3d/systems/CommandEncoder;', 'Lcom/mojang/renderpearl/api/commands/CommandEncoder;')
        text = text.replace('@Inject(method = "renderLevel", at = @At(value = "INVOKE",', '@Inject(method = "render", at = @At(value = "INVOKE",')
    if path.name == 'AmneticBloomPerformanceMixin.java':
        if platform in {'forge', 'neoforge'}:
            # The native path always runs its prefilter: threshold zero means
            # bloom every terrain pixel, unlike the GL path's skip sentinel.
            # Preserve the real threshold for emissive sources and guard the
            # unwanted whole-scene fallback before allocating/rendering it.
            text = '''package net.krodark.asterion.mixin;
import com.meekdev.amnetic.client.bloom.BloomSettings;
import com.meekdev.amnetic.client.bloom.internal.BloomRenderer;
import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;
import net.krodark.asterion.client.light.AsterionEmissiveConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value = BloomRenderer.class, remap = false)
public abstract class AmneticBloomPerformanceMixin {
    @Inject(method = "renderNative", at = @At("HEAD"), cancellable = true, require = 1)
    private void asterion$nativeBloomPolicy(BloomSettings settings, CallbackInfo callback) {
        boolean emissive = GBufferTargets.INSTANCE.isPopulated() && GBufferTargets.INSTANCE.hasEmissive();
        if (net.krodark.asterion.port.client.ShaderCompatibility.externalShadersActive()
                || (!emissive && !AsterionEmissiveConfig.sceneBloomEnabled())) callback.cancel();
    }
}
'''
        else:
            text = text.replace('@WrapOperation(method = "render",', '@WrapOperation(method = {"render", "renderNative"},')
    if path.name == 'RagdollItemInHandRendererMixin.java':
        text = text.replace('"renderHandsWithItems"', '"submitHandsWithItems"')
        text = re.sub(r'private void asterion\$hideHands\([^{}]+\) \{',
            'private void asterion$hideHands(CallbackInfo ci) {\n'
            '        LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;\n'
            '        if (player == null) return;', text)
    if path.name == 'LimboWaterRenderer.java':
        text = text.replace('.withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)',
            '.withVertexBinding(0, DefaultVertexFormat.ENTITY).withPrimitiveTopology(com.mojang.renderpearl.api.pipeline.PrimitiveTopology.QUADS)')
        text = text.replace('.withUniform("Sampler0", com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER)\n                    .withUniform("Sampler1", com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER)',
            '.withBindGroupLayout(com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder()\n'
            '                        .withUniform("Fog", com.mojang.renderpearl.api.pipeline.UniformType.UNIFORM_BUFFER)\n'
            '                        .withUniform("Sampler0", com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER)\n'
            '                        .withUniform("Sampler1", com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER).build())')
    if path.name == 'LayeredMazeBiomeSource.java' and 'createResolver(' not in text:
        text = text.replace('@Override\n    public Holder<Biome> getNoiseBiome', 'public Holder<Biome> getNoiseBiome')
        text = text.rsplit('}', 1)[0] + '''
    @Override public net.minecraft.world.level.biome.BiomeResolver createResolver(Climate.Sampler sampler) {
        return (x, y, z) -> getNoiseBiome(x, y, z, sampler);
    }
}
'''
    if re.search(r'\bclass\s+\w+\s+extends Feature<NoneFeatureConfiguration>', text) and 'ASTERION_PORT_CODEC' not in text:
        feature_class = path.stem
        text = text.rsplit('}', 1)[0] + f'''
    private static final com.mojang.serialization.MapCodec<{feature_class}> ASTERION_PORT_CODEC =
            com.mojang.serialization.MapCodec.unit(() -> new {feature_class}(NoneFeatureConfiguration.CODEC));
    @Override public com.mojang.serialization.MapCodec<{feature_class}> codec() {{ return ASTERION_PORT_CODEC; }}
}}
'''
    if path.name in {'MazeChunkGenerator.java', 'UnderworldChunkGenerator.java'}:
        for name in ['buildSurface', 'applyCarvers', 'addDebugScreenInfo']:
            text = remove_method(text, name)
        # 26.3 folds the flat generator's surface/carver/noise phases into buildTerrain.
        old = re.search(r'public CompletableFuture<ChunkAccess> fillFromNoise\([^{}]+\)\s*\{', text)
        if old:
            args = old.group().replace('\n', ' ')
            random_name = 'randomState' if 'RandomState randomState' in args else 'random'
            structures_name = 'structureManager' if 'StructureManager structureManager' in args else 'structures'
            text = text[:old.start()] + '''public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender,
            RandomState ''' + random_name + ''', StructureManager ''' + structures_name + ''',
            net.minecraft.world.level.biome.BiomeManager biomes, net.minecraft.server.level.WorldGenRegion region,
            java.util.Set<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> relevantBiomes) {''' + text[old.end():]
            text = text.replace(f'flat.fillFromNoise(blender, {random_name}, {structures_name}, chunk)',
                f'flat.buildTerrain(chunk, blender, {random_name}, {structures_name}, biomes, region, relevantBiomes)')
        text = text.rsplit('}', 1)[0] + '''
    @Override public void addDebugScreenInfo(java.util.List<String> lines, RandomState random,
            net.minecraft.core.BlockPos pos, net.minecraft.world.level.levelgen.densityfunction.SamplerContext context) {
        flat.addDebugScreenInfo(lines, random, pos, context);
    }
}
'''
    path.write_text(text, encoding='utf-8')

if platform in {'forge', 'neoforge'} and (root / 'net/krodark/asterion/Asterion.java').is_file():
    dispatch = ('LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.fire(context);\n'
                '        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.fire(context);') if platform == 'forge' else (
                'LevelRenderEvents.before(context);\n        LevelRenderEvents.after(context);\n        LevelRenderEvents.end(context);')
    (root / 'net/krodark/asterion/mixin/NativeLevelSubmissionMixin.java').write_text('''package net.krodark.asterion.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Queue native overlay geometry before Minecraft consumes the feature submissions. */
@Mixin(LevelRenderer.class)
abstract class NativeLevelSubmissionMixin {
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void asterion$overlays(PoseStack poses, LevelRenderState state,
            SubmitNodeCollector collector, CallbackInfo ci) {
        LevelRenderContext context = new LevelRenderContext() {
            public LevelRenderState levelState() { return state; }
            public PoseStack poseStack() { return poses; }
            public SubmitNodeCollector submitNodeCollector() { return collector; }
        };
        ''' + dispatch + '''
    }
}
''', encoding='utf-8')
