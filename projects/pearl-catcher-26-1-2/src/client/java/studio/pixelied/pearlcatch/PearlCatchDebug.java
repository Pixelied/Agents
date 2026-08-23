package studio.pixelied.pearlcatch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import studio.pixelied.pearlcatch.core.GeneralCatchSolver;
import studio.pixelied.pearlcatch.core.Rotation;
import studio.pixelied.pearlcatch.core.VanillaProjectilePhysics;
import studio.pixelied.pearlcatch.core.Vec3d;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Debug visualization, trace DTOs, and export helpers. No catch decisions live here. */
final class PearlCatchDebug {
    private PearlCatchDebug() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    static final DustParticleOptions PREDICTED_PEARL = new DustParticleOptions(0xB45CFF, 0.72F);
    static final DustParticleOptions PREDICTED_WIND = new DustParticleOptions(0x45E8FF, 0.72F);
    static final DustParticleOptions ACTUAL_PEARL = new DustParticleOptions(0xFF55D8, 0.90F);
    static final DustParticleOptions ACTUAL_WIND = new DustParticleOptions(0x55FFB0, 0.90F);
    static final DustParticleOptions INTERCEPT = new DustParticleOptions(0xFFFFFF, 1.15F);

    static void particle(ClientLevel level, DustParticleOptions particle, Vec3 p) {
        level.addParticle(particle, p.x, p.y, p.z, 0, 0, 0);
    }

    static List<Vec3> predictPearl(Vec3 eye, Rotation rotation, Vec3d inherited, int ticks) {
        List<Vec3> points = new ArrayList<>();
        Vec3d p = toCore(eye).add(0.0, VanillaProjectilePhysics.PEARL_SPAWN_Y_OFFSET, 0.0);
        Vec3d v = VanillaProjectilePhysics.nominalLaunchVelocity(rotation, inherited);
        points.add(fromCore(p));
        for (int i = 0; i < ticks; i++) {
            v = VanillaProjectilePhysics.pearlVelocityAfterTick(v);
            p = p.add(v);
            points.add(fromCore(p));
        }
        return points;
    }

    static List<Vec3> predictPearlFromVelocity(Vec3d start, Vec3d launchVelocity, int ticks) {
        List<Vec3> points = new ArrayList<>();
        Vec3d p = start;
        Vec3d v = launchVelocity;
        points.add(fromCore(p));
        for (int i = 0; i < ticks; i++) {
            v = VanillaProjectilePhysics.pearlVelocityAfterTick(v);
            p = p.add(v);
            points.add(fromCore(p));
        }
        return points;
    }

    static List<Vec3> predictWind(Vec3 eye, Rotation rotation, Vec3d inherited, int ticks) {
        List<Vec3> points = new ArrayList<>();
        Vec3d p = toCore(eye);
        Vec3d v = VanillaProjectilePhysics.nominalLaunchVelocity(rotation, inherited);
        points.add(fromCore(p));
        for (int i = 0; i < ticks; i++) { p = p.add(v); points.add(fromCore(p)); }
        return points;
    }

