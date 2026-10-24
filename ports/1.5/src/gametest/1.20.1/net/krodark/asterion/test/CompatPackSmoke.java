package net.krodark.asterion.test;

import net.krodark.asterion.Asterion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Isolated modpack scenario; never included in a published artifact. */
final class CompatPackSmoke {
    int title, frames, stage, floor;
    long entered;
    boolean started;
    volatile Throwable failure;
    volatile boolean serverReady;
    CompletableFuture<Void> reload;
    final long deadline = System.nanoTime() + 480_000_000_000L;

    void tick(Minecraft client) {
        if (failure != null) throw new AssertionError("Modpack scenario failed", failure);
        if (System.nanoTime() > deadline) throw new AssertionError("Modpack scenario timed out at " + stage);
        client.options.pauseOnLostFocus = false;
        if (client.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen)
            client.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
        if (!started && client.screen instanceof net.minecraft.client.gui.screens.TitleScreen
                && client.getOverlay() == null) {
            title++;
            if (title == 10) {
                org.lwjgl.glfw.GLFW.glfwRestoreWindow(client.getWindow().getWindow());
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 854, 480);
            }
            if (title == 25) {
                try { Class.forName("net.irisshaders.iris.Iris").getMethod("reload").invoke(null); }
                catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            }
            if (title < 45) return;
            started = true;
            client.options.framerateLimit().set(260);
            client.options.enableVsync().set(false);
            client.getWindow().updateVsync(false);
            client.options.renderDistance().set(4);
            var settings = new net.minecraft.world.level.LevelSettings("Modpack compatibility",
                    net.minecraft.world.level.GameType.CREATIVE, false, net.minecraft.world.Difficulty.NORMAL,
                    true, new net.minecraft.world.level.GameRules(), net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
            client.createWorldOpenFlows().createFreshLevel("compat-" + System.currentTimeMillis(), settings,
                    new net.minecraft.world.level.levelgen.WorldOptions(321L, false, false),
                    net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions);
        }
        if (client.level == null || client.player == null || client.getSingleplayerServer() == null) return;
        long time = client.level.getGameTime();
        switch (stage) {
            case 0 -> { setup(client, false); stage=1; entered=time; }
            case 1 -> {
                if (serverReady && time-entered>100) {
                    checkScene(client, false);
                    drawGun(client); entered=time; stage=10;
                }
            }
            case 10 -> {
                if (time-entered>40) { exerciseCombat(client); entered=time; stage=11; }
            }
            case 11 -> {
                if (time-entered>40) { capture(client,"compat-overworld.png"); setup(client,true); stage=2; }
            }
            case 2 -> {
                if (client.level.dimension().equals(Asterion.ASTERION_LEVEL) && serverReady) {
                    entered=time; stage=3;
                }
            }
            case 3 -> {
                if (time-entered>100) {
                    checkScene(client, true);
                    drawGun(client); entered=time; stage=13;
                }
            }
            case 13 -> {
                if (time-entered>40) { exerciseCombat(client); entered=time; stage=12; }
            }
            case 12 -> {
                if (time-entered>40) {
                    verifyRestoration(client);
                    capture(client,"compat-labyrinth.png");
                    PointLightIterationRegression.run(client);
                    var repository = client.getResourcePackRepository();
                    repository.reload();
                    var selected = new ArrayList<>(repository.getSelectedIds());
                    selected.add("file/compat-regression.zip");
                    repository.setSelected(selected);
                    reload=client.reloadResourcePacks(); stage=4;
                }
            }
            case 4 -> {
                if (reload.isDone() && client.getOverlay()==null) {
                    reload.join();
                    if (!Component.translatable("asterion.compat.test").getString().equals("Compatibility pack loaded"))
                        throw new AssertionError("Resource pack did not load");
                    checkScene(client, true);
                    openMap(client); frames=0; stage=5;
                }
            }
            case 5 -> {
                if (++frames>90) {
                    if (client.screen==null || !client.screen.getClass().getName().equals("xaero.map.gui.GuiMap"))
                        throw new AssertionError("Xaero world map did not remain open");
                    capture(client,"compat-worldmap.png");
                    client.setScreen(null);
                    floor=0; moveFloor(client); entered=time; stage=20;
                }
            }
            case 20 -> {
                if (serverReady && time-entered>80) {
                    checkFloor(client); openMap(client); frames=0; stage=21;
                }
            }
            case 21 -> {
                if (++frames>90) {
                    capture(client,"compat-floor-"+floor+".png");
                    logMapLayer(); client.setScreen(null);
                    if (++floor<4) { moveFloor(client); entered=time; stage=20; return; }
                    client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    var engine=net.krodark.asterion.port.client.ragdoll.DismembermentEngine.INSTANCE;
                    engine.forcePlayerTumble(client,client.player.position(),new Vec3(.1,.1,.1),1F);
                    if (!engine.isRagdolled(client.player.getId())) throw new AssertionError("Player ragdoll not created");
                    entered=time; stage=6;
                }
            }
            case 6 -> {
                if (time-entered>40) {
                    capture(client,"compat-ragdoll.png");
                    var engine=net.krodark.asterion.port.client.ragdoll.DismembermentEngine.INSTANCE;
                    var hand=engine.ragdollHandPosition(client.player.getId(),true);
                    if (hand!=null && (!Double.isFinite(hand.x) || !Double.isFinite(hand.y) || !Double.isFinite(hand.z)))
                        throw new AssertionError("Non-finite player ragdoll with animation mods");
                    engine.releaseRagdoll(client.player.getId());
                    if (engine.isRagdolled(client.player.getId())) throw new AssertionError("Ragdoll recovery failed");
                    client.options.setCameraType(CameraType.FIRST_PERSON);
                    setup(client, false); stage=7;
                }
            }
            case 7 -> {
                if (!client.level.dimension().equals(Asterion.ASTERION_LEVEL) && serverReady) { entered=time; stage=8; }
            }
            case 8 -> {
                if (time-entered>80) {
                    checkScene(client,false);
                    Asterion.LOGGER.info("ASTERION_MODPACK PASS: magic block renderers, gun model, Xaero dimension/map GUI, resource reload, NEA ragdoll/recovery, shader restoration and isolated structures");
                    client.stop(); stage=9;
                }
            }
        }
    }

