package dev.pixelied.survival.planner;

import dev.pixelied.survival.config.RescuePolicy;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.execution.DeathProtectionPopTracker;
import dev.pixelied.survival.execution.EquipmentAuthorityProjection;
import dev.pixelied.survival.inventory.DeathProtectionRoute;
import dev.pixelied.survival.inventory.DeathProtectionRoutePlanner;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.inventory.ProtectionRouteScorer;
import dev.pixelied.survival.threat.ExplosionPredictor;
import dev.pixelied.survival.timeline.CausalThreatTimeline;
import dev.pixelied.survival.timeline.ThreatTimeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Applies server-authority hand projection before delegating to the existing candidate generator. */
public final class AuthorityAwareCandidateGenerator {
    private final SurvivalCandidateGenerator delegate;
    private final ExplosionPredictor causalizer;
    private final DeathProtectionRoutePlanner routePlanner;
    private final ProtectionRouteScorer routeScorer;

    public AuthorityAwareCandidateGenerator() {
        this(
            new SurvivalCandidateGenerator(),
            new ExplosionPredictor(),
            new DeathProtectionRoutePlanner(),
            new ProtectionRouteScorer()
        );
    }

    AuthorityAwareCandidateGenerator(SurvivalCandidateGenerator delegate) {
        this(delegate, new ExplosionPredictor(), new DeathProtectionRoutePlanner(), new ProtectionRouteScorer());
    }

    AuthorityAwareCandidateGenerator(SurvivalCandidateGenerator delegate, ExplosionPredictor causalizer) {
        this(delegate, causalizer, new DeathProtectionRoutePlanner(), new ProtectionRouteScorer());
    }

    AuthorityAwareCandidateGenerator(
        SurvivalCandidateGenerator delegate,
        ExplosionPredictor causalizer,
        DeathProtectionRoutePlanner routePlanner,
        ProtectionRouteScorer routeScorer
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.causalizer = Objects.requireNonNull(causalizer, "causalizer");
        this.routePlanner = Objects.requireNonNull(routePlanner, "routePlanner");
        this.routeScorer = Objects.requireNonNull(routeScorer, "routeScorer");
    }

    public List<SurvivalAction> generate(
        PredictionContext context,
        ThreatTimeline timeline,
        InventorySnapshot inventory,
        MenuSlotMap menu,
        RescuePolicy policy,
        EquipmentAuthorityProjection equipment
    ) {
        return generate(context, timeline, inventory, menu, policy, equipment, null);
    }

    public List<SurvivalAction> generate(
        PredictionContext context,
        CausalThreatTimeline timeline,
        InventorySnapshot inventory,
        MenuSlotMap menu,
        RescuePolicy policy,
        EquipmentAuthorityProjection equipment
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(equipment, "equipment");

        var earliestRelativeImpact = timeline.expandedTimeline().events().stream()
            .mapToLong(event -> event.impact().earliest())
            .min();
        long damageDeadline = earliestRelativeImpact.isPresent()
            ? saturatingAdd(context.timing().clientTick(), earliestRelativeImpact.getAsLong())
            : context.timing().nextPacketProcessingWindow().latest();

        InventorySnapshot projectedInventory = equipment.conservativeInventoryAt(inventory, damageDeadline);
        DeathProtectionSnapshot guaranteedProtection = equipment.guaranteedDeathProtectionAt(damageDeadline);
        PredictionContext projectedContext = withGuaranteedProtection(context, guaranteedProtection);
        List<SurvivalAction> base = delegate.generate(projectedContext, timeline, projectedInventory, menu, policy);
        return applyProtectionPreference(
            projectedContext,
            projectedInventory,
            menu,
            policy,
            damageDeadline,
            equipment,
            guaranteedProtection,
            base
        );
    }

    public List<SurvivalAction> generate(
        PredictionContext context,
        CausalThreatTimeline timeline,
        InventorySnapshot inventory,
        MenuSlotMap menu,
        RescuePolicy policy,
        EquipmentAuthorityProjection equipment,
        DeathProtectionPopTracker pops
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(equipment, "equipment");

        var earliestRelativeImpact = timeline.expandedTimeline().events().stream()
            .mapToLong(event -> event.impact().earliest())
            .min();
        long damageDeadline = earliestRelativeImpact.isPresent()
            ? saturatingAdd(context.timing().clientTick(), earliestRelativeImpact.getAsLong())
            : context.timing().nextPacketProcessingWindow().latest();

        InventorySnapshot projectedInventory = pops == null
            ? equipment.conservativeInventoryAt(inventory, damageDeadline)
            : pops.conservativeInventoryAfterPop(inventory, equipment, damageDeadline);
        DeathProtectionSnapshot guaranteedProtection = pops == null
            ? equipment.guaranteedDeathProtectionAt(damageDeadline)
            : pops.projectedDeathProtectionAt(equipment, damageDeadline);
        PredictionContext projectedContext = withGuaranteedProtection(context, guaranteedProtection);
        List<SurvivalAction> base = delegate.generate(projectedContext, timeline, projectedInventory, menu, policy);
        return applyProtectionPreference(
            projectedContext,
            projectedInventory,
            menu,
            policy,
            damageDeadline,
            equipment,
            guaranteedProtection,
            base
        );
    }

