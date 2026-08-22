package dev.adrien.crystaloptimizer.v2.debug;

import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.config.OptimizerStrategy;
import dev.adrien.crystaloptimizer.execution.RotationMode;
import dev.adrien.crystaloptimizer.prediction.MovementSample;
import dev.adrien.crystaloptimizer.sim.model.AnchorState;
import dev.adrien.crystaloptimizer.sim.model.ArmorPieceState;
import dev.adrien.crystaloptimizer.sim.model.BlockingState;
import dev.adrien.crystaloptimizer.sim.model.CombatantSpatialState;
import dev.adrien.crystaloptimizer.sim.model.EffectState;
import dev.adrien.crystaloptimizer.sim.model.EquipmentState;
import dev.adrien.crystaloptimizer.sim.model.HurtWindowState;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.sim.model.KnownCrystal;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import dev.adrien.crystaloptimizer.sim.model.TotemState;
import dev.adrien.crystaloptimizer.v2.state.StrategicSnapshot;
import dev.adrien.crystaloptimizer.v2.strategy.TargetProtectionPolicyConfig;
import dev.adrien.crystaloptimizer.v2.timing.TimingDistribution;
import dev.adrien.crystaloptimizer.v2.timing.TimingSnapshot;
import dev.adrien.crystaloptimizer.v2.timing.TimingTransition;
import dev.adrien.crystaloptimizer.world.CombatRegion;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import dev.adrien.crystaloptimizer.world.LegalitySnapshot;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Explicit conversion between immutable combat snapshots and stable JSON-friendly DTOs. */
final class ReplaySnapshotSerde {
    static final int SCHEMA_VERSION = 1;

    private ReplaySnapshotSerde() {
    }

    static RootDto encode(ReplayFixture fixture) {
        return new RootDto(
            SCHEMA_VERSION,
            snapshot(fixture.snapshot()),
            config(fixture.config()),
            fixture.events().stream().map(ReplaySnapshotSerde::event).toList()
        );
    }

    static ReplayFixture decode(RootDto root) {
        if (root == null || root.schemaVersion() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported replay schema version");
        }
        return new ReplayFixture(
            snapshot(root.snapshot()),
            config(root.config()),
            root.events().stream().map(ReplaySnapshotSerde::event).toList()
        );
    }

