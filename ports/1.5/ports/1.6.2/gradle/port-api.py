"""Idempotent API adaptation of generated 1.6 sources; never edits shared gameplay sources."""
from pathlib import Path
import re, sys

root = Path(sys.argv[1])
mc = sys.argv[2]
platform = sys.argv[3] if len(sys.argv) > 3 else 'fabric'
native = platform in {'forge', 'neoforge'}
if mc == '26.3':
    import runpy
    runpy.run_path(str(Path(__file__).with_name('port-api263.py')), init_globals={'root': root, 'platform': platform})
    raise SystemExit(0)
if mc != '1.21.11':
    raise SystemExit(0)

names = [
    ('com.geckolib', 'software.bernie.geckolib'),
    ('com/geckolib', 'software/bernie/geckolib'),
    ('net.minecraft.client.renderer.state.level.', 'net.minecraft.client.renderer.state.'),
    ('net/minecraft/client/renderer/state/level/', 'net/minecraft/client/renderer/state/'),
    ('GuiGraphicsExtractor', 'GuiGraphics'),
    ('fabric.api.creativetab.v1', 'fabric.api.itemgroup.v1'),
    ('FabricCreativeModeTab', 'FabricItemGroup'), ('CreativeModeTabEvents', 'ItemGroupEvents'),
    ('.modifyOutputEvent(', '.modifyEntriesEvent('), ('ParticleProviderRegistry', 'ParticleFactoryRegistry'),
    ('fabric.api.client.rendering.v1.level.', 'fabric.api.client.rendering.v1.world.'),
    ('LevelRenderEvents', 'WorldRenderEvents'), ('LevelRenderContext', 'WorldRenderContext'),
    ('fabric.api.client.keymapping.v1.KeyMappingHelper', 'fabric.api.client.keybinding.v1.KeyBindingHelper'),
    ('KeyMappingHelper.registerKeyMapping(', 'KeyBindingHelper.registerKeyBinding('),
    ('ClientCommands', 'ClientCommandManager'), ('ClientTooltipComponentCallback', 'TooltipComponentCallback'),
    ('FluidRenderingRegistry', 'FluidRenderHandlerRegistry'),
    ('FluidRenderHandlerRegistry.register(', 'FluidRenderHandlerRegistry.INSTANCE.register('),
    ('net.minecraft.client.renderer.block.BlockAndTintGetter', 'net.minecraft.world.level.BlockAndTintGetter'),
    ('LightmapRenderStateExtractor', 'LightTexture'),
    ('@ModifyExpressionValue(method = "extract",', '@ModifyExpressionValue(method = "updateLightTexture",'),
    ('NetherRootsBlock', 'RootsBlock'),
    ('PayloadTypeRegistry.clientboundPlay()', 'PayloadTypeRegistry.playS2C()'),
    ('PayloadTypeRegistry.serverboundPlay()', 'PayloadTypeRegistry.playC2S()'),
    ('registry.FuelValueEvents', 'registry.FuelRegistryEvents'), ('.entityTags()', '.getTags()'),
    ('getLightCoords(', 'getLightColor('),
    ('new net.minecraft.world.item.crafting.RecipeSerializer<>(', 'new net.krodark.asterion.compat.LegacyRecipeSerializer<>('),
    ('Asterion.id("world_state"),', 'Asterion.id("world_state").toString(),'),
    ('Asterion.id("ferry_journeys"),', 'Asterion.id("ferry_journeys").toString(),'),
    ('extractBackground(', 'renderBackground('), ('extractImage(', 'renderImage('),
    ('DataTickets.create(', 'DataTicket.create('),
    ('WorldRenderEvents.AFTER_TRANSLUCENT_TERRAIN', 'WorldRenderEvents.END_MAIN'),
    ('WorldRenderEvents.BEFORE_TRANSLUCENT_TERRAIN', 'WorldRenderEvents.BEFORE_TRANSLUCENT'),
    ('context.levelState()', 'context.worldState()'), ('context.bufferSource()', 'context.consumers()'),
    ('context.poseStack()', 'context.matrices()'), ('context.submitNodeCollector()', 'context.commandQueue()'),
    ('WorldGenerator::onChunkLoad', '(level, chunk) -> WorldGenerator.onChunkLoad(level, chunk, false)'),
    ('CatacombFloodState::onChunkLoad', '(level, chunk) -> CatacombFloodState.onChunkLoad(level, chunk, false)'),
    ('net.krodark.asterion.worldgen.AuthoredForge::onChunkLoad', '(level, chunk) -> net.krodark.asterion.worldgen.AuthoredForge.onChunkLoad(level, chunk, false)'),
    ('ChunkPos.containing(', 'new ChunkPos('),
    ('net.minecraft.world.level.new ChunkPos(', 'new net.minecraft.world.level.ChunkPos('),
    ('ChunkPos.pack(', 'ChunkPos.asLong('),
    ('.pack()', '.toLong()'),
    ('element.extractRenderState(graphics, tracker)', 'element.render(graphics, tracker)'),
    ('ServerChunkEvents.CHUNK_LOAD.register((level,chunk,fresh)', 'ServerChunkEvents.CHUNK_LOAD.register((level,chunk)'),
    ('.registerReloadListener(Asterion.id("render_caches"),', '.registerReloader(Asterion.id("render_caches"),'),
    ('RenderSystem.getDevice().isZZeroToOne()', 'false'),
    ('FluidRenderer.Output', 'com.mojang.blaze3d.vertex.VertexConsumer'),
    ('FluidRenderer', 'LiquidBlockRenderer'),
    ('import com.mojang.blaze3d.pipeline.DepthStencilState;', ''),
    ('.withDepthStencilState(DepthStencilState.DEFAULT)', '.withDepthTestFunction(com.mojang.blaze3d.platform.DepthTestFunction.LEQUAL_DEPTH_TEST)'),
    ('DefaultVertexFormat.ENTITY', 'DefaultVertexFormat.NEW_ENTITY'),
]

