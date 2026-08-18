package dev.adrien.crystaloptimizer.timing;

public enum PacketDependency {
    NONE,
    LOCAL_STATE,
    CLIENT_PREDICTION,
    SERVER_FEEDBACK_FOR_NEW_ENTITY
}
