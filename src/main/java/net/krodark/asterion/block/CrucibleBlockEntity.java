package net.krodark.asterion.block;

import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.network.CrucibleControlPayload;
import net.krodark.asterion.network.CrucibleScreenPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class CrucibleBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    public static final int MIN_TEMPERATURE = 0;
    public static final int MAX_TEMPERATURE = 1000;
    public static final int STEP = 25;
    public static final int TOLERANCE = 25;
    private int temperature;
    private int targetTemperature = 350;
    private int fuelTicks;
    private int mold;
    private boolean moldInserted;
    private int iron;
    private int copper;
    private int gold;
    private int netherite;
    private int celestialBronze;
    private int bonesteel;
    private int celestialSteel;
    private int celestialGold;
    private int regularGold;
    private int carbon;
    private int purityPoints;
    private int pouringTicks;
    private int autoPourTicks;
    /** IDs follow the artist folders; 2 is tarnished_gold and 8 is ordinary gold. */
    private int primaryMetal = -1;
    private int secondaryMetal = -1;
    private String metalSequence = "";

    public CrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(Asterion.CRUCIBLE_BLOCK_ENTITY, pos, state);
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }

    public enum Mold {
        INGOT("Ingot Cast", 350), SWORD_GUARD("Sword Guard Cast", 500),
        SWORD_POMMEL("Sword Pommel Cast", 425), SWORD_BLADE("Sword Blade Cast", 650),
        AXE_HEAD("Axe Head Cast", 725);
        private final String label;
        private final int target;
        Mold(String label, int target) { this.label = label; this.target = target; }
        public String label() { return label; }
        public int target() { return target; }
    }

    public int temperature() { return temperature; }
    public int targetTemperature() { return targetTemperature; }
    public int fuelTicks() { return fuelTicks; }
    public String metalSequence() { return metalSequence; }
    public int autoPourProgress() { return autoPourTicks; }
    public int selectedMoldIndex() { return moldInserted ? mold().ordinal() : -1; }
    public int materialUnits() {
        return iron + copper + gold + netherite + celestialBronze + bonesteel + celestialSteel + celestialGold + regularGold;
    }
    public int purity() { return materialUnits() == 0 ? 0 : Math.round(purityPoints / (float)materialUnits()); }
    public int mixColor() {
        if (metalSequence.isEmpty()) return 0x514A43;
        int color = metalColor(metalSequence.charAt(0) - '0');
        // Every later pour is treated as a half-transparent coat over what is already molten.
        // This intentionally makes A→B different from B→A.
        for (int index = 1; index < metalSequence.length(); index++)
            color = overlay(color, metalColor(metalSequence.charAt(index) - '0'), 0.5F);
        return color;
    }

    private static int overlay(int base, int coat, float alpha) {
        int r = Math.round(((base >> 16) & 255) * (1F - alpha) + ((coat >> 16) & 255) * alpha);
        int g = Math.round(((base >> 8) & 255) * (1F - alpha) + ((coat >> 8) & 255) * alpha);
        int b = Math.round((base & 255) * (1F - alpha) + (coat & 255) * alpha);
        return r << 16 | g << 8 | b;
    }

    private static int metalColor(int metal) {
        return switch (metal) {
            case 0 -> 0xD8DCE0; case 1 -> 0xD9784A; case 2 -> 0xFFCD42;
            case 3 -> 0x443A4D; case 4 -> 0xD89A54; case 5 -> 0xAAA49C;
            case 6 -> 0x91C7D9; case 7 -> 0xFFE47A; case 8 -> 0xFFD24A; default -> 0xFFFFFF;
        };
    }
    public Mold mold() { return Mold.values()[Mth.clamp(mold, 0, Mold.values().length - 1)]; }
    public boolean calibrated() { return moldInserted && Math.abs(temperature - mold().target()) <= TOLERANCE; }

    public static int moldIndex(Item item) {
        if (item == Asterion.INGOT_CAST) return Mold.INGOT.ordinal();
        if (item == Asterion.SWORD_GUARD_CAST) return Mold.SWORD_GUARD.ordinal();
        if (item == Asterion.SWORD_POMMEL_CAST) return Mold.SWORD_POMMEL.ordinal();
        if (item == Asterion.SWORD_BLADE_CAST) return Mold.SWORD_BLADE.ordinal();
        if (item == Asterion.AXE_HEAD_CAST) return Mold.AXE_HEAD.ordinal();
        return -1;
    }

    public static Item moldItem(int index) {
        return switch (Mth.clamp(index, 0, Mold.values().length - 1)) {
            case 0 -> Asterion.INGOT_CAST; case 1 -> Asterion.SWORD_GUARD_CAST;
            case 2 -> Asterion.SWORD_POMMEL_CAST; case 3 -> Asterion.SWORD_BLADE_CAST;
            default -> Asterion.AXE_HEAD_CAST;
        };
    }

    public boolean insert(ServerPlayer player, ItemStack stack) {
        int insertedMold = moldIndex(stack.getItem());
        if (insertedMold >= 0) {
            if (pouringTicks > 0 || moldInserted) return false;
            mold = insertedMold;
            moldInserted = true;
            targetTemperature = mold().target();
            stack.shrink(1);
            changedAndSync();
            return true;
        }
        if (insertForgedAlloy(stack, player)) {
            stack.shrink(1);
            changedAndSync();
            return true;
        }
        if ((stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) && iron > 0) {
            iron--;
            celestialSteel++;
            metalSequence = replaceFirstMetal(metalSequence, '0', '6');
            if (primaryMetal == 0) primaryMetal = 6;
            if (secondaryMetal == 0) secondaryMetal = 6;
            stack.shrink(1);
            changedAndSync();
            return true;
        }
        if (materialUnits() < 4 && insertMaterial(stack, player)) {
            reactIronAndCarbon();
            reactIronAndCopper();
            stack.shrink(1);
            changedAndSync();
            return true;
        }
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) return false;
        FuelValues fuels = FuelValues.vanillaBurnTimes(server.registryAccess(), server.enabledFeatures());
        int burn = fuels.burnDuration(stack);
        if (burn <= 0) return false;
        if ((stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) && carbon < 4) carbon++;
        fuelTicks = Math.min(20 * 60 * 10, fuelTicks + burn);
        stack.shrink(1);
        changedAndSync();
        return true;
    }

    /** Re-melts a previously poured alloy without flattening its insertion order or ratios. */
    private boolean insertForgedAlloy(ItemStack stack, ServerPlayer player) {
        if (!stack.is(Asterion.FORGED_INGOT) && !stack.is(Asterion.TARNISHED_GOLD_INGOT)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) return false;
        net.minecraft.nbt.CompoundTag tag = data.copyTag();
        String sequence = tag.getStringOr("metal_sequence", "");
        if (sequence.isEmpty()) {
            sequence = "0".repeat(Mth.clamp(tag.getIntOr("iron", 0), 0, 4))
                    + "1".repeat(Mth.clamp(tag.getIntOr("copper", 0), 0, 4))
                    + "2".repeat(Mth.clamp(tag.getIntOr("gold", 0), 0, 4));
        }
        if (sequence.isEmpty() || materialUnits() + sequence.length() > 4
                || sequence.chars().anyMatch(value -> value < '0' || value > '8')) return false;
        int storedPurity = player.isCreative() ? 100 : Mth.clamp(tag.getIntOr("purity", 75), 1, 100);
        for (int index = 0; index < sequence.length(); index++) {
            int metal = sequence.charAt(index) - '0';
            if (metal == 0) iron++;
            else if (metal == 1) copper++;
            else if (metal == 2) gold++;
            else if (metal == 3) netherite++;
            else if (metal == 4) celestialBronze++;
            else if (metal == 5) bonesteel++;
            else if (metal == 6) celestialSteel++;
            else if (metal == 7) celestialGold++;
            else regularGold++;
            purityPoints += storedPurity;
            if (primaryMetal < 0) primaryMetal = metal;
            else if (metal != primaryMetal && secondaryMetal < 0) secondaryMetal = metal;
            metalSequence += (char)('0' + metal);
        }
        return true;
    }

    private boolean insertMaterial(ItemStack stack, ServerPlayer player) {
        int metal;
        if (stack.is(Items.IRON_INGOT)) { iron++; metal = 0; }
        else if (stack.is(Items.COPPER_INGOT)) { copper++; metal = 1; }
        else if (stack.is(Asterion.TARNISHED_GOLD_INGOT)) { gold++; metal = 2; }
        else if (stack.is(Items.GOLD_INGOT)) {
            int purity = materialPurity(stack, player);
            if (purity < 50) { gold++; metal = 2; }
            else if (purity > 90) { celestialGold++; metal = 7; }
            else { regularGold++; metal = 8; }
            purityPoints += purity;
            if (primaryMetal < 0) primaryMetal = metal;
            else if (metal != primaryMetal && secondaryMetal < 0) secondaryMetal = metal;
            metalSequence += (char)('0' + metal);
            return true;
        }
        else if (stack.is(Items.NETHERITE_INGOT)) { netherite++; metal = 3; }
        else if (stack.is(Asterion.CELESTIAL_BRONZE_INGOT)) { celestialBronze++; metal = 4; }
        else if (stack.is(Asterion.BONESTEEL_INGOT)) { bonesteel++; metal = 5; }
        else if (stack.is(Asterion.CELESTIAL_STEEL_INGOT)) { celestialSteel++; metal = 6; }
        else if (stack.is(Asterion.CELESTIAL_GOLD_INGOT)) { celestialGold++; metal = 7; }
        else return false;
        purityPoints += materialPurity(stack, player);
        if (primaryMetal < 0) primaryMetal = metal;
        else if (metal != primaryMetal && secondaryMetal < 0) secondaryMetal = metal;
        metalSequence += (char)('0' + metal);
        return true;
    }

    private void reactIronAndCopper() {
        while (iron > 0 && copper > 0) {
            iron--; copper--; celestialBronze += 2;
            metalSequence = replaceFirstMetal(replaceFirstMetal(metalSequence, '0', '4'), '1', '4');
        }
        if (!metalSequence.isEmpty()) {
            primaryMetal = metalSequence.charAt(0) - '0';
            secondaryMetal = -1;
            for (int i = 1; i < metalSequence.length(); i++) if (metalSequence.charAt(i) != metalSequence.charAt(0)) {
                secondaryMetal = metalSequence.charAt(i) - '0'; break;
            }
        }
    }

    private void reactIronAndCarbon() {
        while (iron > 0 && carbon > 0) {
            iron--; carbon--; celestialSteel++;
            metalSequence = replaceFirstMetal(metalSequence, '0', '6');
        }
        if (!metalSequence.isEmpty()) primaryMetal = metalSequence.charAt(0) - '0';
    }

    private static String replaceFirstMetal(String sequence, char from, char to) {
        int index = sequence.indexOf(from);
        return index < 0 ? sequence : sequence.substring(0, index) + to + sequence.substring(index + 1);
    }

    private static int materialPurity(ItemStack stack, ServerPlayer player) {
        if (player.isCreative()) return 100;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            int stored = data.copyTag().getIntOr("purity", 0);
            if (stored > 0) return Mth.clamp(stored, 1, 100);
        }
        return 55 + player.getRandom().nextInt(46);
    }

    public boolean removeMold(ServerPlayer player) {
        if (!moldInserted || pouringTicks > 0) return false;
        give(player, new ItemStack(moldItem(mold)));
        moldInserted = false;
        changedAndSync();
        return true;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    public void open(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, CrucibleScreenPayload.TYPE))
            ServerPlayNetworking.send(player, snapshot());
    }

    public void control(ServerPlayer player, int action) {
        if (CrucibleControlPayload.isInsertSlot(action)) {
            int slot = CrucibleControlPayload.inventorySlot(action);
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && insert(player, stack)) open(player);
            return;
        }
        if (CrucibleControlPayload.isRemoveMaterial(action)) {
            removeMaterial(player, CrucibleControlPayload.materialLayer(action));
            open(player);
            return;
        }
        if (CrucibleControlPayload.isSelectMold(action)) {
            selectMold(player, CrucibleControlPayload.moldIndex(action));
            open(player);
            return;
        }
        if (action == CrucibleControlPayload.HEAT)
            targetTemperature = Math.min(MAX_TEMPERATURE, targetTemperature + STEP);
        else if (action == CrucibleControlPayload.COOL)
            targetTemperature = Math.max(MIN_TEMPERATURE, targetTemperature - STEP);
        else if (action == CrucibleControlPayload.NEXT_MOLD)
            targetTemperature = moldInserted ? mold().target() : targetTemperature;
        else if (action == CrucibleControlPayload.POUR) {
            pour(player);
            return;
        }
        else return;
        changedAndSync();
        open(player);
    }

    private void selectMold(ServerPlayer player, int selected) {
        if (pouringTicks > 0 || selected < 0 || selected >= Mold.values().length
                || moldInserted && mold == selected) return;
        Item wanted = moldItem(selected);
        int foundSlot = -1;
        for (int slot = 0; slot < 36; slot++) if (player.getInventory().getItem(slot).is(wanted)) {
            foundSlot = slot; break;
        }
        if (foundSlot < 0) return;
        ItemStack oldMold = moldInserted ? new ItemStack(moldItem(mold)) : ItemStack.EMPTY;
        player.getInventory().getItem(foundSlot).shrink(1);
        mold = selected;
        moldInserted = true;
        targetTemperature = mold().target();
        autoPourTicks = 0;
        if (!oldMold.isEmpty()) give(player, oldMold);
        changedAndSync();
    }

    private void removeMaterial(ServerPlayer player, int layer) {
        if (pouringTicks > 0 || layer < 0 || layer >= metalSequence.length()) return;
        int metal = metalSequence.charAt(layer) - '0';
        int returnedPurity = purity();
        metalSequence = metalSequence.substring(0, layer) + metalSequence.substring(layer + 1);
        switch (metal) {
            case 0 -> iron--; case 1 -> copper--; case 2 -> gold--; case 3 -> netherite--;
            case 4 -> celestialBronze--; case 5 -> bonesteel--; case 6 -> celestialSteel--;
            case 7 -> celestialGold--; case 8 -> regularGold--;
        }
        purityPoints = Math.max(0, purityPoints - returnedPurity);
        primaryMetal = metalSequence.isEmpty() ? -1 : metalSequence.charAt(0) - '0';
        secondaryMetal = -1;
        for (int i = 1; i < metalSequence.length(); i++) if (metalSequence.charAt(i) - '0' != primaryMetal) {
            secondaryMetal = metalSequence.charAt(i) - '0'; break;
        }
        autoPourTicks = 0;
        give(player, returnedMetal(metal, returnedPurity));
        changedAndSync();
    }

    private static ItemStack returnedMetal(int metal, int purity) {
        Item item = switch (metal) {
            case 0 -> Items.IRON_INGOT; case 1 -> Items.COPPER_INGOT; case 2 -> Asterion.TARNISHED_GOLD_INGOT;
            case 3 -> Items.NETHERITE_INGOT; case 4 -> Asterion.CELESTIAL_BRONZE_INGOT;
            case 5 -> Asterion.BONESTEEL_INGOT; case 6 -> Asterion.CELESTIAL_STEEL_INGOT;
            case 7 -> Asterion.CELESTIAL_GOLD_INGOT; default -> Items.GOLD_INGOT;
        };
        ItemStack stack = new ItemStack(item);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("purity", Mth.clamp(purity, 1, 100));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private void pour(ServerPlayer player) {
        if (!calibrated() || materialUnits() == 0) return;
        Item output = switch (mold()) {
            case INGOT -> gold == materialUnits() ? Asterion.TARNISHED_GOLD_INGOT : Asterion.FORGED_INGOT;
            case SWORD_GUARD -> Asterion.FORGED_SWORD_GUARD;
            case SWORD_POMMEL -> Asterion.FORGED_SWORD_POMMEL;
            case SWORD_BLADE -> Asterion.FORGED_SWORD_BLADE;
            case AXE_HEAD -> Asterion.FORGED_AXE_HEAD;
        };
        ItemStack result = new ItemStack(output);
        int error = Math.abs(temperature - mold().target());
        String quality = error <= 5 ? "Masterwork" : error <= 15 ? "Fine" : "Serviceable";
        result.set(DataComponents.CUSTOM_NAME,
                Component.literal(alloyName() + " " + partName())
                        .withStyle(error <= 5 ? ChatFormatting.GOLD : ChatFormatting.WHITE));
        int total = materialUnits();
        int hardness = purityScaled(weightedTrait(8, 4, 2, 12, 9, 11, 13, 7, 3));
        int edge = purityScaled(weightedTrait(9, 5, 3, 13, 10, 12, 14, 8, 4));
        int conductivity = purityScaled(weightedTrait(3, 10, 9, 2, 8, 2, 11, 15, 12));
        int weight = weightedTrait(8, 7, 10, 11, 8, 9, 7, 8, 10);
        int baseColor = metalColor(primaryMetal);
        int overlayMetal = secondaryMetal < 0 ? primaryMetal : secondaryMetal;
        int overlayColor = metalSequence.length() < 2 ? 0 : 0x80000000
                | metalColor(metalSequence.charAt(metalSequence.length() - 1) - '0');
        result.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                java.util.List.of(purity() / 100.0F), java.util.List.of(),
                layerMaterials(),
                layerColors()));
        result.set(DataComponents.LORE, new ItemLore(java.util.List.of(
                Component.literal(compositionLine(total)).withStyle(ChatFormatting.GRAY),
                Component.literal("Purity " + purity() + "%").withStyle(
                        purity() == 100 ? ChatFormatting.AQUA : ChatFormatting.GRAY),
                Component.literal("Hardness " + hardness + "  Edge " + edge).withStyle(ChatFormatting.DARK_GRAY),
                Component.literal("Conductivity " + conductivity + "  Weight " + weight).withStyle(ChatFormatting.DARK_GRAY),
                Component.literal("Pour accuracy: ±" + error + "°").withStyle(
                        error <= 5 ? ChatFormatting.GREEN : ChatFormatting.YELLOW))));
        net.minecraft.nbt.CompoundTag forging = new net.minecraft.nbt.CompoundTag();
        forging.putString("alloy", alloyName());
        forging.putString("quality", quality);
        forging.putString("part", mold().name().toLowerCase(java.util.Locale.ROOT));
        forging.putInt("iron", iron);
        forging.putInt("copper", copper);
        forging.putInt("gold", gold);
        forging.putInt("netherite", netherite);
        forging.putInt("celestial_bronze", celestialBronze);
        forging.putInt("bonesteel", bonesteel);
        forging.putInt("celestial_steel", celestialSteel);
        forging.putInt("celestial_gold", celestialGold);
        forging.putInt("regular_gold", regularGold);
        forging.putInt("purity", purity());
        forging.putInt("mix_color", mixColor());
        forging.putInt("hardness", hardness);
        forging.putInt("edge", edge);
        forging.putInt("conductivity", conductivity);
        forging.putInt("weight", weight);
        forging.putInt("temperature_error", error);
        forging.putString("primary_metal", metalName(primaryMetal));
        forging.putString("secondary_metal", secondaryMetal < 0 ? "none" : metalName(secondaryMetal));
        forging.putInt("base_color", baseColor);
        forging.putInt("overlay_color", overlayColor);
        forging.putString("metal_sequence", metalSequence);
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(forging));
        give(player, result);
        iron = copper = gold = netherite = celestialBronze = bonesteel = celestialSteel = celestialGold = regularGold = purityPoints = 0;
        primaryMetal = secondaryMetal = -1;
        metalSequence = "";
        pouringTicks = 40;
        temperature = Math.max(0, temperature - 90);
        changedAndSync();
        open(player);
    }

    /** Four real render layers: the first metal is opaque and every later pour is a 50% coat. */
    private java.util.List<Integer> layerColors() {
        java.util.ArrayList<Integer> colors = new java.util.ArrayList<>(4);
        for (int layer = 0; layer < 4; layer++) {
            if (layer >= metalSequence.length()) colors.add(0x00FFFFFF);
            // Every material already has an artist-authored, correctly colored texture.
            // Keep RGB neutral so gold remains gold, iron remains grey, etc.; only alpha
            // participates in the procedural stack.
            else colors.add(layer == 0 ? 0xFFFFFFFF : 0x80FFFFFF);
        }
        return colors;
    }

    private java.util.List<String> layerMaterials() {
        java.util.ArrayList<String> materials = new java.util.ArrayList<>(4);
        for (int layer = 0; layer < 4; layer++) materials.add(layer < metalSequence.length()
                ? metalId(metalSequence.charAt(layer) - '0') : "none");
        return materials;
    }

    private String partName() {
        return switch (mold()) {
            case INGOT -> "Ingot";
            case SWORD_GUARD -> "Sword Guard";
            case SWORD_POMMEL -> "Sword Pommel";
            case SWORD_BLADE -> "Sword Blade";
            case AXE_HEAD -> "Axe Head";
        };
    }

    private int weightedTrait(int ironValue, int copperValue, int goldValue,
                              int netheriteValue, int celestialValue, int boneValue,
                              int celestialSteelValue, int celestialGoldValue, int regularGoldValue) {
        int total = Math.max(1, materialUnits());
        int value = Math.round((iron * ironValue + copper * copperValue + gold * goldValue
                + netherite * netheriteValue + celestialBronze * celestialValue
                + bonesteel * boneValue + celestialSteel * celestialSteelValue
                + celestialGold * celestialGoldValue + regularGold * regularGoldValue) / (float) total);
        // The base metal defines the structure; the later metal behaves as an overlay/coating.
        if (primaryMetal == 0 && (ironValue == 8 || ironValue == 9)) value += 2;
        if (primaryMetal == 1 && copperValue == 10) value += 2;
        if (primaryMetal == 2 && goldValue == 9) value += 2;
        if (secondaryMetal == 0 && ironValue == 9) value += 1;
        if (secondaryMetal == 1 && copperValue == 10) value += 1;
        if (secondaryMetal == 2 && goldValue == 3) value += 1;
        return value;
    }

    private int purityScaled(int value) {
        return Math.max(1, Math.round(value * (0.65F + purity() * 0.0035F)));
    }

    private String compositionLine(int total) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (iron > 0) parts.add("Iron " + Math.round(iron * 100F / total) + "%");
        if (copper > 0) parts.add("Copper " + Math.round(copper * 100F / total) + "%");
        if (gold > 0) parts.add("Tarnished Gold " + Math.round(gold * 100F / total) + "%");
        if (netherite > 0) parts.add("Netherite " + Math.round(netherite * 100F / total) + "%");
        if (celestialBronze > 0) parts.add("Celestial Bronze (Iron + Copper) " + Math.round(celestialBronze * 100F / total) + "%");
        if (bonesteel > 0) parts.add("Bonesteel " + Math.round(bonesteel * 100F / total) + "%");
        if (celestialSteel > 0) parts.add("Celestial Steel (Iron + Carbon) " + Math.round(celestialSteel * 100F / total) + "%");
        if (celestialGold > 0) parts.add("Celestial Gold " + Math.round(celestialGold * 100F / total) + "%");
        if (regularGold > 0) parts.add("Gold " + Math.round(regularGold * 100F / total) + "%");
        return String.join(" · ", parts);
    }

    private String alloyName() {
        if (iron > 0 && materialUnits() == iron) return "Iron";
        if (copper > 0 && materialUnits() == copper) return "Copper";
        if (gold > 0 && materialUnits() == gold) return purity() > 90 ? "Pure Gold" : "Tarnished Gold";
        if (netherite > 0 && materialUnits() == netherite) return "Netherite";
        if (celestialBronze > 0 && materialUnits() == celestialBronze) return "Celestial Bronze";
        if (bonesteel > 0 && materialUnits() == bonesteel) return "Bonesteel";
        if (celestialSteel > 0 && materialUnits() == celestialSteel) return "Celestial Steel";
        if (celestialGold > 0 && materialUnits() == celestialGold) return "Celestial Gold";
        if (regularGold > 0 && materialUnits() == regularGold) return purity() > 90 ? "Pure Gold" : "Gold";
        if (materialUnits() > 0 && secondaryMetal >= 0 && distinctMetals() == 2)
            return metalName(secondaryMetal) + "-Plated " + metalName(primaryMetal);
        return distinctMetals() == 3 ? "Triune Alloy" : "Composite Alloy";
    }

    private int distinctMetals() {
        return (iron > 0 ? 1 : 0) + (copper > 0 ? 1 : 0) + (gold > 0 ? 1 : 0)
                + (netherite > 0 ? 1 : 0) + (celestialBronze > 0 ? 1 : 0)
                + (bonesteel > 0 ? 1 : 0) + (celestialSteel > 0 ? 1 : 0)
                + (celestialGold > 0 ? 1 : 0) + (regularGold > 0 ? 1 : 0);
    }

    private int metalUnits(int metal) {
        return switch (metal) {
            case 0 -> iron; case 1 -> copper; case 2 -> gold;
            case 3 -> netherite; case 4 -> celestialBronze; case 5 -> bonesteel;
            case 6 -> celestialSteel; case 7 -> celestialGold; case 8 -> regularGold; default -> 0;
        };
    }

    private static String metalName(int metal) {
        return switch (metal) {
            case 0 -> "Iron"; case 1 -> "Copper"; case 2 -> "Tarnished Gold";
            case 3 -> "Netherite"; case 4 -> "Celestial Bronze"; case 5 -> "Bone Steel";
            case 6 -> "Celestial Steel"; case 7 -> "Celestial Gold"; case 8 -> "Gold"; default -> "Unknown";
        };
    }

    public static String metalId(int metal) {
        return switch (metal) {
            case 0 -> "iron"; case 1 -> "copper"; case 2 -> "tarnished_gold";
            case 3 -> "netherite"; case 4 -> "celestial_bronze"; case 5 -> "bone_steel";
            case 6 -> "celestial_steel"; case 7 -> "celestial_gold"; case 8 -> "gold"; default -> "none";
        };
    }

    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                            CrucibleBlockEntity crucible) {
        if (level.isClientSide()) return;
        boolean changed = false;
        boolean wasPouring = crucible.pouringTicks > 0;
        if (crucible.pouringTicks > 0) {
            crucible.pouringTicks--;
            changed = true;
        }
        if (crucible.fuelTicks > 0) {
            crucible.fuelTicks--;
            if (crucible.temperature < crucible.targetTemperature && level.getGameTime() % 4L == 0L) {
                crucible.temperature++;
                changed = true;
            } else if (crucible.temperature > crucible.targetTemperature && level.getGameTime() % 3L == 0L) {
                crucible.temperature--;
                changed = true;
            }
        } else if (crucible.temperature > 0 && level.getGameTime() % 8L == 0L) {
            crucible.temperature--;
            changed = true;
        }
        if (crucible.calibrated() && crucible.materialUnits() > 0 && crucible.pouringTicks == 0) {
            crucible.autoPourTicks++;
            changed = true;
            if (crucible.autoPourTicks >= 30 && level instanceof net.minecraft.server.level.ServerLevel server) {
                net.minecraft.world.entity.player.Player nearest = server.getNearestPlayer(
                        pos.getX() + .5D, pos.getY() + .5D, pos.getZ() + .5D, 8D, false);
                if (nearest instanceof ServerPlayer player) crucible.pour(player);
                crucible.autoPourTicks = 0;
            }
        } else if (crucible.autoPourTicks != 0) {
            crucible.autoPourTicks = 0;
            changed = true;
        }
        // Do not broadcast every idle crucible every tick (fuelTicks == 0 also satisfies
        // `fuelTicks % 20 == 0`). Persist active state cheaply and only send the values used
        // by the screen/renderer at a smooth 5 Hz, plus the final state transition.
        if (changed) crucible.setChanged();
        boolean periodicActiveSync = changed && level.getGameTime() % 4L == 0L;
        boolean fuelCheckpoint = crucible.fuelTicks > 0 && crucible.fuelTicks % 20 == 0;
        boolean finishedPouring = wasPouring && crucible.pouringTicks == 0;
        if (periodicActiveSync || fuelCheckpoint || finishedPouring) crucible.syncClient();
    }

    private void changedAndSync() {
        setChanged();
        syncClient();
    }

    private void syncClient() {
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private CrucibleScreenPayload snapshot() {
        return new CrucibleScreenPayload(worldPosition, temperature, targetTemperature, fuelTicks,
                selectedMoldIndex(), mixColor(), materialUnits(), metalSequence, autoPourTicks);
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("temperature", temperature);
        output.putInt("targetTemperature", targetTemperature);
        output.putInt("fuelTicks", fuelTicks);
        output.putInt("mold", mold);
        output.putBoolean("moldInserted", moldInserted);
        output.putInt("iron", iron);
        output.putInt("copper", copper);
        output.putInt("gold", gold);
        output.putInt("netherite", netherite);
        output.putInt("celestialBronze", celestialBronze);
        output.putInt("bonesteel", bonesteel);
        output.putInt("celestialSteel", celestialSteel);
        output.putInt("celestialGold", celestialGold);
        output.putInt("regularGold", regularGold);
        output.putInt("carbon", carbon);
        output.putInt("purityPoints", purityPoints);
        output.putInt("pouringTicks", pouringTicks);
        output.putInt("autoPourTicks", autoPourTicks);
        output.putInt("primaryMetal", primaryMetal);
        output.putInt("secondaryMetal", secondaryMetal);
        output.putString("metalSequence", metalSequence);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        temperature = Mth.clamp(input.getIntOr("temperature", 0), MIN_TEMPERATURE, MAX_TEMPERATURE);
        mold = Mth.clamp(input.getIntOr("mold", 0), 0, Mold.values().length - 1);
        moldInserted = input.getBooleanOr("moldInserted", false);
        iron = Mth.clamp(input.getIntOr("iron", 0), 0, 4);
        copper = Mth.clamp(input.getIntOr("copper", 0), 0, 4 - iron);
        gold = Mth.clamp(input.getIntOr("gold", 0), 0, 4 - iron - copper);
        netherite = Mth.clamp(input.getIntOr("netherite", 0), 0, 4 - iron - copper - gold);
        celestialBronze = Mth.clamp(input.getIntOr("celestialBronze", 0), 0,
                4 - iron - copper - gold - netherite);
        bonesteel = Mth.clamp(input.getIntOr("bonesteel", 0), 0,
                4 - iron - copper - gold - netherite - celestialBronze);
        celestialSteel = Mth.clamp(input.getIntOr("celestialSteel", 0), 0,
                4 - iron - copper - gold - netherite - celestialBronze - bonesteel);
        celestialGold = Mth.clamp(input.getIntOr("celestialGold", 0), 0,
                4 - iron - copper - gold - netherite - celestialBronze - bonesteel - celestialSteel);
        regularGold = Mth.clamp(input.getIntOr("regularGold", 0), 0,
                4 - iron - copper - gold - netherite - celestialBronze - bonesteel - celestialSteel - celestialGold);
        carbon = Mth.clamp(input.getIntOr("carbon", 0), 0, 4);
        purityPoints = Mth.clamp(input.getIntOr("purityPoints", materialUnits() * 75),
                0, materialUnits() * 100);
        pouringTicks = Mth.clamp(input.getIntOr("pouringTicks", 0), 0, 40);
        autoPourTicks = Mth.clamp(input.getIntOr("autoPourTicks", 0), 0, 30);
        primaryMetal = Mth.clamp(input.getIntOr("primaryMetal", -1), -1, 8);
        secondaryMetal = Mth.clamp(input.getIntOr("secondaryMetal", -1), -1, 8);
        metalSequence = input.getStringOr("metalSequence", "");
        if (metalSequence.length() != materialUnits()
                || metalSequence.chars().anyMatch(value -> value < '0' || value > '8'))
            metalSequence = legacySequence();
        if (primaryMetal < 0 && !metalSequence.isEmpty()) primaryMetal = metalSequence.charAt(0) - '0';
        targetTemperature = Mth.clamp(input.getIntOr("targetTemperature", mold().target()),
                MIN_TEMPERATURE, MAX_TEMPERATURE);
        fuelTicks = Math.max(0, input.getIntOr("fuelTicks", 0));
    }

    private String legacySequence() {
        StringBuilder sequence = new StringBuilder(4);
        sequence.append("0".repeat(iron));
        sequence.append("1".repeat(copper));
        sequence.append("2".repeat(gold));
        sequence.append("3".repeat(netherite));
        sequence.append("4".repeat(celestialBronze));
        sequence.append("5".repeat(bonesteel));
        sequence.append("6".repeat(celestialSteel));
        sequence.append("7".repeat(celestialGold));
        sequence.append("8".repeat(regularGold));
        return sequence.toString();
    }

    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>
    getUpdatePacket() { return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this); }

    @Override public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
}