if native:
    # Native loaders provide the release's event adapters; only Minecraft APIs change.
    fabric_only = ['fabric.api.', 'FabricCreativeModeTab', 'CreativeModeTabEvents', '.modifyOutputEvent(',
        'ParticleProviderRegistry', 'LevelRenderEvents', 'LevelRenderContext', 'KeyMappingHelper',
        'ClientCommands', 'ClientTooltipComponentCallback', 'FluidRenderingRegistry', 'FluidRenderHandlerRegistry',
        'PayloadTypeRegistry.', 'registry.FuelValueEvents', 'WorldRenderEvents.', 'context.', '::onChunkLoad',
        'ServerChunkEvents.CHUNK_LOAD', '.registerReloadListener(']
    names = [(before, after) for before, after in names if not any(part in before for part in fabric_only)]
gui_methods = {'text':'drawString', 'centeredText':'drawCenteredString', 'outline':'renderOutline',
               'item':'renderItem', 'itemDecorations':'renderItemDecorations'}

def extract_handler(text, marker):
    start = text.index(marker)
    body = text.index('{', start)
    end, depth = body + 1, 1
    while depth:
        if text[end] == '{': depth += 1
        elif text[end] == '}': depth -= 1
        end += 1
    return text[:start] + text[end:], text[start:end]

