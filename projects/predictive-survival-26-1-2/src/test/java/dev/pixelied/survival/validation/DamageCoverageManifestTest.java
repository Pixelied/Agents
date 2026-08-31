package dev.pixelied.survival.validation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageCoverageManifestTest {
    private static final Path MANIFEST = Path.of(
        "src/test/resources/predictive_survival_damage_coverage_26_1_2.json"
    );
    private static final Pattern ENTRY = Pattern.compile("\\{([^{}]*)}", Pattern.DOTALL);
    private static final Set<String> EXPECTED_IDS = Set.of(
        "minecraft:arrow",
        "minecraft:bad_respawn_point",
        "minecraft:cactus",
        "minecraft:campfire",
        "minecraft:cramming",
        "minecraft:dragon_breath",
        "minecraft:drown",
        "minecraft:dry_out",
        "minecraft:ender_pearl",
        "minecraft:explosion",
        "minecraft:fall",
        "minecraft:falling_anvil",
        "minecraft:falling_block",
        "minecraft:falling_stalactite",
        "minecraft:fireball",
        "minecraft:fireworks",
        "minecraft:fly_into_wall",
        "minecraft:freeze",
        "minecraft:generic",
        "minecraft:generic_kill",
        "minecraft:hot_floor",
        "minecraft:in_fire",
        "minecraft:in_wall",
        "minecraft:indirect_magic",
        "minecraft:lava",
        "minecraft:lightning_bolt",
        "minecraft:mace_smash",
        "minecraft:magic",
        "minecraft:mob_attack",
        "minecraft:mob_attack_no_aggro",
        "minecraft:mob_projectile",
        "minecraft:on_fire",
        "minecraft:out_of_world",
        "minecraft:outside_border",
        "minecraft:player_attack",
        "minecraft:player_explosion",
        "minecraft:sonic_boom",
        "minecraft:spear",
        "minecraft:spit",
        "minecraft:stalagmite",
        "minecraft:starve",
        "minecraft:sting",
        "minecraft:sweet_berry_bush",
        "minecraft:thorns",
        "minecraft:thrown",
        "minecraft:trident",
        "minecraft:unattributed_fireball",
        "minecraft:wind_charge",
        "minecraft:wither",
        "minecraft:wither_skull"
    );
    private static final Set<String> PLAYER_RELEVANCE = Set.of("YES", "NO", "CONDITIONAL");
    private static final Set<String> STATUSES = Set.of(
        "RUNTIME_CONFIRMED",
        "COVERED_NEEDS_EXPANSION",
        "GAP",
        "EXPLICIT_LIMITATION",
        "UNSAVABLE",
        "NOT_PLAYER_RELEVANT"
    );
    private static final String[] REQUIRED_FIELDS = {
        "registryId",
        "playerRelevant",
        "vanillaFamily",
        "directPredictor",
        "opportunityPrecursor",
        "sourceTagSemantics",
        "oracleParity",
        "snapshotProof",
        "naturalE2E",
        "authorityRace",
        "currentStatus",
        "knownHiddenState",
        "disposition"
    };

    @Test
    void manifestClassifiesEveryVanilla2612DamageTypeExactlyOnce() throws Exception {
        assertTrue(Files.exists(MANIFEST), "damage coverage manifest must be checked in");
        String json = Files.readString(MANIFEST);
        Matcher matcher = ENTRY.matcher(json);
        Set<String> ids = new LinkedHashSet<>();
        int entries = 0;
        while (matcher.find()) {
            String entry = matcher.group(1);
            entries++;
            for (String field : REQUIRED_FIELDS) {
                assertTrue(!field(entry, field).isBlank(), "manifest entry is missing nonblank field " + field);
            }
            String registryId = field(entry, "registryId");
            assertTrue(ids.add(registryId), "duplicate damage-type classification: " + registryId);
            assertTrue(PLAYER_RELEVANCE.contains(field(entry, "playerRelevant")),
                "invalid playerRelevant for " + registryId);
            assertTrue(STATUSES.contains(field(entry, "currentStatus")),
                "invalid currentStatus for " + registryId);
        }

        Set<String> missing = new LinkedHashSet<>(EXPECTED_IDS);
        missing.removeAll(ids);
        Set<String> unexpected = new LinkedHashSet<>(ids);
        unexpected.removeAll(EXPECTED_IDS);
        assertTrue(missing.isEmpty(), "missing damage-type classifications: " + missing);
        assertTrue(unexpected.isEmpty(), "unexpected damage-type classifications: " + unexpected);
        assertEquals(50, entries, "26.1.2 must have exactly 50 classified damage types");
        assertEquals(EXPECTED_IDS, ids, "manifest differs from the audited 26.1.2 damage-type registry");
    }

    private static String field(String entry, String key) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        Matcher matcher = pattern.matcher(entry);
        return matcher.find() ? matcher.group(1).trim() : "";
    }
}