    public List<SurvivalAction> generate(
        PredictionContext context,
        ThreatTimeline timeline,
        InventorySnapshot inventory,
        MenuSlotMap menu,
        RescuePolicy policy,
        EquipmentAuthorityProjection equipment,
        DeathProtectionPopTracker pops
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(timeline, "timeline");
        return generate(
            context,
            causalizer.causalize(context, timeline),
            inventory,
            menu,
            policy,
            equipment,
            pops
        );
    }

    private List<SurvivalAction> applyProtectionPreference(
        PredictionContext context,
        InventorySnapshot inventory,
        MenuSlotMap menu,
        RescuePolicy policy,
        long damageDeadline,
        EquipmentAuthorityProjection equipment,
        DeathProtectionSnapshot guaranteedProtection,
        List<SurvivalAction> base
    ) {
        if (!policy.deathProtection() || !policy.inventoryRouting() || guaranteedProtection.anyHandAvailable()) {
            return base;
        }

        // Hand preference is a tie-break only after authority feasibility. If a dispatched hand
        // mutation can still land on either side of the lethal deadline, the delegate's candidate
        // is the conservative re-arm/repair for that exact in-flight state. Re-routing it from the
        // projected inventory can erase the only action that closes the authority race.
        if (equipment.pending().stream().anyMatch(mutation -> mutation.uncertainAt(damageDeadline))) {
            return base;
        }

        long existingEquipCount = base.stream()
            .filter(SurvivalAction.EquipDeathProtection.class::isInstance)
            .count();
        // Keep the causal dual-protection path intact. Preference is a tie-break for alternative
        // single-rescue routes, not a license to collapse two physically required protections.
        if (existingEquipCount != 1L) return base;

        Map<DeathProtectionRoute, ProtectionRouteScorer.Candidate> alternatives = new LinkedHashMap<>();
        if (policy.mainHandTakeover()) {
            routePlanner.choose(inventory, menu, DeathProtectionRoute.Destination.MAIN_HAND)
                .filter(route -> !(route instanceof DeathProtectionRoute.AlreadyInHand))
                .ifPresent(route -> addRouteCandidate(alternatives, context, inventory, menu, route, damageDeadline));
        }
        routePlanner.choose(inventory, menu, DeathProtectionRoute.Destination.OFF_HAND)
            .filter(route -> !(route instanceof DeathProtectionRoute.AlreadyInHand))
            .ifPresent(route -> addRouteCandidate(alternatives, context, inventory, menu, route, damageDeadline));
        if (alternatives.isEmpty()) return base;

        ProtectionRouteScorer.Context scoreContext = new ProtectionRouteScorer.Context(
            policy.totemHandPriority(),
            inventory.activeOffhandShield(),
            activeUseHand(context.player()),
            policy.mainHandTakeover()
        );
        List<ProtectionRouteScorer.Candidate> safeAlternatives = alternatives.values().stream()
            .filter(candidate -> {
                ProtectionRouteScorer.ProtectionRouteScore score = routeScorer.score(candidate, scoreContext);
                return score.allowed() && score.deadlineSafe();
            })
            .toList();
        if (safeAlternatives.isEmpty()) return base;

        ProtectionRouteScorer.ScoredRoute chosen = routeScorer.rank(safeAlternatives, scoreContext).getFirst();
        ProtectionRouteScorer.Candidate route = alternatives.get(chosen.route());
        if (route == null) return base;

        List<SurvivalAction> result = new ArrayList<>(base.size());
        for (SurvivalAction action : base) {
            if (!(action instanceof SurvivalAction.EquipDeathProtection)) result.add(action);
        }
        result.add(toProtectionAction(context, inventory, menu, route, scoreContext));
        return List.copyOf(result);
    }