    void setup(Minecraft client, boolean maze) {
        serverReady=false;
        client.getSingleplayerServer().execute(() -> {
            try {
                var server=client.getSingleplayerServer();
                var level=server.getLevel(maze ? Asterion.ASTERION_LEVEL : Level.OVERWORLD);
                var source=server.createCommandSourceStack();
                String dimension=level.dimension().location().toString();
                int y=maze?180:160;
                server.getCommands().performPrefixedCommand(source,"execute in "+dimension+" run tp @a 0 "+y+" 0 0 15");
                server.getCommands().performPrefixedCommand(source,"execute in "+dimension+" run fill -8 "+(y-1)+" -8 8 "+(y-1)+" 8 minecraft:stone");
                for (var entry : Map.of(new BlockPos(-2,y,4),"bloodmagic:altar",new BlockPos(2,y,4),"irons_spellbooks:scroll_forge").entrySet()) {
                    var block=BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(entry.getValue()));
                    if (block==net.minecraft.world.level.block.Blocks.AIR) throw new AssertionError("Missing mod block "+entry);
                    level.setBlockAndUpdate(entry.getKey(),block.defaultBlockState());
                }
                var gun=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("tacz:modern_kinetic_gun")));
                var api=Class.forName("com.tacz.guns.api.item.IGun");
                var instance=api.getMethod("getIGunOrNull",ItemStack.class).invoke(null,gun);
                if (instance==null) throw new AssertionError("TaCZ gun missing");
                api.getMethod("setGunId",ItemStack.class,ResourceLocation.class).invoke(instance,gun,ResourceLocation.tryParse("tacz:ak47"));
                api.getMethod("setCurrentAmmoCount",ItemStack.class,int.class).invoke(instance,gun,30);
                api.getMethod("setBulletInBarrel",ItemStack.class,boolean.class).invoke(instance,gun,true);
                var mode=Class.forName("com.tacz.guns.api.item.gun.FireMode");
                api.getMethod("setFireMode",ItemStack.class,mode).invoke(instance,gun,mode.getField("AUTO").get(null));
                var player=server.getPlayerList().getPlayers().get(0);
                player.setItemInHand(InteractionHand.MAIN_HAND,gun);
                player.inventoryMenu.broadcastChanges();
                var allowed=level.getChunkSource().getGeneratorState().possibleStructureSets();
                if (maze && (allowed.isEmpty() || allowed.stream().anyMatch(h -> !h.unwrapKey().orElseThrow().location().getNamespace().equals("asterion"))))
                    throw new AssertionError("Non-Asterion structures admitted into the Labyrinth: "+allowed);
                var global=server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
                if (global.keySet().stream().noneMatch(id -> id.getNamespace().equals("repurposed_structures")))
                    throw new AssertionError("Repurposed Structures fixture missing");
                if (maze) prepareExplosionAndBalance(level);
                serverReady=true;
            } catch (Throwable error) { failure=error; }
        });
    }

    void moveFloor(Minecraft client) {
        serverReady=false;
        int height=new int[]{100,70,20,-55}[floor];
        client.getSingleplayerServer().execute(() -> {
            try {
                var server=client.getSingleplayerServer();
                var source=server.createCommandSourceStack();
                String dim=Asterion.ASTERION_LEVEL.location().toString();
                server.getCommands().performPrefixedCommand(source,"execute in "+dim+" run fill 7 "+height+" 7 13 "+(height+4)+" 13 minecraft:air");
                server.getCommands().performPrefixedCommand(source,"execute in "+dim+" run fill 7 "+(height-1)+" 7 13 "+(height-1)+" 13 minecraft:stone");
                server.getCommands().performPrefixedCommand(source,"execute in "+dim+" run tp @a 10 "+height+" 10");
                serverReady=true;
            } catch (Throwable error) { failure=error; }
        });
    }

    void checkFloor(Minecraft client) {
        try {
            var type=Class.forName("xaero.common.XaeroMinimapSession");
            Object session=type.getMethod("getCurrentSession").invoke(null);
            Object processor=type.getMethod("getMinimapProcessor").invoke(session);
            Object cave=processor.getClass().getMethod("isCaveModeDisplayed").invoke(processor);
            String biome=client.level.getBiome(client.player.blockPosition()).unwrapKey().orElseThrow().location().toString();
            Asterion.LOGGER.info("ASTERION_XAERO_FLOOR: floor={} y={} biome={} minimapCaves={}",floor,client.player.getY(),biome,cave);
            if (!client.level.dimension().equals(Asterion.ASTERION_LEVEL)) throw new AssertionError("Lost map dimension");
            if (floor>0 && !Boolean.TRUE.equals(cave)) throw new AssertionError("Underground minimap did not switch to cave rendering");
            if (net.krodark.asterion.compat.MinimapHudLayout.top(236,52,client.getWindow().getGuiScaledHeight())<=12)
                throw new AssertionError("Objective still overlaps the default top-left minimap");
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    static void logMapLayer() {
        try {
            Object session=Class.forName("xaero.map.WorldMapSession").getMethod("getCurrentSession").invoke(null);
            Object processor=session.getClass().getMethod("getMapProcessor").invoke(session);
            Asterion.LOGGER.info("ASTERION_XAERO_MAP_LAYER: {}",processor.getClass().getMethod("getCurrentCaveLayer").invoke(processor));
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    final BlockPos blast = new BlockPos(80, 180, 80);
    final BlockPos placed = blast.east();
    final BlockPos changed = blast.west();

    void prepareExplosionAndBalance(net.minecraft.server.level.ServerLevel level) {
        level.setChunkForced(5,5,true);
        var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        for (var pos : java.util.List.of(blast, placed, changed)) level.setBlockAndUpdate(pos, stone);
        net.krodark.asterion.worldgen.WorldGenerator.trackPlayerPlacement(level, placed, stone);
        level.explode(null, 80.5, 180.5, 80.5, 12F, Level.ExplosionInteraction.TNT);
        for (var pos : java.util.List.of(blast, placed, changed))
            if (!level.getBlockState(pos).isAir()) throw new AssertionError("Explosion did not destroy fixture " + pos);
        var gold = net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState();
        level.setBlockAndUpdate(changed, gold);
        net.krodark.asterion.worldgen.WorldGenerator.trackPlayerPlacement(level, changed, gold);
        var items = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(blast).inflate(8));
        if (items.size() > 1) throw new AssertionError("Renewable terrain dropped duplicate loot: " + items.size());
        var looseItem=new net.minecraft.world.entity.item.ItemEntity(level,80.5,180.2,80.5,
                new ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
        looseItem.setNoGravity(true);
        looseItem.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(looseItem);
        net.minecraft.world.entity.LivingEntity brazier = net.krodark.asterion.game.GameplayContent.CURSED_BRAZIER.create(level);
        if (brazier == null || brazier.getMaxHealth() != 280F) throw new AssertionError("Brazier health not doubled");
        brazier.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(140D);
        brazier.setHealth(70F);
        var saved = new net.minecraft.nbt.CompoundTag();
        brazier.saveWithoutId(saved);
        brazier.load(saved);
        if (brazier.getMaxHealth() != 280F || brazier.getHealth() != 140F)
            throw new AssertionError("Old brazier health percentage not preserved");
        var damage = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        for (String id : java.util.List.of("bullet", "bullet_ignore_armor", "bullet_void", "bullet_void_ignore_armor")) {
            var holder = damage.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.DAMAGE_TYPE,
                    new ResourceLocation("tacz", id)));
            if (net.krodark.asterion.game.BossGunDamage.scale(new net.minecraft.world.damagesource.DamageSource(holder), 20F) != 10F)
                throw new AssertionError("TaCZ bullet not reduced: " + id);
        }
        if (net.krodark.asterion.game.BossGunDamage.scale(level.damageSources().magic(), 20F) != 20F)
            throw new AssertionError("Magic damage was reduced");
        Asterion.LOGGER.info("ASTERION_BALANCE PASS: health 280, saved injury migration, four TaCZ bullet types halved, magic unchanged");
    }

    void verifyRestoration(Minecraft client) {
        serverReady=false;
        client.getSingleplayerServer().execute(() -> {
            try {
                var level=client.getSingleplayerServer().getLevel(Asterion.ASTERION_LEVEL);
                if (!level.getBlockState(blast).is(net.minecraft.world.level.block.Blocks.STONE)
                        || !level.getBlockState(placed).isAir()
                        || !level.getBlockState(changed).is(net.minecraft.world.level.block.Blocks.GOLD_BLOCK))
                    throw new AssertionError("Explosion restoration replaced player blocks or lost terrain");
                level.setChunkForced(5,5,false);
                Asterion.LOGGER.info("ASTERION_EXPLOSION PASS: terrain restored; player-built blocks not renewed; replacement preserved");
                serverReady=true;
            } catch (Throwable error) { failure=error; }
        });
    }

    static void drawGun(Minecraft client) {
        try {
            var operator=Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");
            var gunner=operator.getMethod("fromLocalPlayer",net.minecraft.client.player.LocalPlayer.class).invoke(null,client.player);
            operator.getMethod("draw",ItemStack.class).invoke(gunner,client.player.getMainHandItem());
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    void exerciseCombat(Minecraft client) {
        try {
            var operator=Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");
            var gunner=operator.getMethod("fromLocalPlayer",net.minecraft.client.player.LocalPlayer.class).invoke(null,client.player);
            Object result=operator.getMethod("shoot").invoke(gunner);
            if (!result.toString().equals("SUCCESS")) throw new AssertionError("TaCZ shooting rejected: "+result);
            client.getSingleplayerServer().execute(() -> {
                try {
                    var player=client.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                    var spells=Class.forName("io.redspace.ironsspellbooks.api.registry.SpellRegistry");
                    var holder=spells.getField("FIREBOLT_SPELL").get(null);
                    var spell=holder.getClass().getMethod("get").invoke(holder);
                    var source=Class.forName("io.redspace.ironsspellbooks.api.spells.CastSource");
                    spell.getClass().getMethod("castSpell",Level.class,int.class,net.minecraft.server.level.ServerPlayer.class,source,boolean.class)
                            .invoke(spell,player.level(),1,player,source.getField("COMMAND").get(null),true);
                    Asterion.LOGGER.info("ASTERION_MODPACK combat PASS: TaCZ shot and Iron's Firebolt in {}",player.level().dimension().location());
                } catch (Throwable error) { failure=error; }
            });
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    static void capture(Minecraft client,String name) {
        net.minecraft.client.Screenshot.grab(client.gameDirectory,name,client.getMainRenderTarget(),message -> {});
    }

    void checkScene(Minecraft client, boolean maze) {
        checkShaders(!maze);
        ShaderFramebufferRegression.run();
        int y=maze?180:160;
        for (BlockPos position : List.of(new BlockPos(-2,y,4),new BlockPos(2,y,4))) {
            var entity=client.level.getBlockEntity(position);
            if (entity==null || client.getBlockEntityRenderDispatcher().getRenderer(entity)==null)
                throw new AssertionError("Magic block entity renderer missing at "+position);
        }
        try {
            var api=Class.forName("com.tacz.guns.api.TimelessAPI");
            var model=(Optional<?>)api.getMethod("getGunDisplay",ItemStack.class).invoke(null,client.player.getMainHandItem());
            if (model.isEmpty()) throw new AssertionError("TaCZ display model missing");
            var session=Class.forName("xaero.map.WorldMapSession").getMethod("getCurrentSession").invoke(null);
            var processor=session.getClass().getMethod("getMapProcessor").invoke(session);
            var world=processor.getClass().getMethod("getMapWorld").invoke(processor);
            var dimension=world.getClass().getMethod("getCurrentDimensionId").invoke(world);
            if (!client.level.dimension().equals(dimension)) throw new AssertionError("Map dimension stale: "+dimension);
            var overlay=client.gui.getBossOverlay();
            var hook=Arrays.stream(overlay.getClass().getDeclaredMethods()).filter(m -> m.getName().contains("asterion$hideVanillaName") && m.getParameterCount()==1).findFirst().orElseThrow();
            hook.setAccessible(true);
            var other=Component.literal("Foreign boss");
            if (hook.invoke(overlay,other)!=other || !((Component)hook.invoke(overlay,Component.literal("THE MINOTAUR"))).getString().isEmpty())
                throw new AssertionError("Boss name compatibility changed other bosses");
            Asterion.LOGGER.info("ASTERION_MODPACK scene PASS: {}",client.level.dimension().location());
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void checkShaders(boolean expected) {
        try {
            Class<?> api;
            try { api=Class.forName("net.irisshaders.iris.api.v0.IrisApi"); }
            catch (ClassNotFoundException absent) { api=Class.forName("net.coderbot.iris.api.v0.IrisApi"); }
            boolean active=(Boolean)api.getMethod("isShaderPackInUse").invoke(api.getMethod("getInstance").invoke(null));
            if (active!=expected || net.krodark.asterion.port.client.ShaderCompatibility.externalShadersActive()!=expected)
                throw new AssertionError("Shader state disagrees with current dimension");
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    void openMap(Minecraft client) {
        try {
            var session=Class.forName("xaero.map.WorldMapSession").getMethod("getCurrentSession").invoke(null);
            var processor=session.getClass().getMethod("getMapProcessor").invoke(session);
            var type=Class.forName("xaero.map.gui.GuiMap");
            var screen=(net.minecraft.client.gui.screens.Screen)type.getConstructors()[0].newInstance(null,null,processor,client.player);
            client.setScreen(screen);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}

