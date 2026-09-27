package net.krodark.asterion.port.legacy;
import net.minecraft.world.InteractionResult;
public enum ItemInteractionResult {
 SUCCESS(InteractionResult.SUCCESS), CONSUME(InteractionResult.CONSUME), CONSUME_PARTIAL(InteractionResult.CONSUME_PARTIAL), FAIL(InteractionResult.FAIL), SKIP_DEFAULT_BLOCK_INTERACTION(InteractionResult.PASS), PASS_TO_DEFAULT_BLOCK_INTERACTION(InteractionResult.PASS);
 private final InteractionResult result;
 ItemInteractionResult(InteractionResult result){this.result=result;}
 public InteractionResult result(){return result;}
 public static ItemInteractionResult sidedSuccess(boolean client){return client?SUCCESS:CONSUME;}
}
