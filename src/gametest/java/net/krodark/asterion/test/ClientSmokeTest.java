package net.krodark.asterion.test;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.krodark.asterion.Asterion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/** Opt-in development test; this source set is never shipped in the mod. */
public final class ClientSmokeTest implements ClientModInitializer {
    private int titleTicks, worldTicks, encounterWaitTicks;
    private static int graphicsErrors;
    private long previousFrame;
    private final java.util.List<Double> frameTimes = new java.util.ArrayList<>();
    private static org.lwjgl.opengl.GLDebugMessageCallback debugCallback;
    private boolean started;
    private final long deadline = System.nanoTime() + 480_000_000_000L;

    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("asterion.clientSmoke")) return;
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        BloomSmoke.register(() -> worldTicks);
        VineSmoke.register(() -> worldTicks);
        PolishSmoke.register(() -> worldTicks);
        AtmosphereSmoke.register(() -> worldTicks);
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.END.register(context -> {
            long now=System.nanoTime();
            if(worldTicks>=200 && previousFrame!=0) frameTimes.add((now-previousFrame)/1_000_000.0);
            previousFrame=now;
        });
    }
    static void verifyGraphicsAndTaa() {
        if(graphicsErrors!=0) throw new AssertionError("OpenGL errors: "+graphicsErrors);
        if(com.meekdev.amnetic.client.taa.Taa.settings().isEnabled() || com.meekdev.amnetic.client.taa.Taa.jitterActive())
            throw new AssertionError("TAA must stay disabled");
    }
    private void tick(Minecraft client) {
        if(GameplaySmoke.failure!=null) throw new AssertionError("Gameplay scenario failed",GameplaySmoke.failure);
        if (System.nanoTime() > deadline) throw new AssertionError("Client smoke test timed out");
        if (!started && client.screen instanceof TitleScreen && client.getOverlay() == null && ++titleTicks >= 20) {
            started = true;
            org.lwjgl.glfw.GLFW.glfwRestoreWindow(client.getWindow().getWindow());
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getWindow(),854,480);
            net.krodark.asterion.AsterionConfig.INSTANCE.cinematicsEnabled = false;
            net.krodark.asterion.AsterionConfig.INSTANCE.objectiveHudEnabled = false;
            client.options.pauseOnLostFocus = false;
            client.options.renderDistance().set(6);
            client.options.simulationDistance().set(5);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS);
            debugCallback = org.lwjgl.opengl.GLDebugMessageCallback.create((source,type,id,severity,length,message,user) -> {
                if(type == org.lwjgl.opengl.GL43.GL_DEBUG_TYPE_ERROR && ++graphicsErrors <= 3)
                    Asterion.LOGGER.error("ASTERION_SMOKE OpenGL: {}",org.lwjgl.opengl.GLDebugMessageCallback.getMessage(length,message),new Exception("Graphics call trace"));
            });
            org.lwjgl.opengl.GL43.glDebugMessageCallback(debugCallback,0L);
            Asterion.LOGGER.info("ASTERION_SMOKE title ready; creating test world");
            SmokeWorld.create(client);
        }
        if (client.level == null || client.player == null || client.getSingleplayerServer() == null) return;
        worldTicks++;
        if((worldTicks>=130 && worldTicks<440 || worldTicks>=501) && !(client.screen instanceof InputShield)) client.setScreen(new InputShield());
        // The integrated server can fall behind the rendering thread during chunk work.
        // Wait for authoritative phase changes instead of assuming equal tick counts.
        if(worldTicks==650) {
            var boss=client.level.getEntity(GameplaySmoke.bossId);
            if(!(boss instanceof net.krodark.asterion.entity.CursedBrazierEntity brazier)
                    || brazier.phase()!=net.krodark.asterion.entity.CursedBrazierEntity.Phase.ACTIVE) {
                worldTicks--;
                if(++encounterWaitTicks%100==0) GameplaySmoke.server(client,GameplaySmoke::diagnose);
                if(encounterWaitTicks>400) throw new AssertionError("Brazier did not enter combat within 20 seconds");
            }
        }
        // Tests share the desktop; typing elsewhere must not steer the scenario.
        for(var key:java.util.List.of(client.options.keyUp,client.options.keyDown,client.options.keyLeft,client.options.keyRight,client.options.keyJump,client.options.keyShift,client.options.keyAttack,client.options.keyUse)) key.setDown(false);
        if(worldTicks>=130 && worldTicks<345) {
            client.player.setPos(.5,178,-4.5);
            client.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        }
        if(worldTicks>=130 && worldTicks<345) {
            client.player.setYRot(0); client.player.setXRot(0);
            client.player.yRotO=0; client.player.xRotO=0;
        }
        if(worldTicks==100) client.player.getInventory().selected=0;
        if (worldTicks == 60) {
            Asterion.LOGGER.info("ASTERION_SMOKE overworld ready");
            // This artificial platform is above the maze walls. Suppress the
            // roof trespass timer in this test world so it cannot stun fixtures.
            net.krodark.asterion.AsterionConfig.INSTANCE.wallZapDelayTicks = Integer.MAX_VALUE;
            client.getSingleplayerServer().execute(() -> {
                var server = client.getSingleplayerServer();
                var source = server.createCommandSourceStack();
                server.getCommands().performPrefixedCommand(source,"gamerule doMobSpawning false");
                server.getCommands().performPrefixedCommand(source,"give @a minecraft:torch");
                server.getCommands().performPrefixedCommand(source,"execute in asterion:asterion_dimension run tp @a 0 180 0");
            });
        }
        if (worldTicks == 120) {
            client.getSingleplayerServer().execute(() -> {
                var server = client.getSingleplayerServer();
                var source = server.createCommandSourceStack();
                for(String command:java.util.List.of(
                    "execute in asterion:asterion_dimension run fill -7 178 -7 7 189 7 minecraft:air",
                    "execute in asterion:asterion_dimension run fill -7 177 -7 7 177 7 minecraft:stone",
                    "execute in asterion:asterion_dimension run tp @a 0 178 -5 0 0",
                    "execute in asterion:asterion_dimension run summon asterion:minotaur 0 178 3 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[180.0f,0.0f]}"))
                    server.getCommands().performPrefixedCommand(source,command);
            });
        }
        if(Boolean.getBoolean("asterion.vineSmoke") && worldTicks>=160) { VineSmoke.tick(client,worldTicks);return; }
        if(Boolean.getBoolean("asterion.polishSmoke") && worldTicks>=160) { PolishSmoke.tick(client,worldTicks);return; }
        if(Boolean.getBoolean("asterion.atmosphereSmoke") && worldTicks>=160) { AtmosphereSmoke.tick(client,worldTicks);return; }
        if(Boolean.getBoolean("asterion.bloomSmoke") && worldTicks>=160) { BloomSmoke.tick(client,worldTicks);return; }
        if (worldTicks == 160) {
            if (!client.level.dimension().equals(Asterion.ASTERION_LEVEL)) throw new AssertionError("Labyrinth transfer failed");
            Asterion.LOGGER.info("ASTERION_SMOKE Labyrinth ready; exercising particles");
            var pos=client.player.position();
            for(var type:java.util.List.of(Asterion.GREEK_FIRE,Asterion.BOMBARDIER_STENCH,Asterion.BOMBARDIER_GAS_FIRE,Asterion.FIREFLY,Asterion.DOOR_SMOKE,Asterion.LAMENTER_TEAR))
                client.level.addParticle(type,pos.x+2,pos.y,pos.z+2,0,.02,0);
        }
        if(worldTicks==180) {
            GameplaySmoke.server(client,p -> GameplaySmoke.weaponMode(p,1));
            net.krodark.asterion.port.client.PortPortalRenderer.receive(new net.krodark.asterion.network.GatewayPortalPayload(true,new net.minecraft.core.BlockPos(4,180,3),180,321));
        }
        if(worldTicks==220) {
            com.meekdev.amnetic.client.bloom.Bloom.settings().enabled(false);
        }
        if(worldTicks==230) screenshot(client,"bloom-off");
        if(worldTicks==240) net.krodark.asterion.port.client.PortEmissiveConfig.apply();
        if(worldTicks==280) screenshot(client,"bloom-on");
        if(worldTicks==285) GameplaySmoke.server(client,p -> GameplaySmoke.weaponMode(p,2));
        if(worldTicks==310) screenshot(client,"minotaur-swords");
        if(worldTicks==320) client.getSingleplayerServer().execute(() -> {
            var server=client.getSingleplayerServer();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"execute in asterion:asterion_dimension run fill -6 178 0 6 185 0 minecraft:stone");
        });
        if (worldTicks == 340) {
            screenshot(client,"occluded");
            frameTimes.sort(Double::compare);
            if(!frameTimes.isEmpty()) Asterion.LOGGER.info("ASTERION_SMOKE frame time median={} ms, p95={} ms ({} frames)",frameTimes.get(frameTimes.size()/2),frameTimes.get(Math.min(frameTimes.size()-1,(int)(frameTimes.size()*.95))),frameTimes.size());
            net.minecraft.client.Screenshot.grab(client.gameDirectory,client.getMainRenderTarget(),message -> Asterion.LOGGER.info("ASTERION_SMOKE screenshot: {}",message.getString()));
            if (graphicsErrors != 0) throw new AssertionError("OpenGL errors during smoke test: " + graphicsErrors);
            if(com.meekdev.amnetic.client.taa.Taa.settings().isEnabled() || com.meekdev.amnetic.client.taa.Taa.jitterActive()) throw new AssertionError("TAA must stay disabled");
        }
        if(worldTicks==345) GameplaySmoke.server(client,p -> p.connection.teleport(.5,178,-4.5,0,0));
        if(worldTicks==350) {
            client.player.setPos(.5,178,-4.5);
            client.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            net.krodark.asterion.port.client.ragdoll.DismembermentEngine.INSTANCE.forcePlayerTumble(client,client.player.position().add(0,0,1),new net.minecraft.world.phys.Vec3(.05,.12,.03),.5F);
        }
        if(worldTicks>=351 && worldTicks<=410) {
            try { PhysicsSmokeAssertions.verify(client.player.getId()); }
            catch(AssertionError error) { throw new AssertionError("Physics at tick="+worldTicks+", player="+client.player.position()+", alive="+client.player.isAlive(),error); }
        }
        if(worldTicks==410) {
            screenshot(client,"ragdoll");
            net.krodark.asterion.port.client.ragdoll.DismembermentEngine.INSTANCE.releaseRagdoll(client.player.getId());
        }
        if(worldTicks==440) {
            if(net.krodark.asterion.port.client.PortRagdolls.localMovementLocked()) throw new AssertionError("Ragdoll recovery did not release movement");
            if(graphicsErrors!=0) throw new AssertionError("OpenGL errors: "+graphicsErrors);
            Asterion.LOGGER.info("ASTERION_SMOKE rendering and physics passed; starting gameplay checks");
            GameplaySmoke.server(client,GameplaySmoke::forge);
        }
        if(worldTicks==480) {
            if(!(client.screen instanceof net.krodark.asterion.port.client.PortCrucibleScreen)) throw new AssertionError("Forge screen packet did not open the interface");
            screenshot(client,"forging");
            GameplaySmoke.server(client,p -> VersionGameplay.advancement(p,"tarnished_gold"));
        }
        if(worldTicks==500) client.setScreen(new InputShield());
        if(worldTicks==530) {
            client.player.getInventory().selected=0;
            if(!client.player.getMainHandItem().is(Asterion.FORGED_SWORD)) throw new AssertionError("Forged sword did not synchronize to slot 0: "+client.player.getMainHandItem());
            var model=client.getItemRenderer().getModel(client.player.getMainHandItem(),client.level,client.player,0);
            if(model==client.getModelManager().getMissingModel()) throw new AssertionError("Forged item model missing");
            screenshot(client,"forged-sword");
        }
        if(worldTicks==550) {
            net.krodark.asterion.AsterionConfig.INSTANCE.cinematicsEnabled=true;
            GameplaySmoke.server(client,GameplaySmoke::spawnEncounter);
        }
        if(worldTicks==590) {
            if(!net.krodark.asterion.port.client.PortCursedBrazierCinematic.isActive()) throw new AssertionError("Awakening packet did not start the cutscene");
            var pose=net.krodark.asterion.port.client.PortCursedBrazierCinematic.cameraPose(client.player.getEyePosition(),0);
            if(pose==null || !Double.isFinite(pose.position().lengthSqr())) throw new AssertionError("Invalid miniboss camera track");
            screenshot(client,"brazier-cutscene");
        }
        if(worldTicks==710) {
            if(net.krodark.asterion.port.client.PortCursedBrazierCinematic.isActive() || client.options.hideGui) throw new AssertionError("Miniboss cutscene failed to restore HUD/control");
            GameplaySmoke.server(client,GameplaySmoke::verifyShield);
            screenshot(client,"brazier-shield");
        }
        if(worldTicks>730 && worldTicks<1050 && worldTicks%10==0) GameplaySmoke.server(client,GameplaySmoke::sampleCombat);
        if(worldTicks==930) screenshot(client,"brazier-combat");
        if(worldTicks==1060) GameplaySmoke.server(client,GameplaySmoke::defeat);
        if(worldTicks==1110) GameplaySmoke.server(client,VersionGameplay::roof);
        if(worldTicks==1160) {
            var pose=net.krodark.asterion.port.client.PortRoofCollapseCinematic.cameraPose(client.player.getEyePosition(),0);
            if(pose==null || !Double.isFinite(pose.position().lengthSqr())) throw new AssertionError("Roof cutscene camera missing");
            screenshot(client,"roof-cutscene");
        }
        if(worldTicks==1320) {
            if(net.krodark.asterion.port.client.PortRoofCollapseCinematic.isActive() || client.options.hideGui) throw new AssertionError("Roof cinematic did not restore control");
            if(graphicsErrors!=0) throw new AssertionError("OpenGL errors: "+graphicsErrors);
            Asterion.LOGGER.info("ASTERION_SMOKE PASSED: dimension, rendering, TAA off, physics, forging, crafting, advancements, miniboss and cutscenes");
            client.stop();
        }
    }
    private static final class InputShield extends net.minecraft.client.gui.screens.Screen {
        InputShield() { super(net.minecraft.network.chat.Component.literal("Automated gameplay test")); }
        public void renderBackground(net.minecraft.client.gui.GuiGraphics g,int x,int y,float partial) {}
        public void renderBackground(net.minecraft.client.gui.GuiGraphics g) {}
        public void renderBlurredBackground(float partial) {}
        @Override public boolean isPauseScreen() { return false; }
        @Override public boolean shouldCloseOnEsc() { return false; }
    }
    private static void screenshot(Minecraft client,String stage) {
        net.minecraft.client.Screenshot.grab(client.gameDirectory,"asterion-"+stage+".png",client.getMainRenderTarget(),message -> Asterion.LOGGER.info("ASTERION_SMOKE {}: {}",stage,message.getString()));
    }
}
