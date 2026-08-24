package dev.adrien.spearclient.combat;

record TargetScore(boolean directRay, double angleCost, double distanceSquared)
    implements Comparable<TargetScore> {

    @Override
    public int compareTo(TargetScore other) {
        if (directRay != other.directRay) {
            return directRay ? -1 : 1;
        }
        int angle = Double.compare(angleCost, other.angleCost);
        if (angle != 0) {
            return angle;
        }
        return Double.compare(distanceSquared, other.distanceSquared);
    }
}
