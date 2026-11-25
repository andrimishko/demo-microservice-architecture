package ua.lab3.aggregator;

/** Static sharding: every replica sees all nodes but handles only its own slice. */
public final class Sharding {
    private Sharding() {}

    public static boolean owns(String nodeId, int shardIndex, int shardCount) {
        return Math.floorMod(nodeId.hashCode(), shardCount) == shardIndex;
    }
}
