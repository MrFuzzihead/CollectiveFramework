package com.austinv11.collectiveframework.minecraft.utils;

import java.util.HashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import com.austinv11.collectiveframework.utils.math.ThreeDimensionalVector;

/**
 * A class which extends upon {@link com.austinv11.collectiveframework.utils.math.ThreeDimensionalVector} in order to
 * provide
 * Minecraft-specific functionality
 */
public class Location extends ThreeDimensionalVector {

    private World world;

    /**
     * Default constructor, missing required world variable
     */
    private Location(double x, double y, double z) {
        super(x, y, z);
    }

    /**
     * Constructs a location object
     *
     * @param x     X coord
     * @param y     Y coord
     * @param z     Z coord
     * @param world The world
     */
    public Location(double x, double y, double z, World world) {
        super(x, y, z);
        this.world = world;
    }

    /**
     * Constructs a location object at an entity's location
     *
     * @param entity The entity
     */
    public Location(Entity entity) {
        this(entity.posX, entity.posY, entity.posZ, entity.worldObj);
    }

    /**
     * Constructs a location object at a tile entity's location
     *
     * @param tileEntity The tile entity
     */
    public Location(TileEntity tileEntity) {
        this(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord, tileEntity.getWorldObj());
    }

    /**
     * Clones a location object
     *
     * @param location The location to clone
     */
    public Location(Location location) {
        this(location.getX(), location.getY(), location.getZ(), location.getWorld());
    }

    /**
     * Converts ChunkCoordinates to a location
     *
     * @param coords The coords
     * @param world  The world
     * @return The location equivalent
     */
    public static Location locationFromChunkCoords(ChunkCoordinates coords, World world) {
        return new Location(coords.posX, coords.posY, coords.posZ, world);
    }

    /**
     * Converts the location into ChunkCoordinates
     *
     * @return The chunk coordinate equivalent
     */
    public ChunkCoordinates chunkCoordsFromLocation() {
        return new ChunkCoordinates(getRoundedX(), getRoundedY(), getRoundedZ());
    }

    /**
     * Gets the world for this object
     *
     * @return The world
     */
    public World getWorld() {
        return world;
    }

    /**
     * Gets the nearby players to this location
     *
     * @param range The range to search
     * @return A map, Key = Username, Value = Range
     */
    public HashMap<String, Double> getPlayers(double range) {
        HashMap<String, Double> map = new HashMap<String, Double>();
        if (world == null) return map;
        for (EntityPlayer player : (Iterable<EntityPlayer>) world.playerEntities) {
            // The distance was computed twice per player, allocating a Location each time.
            double distance = new Location(player).distanceTo(this);
            if (distance <= range) map.put(player.getDisplayName(), distance);
        }
        return map;
    }

    /**
     * Converts this into a chunk coordinates object.
     *
     * @return The chunk coordinates
     * @deprecated Duplicate of {@link #chunkCoordsFromLocation()}; use that instead. Kept as a delegating alias
     *             so existing callers keep working (CODE_REVIEW §4.1).
     */
    @Deprecated
    public ChunkCoordinates toChunkCoordinates() {
        return chunkCoordsFromLocation();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Location)) return false;
        Location location = (Location) other;
        // getWorld().provider was dereferenced directly, so comparing a Location with a null world threw
        // (CODE_REVIEW §2.14).
        return location.getRoundedX() == getRoundedX() && location.getRoundedY() == getRoundedY()
            && location.getRoundedZ() == getRoundedZ()
            && location.dimensionId() == dimensionId();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This returned a constant 0, so every {@link Location} landed in the same hash bucket: any
     * {@code HashMap} keyed by a location degraded to a linked list, which made {@code StructureCreator}'s
     * three maps O(n²) in the number of blocks (CODE_REVIEW §2.14).
     */
    @Override
    public int hashCode() {
        int result = getRoundedX();
        result = 31 * result + getRoundedY();
        result = 31 * result + getRoundedZ();
        result = 31 * result + dimensionId();
        return result;
    }

    /**
     * @return The dimension this location is in, or {@link Integer#MIN_VALUE} if it has no world
     */
    private int dimensionId() {
        if (world == null || world.provider == null) return Integer.MIN_VALUE;
        return world.provider.dimensionId;
    }
}
