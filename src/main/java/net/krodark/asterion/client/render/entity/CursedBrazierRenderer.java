package net.krodark.asterion.client.render.entity;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.light.AsterionEmissiveBoneLayer;
import net.krodark.asterion.client.light.LedAmneticLight;
import net.krodark.asterion.entity.CursedBrazierEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public final class CursedBrazierRenderer extends GeoEntityRenderer<CursedBrazierEntity, EntityRenderState> {
    private static final Identifier MODEL = Asterion.id("entity/cursed_brazier");
    private static final Identifier TEXTURE = Asterion.id("textures/entity/cursed_brazier.png");
    private static final Identifier ANIMATION = Asterion.id("entity/cursed_brazier");
    private static final DataTicket<Boolean> SHIELDED =
            DataTickets.create("asterion_brazier_shielded", Boolean.class);
    private static final DataTicket<Boolean> FLAMES_ACTIVE =
            DataTickets.create("asterion_brazier_flames_active", Boolean.class);
    private static final DataTicket<Integer> PHASE =
            DataTickets.create("asterion_brazier_phase", Integer.class);
    private static final DataTicket<Integer> ATTACK =
            DataTickets.create("asterion_brazier_attack", Integer.class);
    private static final DataTicket<Float> PHASE_AGE =
            DataTickets.create("asterion_brazier_phase_age", Float.class);
    private static final DataTicket<Float> ATTACK_AGE =
            DataTickets.create("asterion_brazier_attack_age", Float.class);
    private static final DataTicket<Float> TIME =
            DataTickets.create("asterion_brazier_time", Float.class);
    private static final String[] GLOW_BONES = {
            "topglow", "middleglow", "bottomglow", "centerglow"
    };
    private static final int[] GLOW_ATTACKS = {
            CursedBrazierEntity.Attack.FIRE_BEAM.ordinal(),
            CursedBrazierEntity.Attack.CARDINAL_DASH.ordinal(),
            CursedBrazierEntity.Attack.FLOOR_JETS.ordinal(),
            CursedBrazierEntity.Attack.SPIN_TORNADO.ordinal()
    };

    public CursedBrazierRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<>() {
            @Override
            public Identifier getModelResource(GeoRenderState state) {
                return MODEL;
            }

            @Override
            public Identifier getTextureResource(GeoRenderState state) {
                return TEXTURE;
            }

            @Override
            public Identifier getAnimationResource(CursedBrazierEntity entity) {
                return ANIMATION;
            }
        });

        withRenderLayer(new AsterionEmissiveBoneLayer<>(this, "full", TEXTURE) {
            @Override
            public boolean shouldRenderBone(EntityRenderState state) {
                return state.getOrDefaultGeckolibData(SHIELDED, false);
            }

            @Override
            protected float surfaceBrightness(EntityRenderState state) {
                return 0.9F;
            }

            @Override
            protected int emissiveColor(EntityRenderState state) {
                return 0xFF55FF72;
            }
        });
        for (int index = 0; index < GLOW_BONES.length; index++) {
            addGlowLayer(GLOW_BONES[index], GLOW_ATTACKS[index]);
        }
        shadowRadius = 2.35F;
    }

    private void addGlowLayer(String boneName, int matchingAttack) {
        withRenderLayer(new AsterionEmissiveBoneLayer<>(this, boneName, TEXTURE) {
            @Override
            public boolean shouldRenderBone(EntityRenderState state) {
                return state.getOrDefaultGeckolibData(FLAMES_ACTIVE, false)
                        && state.getOrDefaultGeckolibData(ATTACK,
                        CursedBrazierEntity.Attack.NONE.ordinal()) == matchingAttack;
            }

            @Override
            protected float surfaceBrightness(EntityRenderState state) {
                int attack = state.getOrDefaultGeckolibData(ATTACK,
                        CursedBrazierEntity.Attack.NONE.ordinal());
                return attack == matchingAttack ? 1F : 0.72F;
            }

            @Override
            protected boolean enhancedSurface(EntityRenderState state) {
                return true;
            }

            @Override
            protected int emissiveColor(EntityRenderState state) {
                int attack = state.getOrDefaultGeckolibData(ATTACK,
                        CursedBrazierEntity.Attack.NONE.ordinal());
                if (attack != matchingAttack) return 0xFF78FF82;
                float time = state.getOrDefaultGeckolibData(TIME, 0F);
                int greenBlue = 54 + Math.round((Mth.sin(time * 0.55F) + 1F) * 18F);
                return 0xFFFF0000 | greenBlue << 8 | greenBlue;
            }
        });
    }

    @Override
    public void addRenderData(CursedBrazierEntity brazier, Void related,
                              EntityRenderState state, float partialTick) {
        state.addGeckolibData(SHIELDED, brazier.shielded());
        state.addGeckolibData(FLAMES_ACTIVE, brazier.flamesActive());
        state.addGeckolibData(PHASE, brazier.phase().ordinal());
        state.addGeckolibData(ATTACK, brazier.attack().ordinal());
        state.addGeckolibData(PHASE_AGE, brazier.phaseAge(partialTick));
        state.addGeckolibData(ATTACK_AGE, brazier.attackAge(partialTick));
        state.addGeckolibData(TIME, brazier.tickCount + partialTick);
        if (!brazier.flamesActive()) {
            LedAmneticLight.removeItemGlowLight(brazier);
            return;
        }

        float strength = brazier.shielded() ? 3.2F : 1.8F;
        float radius = brazier.shielded() ? 11F : 8F;
        LedAmneticLight.updateItemGlowLight(brazier,
                brazier.position().add(0, brazier.getBbHeight() * 0.62, 0),
                0.18F, 1F, 0.30F, strength, radius, false);
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<EntityRenderState> pass,
                                          BoneSnapshots bones) {
        super.adjustModelBonesForRender(pass, bones);
        int phase = pass.getOrDefaultGeckolibData(
                PHASE, CursedBrazierEntity.Phase.DORMANT.ordinal());
        float phaseAge = pass.getOrDefaultGeckolibData(PHASE_AGE, 0F);
        float time = pass.getOrDefaultGeckolibData(TIME, 0F);

        int selectedGlowAttack = pass.getOrDefaultGeckolibData(
                ATTACK, CursedBrazierEntity.Attack.NONE.ordinal());
        boolean flamesActive = pass.getOrDefaultGeckolibData(FLAMES_ACTIVE, false);
        // Glow geometry is never part of the idle/dormant/cutscene model. Reveal exactly
        // one assigned bone during its attack; the matching emissive layer draws it again.
        for (int index = 0; index < GLOW_BONES.length; index++) {
            boolean visible = flamesActive && selectedGlowAttack == GLOW_ATTACKS[index];
            bones.ifPresent(GLOW_BONES[index], bone -> bone.skipRender(!visible)
                    .skipChildrenRender(!visible).setScale(1F, 1F, 1F));
        }

        bones.ifPresent("full", bone -> {
            if (phase == CursedBrazierEntity.Phase.DORMANT.ordinal()) {
                bone.setTranslation(bone.getTranslateX(), bone.getTranslateY() - 3.5F,
                        bone.getTranslateZ());
                return;
            }

            if (phase == CursedBrazierEntity.Phase.AWAKENING.ordinal()) {
                float rise = smooth((phaseAge - 10F) / 40F);
                float settle = 1F - smooth((phaseAge - 56F) / 22F);
                float wobble = Mth.sin(phaseAge * 0.34F) * 0.055F * settle;
                bone.setTranslation(bone.getTranslateX(),
                        bone.getTranslateY() - 3.5F + rise * 5.2F,
                        bone.getTranslateZ());
                bone.setRotation(bone.getRotX() + wobble * 0.55F,
                        bone.getRotY() + rise * 0.42F,
                        bone.getRotZ() + wobble);
                return;
            }

            float bob = Mth.sin(time * 0.105F) * 0.22F;
            float sway = Mth.sin(time * 0.052F + 0.8F) * 0.012F;
            bone.setTranslation(bone.getTranslateX(), bone.getTranslateY() + 1.7F + bob,
                    bone.getTranslateZ());
            bone.setRotation(bone.getRotX() + sway * 0.55F,
                    bone.getRotY(),
                    bone.getRotZ() + sway);
        });

        int attack = pass.getOrDefaultGeckolibData(ATTACK, CursedBrazierEntity.Attack.NONE.ordinal());
        float attackAge = pass.getOrDefaultGeckolibData(ATTACK_AGE, 0F);
        bones.ifPresent("full", bone -> applyAttackMotion(bone, attack, attackAge));
    }

    private static void applyAttackMotion(com.geckolib.animation.state.BoneSnapshot bone,
                                          int attack, float age) {
        if (attack == CursedBrazierEntity.Attack.SPIN_TORNADO.ordinal()) {
            float acceleration = Math.clamp((age - 70F) / 90F, 0F, 1F);
            bone.setRotation(bone.getRotX(),
                    bone.getRotY(),
                    bone.getRotZ() + Mth.sin(age * 0.22F) * 0.035F * acceleration);
        } else if (attack == CursedBrazierEntity.Attack.CARDINAL_DASH.ordinal()) {
            float leg = age % CursedBrazierEntity.DASH_LEG_TICKS;
            float stride = Math.clamp(leg / CursedBrazierEntity.DASH_MOVE_TICKS, 0F, 1F);
            float lean = leg < CursedBrazierEntity.DASH_MOVE_TICKS
                    ? Mth.sin(stride * Mth.PI) * 0.12F : 0F;
            bone.setRotation(bone.getRotX() + lean, bone.getRotY(), bone.getRotZ());
        } else if (attack == CursedBrazierEntity.Attack.FIRE_BEAM.ordinal()) {
            float brace = smooth(age / 28F) * (1F - smooth((age - 82F) / 10F));
            bone.setRotation(bone.getRotX() - brace * 0.055F,
                    bone.getRotY(), bone.getRotZ());
        }
    }

    private static float smooth(float value) {
        value = Math.clamp(value, 0F, 1F);
        return value * value * (3F - 2F * value);
    }
}
