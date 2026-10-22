"""Version the native callbacks for NeoForge's 1.20.1 Forge-compatible API."""
from pathlib import Path
import re
import sys

base=Path(__file__).resolve().parents[1]
target=Path(sys.argv[1])
if not target.resolve().is_relative_to(base.resolve()) or target.name!='native-adapters':
    raise ValueError('Native adapter output must stay inside this port workspace')
for obsolete in target.rglob('*.java'): obsolete.unlink()

def write(relative,source):
    source=source.replace('net.neoforged.neoforge.common.NeoForge','net.minecraftforge.common.MinecraftForge').replace('NeoForge.EVENT_BUS','MinecraftForge.EVENT_BUS')
    source=source.replace('net.neoforged.neoforge.client.event.ClientTickEvent','net.minecraftforge.event.TickEvent')
    source=source.replace('net.neoforged.neoforge.event.','net.minecraftforge.event.')
    source=source.replace('net.neoforged.neoforge.client.event.','net.minecraftforge.client.event.')
    source=source.replace('net.neoforged.api.','net.minecraftforge.api.').replace('net.neoforged.bus.api.','net.minecraftforge.eventbus.api.')
    source=source.replace('import net.neoforged.fml.common.EventBusSubscriber;', 'import net.minecraftforge.fml.common.Mod;').replace('@EventBusSubscriber','@Mod.EventBusSubscriber')
    source=source.replace('net.krodark.asterion.port.neoforge','net.krodark.asterion.port.forge.nativelegacy')
    source=source.replace('TickEvent.ServerTickEvent.Post','TickEvent.ServerTickEvent')
    source=source.replace('END.forEach(listener -> listener.onEndTick(event.getServer()))','{ if(event.phase==TickEvent.Phase.END) END.forEach(listener -> listener.onEndTick(event.getServer())); }')
    source=re.sub(r'\(ClientTickEvent.Post event\)\s*->\s*listener.onEndTick\(Minecraft.getInstance\(\)\)',
            '(TickEvent.ClientTickEvent event) -> {if(event.phase==TickEvent.Phase.END)listener.onEndTick(Minecraft.getInstance());}', source)
    source=source.replace('import net.minecraft.client.DeltaTracker;','').replace('DeltaTracker tickCounter','float tickDelta').replace('DeltaTracker delta','float delta')
    source=source.replace('client.getTimer()', 'event.getPartialTick()')
    source=re.sub(r'= ([A-Z_]+)::add;',r'= new Event<>(\1::add);',source)
    source=source.replace('return listener -> LISTENERS.computeIfAbsent(key, ignored -> new ArrayList<>()).add(listener);','return new Event<>(listener -> LISTENERS.computeIfAbsent(key, ignored -> new ArrayList<>()).add(listener));')
    source=source.replace('DeltaTracker tickCounter();', 'float tickDelta();')
    if relative.name=='ServerPlayConnectionEvents.java':
        source=source.replace('PacketSender sender = ServerPlayNetworking.getSender(handler.getPlayer());','Object sender = null;').replace('PacketSender sender','Object sender')
    output=target/relative
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(source,'utf8')

common=base/'forge/src/1.21.1/java'
prefixes=['net/fabricmc/fabric/api/event/','net/fabricmc/fabric/api/entity/',
          'net/fabricmc/fabric/api/registry/','net/fabricmc/fabric/api/particle/',
          'net/fabricmc/fabric/api/itemgroup/','net/fabricmc/fabric/api/command/',
          'net/fabricmc/fabric/api/biome/','net/fabricmc/fabric/api/object/',
          'net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientLifecycleEvents.java',
          'net/fabricmc/fabric/api/client/rendering/v1/WorldRenderContext.java',
          'net/fabricmc/fabric/api/client/rendering/v1/HudRenderCallback.java',
          'net/fabricmc/fabric/api/client/rendering/v1/CoreShaderRegistrationCallback.java',
          'net/fabricmc/fabric/api/networking/v1/EntityTrackingEvents.java',
          'net/fabricmc/fabric/api/networking/v1/ServerPlayConnectionEvents.java']
for path in common.rglob('*.java'):
    rel=path.relative_to(common)
    if any(rel.as_posix().startswith(prefix) for prefix in prefixes) and rel.name!='Event.java': write(rel,path.read_text('utf8'))
native=base/'neoforge/src/main/java'
for rel in ['net/fabricmc/api/ModInitializer.java','net/fabricmc/api/ClientModInitializer.java','net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents.java',
            'net/fabricmc/fabric/api/client/rendering/v1/WorldRenderEvents.java',
            'net/fabricmc/fabric/api/blockrenderlayer/v1/BlockRenderLayerMap.java',
            'net/fabricmc/fabric/api/networking/v1/PlayerLookup.java',
            'net/krodark/asterion/port/neoforge/NativeLifecycle.java',
            'net/krodark/asterion/port/neoforge/NativeWorldEvents.java']:
    output=Path(rel.replace('/neoforge/','/forge/nativelegacy/'))
    write(output,(native/rel).read_text('utf8'))

entrypoint=(base/'forge/src/1.20.1/java/net/krodark/asterion/port/forge/AsterionForge.java').read_text('utf8')
entrypoint=entrypoint.replace('AsterionForge','AsterionNeoLegacy')
entrypoint=re.sub(r'        net\.minecraftforge\.fml\.DistExecutor\.safeRunWhenOn\(net\.minecraftforge\.api\.distmarker\.Dist\.CLIENT,\s*\(\) -> AsterionLegacyForgeClient::initialize\);', '', entrypoint)
entrypoint=entrypoint.replace('IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();',
        'IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();\n        net.krodark.asterion.port.forge.nativelegacy.NativeLegacyBindings.register(modBus);')
write(Path('net/krodark/asterion/port/forge/AsterionNeoLegacy.java'),entrypoint)