    private static SnapshotDto snapshot(StrategicSnapshot snapshot) {
        return new SnapshotDto(
            snapshot.snapshotId(),
            snapshot.worldRevision(),
            snapshot.inventoryRevision(),
            snapshot.configRevision(),
            snapshot.capturedAtNanos(),
            snapshot.selfId().toString(),
            snapshot.targetRevisions().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new RevisionDto(entry.getKey().toString(), entry.getValue()))
                .toList(),
            combat(snapshot.combat()),
            snapshot.movementHistory().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new MovementHistoryDto(
                    entry.getKey().toString(),
                    entry.getValue().stream().map(ReplaySnapshotSerde::movement).toList()
                ))
                .toList(),
            snapshot.protectedPlayerIds().stream().sorted().map(UUID::toString).toList(),
            targetProtection(snapshot.targetProtection()),
            timing(snapshot.timing())
        );
    }

    private static StrategicSnapshot snapshot(SnapshotDto dto) {
        LinkedHashMap<UUID, Long> revisions = new LinkedHashMap<>();
        dto.targetRevisions().forEach(entry -> revisions.put(UUID.fromString(entry.id()), entry.revision()));
        LinkedHashMap<UUID, List<MovementSample>> history = new LinkedHashMap<>();
        dto.movementHistory().forEach(entry -> history.put(
            UUID.fromString(entry.id()),
            entry.samples().stream().map(ReplaySnapshotSerde::movement).toList()
        ));
        return new StrategicSnapshot(
            dto.snapshotId(),
            dto.worldRevision(),
            dto.inventoryRevision(),
            dto.configRevision(),
            dto.capturedAtNanos(),
            UUID.fromString(dto.selfId()),
            revisions,
            combat(dto.combat()),
            history,
            dto.protectedPlayerIds().stream().map(UUID::fromString).collect(java.util.stream.Collectors.toSet()),
            targetProtection(dto.targetProtection()),
            timing(dto.timing())
        );
    }

    private static CombatDto combat(CombatSnapshot combat) {
        List<BlockDto> blocks = combat.region().states().entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator
                .comparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getZ)))
            .map(entry -> block(entry.getKey(), entry.getValue()))
            .toList();
        List<CombatantDto> combatants = combat.combatants().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> combatant(entry.getKey(), entry.getValue()))
            .toList();
        List<CrystalDto> crystals = combat.crystals().stream()
            .sorted(Comparator.comparingInt(KnownCrystal::entityId))
            .map(crystal -> new CrystalDto(crystal.entityId(), vec(crystal.position())))
            .toList();
        List<AnchorDto> anchors = combat.anchors().entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator
                .comparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getZ)))
            .map(entry -> new AnchorDto(pos(entry.getKey()), entry.getValue().charges()))
            .toList();
        List<SpatialDto> spatial = combat.spatial().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> spatial(entry.getKey(), entry.getValue()))
            .toList();
        return new CombatDto(
            combat.worldRevision(),
            combat.selfId().toString(),
            blocks,
            combatants,
            crystals,
            anchors,
            inventory(combat.inventory()),
            new TimingStateDto(
                combat.timing().estimatedServerTick(),
                combat.timing().confidence(),
                combat.timing().roundTripMillis(),
                combat.timing().jitterMillis()
            ),
            legality(combat.legality()),
            spatial,
            combat.difficulty().name()
        );
    }

    private static CombatSnapshot combat(CombatDto dto) {
        LinkedHashMap<BlockPos, BlockState> states = new LinkedHashMap<>();
        dto.blocks().forEach(block -> states.put(pos(block.pos()), blockState(block)));
        CombatRegion region = CombatRegion.of(states, Map.of());
        LinkedHashMap<UUID, SimCombatant> combatants = new LinkedHashMap<>();
        dto.combatants().forEach(entry -> combatants.put(UUID.fromString(entry.id()), combatant(entry)));
        List<KnownCrystal> crystals = dto.crystals().stream()
            .map(entry -> new KnownCrystal(entry.entityId(), vec(entry.position())))
            .toList();
        LinkedHashMap<BlockPos, AnchorState> anchors = new LinkedHashMap<>();
        dto.anchors().forEach(entry -> anchors.put(pos(entry.pos()), new AnchorState(entry.charges())));
        LinkedHashMap<UUID, CombatantSpatialState> spatial = new LinkedHashMap<>();
        dto.spatial().forEach(entry -> spatial.put(UUID.fromString(entry.id()), spatial(entry)));
        TimingStateDto timing = dto.timing();
        return new CombatSnapshot(
            dto.worldRevision(),
            UUID.fromString(dto.selfId()),
            region,
            combatants,
            crystals,
            anchors,
            inventory(dto.inventory()),
            new TimingState(
                timing.estimatedServerTick(),
                timing.confidence(),
                timing.roundTripMillis(),
                timing.jitterMillis()
            ),
            legality(dto.legality()),
            spatial,
            Difficulty.valueOf(dto.difficulty())
        );
    }

    private static CombatantDto combatant(UUID id, SimCombatant combatant) {
        return new CombatantDto(
            id.toString(),
            combatant.health(),
            combatant.absorption(),
            equipment(combatant.equipment()),
            effects(combatant.effects()),
            blocking(combatant.blocking()),
            new HurtDto(
                combatant.hurtWindow().invulnerableTime(),
                combatant.hurtWindow().lastHurt(),
                combatant.hurtWindow().lastHurtKnown()
            ),
            combatant.totem().name(),
            combatant.dead()
        );
    }

    private static SimCombatant combatant(CombatantDto dto) {
        HurtDto hurt = dto.hurtWindow();
        return new SimCombatant(
            dto.health(),
            dto.absorption(),
            equipment(dto.equipment()),
            effects(dto.effects()),
            blocking(dto.blocking()),
            new HurtWindowState(hurt.invulnerableTime(), hurt.lastHurt(), hurt.lastHurtKnown()),
            TotemState.valueOf(dto.totem()),
            dto.dead()
        );
    }

    private static EquipmentDto equipment(EquipmentState equipment) {
        return new EquipmentDto(
            armor(equipment.head()),
            armor(equipment.chest()),
            armor(equipment.legs()),
            armor(equipment.feet())
        );
    }

    private static EquipmentState equipment(EquipmentDto dto) {
        return new EquipmentState(
            armor(dto.head()),
            armor(dto.chest()),
            armor(dto.legs()),
            armor(dto.feet())
        );
    }

    private static ArmorDto armor(Optional<ArmorPieceState> piece) {
        return piece.map(value -> new ArmorDto(
            value.armorPoints(),
            value.toughness(),
            value.durabilityRemaining(),
            value.enchantmentProtection()
        )).orElse(null);
    }

    private static Optional<ArmorPieceState> armor(ArmorDto dto) {
        return dto == null
            ? Optional.empty()
            : Optional.of(new ArmorPieceState(
                dto.armorPoints(),
                dto.toughness(),
                dto.durabilityRemaining(),
                dto.enchantmentProtection()
            ));
    }

    private static EffectsDto effects(EffectState effects) {
        return new EffectsDto(
            effect(effects.resistance()),
            effect(effects.regeneration()),
            effect(effects.absorption()),
            effect(effects.fireResistance())
        );
    }

    private static EffectState effects(EffectsDto dto) {
        return new EffectState(
            effect(dto.resistance()),
            effect(dto.regeneration()),
            effect(dto.absorption()),
            effect(dto.fireResistance())
        );
    }

    private static EffectDto effect(Optional<EffectState.EffectInstance> effect) {
        return effect.map(value -> new EffectDto(value.amplifier(), value.durationTicks())).orElse(null);
    }

    private static Optional<EffectState.EffectInstance> effect(EffectDto dto) {
        return dto == null
            ? Optional.empty()
            : Optional.of(new EffectState.EffectInstance(dto.amplifier(), dto.durationTicks()));
    }

    private static BlockingDto blocking(BlockingState blocking) {
        return new BlockingDto(
            blocking.active(),
            vec(blocking.position()),
            blocking.headYawDegrees(),
            blocking.horizontalBlockingAngle(),
            blocking.baseReduction(),
            blocking.factorReduction()
        );
    }

    private static BlockingState blocking(BlockingDto dto) {
        return new BlockingState(
            dto.active(),
            vec(dto.position()),
            dto.headYawDegrees(),
            dto.horizontalBlockingAngle(),
            dto.baseReduction(),
            dto.factorReduction()
        );
    }

    private static InventoryDto inventory(InventoryState inventory) {
        List<ItemCountDto> counts = inventory.knownCounts().entrySet().stream()
            .map(entry -> new ItemCountDto(itemSymbol(entry.getKey()), entry.getValue()))
            .sorted(Comparator.comparing(ItemCountDto::item))
            .toList();
        List<HotbarDto> hotbar = inventory.hotbarItems().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new HotbarDto(
                entry.getKey(),
                itemSymbol(entry.getValue()),
                inventory.hotbarCount(entry.getKey())
            ))
            .toList();
        return new InventoryDto(
            inventory.selectedHotbarSlot(),
            counts,
            hotbar,
            inventory.offhandItem().map(ReplaySnapshotSerde::itemSymbol).orElse(null)
        );
    }

    private static InventoryState inventory(InventoryDto dto) {
        LinkedHashMap<Item, Integer> counts = new LinkedHashMap<>();
        dto.counts().forEach(entry -> counts.put(item(entry.item()), entry.count()));
        LinkedHashMap<Integer, Item> hotbar = new LinkedHashMap<>();
        LinkedHashMap<Integer, Integer> hotbarCounts = new LinkedHashMap<>();
        dto.hotbar().forEach(entry -> {
            hotbar.put(entry.slot(), item(entry.item()));
            hotbarCounts.put(entry.slot(), entry.count());
        });
        return new InventoryState(
            dto.selectedHotbarSlot(),
            counts,
            hotbar,
            hotbarCounts,
            Optional.ofNullable(dto.offhandItem()).map(ReplaySnapshotSerde::item)
        );
    }

    private static LegalityDto legality(LegalitySnapshot legality) {
        return new LegalityDto(
            vec(legality.eyePosition()),
            legality.blockInteractionRange(),
            legality.entityInteractionRange(),
            legality.occupiedEntityBoxes().stream().map(ReplaySnapshotSerde::box).toList(),
            legality.respawnAnchorWorks()
        );
    }

    private static LegalitySnapshot legality(LegalityDto dto) {
        return new LegalitySnapshot(
            vec(dto.eyePosition()),
            dto.blockInteractionRange(),
            dto.entityInteractionRange(),
            dto.occupiedEntityBoxes().stream().map(ReplaySnapshotSerde::box).toList(),
            dto.respawnAnchorWorks()
        );
    }

    private static SpatialDto spatial(UUID id, CombatantSpatialState spatial) {
        return new SpatialDto(
            id.toString(),
            vec(spatial.position()),
            box(spatial.boundingBox()),
            vec(spatial.velocity())
        );
    }

    private static CombatantSpatialState spatial(SpatialDto dto) {
        return new CombatantSpatialState(vec(dto.position()), box(dto.boundingBox()), vec(dto.velocity()));
    }

    private static MovementDto movement(MovementSample sample) {
        return new MovementDto(sample.timestampNanos(), vec(sample.position()), vec(sample.velocity()));
    }

    private static MovementSample movement(MovementDto dto) {
        return new MovementSample(dto.timestampNanos(), vec(dto.position()), vec(dto.velocity()));
    }

    private static ProtectionDto targetProtection(TargetProtectionPolicyConfig config) {
        return new ProtectionDto(
            config.protectedPlayerIds().stream().sorted().map(UUID::toString).toList(),
            config.protectScoreboardTeam(),
            config.maxProtectedDamage()
        );
    }

    private static TargetProtectionPolicyConfig targetProtection(ProtectionDto dto) {
        return new TargetProtectionPolicyConfig(
            dto.protectedPlayerIds().stream().map(UUID::fromString).collect(java.util.stream.Collectors.toSet()),
            dto.protectScoreboardTeam(),
            dto.maxProtectedDamage()
        );
    }

    private static TimingSnapshotDto timing(TimingSnapshot timing) {
        List<TimingDistributionDto> distributions = timing.distributions().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new TimingDistributionDto(
                entry.getKey().name(),
                entry.getValue().sampleCount(),
                Double.toString(entry.getValue().p50Millis()),
                Double.toString(entry.getValue().p90Millis()),
                entry.getValue().medianAbsoluteDeviationMillis(),
                entry.getValue().confidence(),
                entry.getValue().newestSampleNanos()
            ))
            .toList();
        return new TimingSnapshotDto(timing.capturedAtNanos(), distributions);
    }

    private static TimingSnapshot timing(TimingSnapshotDto dto) {
        EnumMap<TimingTransition, TimingDistribution> distributions = new EnumMap<>(TimingTransition.class);
        dto.distributions().forEach(entry -> distributions.put(
            TimingTransition.valueOf(entry.transition()),
            new TimingDistribution(
                entry.sampleCount(),
                Double.parseDouble(entry.p50Millis()),
                Double.parseDouble(entry.p90Millis()),
                entry.medianAbsoluteDeviationMillis(),
                entry.confidence(),
                entry.newestSampleNanos()
            )
        ));
        return new TimingSnapshot(dto.capturedAtNanos(), distributions);
    }

    private static ConfigDto config(OptimizerConfig config) {
        return new ConfigDto(
            config.enabled(),
            config.strategy().name(),
            config.targetRange(),
            config.minDamage(),
            config.maxSelfDamage(),
            config.facePlaceHealth(),
            config.crystals(),
            config.anchors(),
            config.autoRestock(),
            config.rotationMode().name(),
            config.hud()
        );
    }

    private static OptimizerConfig config(ConfigDto dto) {
        return new OptimizerConfig(
            dto.enabled(),
            OptimizerStrategy.valueOf(dto.strategy()),
            dto.targetRange(),
            dto.minDamage(),
            dto.maxSelfDamage(),
            dto.facePlaceHealth(),
            dto.crystals(),
            dto.anchors(),
            dto.autoRestock(),
            RotationMode.valueOf(dto.rotationMode()),
            dto.hud()
        ).validated();
    }

    private static EventDto event(ReplayEvent event) {
        return new EventDto(event.relativeNanos(), event.type(), new LinkedHashMap<>(event.fields()));
    }

    private static ReplayEvent event(EventDto dto) {
        return new ReplayEvent(dto.relativeNanos(), dto.type(), dto.fields());
    }

    private static BlockDto block(BlockPos pos, BlockState state) {
        TreeMap<String, String> properties = new TreeMap<>();
        for (Property<?> property : state.getProperties()) {
            properties.put(property.getName(), serializedPropertyValue(state, property));
        }
        return new BlockDto(pos(pos), blockSymbol(state.getBlock()), properties);
    }

    private static BlockState blockState(BlockDto dto) {
        BlockState state = block(dto.block()).defaultBlockState();
        for (Map.Entry<String, String> entry : dto.properties().entrySet()) {
            Property<?> property = state.getProperties().stream()
                .filter(candidate -> candidate.getName().equals(entry.getKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "unknown block property " + entry.getKey() + " for " + dto.block()
                ));
            state = withProperty(state, property, entry.getValue());
        }
        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String serializedPropertyValue(BlockState state, Property property) {
        Comparable value = state.getValue(property);
        return property.getName(value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState withProperty(BlockState state, Property property, String value) {
        Comparable parsed = (Comparable) property.getValue(value)
            .orElseThrow(() -> new IllegalArgumentException(
                "invalid value " + value + " for property " + property.getName()
            ));
        return state.setValue(property, parsed);
    }

    private static String itemSymbol(Item item) {
        return staticSymbol(Items.class, item, Item.class);
    }

    private static Item item(String symbol) {
        return staticValue(Items.class, symbol, Item.class);
    }

    private static String blockSymbol(Block block) {
        return staticSymbol(Blocks.class, block, Block.class);
    }

    private static Block block(String symbol) {
        return staticValue(Blocks.class, symbol, Block.class);
    }

    private static <T> String staticSymbol(Class<?> holder, T value, Class<T> type) {
        return java.util.Arrays.stream(holder.getFields())
            .filter(field -> Modifier.isStatic(field.getModifiers()))
            .filter(field -> type.isAssignableFrom(field.getType()))
            .sorted(Comparator.comparing(Field::getName))
            .filter(field -> readStatic(field) == value)
            .map(Field::getName)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("unregistered vanilla value: " + value));
    }

    private static <T> T staticValue(Class<?> holder, String symbol, Class<T> type) {
        try {
            Field field = holder.getField(symbol.toUpperCase(Locale.ROOT));
            Object value = field.get(null);
            return type.cast(value);
        } catch (ReflectiveOperationException | ClassCastException invalid) {
            throw new IllegalArgumentException("unknown vanilla symbol: " + symbol, invalid);
        }
    }

    private static Object readStatic(Field field) {
        try {
            return field.get(null);
        } catch (IllegalAccessException impossible) {
            throw new IllegalStateException("public vanilla registry field became inaccessible", impossible);
        }
    }

    private static PosDto pos(BlockPos pos) {
        return new PosDto(pos.getX(), pos.getY(), pos.getZ());
    }

    private static BlockPos pos(PosDto pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }

    private static VecDto vec(Vec3 vec) {
        return new VecDto(vec.x, vec.y, vec.z);
    }

    private static Vec3 vec(VecDto vec) {
        return new Vec3(vec.x(), vec.y(), vec.z());
    }

    private static BoxDto box(AABB box) {
        return new BoxDto(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    private static AABB box(BoxDto box) {
        return new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    record RootDto(int schemaVersion, SnapshotDto snapshot, ConfigDto config, List<EventDto> events) {}
    record SnapshotDto(long snapshotId, long worldRevision, long inventoryRevision, long configRevision,
                       long capturedAtNanos, String selfId, List<RevisionDto> targetRevisions,
                       CombatDto combat, List<MovementHistoryDto> movementHistory,
                       List<String> protectedPlayerIds, ProtectionDto targetProtection,
                       TimingSnapshotDto timing) {}
    record RevisionDto(String id, long revision) {}
    record CombatDto(long worldRevision, String selfId, List<BlockDto> blocks,
                     List<CombatantDto> combatants, List<CrystalDto> crystals,
                     List<AnchorDto> anchors, InventoryDto inventory, TimingStateDto timing,
                     LegalityDto legality, List<SpatialDto> spatial, String difficulty) {}
    record BlockDto(PosDto pos, String block, Map<String, String> properties) {}
    record CombatantDto(String id, float health, float absorption, EquipmentDto equipment,
                        EffectsDto effects, BlockingDto blocking, HurtDto hurtWindow,
                        String totem, boolean dead) {}
    record EquipmentDto(ArmorDto head, ArmorDto chest, ArmorDto legs, ArmorDto feet) {}
    record ArmorDto(float armorPoints, float toughness, int durabilityRemaining,
                    float enchantmentProtection) {}
    record EffectsDto(EffectDto resistance, EffectDto regeneration, EffectDto absorption,
                      EffectDto fireResistance) {}
    record EffectDto(int amplifier, int durationTicks) {}
    record BlockingDto(boolean active, VecDto position, float headYawDegrees,
                       float horizontalBlockingAngle, float baseReduction, float factorReduction) {}
    record HurtDto(int invulnerableTime, float lastHurt, boolean lastHurtKnown) {}
    record CrystalDto(int entityId, VecDto position) {}
    record AnchorDto(PosDto pos, int charges) {}
    record InventoryDto(int selectedHotbarSlot, List<ItemCountDto> counts,
                        List<HotbarDto> hotbar, String offhandItem) {}
    record ItemCountDto(String item, int count) {}
    record HotbarDto(int slot, String item, int count) {}
    record TimingStateDto(long estimatedServerTick, double confidence,
                          double roundTripMillis, double jitterMillis) {}
    record LegalityDto(VecDto eyePosition, double blockInteractionRange,
                       double entityInteractionRange, List<BoxDto> occupiedEntityBoxes,
                       boolean respawnAnchorWorks) {}
    record SpatialDto(String id, VecDto position, BoxDto boundingBox, VecDto velocity) {}
    record MovementHistoryDto(String id, List<MovementDto> samples) {}
    record MovementDto(long timestampNanos, VecDto position, VecDto velocity) {}
    record ProtectionDto(List<String> protectedPlayerIds, boolean protectScoreboardTeam,
                         float maxProtectedDamage) {}
    record TimingSnapshotDto(long capturedAtNanos, List<TimingDistributionDto> distributions) {}
    record TimingDistributionDto(String transition, int sampleCount, String p50Millis,
                                 String p90Millis, double medianAbsoluteDeviationMillis,
                                 double confidence, long newestSampleNanos) {}
    record ConfigDto(boolean enabled, String strategy, double targetRange, float minDamage,
                     float maxSelfDamage, float facePlaceHealth, boolean crystals,
                     boolean anchors, boolean autoRestock, String rotationMode, boolean hud) {}
    record EventDto(long relativeNanos, String type, Map<String, String> fields) {}
    record PosDto(int x, int y, int z) {}
    record VecDto(double x, double y, double z) {}
    record BoxDto(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}
}