    static String firstBlockObstruction(ClientLevel level, LocalPlayer player, List<Vec3> pearl, List<Vec3> wind) {
        for (int i = 1; i < pearl.size(); i++) {
            BlockHitResult hit = level.clip(new ClipContext(pearl.get(i - 1), pearl.get(i), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) return "PEARL_BLOCKED_AT_SEGMENT_" + i;
        }
        for (int i = 1; i < wind.size(); i++) {
            BlockHitResult hit = level.clip(new ClipContext(wind.get(i - 1), wind.get(i), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) return "WIND_BLOCKED_AT_SEGMENT_" + i;
        }
        return null;
    }

    static void updateClosest(CatchAttemptTracker.TrackingShot shot, ThrownEnderpearl pearl, WindCharge wind) {
        Vec3 currentPearl = pearl.position();
        Vec3 previousPearl = shot.previousPearlForGap == null ? currentPearl : shot.previousPearlForGap;
        double margin = VanillaProjectilePhysics.collisionMarginForCompletedSegment(pearl.tickCount);
        AABB box = wind.getBoundingBox().inflate(margin);
        var clipLocation = box.clip(previousPearl, currentPearl);
        boolean clip = clipLocation.isPresent();
        double gap = CatchAttemptTracker.approximateSegmentAabbGap(previousPearl, currentPearl, box);
        double centerGap = currentPearl.distanceTo(box.getCenter());
        if (gap < shot.closestGap) {
            shot.closestGap = gap;
            shot.closestCenterGap = centerGap;
            shot.closestClientTick = shot.ageTicks;
            shot.closestPearl = currentPearl;
            shot.closestWind = wind.position();
        }
        if (clip) {
            shot.clientInterpolatedClipHint = true;
            if (shot.firstClientClipHintTick < 0) {
                shot.firstClientClipHintTick = shot.ageTicks;
                shot.firstClientClipHintPoint = clipLocation.orElse(null);
            }
        }
        shot.previousPearlForGap = currentPearl;
    }

    static SolverTickTrace solverTrace(CatchAttemptTracker.TrackingShot shot, ThrownEnderpearl pearl, WindCharge wind) {
        int elapsed = shot.ageTicks;
        Vec3 expectedPearl = shot.predictedPearl.isEmpty() ? null
                : shot.predictedPearl.get(Math.min(elapsed, shot.predictedPearl.size() - 1));
        int windThrowPearlTick = Math.max(0, shot.plan.firstWindPearlSegment() - 1);
        int windIndex = Math.max(0, elapsed - windThrowPearlTick);
        Vec3 expectedWind = shot.predictedWind.isEmpty() ? null
                : shot.predictedWind.get(Math.min(windIndex, shot.predictedWind.size() - 1));
        return new SolverTickTrace(
                elapsed,
                shot.plan.pearlCatchTick(),
                shot.plan.windCompletedTicksAtCatch(),
                shot.plan.firstWindPearlSegment(),
                windThrowPearlTick,
                rotationTrace(shot.plan.pearlRotation()),
                rotationTrace(shot.plan.windRotation()),
                vecTrace(shot.plan.interceptPoint()),
                vecTrace(expectedPearl),
                vecTrace(expectedWind),
                pearl == null ? null : pearl.tickCount,
                wind == null ? null : wind.tickCount
        );
    }

    static void showOverlay(Minecraft mc, CatchAttemptTracker.TrackingShot shot, int activeCount) {
        String text = "PearlCatch " + shot.execution
                + " | t=" + shot.ageTicks + "/" + shot.plan.pearlCatchTick()
                + " | target=" + fmt(PearlCatchClient.CONFIG.targetCatchDistance) + "→" + fmt(shot.plan.crosshairRange()) + "b"
                + " | clearance=" + fmt(shot.plan.collisionClearance()) + "b"
                + " | reliability=" + fmt(shot.plan.robustHitFraction() * 100.0) + "%"
                + " | gapHint=" + (Double.isFinite(shot.closestGap) ? fmt(shot.closestGap) : "—")
                + " | active=" + activeCount
                + " | P#" + shot.pearlId + " W#" + shot.windId;
        mc.gui.setOverlayMessage(Component.literal(text), false);
    }

    static void renderVisualization(ClientLevel level, CatchAttemptTracker.TrackingShot shot, PearlCatchConfig config) {
        int stride = Math.max(1, config.debugParticleStride);
        for (int i = 0; i < shot.predictedPearl.size(); i += Math.max(2, stride * 2)) particle(level, PREDICTED_PEARL, shot.predictedPearl.get(i));
        for (int i = 0; i < shot.predictedWind.size(); i += Math.max(2, stride * 2)) particle(level, PREDICTED_WIND, shot.predictedWind.get(i));
        for (int i = Math.max(0, shot.actualPearl.size() - 30); i < shot.actualPearl.size(); i += stride) particle(level, ACTUAL_PEARL, shot.actualPearl.get(i));
        for (int i = Math.max(0, shot.actualWind.size() - 30); i < shot.actualWind.size(); i += stride) particle(level, ACTUAL_WIND, shot.actualWind.get(i));
        Vec3 hit = fromCore(shot.plan.interceptPoint());
        particle(level, INTERCEPT, hit);
        particle(level, INTERCEPT, hit.add(0.08, 0, 0));
        particle(level, INTERCEPT, hit.add(-0.08, 0, 0));
        particle(level, INTERCEPT, hit.add(0, 0.08, 0));
        particle(level, INTERCEPT, hit.add(0, -0.08, 0));
    }

    static EntityTickTrace entityTrace(Entity e) {
        AABB b = e.getBoundingBox();
        return new EntityTickTrace(
                e.getId(), e.tickCount, vecTrace(e.position()), vecTrace(e.getDeltaMovement()),
                new BoxTrace(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ)
        );
    }

    static PlayerTickTrace playerTrace(LocalPlayer p) {
        return new PlayerTickTrace(vecTrace(p.position()), vecTrace(p.getEyePosition()), vecTrace(p.getKnownMovement()), p.getYRot(), p.getXRot(), p.onGround());
    }

    static RotationTrace rotationTrace(Rotation r) { return r == null ? null : new RotationTrace(r.yaw(), r.pitch()); }
    static VecTrace vecTrace(Vec3 p) { return p == null ? null : new VecTrace(p.x, p.y, p.z); }
    static VecTrace vecTrace(Vec3d p) { return p == null ? null : new VecTrace(p.x(), p.y(), p.z()); }
    static Vec3d toCore(Vec3 v) { return new Vec3d(v.x, v.y, v.z); }
    static Vec3 fromCore(Vec3d v) { return new Vec3(v.x(), v.y(), v.z()); }
    static Double finiteOrNull(double v) { return Double.isFinite(v) ? v : null; }

    static <T> void trim(List<T> list, int max) {
        while (list.size() > max) list.remove(0);
    }

    static String fmt(double v) { return String.format(Locale.ROOT, "%.3f", v).replaceAll("0+$", "").replaceAll("\\.$", ""); }
    static String rot(Rotation r) { return "(" + fmt(r.yaw()) + "°, " + fmt(r.pitch()) + "°)"; }
    static String vec(Vec3d v) { return "(" + fmt(v.x()) + ", " + fmt(v.y()) + ", " + fmt(v.z()) + ")"; }

    static final class DebugSweep {
        final float yaw;
        final double startPitch;
        final double endPitch;
        final double step;
        int shotIndex;
        int waitTicks;
        int readinessWaitTicks;
        boolean active;
        final float originalYaw;
        final float originalPitch;
        double currentPitch;

        DebugSweep(float yaw, double startPitch, double endPitch, double step, int shotIndex, int waitTicks,
                   boolean active, float originalYaw, float originalPitch) {
            this.yaw = yaw; this.startPitch = startPitch; this.endPitch = endPitch; this.step = step;
            this.shotIndex = shotIndex; this.waitTicks = waitTicks; this.active = active;
            this.originalYaw = originalYaw; this.originalPitch = originalPitch; this.currentPitch = startPitch;
        }

        void advance() { shotIndex++; currentPitch += step; }
        boolean done() { return step > 0 ? currentPitch > endPitch + 1.0e-6 : currentPitch < endPitch - 1.0e-6; }
        boolean active() { return active; }
    }

    record VecTrace(double x, double y, double z) {}
    record BoxTrace(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}
    record RotationTrace(double yaw, double pitch) {}
    record PlayerTickTrace(VecTrace position, VecTrace eye, VecTrace movement, float yaw, float pitch, boolean onGround) {}
    record EntityTickTrace(int entityId, int entityTickCount, VecTrace position, VecTrace velocity, BoxTrace aabb) {}
    record SolverTickTrace(
            int localShotTick,
            int plannedPearlCatchTick,
            int plannedWindCompletedTicks,
            int firstWindPearlSegment,
            int windThrowPearlTick,
            RotationTrace pearlRotation,
            RotationTrace windRotation,
            VecTrace plannedIntercept,
            VecTrace expectedPearlPosition,
            VecTrace expectedWindPosition,
            Integer observedPearlEntityTick,
            Integer observedWindEntityTick
    ) {}
    record TickTrace(int shotTick, long clientTick, PlayerTickTrace player, EntityTickTrace pearl, EntityTickTrace wind, SolverTickTrace solverPlan) {}
    record ClosestTrace(
            Double boxGap,
            Double centerGap,
            int shotTick,
            boolean clientInterpolatedClipHint,
            int firstClientClipHintTick,
            VecTrace clientClipHintPoint,
            VecTrace pearlPosition,
            VecTrace windPosition,
            String authority
    ) {}

    static final class ShotTrace {
        String label;
        String action;
        RotationTrace targetRotation;
        PlayerTickTrace playerStart;
        long clientStartTick;
        int activeAttemptCountAtLaunch;
        String itemSwitchMode;
        String resolvedHand;
        Integer resolvedSlot;
        Integer minimumExecutableWindDelay;
        Long pearlItemPrepRequestedClientTick;
        Long pearlItemPrepConfirmedClientTick;
        Long windItemPrepRequestedClientTick;
        Long windItemPrepConfirmedClientTick;
        Long useRequestedClientTick;
        Long projectileObservedClientTick;
        Integer attemptedWindThrowShotTick;
        Long attemptedWindThrowClientTick;
        Integer pearlEntityId;
        Integer windEntityId;
        VecTrace actualPearlLaunchVelocity;
        VecTrace projectileInferredInheritedMovement;
        VecTrace solverInheritedMovementAtLaunch;
        VecTrace clientKnownMovementAtLaunch;
        Double projectileMovementEstimateError;
        Integer actualPearlObservedEntityTick;
        boolean solvedFromEntityLoadEvent;
        Double teleportDistanceToPlannedIntercept;
        PlanTrace plan;
        List<VecTrace> predictedPearlTrajectory = new ArrayList<>();
        List<VecTrace> predictedWindTrajectory = new ArrayList<>();
        List<TickTrace> ticks = new ArrayList<>();
        ClosestTrace closest;
        String finishReason;

        static ShotTrace preparing(String label, Rotation target, LocalPlayer player, long clientTick) {
            ShotTrace s = new ShotTrace();
            s.label = label;
            s.action = "PREPARE";
            s.targetRotation = rotationTrace(target);
            s.playerStart = playerTrace(player);
            s.clientStartTick = clientTick;
            return s;
        }

        static ShotTrace launched(String label, String action, Rotation target, LocalPlayer player, GeneralCatchSolver.Plan plan,
                                  List<Vec3> pearl, List<Vec3> wind, long clientTick) {
            ShotTrace s = new ShotTrace();
            s.label = label;
            s.action = action;
            s.targetRotation = rotationTrace(target);
            s.playerStart = playerTrace(player);
            s.clientStartTick = clientTick;
            s.attemptedWindThrowShotTick = 0;
            s.attemptedWindThrowClientTick = clientTick;
            s.plan = PlanTrace.from(plan);
            for (Vec3 p : pearl) s.predictedPearlTrajectory.add(vecTrace(p));
            for (Vec3 p : wind) s.predictedWindTrajectory.add(vecTrace(p));
            return s;
        }

        static ShotTrace pending(String label, Rotation target, LocalPlayer player,
                                 GeneralCatchSolver.Plan plan, List<Vec3> pearl, long clientTick) {
            ShotTrace s = new ShotTrace();
            s.label = label;
            s.action = "WAIT_" + plan.windDelayTicksFromNow() + "T";
            s.targetRotation = rotationTrace(target);
            s.playerStart = playerTrace(player);
            s.clientStartTick = clientTick;
            s.plan = PlanTrace.from(plan);
            for (Vec3 p : pearl) s.predictedPearlTrajectory.add(vecTrace(p));
            return s;
        }

        static ShotTrace unlaunched(String label, Rotation target, LocalPlayer player, long clientTick, String reason) {
            ShotTrace s = new ShotTrace();
            s.label = label;
            s.action = "UNLAUNCHED";
            s.targetRotation = rotationTrace(target);
            s.playerStart = playerTrace(player);
            s.clientStartTick = clientTick;
            s.finishReason = reason;
            return s;
        }
    }

    record PlanTrace(
            RotationTrace pearlRotation,
            RotationTrace windRotation,
            int windDelayTicksFromNow,
            int pearlCatchTick,
            int windCompletedTicksAtCatch,
            int firstWindPearlSegment,
            VecTrace plannedIntercept,
            VecTrace plannedWindPosition,
            double crosshairDistance,
            double crosshairRange,
            double targetDistanceError,
            double collisionClearance,
            int firstCollisionTick,
            double sampledRobustHitFraction,
            double score,
            boolean pearlLaunchKnown
    ) {
        static PlanTrace from(GeneralCatchSolver.Plan p) {
            return new PlanTrace(rotationTrace(p.pearlRotation()), rotationTrace(p.windRotation()), p.windDelayTicksFromNow(),
                    p.pearlCatchTick(), p.windCompletedTicksAtCatch(), p.firstWindPearlSegment(),
                    vecTrace(p.interceptPoint()), vecTrace(p.windPositionAtCatch()),
                    p.crosshairDistance(), p.crosshairRange(), p.targetDistanceError(), p.collisionClearance(), p.firstCollisionTick(),
                    p.robustHitFraction(), p.score(), p.pearlLaunchKnown());
        }
    }

    static final class TraceSession {
        final String minecraftVersion = "26.1.2";
        final String collisionAuthority = "Pearl movement segment -> WindCharge AABB via ProjectileUtil/AABB.clip; wind cannot entity-hit pearl";
        final String timingModel = "One GeneralCatchSolver; Fast and Legit share identical catch physics; wind timing is solved from confirmed vanilla execution state and real pearl observations";
        final String created = LocalDateTime.now().toString();
        final PlayerTickTrace sessionPlayerStart;
        final ConfigTrace config;
        final List<ShotTrace> shots = new ArrayList<>();
        transient final Path jsonPath;
        transient final Path textPath;
        String finishReason;

        private TraceSession(PlayerTickTrace sessionPlayerStart, ConfigTrace config, Path jsonPath, Path textPath) {
            this.sessionPlayerStart = sessionPlayerStart; this.config = config; this.jsonPath = jsonPath; this.textPath = textPath;
        }

        static TraceSession start(LocalPlayer player, PearlCatchConfig config, int effectiveSolverHorizon) {
            try {
                Path dir = FabricLoader.getInstance().getGameDir().resolve("pearlcatch-debug");
                Files.createDirectories(dir);
                String stamp = LocalDateTime.now().format(FILE_TIME);
                Path json = dir.resolve("pearlcatch-debug-" + stamp + ".json");
                Path text = dir.resolve("pearlcatch-debug-" + stamp + ".txt");
                TraceSession s = new TraceSession(playerTrace(player), ConfigTrace.from(config, effectiveSolverHorizon), json, text);
                s.write();
                return s;
            } catch (Exception e) {
                return null;
            }
        }

        void write() {
            try {
                Files.writeString(jsonPath, GSON.toJson(this), StandardCharsets.UTF_8);
                Files.writeString(textPath, toText(), StandardCharsets.UTF_8);
            } catch (IOException ignored) {
            }
        }

        private String toText() {
            StringBuilder b = new StringBuilder(64_000);
            b.append("Pearl Catcher 26.1.2 debug trace\n")
                    .append("================================\n")
                    .append(collisionAuthority).append('\n')
                    .append(timingModel).append("\n\n")
                    .append("Created: ").append(created).append('\n')
                    .append("Finish: ").append(finishReason).append("\n")
                    .append("Config: ").append(GSON.toJson(config)).append("\n\n");
            for (int i = 0; i < shots.size(); i++) {
                ShotTrace s = shots.get(i);
                b.append("--- Shot ").append(i + 1).append(": ").append(s.label).append(" ---\n")
                        .append("target=").append(GSON.toJson(s.targetRotation)).append('\n')
                        .append("action=").append(s.action).append('\n')
                        .append("execution=itemSwitchMode=").append(s.itemSwitchMode)
                        .append(" resolvedHand=").append(s.resolvedHand)
                        .append(" resolvedSlot=").append(s.resolvedSlot)
                        .append(" minimumWindDelay=").append(s.minimumExecutableWindDelay)
                        .append(" pearlPrep=").append(s.pearlItemPrepRequestedClientTick).append("->").append(s.pearlItemPrepConfirmedClientTick)
                        .append(" windPrep=").append(s.windItemPrepRequestedClientTick).append("->").append(s.windItemPrepConfirmedClientTick).append('\n')
                        .append("plan=").append(GSON.toJson(s.plan)).append('\n')
                        .append("pearlEntityId=").append(s.pearlEntityId).append(" windEntityId=").append(s.windEntityId).append('\n')
                        .append("attemptedWindThrowShotTick=").append(s.attemptedWindThrowShotTick)
                        .append(" attemptedWindThrowClientTick=").append(s.attemptedWindThrowClientTick).append('\n')
                        .append("closest=").append(GSON.toJson(s.closest)).append('\n')
                        .append("result=").append(s.finishReason).append("\n")
                        .append("ticks:\n");
                for (TickTrace t : s.ticks) b.append(GSON.toJson(t)).append('\n');
                b.append('\n');
            }
            return b.toString();
        }
    }

    record ConfigTrace(
            String itemSwitchMode,
            String rotationMode,
            int configuredPredictionFloor,
            int effectiveSolverHorizon,
            double targetCatchDistance,
            double solverSearchDistance,
            double maxCrosshairDistance,
            double pitchSweepStart,
            double pitchSweepEnd,
            double pitchSweepStep,
            int maxTicksPerPitch
    ) {
        static ConfigTrace from(PearlCatchConfig c, int effectiveSolverHorizon) {
            return new ConfigTrace(c.itemSwitchMode.name(), c.rotationMode.name(), c.maxPredictionTicks, effectiveSolverHorizon,
                    c.targetCatchDistance, c.solverSearchDistance(), c.maxCrosshairDistance,
                    c.pitchSweepStart, c.pitchSweepEnd, c.pitchSweepStep, c.maxTicksPerPitch);
        }
    }
}
