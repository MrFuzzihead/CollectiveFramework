package com.austinv11.collectiveframework.minecraft.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.World;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * CODE_REVIEW §2.14 - {@code Location#hashCode} returned a constant 0, so every location landed in the same hash
 * bucket and any {@code HashMap} keyed by a location degraded to a linked list. {@code StructureCreator} keeps
 * three such maps, so it was O(n²) in the number of blocks. {@code equals} also dereferenced
 * {@code getWorld().provider} directly, so comparing a location with no world threw.
 * <p>
 * A {@link World}'s {@code provider} field is {@code final}, so a mock cannot supply one; these tests use
 * world-less locations, which is exactly the path that used to NPE. The dimension-aware behaviour is covered by
 * {@code GameTestMod} against a real world.
 */
public class LocationTest {

    private static Location at(double x, double y, double z) {
        return new Location(x, y, z, null);
    }

    // ---------------------------------------------------------------------------------- hashCode

    @Test
    public void hashCodeIsNotConstant() {
        Assert.assertNotEquals(at(0, 0, 0).hashCode(), at(1, 0, 0).hashCode());
        Assert.assertNotEquals(at(0, 0, 0).hashCode(), at(0, 1, 0).hashCode());
        Assert.assertNotEquals(at(0, 0, 0).hashCode(), at(0, 0, 1).hashCode());
    }

    @Test
    public void equalLocationsShareAHashCode() {
        Assert.assertEquals(at(3, 4, 5).hashCode(), at(3, 4, 5).hashCode());
    }

    /** §2.14 - this was the whole problem: every key collided, so lookup was a linear scan. */
    @Test
    public void locationsSpreadAcrossHashBuckets() {
        Set<Integer> hashes = new HashSet<Integer>();
        for (int x = 0; x < 100; x++) hashes.add(at(x, 64, 0).hashCode());

        Assert.assertTrue("hashCodes are collapsing: " + hashes.size() + " distinct", hashes.size() > 50);
    }

    @Test
    public void aHashMapKeyedByLocationWorks() {
        Map<Location, String> map = new HashMap<Location, String>();
        for (int x = 0; x < 200; x++) map.put(at(x, 64, 0), "value-" + x);

        Assert.assertEquals(200, map.size());
        Assert.assertEquals("value-137", map.get(at(137, 64, 0)));
        Assert.assertNull(map.get(at(137, 65, 0)));
    }

    // ------------------------------------------------------------------------------------ equals

    @Test
    public void equalsComparesRoundedCoordinates() {
        Assert.assertEquals(at(1.2, 2.9, 3.1), at(1.0, 2.0, 3.0));
        Assert.assertNotEquals(at(1, 2, 3), at(1, 2, 4));
    }

    @Test
    public void equalsIsReflexiveAndRejectsOtherTypes() {
        Location location = at(1, 2, 3);
        Assert.assertEquals(location, location);
        Assert.assertNotEquals(location, null);
        Assert.assertNotEquals(location, "not a location");
    }

    /** §2.14 - {@code getWorld().provider.dimensionId} threw for a world-less location. */
    @Test
    public void equalsDoesNotThrowForAWorldLessLocation() {
        Assert.assertEquals(at(1, 2, 3), at(1, 2, 3));
    }

    @Test
    public void aWorldLessLocationCanBeComparedWithAWorldBackedOne() {
        Location withWorld = new Location(1, 2, 3, Mockito.mock(World.class));
        // The mock's provider is null, so both report "no dimension" and only the coordinates matter.
        Assert.assertEquals(at(1, 2, 3), withWorld);
    }

    // ------------------------------------------------------------------------------- conversions

    @Test
    public void chunkCoordinatesRoundTrip() {
        Assert.assertEquals(4, at(4.7, 64.2, -3.9).chunkCoordsFromLocation().posX);
        Assert.assertEquals(-3, at(4.7, 64.2, -3.9).chunkCoordsFromLocation().posZ);
    }

    @Test
    public void locationFromChunkCoordinates() {
        Location location = Location.locationFromChunkCoords(new net.minecraft.util.ChunkCoordinates(1, 2, 3), null);
        Assert.assertEquals(at(1, 2, 3), location);
    }

    @Test
    public void copyConstructorKeepsCoordinates() {
        Assert.assertEquals(at(1, 2, 3), new Location(at(1, 2, 3)));
    }

    @Test
    public void getPlayersReturnsAnEmptyMapWithoutAWorld() {
        Assert.assertTrue(
            at(0, 0, 0).getPlayers(16)
                .isEmpty());
    }
}
