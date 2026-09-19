package net.krodark.asterion.port.compat;
public final class BlockCompat {
 public static net.minecraft.world.level.block.FenceGateBlock fenceGate(net.minecraft.world.level.block.state.properties.WoodType wood,net.minecraft.world.level.block.state.BlockBehaviour.Properties props){
 //? if >=1.20.5 {
 return new net.minecraft.world.level.block.FenceGateBlock(wood,props);
 //?} else {
 /*return new net.minecraft.world.level.block.FenceGateBlock(props,wood);*/
 //?}
 }
}
