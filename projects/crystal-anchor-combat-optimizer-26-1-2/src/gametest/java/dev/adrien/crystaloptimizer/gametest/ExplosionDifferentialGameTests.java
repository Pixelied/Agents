package dev.adrien.crystaloptimizer.gametest;

import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.sim.damage.DamageRequest;
import dev.adrien.crystaloptimizer.sim.damage.DamageResult;
import dev.adrien.crystaloptimizer.sim.damage.ExplosionContext;
import dev.adrien.crystaloptimizer.sim.damage.ExplosionDamageCalculator26;
import dev.adrien.crystaloptimizer.sim.damage.VanillaDamageSimulator;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.v2.strategy.OpportunityIntent;
import dev.adrien.crystaloptimizer.v2.strategy.SelfDamageEstimate;
import dev.adrien.crystaloptimizer.v2.strategy.SelfSurvivalPolicy;
import java.lang.reflect.Method;
import java.util.Objects;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class ExplosionDifferentialGameTests implements CustomTestMethodInvoker {
    private static final float EPSILON = 1.0e-4f;
    private static final float TEST_MAX_HEALTH = 100.0f;

    @GameTest
    public void exposedCrystalDamageMatchesVanilla(GameTestHelper helper) {
        ServerPlayer target = freshTarget(helper);
        assertSingleExplosionMatches(
            helper,
            target,
            ExplosionContext.crystal(target.position().add(3.0, 0.0, 0.0))
        );
        helper.succeed();
    }

    @GameTest
    public void partialCoverCrystalDamageMatchesVanilla(GameTestHelper helper) {
        ServerPlayer target = freshTarget(helper);
        helper.setBlock(new BlockPos(4, 2, 2), Blocks.STONE);
        assertSingleExplosionMatches(
            helper,
            target,
            ExplosionContext.crystal(target.position().add(3.0, 0.0, 0.0))
        );
        helper.succeed();
    }

    @GameTest
    public void slabExposureMatchesVanilla(GameTestHelper helper) {
        ServerPlayer target = freshTarget(helper);
        helper.setBlock(new BlockPos(4, 2, 2), Blocks.STONE_SLAB.defaultBlockState());
        assertSingleExplosionMatches(
            helper,
            target,
            ExplosionContext.crystal(target.position().add(3.0, 0.0, 0.0))
        );
        helper.succeed();
    }

    @GameTest
    public void stairExposureMatchesVanilla(GameTestHelper helper) {
        ServerPlayer target = freshTarget(helper);
        helper.setBlock(new BlockPos(4, 2, 2), Blocks.OAK_STAIRS.defaultBlockState());
        assertSingleExplosionMatches(
            helper,
            target,
            ExplosionContext.crystal(target.position().add(3.0, 0.0, 0.0))
        );
        helper.succeed();
    }

    @GameTest
    public void hardDifficultyScalingMatchesVanilla(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Difficulty previous = level.getDifficulty();
        level.getServer().setDifficulty(Difficulty.HARD, true);
        try {
            ServerPlayer target = freshTarget(helper);
            assertSingleExplosionMatches(
                helper,
                target,
                ExplosionContext.crystal(target.position().add(6.0, 0.0, 0.0))
            );
        } finally {
            level.getServer().setDifficulty(previous, true);
        }
        helper.succeed();
    }

    @GameTest
    public void resistanceMatchesVanilla(GameTestHelper helper) {
        ServerPlayer target = freshTarget(helper);
        target.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 200, 1));
        assertSingleExplosionMatches(
            helper,
            target,
            ExplosionContext.crystal(target.position().add(3.0, 0.0, 0.0))
        );
        helper.succeed();
    }

    @GameTest
    public void blastProtectionMatchesVanilla(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer target = freshTarget(helper);
        ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        chest.enchant(
            level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.BLAST_PROTECTION),
            4
        );
        target.setItemSlot(EquipmentSlot.CHEST, chest);
        syncEquipment(target);
        helper.assertTrue(target.getArmorValue() > 0, "fixture armor attributes must be active");
        assertSingleExplosionMatches(
            helper,
            target,
            ExplosionContext.crystal(target.position().add(3.0, 0.0, 0.0))
        );
        helper.succeed();
    }

    @GameTest
    public void armorBreakOrderingMatchesVanilla(GameTestHelper helper) {
        ServerPlayer target = freshTarget(helper);
        ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        chest.setDamageValue(chest.getMaxDamage() - 1);
        target.setItemSlot(EquipmentSlot.CHEST, chest);
        syncEquipment(target);
        helper.assertTrue(target.getArmorValue() > 0, "fixture armor attributes must be active");

        ExplosionContext explosion = ExplosionContext.crystal(target.position().add(3.0, 0.0, 0.0));
        SimCombatant initial = GameTestCombatants.exactFirstHit(target);
        DamageResult predicted = predict(helper.getLevel(), target, initial, explosion);
        float before = target.getHealth();
        explode(helper.getLevel(), explosion);

        assertClose(
            helper,
            predicted.trace().healthDamage(),
            before - target.getHealth(),
            "armor-break health damage"
        );
        helper.assertTrue(
            predicted.trace().brokenSlots().contains(EquipmentSlot.CHEST),
            "simulator must predict the one-durability chestplate breaking"
        );
        helper.assertTrue(
            target.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),
            "vanilla must break the one-durability chestplate"
        );
        helper.succeed();
    }

    @GameTest
    public void anchorDamageMatchesVanilla(GameTestHelper helper) {
        ServerPlayer target = freshTarget(helper);
        assertSingleExplosionMatches(
            helper,
            target,
            ExplosionContext.anchor(target.blockPosition().offset(3, 0, 0), false)
        );
        helper.succeed();
    }

    @GameTest
    public void strongerProtectedFollowupMatchesVanilla(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer target = freshTarget(helper);
        ExplosionContext first = ExplosionContext.crystal(target.position().add(8.0, 0.0, 0.0));
        ExplosionContext second = ExplosionContext.crystal(target.position().add(4.0, 0.0, 0.0));

        SimCombatant initial = GameTestCombatants.exactFirstHit(target);
        DamageResult firstPredicted = predict(level, target, initial, first);
        float firstBefore = target.getHealth();
        explode(level, first);
        assertClose(
            helper,
            firstPredicted.trace().healthDamage(),
            firstBefore - target.getHealth(),
            "first protected-window hit"
        );

        DamageResult secondPredicted = predict(level, target, firstPredicted.target(), second);
        float secondBefore = target.getHealth();
        explode(level, second);
        assertClose(
            helper,
            secondPredicted.trace().healthDamage(),
            secondBefore - target.getHealth(),
            "stronger protected-window followup"
        );
        helper.succeed();
    }

    @GameTest
    public void totemThenFollowupMatchesVanilla(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer target = freshTarget(helper);
        target.setHealth(6.0f);
        target.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        ExplosionContext pop = ExplosionContext.crystal(target.position().add(5.0, 0.0, 0.0));
        ExplosionContext finisher = ExplosionContext.crystal(target.position().add(3.0, 0.0, 0.0));

        DamageResult popPredicted = predict(
            level,
            target,
            GameTestCombatants.exactFirstHit(target),
            pop
        );
        helper.assertTrue(popPredicted.trace().totemTriggered(), "simulator must predict the pop");
        explode(level, pop);
        assertClose(helper, 1.0f, target.getHealth(), "vanilla post-totem health");
        assertClose(helper, 8.0f, target.getAbsorptionAmount(), "vanilla post-totem absorption");
        helper.assertTrue(
            target.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty(),
            "vanilla must consume the visible offhand totem"
        );

        DamageResult finisherPredicted = predict(level, target, popPredicted.target(), finisher);
        explode(level, finisher);
        helper.assertTrue(
            target.isDeadOrDying() == finisherPredicted.target().dead(),
            "vanilla and simulator must agree on post-pop finisher lethality"
        );
        helper.succeed();
    }

    @GameTest
    public void localCrystalTotemRiskIsRejectedBySafetyPolicy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer self = freshTarget(helper);
        self.setHealth(6.0f);
        self.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        ExplosionContext explosion = ExplosionContext.crystal(self.position().add(5.0, 0.0, 0.0));

        DamageResult predicted = predict(level, self, GameTestCombatants.exactFirstHit(self), explosion);
        helper.assertTrue(predicted.trace().totemTriggered(), "simulator must identify local totem risk");
        SelfSurvivalPolicy.Decision decision = SelfSurvivalPolicy.evaluate(
            new SelfDamageEstimate(predicted.trace().postMagic(), 1.0f, true),
            OpportunityIntent.LETHAL,
            40.0f,
            OptimizerConfig.defaults()
        );
        helper.assertTrue(
            !decision.allowed() && decision.reason() == SelfSurvivalPolicy.Reason.SELF_TOTEM_POP,
            "safety policy must reject an action that would consume the local totem"
        );

        explode(level, explosion);
        helper.assertTrue(
            self.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty(),
            "vanilla oracle must confirm the risky explosion really consumes the totem"
        );
        helper.succeed();
    }

    @GameTest
    public void localAnchorLethalRiskIsRejectedBySafetyPolicy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer self = freshTarget(helper);
        self.setHealth(6.0f);
        ExplosionContext explosion = ExplosionContext.anchor(self.blockPosition().offset(2, 0, 0), false);

        DamageResult predicted = predict(level, self, GameTestCombatants.exactFirstHit(self), explosion);
        helper.assertTrue(predicted.target().dead(), "simulator must identify local lethal anchor damage");
        SelfSurvivalPolicy.Decision decision = SelfSurvivalPolicy.evaluate(
            new SelfDamageEstimate(6.0f, 0.0f, false),
            OpportunityIntent.LETHAL,
            40.0f,
            OptimizerConfig.defaults()
        );
        helper.assertTrue(
            !decision.allowed() && decision.reason() == SelfSurvivalPolicy.Reason.SELF_LETHAL,
            "safety policy must reject local-lethal anchor damage"
        );

        explode(level, explosion);
        helper.assertTrue(
            self.isDeadOrDying() == predicted.target().dead(),
            "vanilla and simulator must agree that the rejected anchor is locally lethal"
        );
        helper.succeed();
    }

    private static ServerPlayer freshTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer target = GameTestCombatants.makeSurvivalPlayer(level);
        Vec3 testPosition = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
        target.absSnapTo(testPosition.x, testPosition.y, testPosition.z, 0.0f, 0.0f);
        level.getChunkSource().move(target);
        Objects.requireNonNull(target.getAttribute(Attributes.MAX_HEALTH), "max health attribute")
            .setBaseValue(TEST_MAX_HEALTH);
        target.setHealth(TEST_MAX_HEALTH);
        helper.assertTrue(
            !target.getAbilities().invulnerable && !target.isCreative() && target.connection.hasClientLoaded(),
            "differential fixture must be a loaded, damageable survival player"
        );
        helper.assertTrue(
            level.getEntities(null, target.getBoundingBox().inflate(0.5)).contains(target),
            "differential fixture must be discoverable by ServerLevel entity queries"
        );
        return target;
    }

    private static void syncEquipment(ServerPlayer target) {
        target.doTick();
    }

    private static void assertSingleExplosionMatches(
        GameTestHelper helper,
        ServerPlayer target,
        ExplosionContext explosion
    ) {
        ServerLevel level = helper.getLevel();
        DamageResult predicted = predict(
            level,
            target,
            GameTestCombatants.exactFirstHit(target),
            explosion
        );
        float before = target.getHealth();
        explode(level, explosion);
        assertClose(
            helper,
            predicted.trace().healthDamage(),
            before - target.getHealth(),
            "single explosion health damage"
        );
    }

    private static DamageResult predict(
        ServerLevel level,
        ServerPlayer target,
        SimCombatant victim,
        ExplosionContext explosion
    ) {
        float raw = ExplosionDamageCalculator26.incoming(
            explosion,
            target.getBoundingBox(),
            target.position(),
            new ServerLevelBlockView(level)
        );
        return VanillaDamageSimulator.apply(
            victim,
            DamageRequest.explosion(raw)
                .withDifficulty(level.getDifficulty())
                .withSourcePosition(explosion.center())
        );
    }

    private static void explode(ServerLevel level, ExplosionContext explosion) {
        level.explode(
            null,
            explosion.center().x,
            explosion.center().y,
            explosion.center().z,
            explosion.radius(),
            Level.ExplosionInteraction.NONE
        );
    }

    private static void assertClose(
        GameTestHelper helper,
        float expected,
        float actual,
        String label
    ) {
        helper.assertTrue(
            Math.abs(expected - actual) <= EPSILON,
            label + " diverged: predicted=" + expected + ", observed=" + actual
        );
    }

    @Override
    public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
        method.invoke(this, context);
    }
}
