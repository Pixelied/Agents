package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.TickWindow;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shared result model and assertions for the exact-runtime damage gauntlet. */
final class DamageGauntletScenarios {
    private DamageGauntletScenarios() {
    }

    static List<GauntletResult> existingCoveredSubset(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        List<GauntletResult> results = new ArrayList<>();
        for (ValidationResult result : DamageValidationScenarios.firstRuntimeSlice(context, singleplayer)) {
            results.add(fromHealthParity(result, sourceForDamageScenario(result.id())));
        }
        for (ValidationResult result : ExplosionValidationScenarios.runtimeSlice(singleplayer)) {
            results.add(fromHealthParity(result, "minecraft:explosion"));
        }
        return List.copyOf(results);
    }

    static void assertResult(GauntletResult result) {
        Objects.requireNonNull(result, "result");
        assertSource(result.expectedSourceKey(), result.actualSourceKey());
        assertAmountOrStateInterval(result.expectedAmountOrState(), result.actualAmountOrState());
        assertTimingWindow(result.expectedTimingWindow(), result.actualTick());
        assertAuthority(result.expectedAuthority(), result.actualAuthority());
    }

    static void assertSource(String expected, String actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("damage source mismatch expected=" + expected + " actual=" + actual);
        }
    }

    static void assertAmountOrStateInterval(ValueInterval expected, float actual) {
        Objects.requireNonNull(expected, "expected");
        if (!Float.isFinite(actual) || !expected.contains(actual)) {
            throw new AssertionError(
                "damage amount/state outside expected interval " + expected + " actual=" + actual
            );
        }
    }

    static void assertTimingWindow(TickWindow expected, Long actualTick) {
        if (expected == null && actualTick == null) return;
        if (expected == null || actualTick == null || !expected.contains(actualTick)) {
            throw new AssertionError("damage timing mismatch expected=" + expected + " actual=" + actualTick);
        }
    }

    static void assertAuthority(AuthorityOutcome expected, AuthorityOutcome actual) {
        if (expected != actual) {
            throw new AssertionError("authority outcome mismatch expected=" + expected + " actual=" + actual);
        }
    }

    private static GauntletResult fromHealthParity(ValidationResult result, String sourceKey) {
        return new GauntletResult(
            result.id(),
            sourceKey,
            sourceKey,
            ValueInterval.around(result.predictedHealth(), result.tolerance()),
            result.actualHealth(),
            null,
            null,
            AuthorityOutcome.NOT_APPLICABLE,
            AuthorityOutcome.NOT_APPLICABLE,
            result.status()
        );
    }

    private static String sourceForDamageScenario(String id) {
        if (id.equals("player_attack_raw_6") || id.startsWith("shield_use_ticks_")) {
            return "minecraft:player_attack";
        }
        if (id.equals("armor_resistance_protection_raw_10") || id.startsWith("hurt_cooldown_")) {
            return "minecraft:generic";
        }
        throw new IllegalArgumentException("unclassified existing damage gauntlet scenario: " + id);
    }

    enum AuthorityOutcome {
        NOT_APPLICABLE,
        SERVER_CONFIRMED,
        GUARANTEED_BEFORE_DAMAGE,
        UNSAVABLE,
        EXPLICIT_LIMITATION
    }

    record ValueInterval(float min, float max) {
        ValueInterval {
            if (!Float.isFinite(min) || !Float.isFinite(max) || min > max) {
                throw new IllegalArgumentException("value interval must be finite and ordered");
            }
        }

        static ValueInterval around(float value, float tolerance) {
            if (!Float.isFinite(value) || !Float.isFinite(tolerance) || tolerance < 0f) {
                throw new IllegalArgumentException("value/tolerance must be finite and tolerance non-negative");
            }
            return new ValueInterval(value - tolerance, value + tolerance);
        }

        boolean contains(float value) {
            return value >= min && value <= max;
        }
    }

    record GauntletResult(
        String scenarioId,
        String expectedSourceKey,
        String actualSourceKey,
        ValueInterval expectedAmountOrState,
        float actualAmountOrState,
        TickWindow expectedTimingWindow,
        Long actualTick,
        AuthorityOutcome expectedAuthority,
        AuthorityOutcome actualAuthority,
        ValidationStatus status
    ) {
        GauntletResult {
            scenarioId = requireText(scenarioId, "scenarioId");
            expectedSourceKey = requireText(expectedSourceKey, "expectedSourceKey");
            actualSourceKey = requireText(actualSourceKey, "actualSourceKey");
            expectedAmountOrState = Objects.requireNonNull(expectedAmountOrState, "expectedAmountOrState");
            if (!Float.isFinite(actualAmountOrState)) {
                throw new IllegalArgumentException("actualAmountOrState must be finite");
            }
            expectedAuthority = Objects.requireNonNull(expectedAuthority, "expectedAuthority");
            actualAuthority = Objects.requireNonNull(actualAuthority, "actualAuthority");
            status = Objects.requireNonNull(status, "status");
        }

        boolean passes() {
            boolean timingPass = expectedTimingWindow == null
                ? actualTick == null
                : actualTick != null && expectedTimingWindow.contains(actualTick);
            return expectedSourceKey.equals(actualSourceKey)
                && expectedAmountOrState.contains(actualAmountOrState)
                && timingPass
                && expectedAuthority == actualAuthority;
        }

        String toJsonLine() {
            String timingEarliest = expectedTimingWindow == null
                ? "null"
                : Long.toString(expectedTimingWindow.earliest());
            String timingLatest = expectedTimingWindow == null
                ? "null"
                : Long.toString(expectedTimingWindow.latest());
            String timingActual = actualTick == null ? "null" : Long.toString(actualTick);
            return "{"
                + "\"scenarioId\":" + quote(scenarioId)
                + ",\"status\":" + quote(status.name())
                + ",\"sourceExpected\":" + quote(expectedSourceKey)
                + ",\"sourceActual\":" + quote(actualSourceKey)
                + ",\"stateMin\":" + Float.toString(expectedAmountOrState.min())
                + ",\"stateMax\":" + Float.toString(expectedAmountOrState.max())
                + ",\"stateActual\":" + Float.toString(actualAmountOrState)
                + ",\"timingEarliest\":" + timingEarliest
                + ",\"timingLatest\":" + timingLatest
                + ",\"timingActual\":" + timingActual
                + ",\"authorityExpected\":" + quote(expectedAuthority.name())
                + ",\"authorityActual\":" + quote(actualAuthority.name())
                + ",\"passed\":" + passes()
                + "}";
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must be nonblank");
        return value;
    }

    private static String quote(String value) {
        String escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
        return "\"" + escaped + "\"";
    }
}