    private void addRouteCandidate(
        Map<DeathProtectionRoute, ProtectionRouteScorer.Candidate> alternatives,
        PredictionContext context,
        InventorySnapshot inventory,
        MenuSlotMap menu,
        DeathProtectionRoute route,
        long damageDeadline
    ) {
        int requiredServerTicks = route instanceof DeathProtectionRoute.HotbarSelect
            ? 0
            : context.timing().serverCorrectionReturnTicks();
        long completionTick = context.timing().deadline(requiredServerTicks).completionWindow().latest();
        alternatives.putIfAbsent(
            route,
            new ProtectionRouteScorer.Candidate(route, completionTick, damageDeadline, 1, 0)
        );
    }

    private SurvivalAction.EquipDeathProtection toProtectionAction(
        PredictionContext context,
        InventorySnapshot inventory,
        MenuSlotMap menu,
        ProtectionRouteScorer.Candidate candidate,
        ProtectionRouteScorer.Context scoreContext
    ) {
        DeathProtectionRoute route = candidate.route();
        InventorySlotSnapshot routedSlot = routedProtectionSlot(inventory, menu, route)
            .orElseThrow(() -> new IllegalStateException("death-protection route has no source inventory slot"));
        DeathProtectionSnapshot.ProtectionItem item = routedSlot.deathProtectionItem().orElseGet(() ->
            "minecraft:totem_of_undying".equals(routedSlot.stackKey())
                ? DeathProtectionSnapshot.ProtectionItem.vanillaTotem()
                : DeathProtectionSnapshot.ProtectionItem.generic()
        );
        SurvivalAction.Hand hand = ProtectionRouteScorer.destination(route) == DeathProtectionRoute.Destination.OFF_HAND
            ? SurvivalAction.Hand.OFF_HAND
            : SurvivalAction.Hand.MAIN_HAND;
        int requiredServerTicks = route instanceof DeathProtectionRoute.HotbarSelect
            ? 0
            : context.timing().serverCorrectionReturnTicks();
        int disruptionCost = routeScorer.disruptionCost(candidate, scoreContext);

        return new SurvivalAction.EquipDeathProtection(
            item,
            hand,
            requiredServerTicks,
            true,
            true,
            1d,
            1,
            disruptionCost,
            Optional.of(new SurvivalAction.DeathProtectionSourceRef(
                routedSlot.inventoryIndex(),
                routedSlot.stackKey(),
                routedSlot.componentFingerprint(),
                route
            ))
        );
    }

    private static Optional<InventorySlotSnapshot> routedProtectionSlot(
        InventorySnapshot inventory,
        MenuSlotMap menu,
        DeathProtectionRoute route
    ) {
        if (route instanceof DeathProtectionRoute.HotbarSelect hotbar) {
            return inventory.slot(hotbar.hotbarIndex());
        }
        if (route instanceof DeathProtectionRoute.ContainerSwap swap) {
            return menu.inventoryIndexToMenuSlot().entrySet().stream()
                .filter(entry -> entry.getValue() == swap.sourceMenuSlot())
                .map(entry -> inventory.slot(entry.getKey()).orElse(null))
                .filter(Objects::nonNull)
                .findFirst();
        }
        return Optional.empty();
    }

    private static Optional<SurvivalAction.Hand> activeUseHand(PlayerSnapshot player) {
        double using = player.state("using_item", 0d);
        if (using < 0.5d) return Optional.empty();
        double hand = player.state("using_hand", -1d);
        if (hand < 0d) return Optional.empty();
        return Optional.of(hand >= 0.5d ? SurvivalAction.Hand.OFF_HAND : SurvivalAction.Hand.MAIN_HAND);
    }

    private static PredictionContext withGuaranteedProtection(
        PredictionContext context,
        DeathProtectionSnapshot guaranteedProtection
    ) {
        PlayerSnapshot player = context.player();
        PlayerSnapshot projectedPlayer = new PlayerSnapshot(
            player.health(),
            player.absorption(),
            player.playerInvulnerable(),
            player.abilityInvulnerable(),
            player.deadOrDying(),
            player.difficulty(),
            player.mitigation(),
            player.statusEffects(),
            player.blocking(),
            player.hurtState(),
            Objects.requireNonNull(guaranteedProtection, "guaranteedProtection"),
            player.boundingBox(),
            player.position(),
            player.velocity(),
            player.equipmentItemKeys(),
            player.stateProperties()
        );
        return new PredictionContext(
            projectedPlayer,
            context.world(),
            context.timing(),
            context.limits(),
            context.safetyMode()
        );
    }

    private static long saturatingAdd(long value, long increment) {
        return increment > 0L && value > Long.MAX_VALUE - increment
            ? Long.MAX_VALUE
            : value + increment;
    }
}