for path in root.rglob('*.java'):
    if native and path.name == 'FluidRenderingRegistry.java':
        # The 26.x model-baking bridge is unused by this release's fluid rendering.
        path.unlink()
        continue
    text = path.read_text(encoding='utf-8')
    for before, after in names:
        text = text.replace(before, after)
    # These receivers are ChunkPos values in the world-generation and flood code.
    if '/worldgen/' in path.as_posix() or path.name == 'CatacombFloodState.java':
        text = re.sub(r'\b(chunk|cp|pos|clip|center)\.([xz])\(\)', r'\1.\2', text)
        text = re.sub(r'chunkPosition\(\)\.([xz])\(\)', r'chunkPosition().\1', text)
    text = re.sub(r'(new (?:net\.minecraft\.world\.level\.saveddata\.)?SavedDataType<>?\(\s*)(?:net\.krodark\.asterion\.)?Asterion\.id\("([^"]+)"\)(?:\.toString\(\))?\s*,',
        lambda match: match[1] + '"asterion_' + match[2].replace('/', '_') + '",', text)
    if path.name in {'MinotaurAxeEntity.java', 'CentipedeSegmentEntity.java', 'LiftCallRuneEntity.java', 'PhysicsChainEntity.java', 'CharonEntity.java'}:
        text = re.sub(r'(interact\([^)]*?InteractionHand hand),\s*(?:net\.minecraft\.world\.phys\.)?Vec3 hit\)', r'\1)', text)
        text = text.replace('super.interact(player, hand, hit)', 'super.interact(player, hand)')
    if '/client/render/block/' in path.as_posix() and path.name not in {'ShatteredDeadWoodGeoRenderer.java', 'SkeletonGeoRenderer.java'}:
        text = re.sub(r'super\(context,\s*', 'super(', text)
        text = text.replace('pass.model().isMissingno()', '(pass.model().topLevelBones().length == 0)')
    if path.name == 'AsterionEmissiveBuffer.java':
        text = text.replace('.withColorTargetState(source.getColorTargetState()).withDepthStencilState(source.getDepthStencilState());',
            '.withDepthTestFunction(source.getDepthTestFunction()).withDepthWrite(source.isWriteDepth())\n'
            '                .withColorWrite(source.isWriteColor(), source.isWriteAlpha()).withColorLogic(source.getColorLogic());\n'
            '        source.getBlendFunction().ifPresent(builder::withBlend);')
    if path.name == 'PhysicsDebrisSystem.java':
        text = text.replace('block.cardinalLighting = client.level.cardinalLighting();', 'block.level = client.level;')
        text = text.replace('block.lightEngine = client.level.getLightEngine();', '')
    text = text.replace('context.consumers().endBatch(SURFACE);',
        'if (context.consumers() instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers) buffers.endBatch(SURFACE);')
    if path.name == 'LimboWaterRenderer.java' and not native:
        text = text.replace('context.level()', 'context.world()').replace('context.deltaTracker()', 'context.tickCounter()')
    if native and path.name == 'HudElementRegistry.java':
        text = text.replace('::extractRenderState', '::render')
    if path.name in {'MinotaurAxeVisual.java', 'MinotaurSwordVisual.java', 'PhysicsDebrisSystem.java'}:
        text = text.replace('RENDERER.performRenderPass(', 'net.krodark.asterion.compat.LegacyGeoObjectPass.render(RENDERER, ')
    if path.name == 'CameraMixin.java':
        if '@Inject(method = "getFov"' in text:
            text, fov_handler = extract_handler(text, '@Inject(method = "getFov"')
            imports = text[text.index('import '):text.index('@Mixin(Camera.class)')]
            fov_source = ('package net.krodark.asterion.mixin;\n' + imports +
                '@Mixin(net.minecraft.client.renderer.GameRenderer.class)\nabstract class LegacyCameraFovMixin {\n'
                '    @Unique private float asterion$flamethrowerFovStrength;\n' + fov_handler + '\n}\n')
            path.with_name('LegacyCameraFovMixin.java').write_text(fov_source, encoding='utf-8')
        text = text.replace('@Shadow private int matrixPropertiesDirty;', '')
        text = text.replace('matrixPropertiesDirty |= 3;', '')
        text = re.sub(r'@Inject\(method = "alignWithEntity", at = @At\(value = "INVOKE",\s*target = "Lnet/minecraft/client/CameraType;isFirstPerson\(\)Z"\)\)',
            '@Inject(method = "setup", at = @At("TAIL"))', text)
        text = text.replace('method = "alignWithEntity"', 'method = "setup"').replace('method = "update"', 'method = "setup"')
        text = re.sub(r'(private void asterion\$\w+)\(float partial, CallbackInfo ci\)',
            r'\1(net.minecraft.world.level.Level level, net.minecraft.world.entity.Entity entity, boolean detached, boolean reverse, float partial, CallbackInfo ci)', text)
        text = re.sub(r'(private void asterion\$\w+)\(DeltaTracker tracker, CallbackInfo ci\) \{',
            r'\1(net.minecraft.world.level.Level level, net.minecraft.world.entity.Entity entity, boolean detached, boolean reverse, float setupPartial, CallbackInfo ci) {\n        DeltaTracker tracker = Minecraft.getInstance().getDeltaTracker();', text)
        text = text.replace('((Camera)(Object)this).getViewRotationMatrix(new Matrix4f())',
            'new Matrix4f().rotation(new org.joml.Quaternionf(((Camera)(Object)this).rotation()).conjugate())')
        text = text.replace('((Camera)(Object)this).getCullFrustum().set(corrected);',
            'net.krodark.asterion.client.render.FrameRenderAccess.recordFrustum(corrected);')
    if path.name == 'MinotaurBodyPickingMixin.java' and '@Inject(method = "pick"' in text:
        text, _ = extract_handler(text, '@Inject(method = "pick"')
    if path.name in {'LevelLoadingScreenMixin.java', 'ForgeHudMixin.java', 'LimboDeathScreenMixin.java', 'ForgeToastMixin.java', 'MinotaurBossBarMixin.java'}:
        text = text.replace('"extractRenderState"', '"render"')
        text = text.replace('"extractBar(', '"drawBar(')
    if path.name == 'ShaleSpikeDripstoneMixin.java':
        text = text.replace('is(Ljava/lang/Object;)Z', 'is(Lnet/minecraft/world/level/block/Block;)Z')
        text = text.replace('BlockState state, Object expected', 'BlockState state, Block expected')
    if path.name == 'CursedBrazierRenderer.java':
        text = text.replace('new CustomBoneTextureGeoLayer<>(', 'new net.krodark.asterion.compat.LegacyBoneTextureLayer<>(')
    if native and path.name == 'HeavyWaterRendering.java':
        text = '''package net.krodark.asterion.client.render;
/** Water sprites come from the native water FluidType; NativeHeavyWaterTintMixin supplies Asterion's tint. */
public final class HeavyWaterRendering {
    private HeavyWaterRendering() { }
    public static void initialize() { }
}
'''
        extensions = ('net.minecraftforge' if platform == 'forge' else 'net.neoforged.neoforge') + '.client.extensions.common.IClientFluidTypeExtensions'
        target = extensions.replace('.', '/')
        mixin_dir = root / 'net/krodark/asterion/mixin'
        mixin_dir.mkdir(parents=True, exist_ok=True)
        mixin_dir.joinpath('NativeHeavyWaterTintMixin.java').write_text('''package net.krodark.asterion.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.krodark.asterion.fluid.HeavyWater;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(LiquidBlockRenderer.class)
abstract class NativeHeavyWaterTintMixin {
    @WrapOperation(method = "tesselate", at = @At(value = "INVOKE", target = "L''' + target + ''';getTintColor(Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I"))
    private int asterion$tint(''' + extensions + ''' extension, FluidState state,
            BlockAndTintGetter level, BlockPos pos, Operation<Integer> original) {
        var fluid = state.getType();
        return fluid == HeavyWater.STILL || fluid == HeavyWater.FLOWING || fluid == HeavyWater.FLUID
                ? 0xff000000 | HeavyWater.COLOR : original.call(extension, state, level, pos);
    }
}
''', encoding='utf-8')
    if platform == 'forge' and path.name == 'ItemTooltipCallback.java':
        text = text.replace('event.getContext()', '(event.getEntity() == null ? Item.TooltipContext.EMPTY : Item.TooltipContext.of(event.getEntity().level()))')
    if platform == 'forge' and path.name == 'ForgeLevelRendererMixin.java':
        text = text.replace('lambda$addMainPass$0', 'lambda$addMainPass$1')
        text = re.sub(r'private void asterion\$prepare\([^{}]+\) \{', 'private void asterion$prepare(CallbackInfo ci) {', text)
    if platform == 'neoforge' and path.name == 'AsterionNeoForgeClient.java':
        text = text.replace('modBus.addListener(AsterionNeoForgeClient::registerFluidModels);', '')
        text, _ = extract_handler(text, 'private static void registerFluidModels(')
        for imported in ['net.neoforged.neoforge.client.fluid.FluidTintSources', 'net.minecraft.client.renderer.block.FluidModel',
                'net.minecraft.client.resources.model.sprite.Material', 'net.neoforged.neoforge.client.event.RegisterFluidModelsEvent']:
            text = text.replace('import ' + imported + ';', '')
    if platform == 'neoforge' and path.name == 'NativeRenderEvents.java':
        text = text.replace('RenderLevelStageEvent.AfterOpaqueFeatures', 'RenderLevelStageEvent.AfterEntities')
    if platform == 'neoforge' and path.name == 'PlayerBlockBreakEvents.java':
        text = text.replace('import net.neoforged.neoforge.event.level.block.BreakBlockEvent;',
            'import net.neoforged.neoforge.event.level.BlockEvent;')
        text = text.replace('(BreakBlockEvent event)', '(BlockEvent.BreakEvent event)')
    if platform == 'neoforge' and path.name == 'ServerLivingEntityEvents.java':
        text = text.replace('event.getHealthDamage()', 'event.getNewDamage()')
    if native and path.name == 'HeavyWaterBucketMixin.java':
        text = text.replace('Inject(method = "emptyContents",', 'Inject(method = EMPTY_CONTENTS_STACK_AWARE,')
        text = text.replace('BlockHitResult hit,', 'BlockHitResult hit, net.minecraft.world.item.ItemStack stack,')
    if platform == 'neoforge' and path.name == 'TidalWaterSurfaceMixin.java' and '@Redirect(method = "tesselate"' in text:
        # Dynamic Limbo surfaces are outside the 1.6 content; keep the maze's flat-water hook.
        text, _ = extract_handler(text, '@Redirect(method = "tesselate"')
    text = re.sub(r'extractRenderState\(\s*((?:net\.minecraft\.client\.gui\.)?GuiGraphics)\b', r'render(\1', text)
    text = re.sub(r'super\.extractRenderState\((graphics|g)\s*,', r'super.render(\1,', text)
    text = re.sub(r'\b([A-Za-z_][\w.]*)\.sendOverlayMessage\(', r'net.krodark.asterion.compat.PlayerMessages.overlay(\1, ', text)
    text = re.sub(r'new ServerBossEvent\(\s*(?:java\.util\.)?UUID\.randomUUID\(\),\s*', 'new ServerBossEvent(', text)
    for before, after in gui_methods.items():
        text = re.sub(r'\b((?:graphics|g|gui)\.)' + before + r'\(', r'\1' + after + '(', text)
    text = re.sub(r'\b(camera|cam)\.getFov\(\)', r'net.krodark.asterion.client.render.FrameRenderAccess.fov(\1)', text)
    text = text.replace('state.cameraRenderState.cullFrustum', 'net.krodark.asterion.client.render.FrameRenderAccess.frustum()')
    text = text.replace('context.worldState().cameraRenderState.cullFrustum', 'net.krodark.asterion.client.render.FrameRenderAccess.frustum()')
    text = text.replace('context.levelState().cameraRenderState.cullFrustum', 'net.krodark.asterion.client.render.FrameRenderAccess.frustum()')
    text = text.replace('net.krodark.asterion.client.render.FrameRenderAccess.frustum().isVisible(', 'net.krodark.asterion.client.render.FrameRenderAccess.visible(')
    path.write_text(text, encoding='utf-8')
