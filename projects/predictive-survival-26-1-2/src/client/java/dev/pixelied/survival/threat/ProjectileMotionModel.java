package dev.pixelied.survival.threat;

@FunctionalInterface
public interface ProjectileMotionModel {
    ProjectileStep step(ProjectileStep current);
}
