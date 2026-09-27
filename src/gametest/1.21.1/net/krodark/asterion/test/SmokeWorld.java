package net.krodark.asterion.test;
final class SmokeWorld {
 static void create(net.minecraft.client.Minecraft client) {
  var settings = new net.minecraft.world.level.LevelSettings("Asterion smoke",net.minecraft.world.level.GameType.CREATIVE,false,net.minecraft.world.Difficulty.NORMAL,true,new net.minecraft.world.level.GameRules(),net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
  client.createWorldOpenFlows().createFreshLevel("asterion-smoke-"+System.currentTimeMillis(),settings,new net.minecraft.world.level.levelgen.WorldOptions(321L,false,false),net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions, new net.minecraft.client.gui.screens.TitleScreen());
 }
}
