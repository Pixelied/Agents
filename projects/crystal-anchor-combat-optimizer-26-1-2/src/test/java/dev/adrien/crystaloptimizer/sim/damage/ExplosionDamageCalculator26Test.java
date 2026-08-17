package dev.adrien.crystaloptimizer.sim.damage;

import dev.adrien.crystaloptimizer.world.BlockView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplosionDamageCalculator26Test {
    private static final Vec3 CENTER = new Vec3(0.5, 64.0, 0.5);
    private static final Vec3 TARGET_POSITION = new Vec3(2.5, 64.0, 0.5);
    private static final AABB TARGET_BOX = new AABB(2.0, 64.0, 0.0, 3.0, 66.0, 1.0);

    @Test
    void unobstructedCrystalUsesTheExactVanillaEntityDamageFormula() {
        var context = ExplosionContext.crystal(CENTER);

        float actual = ExplosionDamageCalculator26.incoming(
            context,
            TARGET_BOX,
            TARGET_POSITION,
            BlockView.allAir()
        );

        double doubleRadius = 12.0;
        double distance = TARGET_POSITION.distanceTo(CENTER) / doubleRadius;
        double power = 1.0 - distance;
        float expected = (float) (((power * power + power) / 2.0) * 7.0 * doubleRadius + 1.0);
        assertEquals(expected, actual, 0.0001f);
    }

    @Test
    void solidObsidianOcclusionReducesExposureAndIncomingDamage() {
        float open = ExplosionDamageCalculator26.incoming(
            ExplosionContext.crystal(CENTER), TARGET_BOX, TARGET_POSITION, BlockView.allAir()
        );
        float blocked = ExplosionDamageCalculator26.incoming(
            ExplosionContext.crystal(CENTER),
            TARGET_BOX,
            TARGET_POSITION,
            BlockView.singleBlock(new BlockPos(1, 64, 0), Blocks.OBSIDIAN.defaultBlockState())
        );

        assertTrue(blocked < open);
        assertTrue(ExplosionExposure.seenPercent(CENTER, TARGET_BOX, BlockView.singleBlock(
            new BlockPos(1, 64, 0), Blocks.OBSIDIAN.defaultBlockState()
        )) < 1.0f);
    }

    @Test
    void crystalAndAnchorUseExactVanillaRadii() {
        assertEquals(6.0f, ExplosionContext.crystal(CENTER).radius(), 0.0001f);
        assertEquals(5.0f, ExplosionContext.anchor(new BlockPos(0, 64, 0), false).radius(), 0.0001f);

        float crystal = ExplosionDamageCalculator26.incoming(
            ExplosionContext.crystal(CENTER), TARGET_BOX, TARGET_POSITION, BlockView.allAir()
        );
        float anchor = ExplosionDamageCalculator26.incoming(
            ExplosionContext.anchor(new BlockPos(0, 64, 0), false), TARGET_BOX, TARGET_POSITION, BlockView.allAir()
        );
        assertTrue(crystal > anchor);
    }

    @Test
    void anchorWaterOnlyOverridesTheFormerAnchorCenterBlockResistance() {
        var sourcePos = new BlockPos(0, 64, 0);
        var dry = ExplosionContext.anchor(sourcePos, false);
        var wet = ExplosionContext.anchor(sourcePos, true);

        assertFalse(dry.blockResistanceOverride(sourcePos).isPresent());
        assertEquals(
            Blocks.WATER.getExplosionResistance(),
            wet.blockResistanceOverride(sourcePos).orElseThrow(),
            0.0001f
        );
        assertFalse(wet.blockResistanceOverride(sourcePos.above()).isPresent());

        float dryIncoming = ExplosionDamageCalculator26.incoming(dry, TARGET_BOX, TARGET_POSITION, BlockView.allAir());
        float wetIncoming = ExplosionDamageCalculator26.incoming(wet, TARGET_BOX, TARGET_POSITION, BlockView.allAir());
        assertEquals(dryIncoming, wetIncoming, 0.0001f);
    }
}
