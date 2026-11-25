package ua.lab3.aggregator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShardingTest {
    @Test
    void singleShardOwnsEverything() {
        assertTrue(Sharding.owns("node-1", 0, 1));
        assertTrue(Sharding.owns("anything", 0, 1));
    }

    @Test
    void everyNodeHasExactlyOneOwner() {
        for (String id : new String[]{"node-1", "node-2", "node-3", "polygenelubricants", "Aa", "BB"}) {
            int owners = 0;
            for (int shard = 0; shard < 3; shard++) if (Sharding.owns(id, shard, 3)) owners++;
            assertEquals(1, owners, id);
        }
    }

    @Test
    void negativeHashCodeStillMapsIntoRange() {
        String id = "polygenelubricants"; // hashCode() == Integer.MIN_VALUE
        assertEquals(Integer.MIN_VALUE, id.hashCode());
        assertTrue(Sharding.owns(id, 1, 3)); // floorMod(MIN_VALUE, 3) == 1, never negative
    }
}
