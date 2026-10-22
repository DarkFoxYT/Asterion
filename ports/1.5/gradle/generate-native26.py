"""Generate version-specific native NeoForge adapters from maintained shared adapters."""
from pathlib import Path
import re
import sys

base = Path(__file__).resolve().parents[1]
target = Path(sys.argv[1])
if not target.resolve().is_relative_to(base.resolve()) or target.name != 'native-adapters':
    raise ValueError('Generated adapters must stay within this port workspace')
for obsolete in target.rglob('*.java'):
    obsolete.unlink()
common = base / 'forge/src/1.21.1/java/net/fabricmc'
native = base / 'neoforge/src/main/java/net/fabricmc'

def write(relative, source):
    source = source.replace('net.minecraftforge.common.MinecraftForge', 'net.neoforged.neoforge.common.NeoForge')
    source = source.replace('MinecraftForge.EVENT_BUS', 'NeoForge.EVENT_BUS')
    source = source.replace('net.minecraftforge.event.TickEvent', 'net.neoforged.neoforge.event.tick.ServerTickEvent')
    source = source.replace('TickEvent.ServerTickEvent.Post', 'ServerTickEvent.Post')
    source = source.replace('net.minecraftforge.event.', 'net.neoforged.neoforge.event.')
    source = source.replace('LivingAttackEvent', 'LivingIncomingDamageEvent')
    source = source.replace('LivingDamageEvent event', 'LivingDamageEvent.Post event')
    source = source.replace('float amount = event.getAmount();', 'float amount = event.getNewDamage();')
    source = source.replace('event.getNewDamage()', 'event.getHealthDamage()') if 'LivingDamageEvent.Post event' in source else source
    source = source.replace('import net.neoforged.neoforge.event.level.BlockEvent;', 'import net.neoforged.neoforge.event.level.block.BreakBlockEvent;')
    source = source.replace('BlockEvent.BreakEvent', 'BreakBlockEvent')
    if relative.as_posix().endswith('ServerChunkEvents.java'):
        source = source.replace('listener.onChunkLoad(level, chunk)', 'listener.onChunkLoad(level, chunk, event.isNewChunk())')
        source = source.replace('void onChunkLoad(ServerLevel level, LevelChunk chunk)', 'void onChunkLoad(ServerLevel level, LevelChunk chunk, boolean newlyGenerated)')
    if relative.as_posix().endswith('ServerPlayConnectionEvents.java'):
        source = source.replace('PacketSender sender = ServerPlayNetworking.getSender(handler.getPlayer());', 'Object sender = null;')
        source = source.replace('PacketSender sender', 'Object sender')
    source = source.replace('ResourceLocation', 'Identifier').replace('player.getServer()', 'player.level().getServer()')
    source = source.replace('net.minecraftforge.client.event.', 'net.neoforged.neoforge.client.event.')
    source = source.replace('RegisterClientReloadListenersEvent', 'AddClientReloadListenersEvent')
    source = source.replace('event.registerReloadListener(listener)', 'event.addListener(id, listener)')
    source = re.sub(r'([\w.]+)\.BUS', r'net.krodark.asterion.neoforge.NativeBus.event(\1.class)', source)
    source = re.sub(r'= ([A-Z_]+)::add;', r'= new Event<>(\1::add);', source)
    output = target / 'net/fabricmc' / relative
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(source, 'utf8')

# Common callbacks use the same native NeoForge bus on both modern versions.
prefixes = ['fabric/api/event/', 'fabric/api/entity/', 'fabric/api/command/',
            'fabric/api/client/event/lifecycle/v1/ClientLifecycleEvents.java',
            'fabric/api/networking/v1/EntityTrackingEvents.java', 'fabric/api/networking/v1/ServerPlayConnectionEvents.java',
            'fabric/api/biome/', 'fabric/api/object/builder/v1/entity/']
for path in common.rglob('*.java'):
    rel = path.relative_to(common)
    if any(rel.as_posix().startswith(p) for p in prefixes): write(rel, path.read_text('utf8'))
for path in native.rglob('*.java'):
    rel = path.relative_to(native)
    if any(rel.as_posix().startswith(p) for p in ['api/', 'loader/', 'fabric/api/event/Event.java',
            'fabric/api/client/event/', 'fabric/api/client/networking/v1/ClientPlayConnectionEvents.java']):
        write(rel, path.read_text('utf8'))

# Pure builders/contexts are shared with the corresponding native Forge port.
modern = base / 'ports/26.1.2/forge/src/main/java/net/fabricmc'
for path in modern.rglob('*.java'):
    rel = path.relative_to(modern)
    if any(rel.as_posix().startswith(p) for p in ['fabric/api/particle/', 'fabric/api/object/builder/v1/block/',
            'fabric/api/biome/', 'fabric/api/client/rendering/v1/level/LevelRenderContext.java',
            'fabric/api/client/rendering/v1/level/LevelExtractionContext.java', 'fabric/api/client/rendering/v1/RenderStateDataKey.java']): write(rel, path.read_text('utf8'))
    if any(rel.as_posix().startswith(p) for p in ['fabric/api/registry/', 'fabric/api/creativetab/',
            'fabric/api/client/item/', 'fabric/api/client/particle/', 'fabric/api/resource/v1/ResourceLoader.java']):
        write(rel, path.read_text('utf8'))
