package net.krodark.asterion.update.underworld;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/** Aim at a floor, wall or ceiling: the test variant freezes behavior, not contact IK. */
public final class SpiderCommands {
    private SpiderCommands() { }
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->dispatcher.register(
                Commands.literal("asterion").then(Commands.literal("spider")
                        .requires(source->source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.literal("test").executes(context->spawn(context.getSource(),true)))
                        .then(Commands.literal("spawn").executes(context->spawn(context.getSource(),false))))));
    }
    private static int spawn(CommandSourceStack source,boolean noAi) throws CommandSyntaxException {
        var player=source.getPlayerOrException();
        var level=source.getLevel();
        var eye=player.getEyePosition();
        var hit=level.clip(new ClipContext(eye,eye.add(player.getLookAngle().scale(24)),
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        if(hit.getType()==HitResult.Type.MISS) {
            source.sendFailure(Component.literal("Aim at a floor, wall, or ceiling within 24 blocks.")); return 0;
        }
        var spider=UnderworldContent.SPIDER.create(level,EntitySpawnReason.COMMAND);
        if(spider==null)return 0;
        var outward=hit.getDirection().getUnitVec3();
        double radius=hit.getDirection().getAxis().isVertical()?spider.getBbHeight()*.5:spider.getBbWidth()*.5;
        var center=hit.getLocation().add(outward.scale(radius+.03));
        spider.setPos(center.x,center.y-spider.getBbHeight()*.5,center.z);
        spider.setYRot(player.getYRot()); spider.setYBodyRot(player.getYRot());
        if(!level.noCollision(spider,spider.getBoundingBox())) {
            source.sendFailure(Component.literal("The spider needs more body clearance at that point.")); return 0;
        }
        spider.configureTest(hit.getDirection().getOpposite(),noAi);
        if(!spider.hasSurfaceSupport()) {
            source.sendFailure(Component.literal("Aim at a wider solid surface so the spider has stable support.")); return 0;
        }
        if(!level.addFreshEntity(spider))return 0;
        source.sendSuccess(()->Component.literal(noAi
                ? "Spawned stationary spider (NoAI). F3+J shows its support and leg contacts in any dimension."
                : "Spawned active spider. F3+J shows its support and leg contacts."),true);
        return 1;
    }
}
