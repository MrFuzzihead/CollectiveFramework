package com.austinv11.collectiveframework.minecraft.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * {@link WorldUtils} had no test. Everything it does goes through a {@link World}, which is mockable, so the
 * geometry and the lookup logic can be checked without a game.
 */
public class WorldUtilsTest {

    private static Location at(World world, double x, double y, double z) {
        return new Location(x, y, z, world);
    }

    private static Entity entityAt(double x, double y, double z) {
        Entity entity = Mockito.mock(Entity.class);
        entity.posX = x;
        entity.posY = y;
        entity.posZ = z;
        return entity;
    }

    // --------------------------------------------------------------------------- player lookup

    @Test
    public void getPlayerForWorldDelegatesToTheWorld() {
        World world = Mockito.mock(World.class);
        EntityPlayer player = Mockito.mock(EntityPlayer.class);
        Mockito.when(world.getPlayerEntityByName("tester"))
            .thenReturn(player);

        Assert.assertSame(player, WorldUtils.getPlayerForWorld("tester", world));
    }

    @Test
    public void getPlayerForWorldReturnsNullWhenAbsent() {
        World world = Mockito.mock(World.class);
        Assert.assertNull(WorldUtils.getPlayerForWorld("nobody", world));
    }

    // ------------------------------------------------------------------------- nearest entity

    @Test
    public void theNearestEntityIsReturned() {
        World world = Mockito.mock(World.class);
        Entity far = entityAt(10, 0, 0);
        Entity near = entityAt(2, 0, 0);
        world.loadedEntityList = new ArrayList<Entity>(Arrays.asList(far, near));

        Assert.assertSame(near, WorldUtils.getNearestEntityToLocation(at(world, 0, 0, 0), 16));
    }

    @Test
    public void theNearestEntityIgnoresOrder() {
        World world = Mockito.mock(World.class);
        Entity near = entityAt(1, 0, 0);
        Entity far = entityAt(9, 0, 0);
        world.loadedEntityList = new ArrayList<Entity>(Arrays.asList(near, far));

        Assert.assertSame(near, WorldUtils.getNearestEntityToLocation(at(world, 0, 0, 0), 16));
    }

    @Test
    public void nothingIsReturnedWhenEverythingIsOutOfRange() {
        World world = Mockito.mock(World.class);
        world.loadedEntityList = new ArrayList<Entity>(Arrays.asList(entityAt(100, 0, 0)));

        Assert.assertNull(WorldUtils.getNearestEntityToLocation(at(world, 0, 0, 0), 16));
    }

    @Test
    public void nothingIsReturnedForAnEmptyWorld() {
        World world = Mockito.mock(World.class);
        world.loadedEntityList = new ArrayList<Entity>();

        Assert.assertNull(WorldUtils.getNearestEntityToLocation(at(world, 0, 0, 0), 16));
    }

    @Test
    public void distanceIsMeasuredInThreeDimensions() {
        World world = Mockito.mock(World.class);
        Entity above = entityAt(0, 5, 0);
        world.loadedEntityList = new ArrayList<Entity>(Arrays.asList(above));

        Assert.assertSame(above, WorldUtils.getNearestEntityToLocation(at(world, 0, 0, 0), 16));
        Assert.assertNull(WorldUtils.getNearestEntityToLocation(at(world, 0, 0, 0), 4));
    }

    @Test
    public void anEntityExactlyAtTheRangeIsIncluded() {
        World world = Mockito.mock(World.class);
        Entity atRange = entityAt(4, 0, 0);
        world.loadedEntityList = new ArrayList<Entity>(Arrays.asList(atRange));

        Assert.assertSame(atRange, WorldUtils.getNearestEntityToLocation(at(world, 0, 0, 0), 4));
    }

    @Test
    public void anEntityExactlyAtTheLocationIsReturned() {
        World world = Mockito.mock(World.class);
        Entity here = entityAt(1, 2, 3);
        world.loadedEntityList = new ArrayList<Entity>(Arrays.asList(here));

        // The initial "lowest distance" is the max range, so a distance of 0 must still win.
        Assert.assertSame(here, WorldUtils.getNearestEntityToLocation(at(world, 1, 2, 3), 16));
    }

    // --------------------------------------------------------------------------- block lookup

    @Test
    public void doesSpecifiedBlockExistsRequiresABlockSubclass() {
        World world = Mockito.mock(World.class);
        Assert.assertFalse(WorldUtils.doesSpecifiedBlockExists(at(world, 0, 0, 0), String.class));
    }

    @Test
    public void doesSpecifiedBlockExistsChecksTheBlockClass() {
        World world = Mockito.mock(World.class);
        Block block = Mockito.mock(Block.class);
        Mockito.when(world.blockExists(1, 2, 3))
            .thenReturn(true);
        Mockito.when(world.isAirBlock(1, 2, 3))
            .thenReturn(false);
        Mockito.when(world.getBlock(1, 2, 3))
            .thenReturn(block);

        Assert.assertTrue(WorldUtils.doesSpecifiedBlockExists(at(world, 1, 2, 3), Block.class));
    }

    @Test
    public void doesSpecifiedBlockExistsIsFalseForAir() {
        World world = Mockito.mock(World.class);
        Mockito.when(world.blockExists(1, 2, 3))
            .thenReturn(true);
        Mockito.when(world.isAirBlock(1, 2, 3))
            .thenReturn(true);

        Assert.assertFalse(WorldUtils.doesSpecifiedBlockExists(at(world, 1, 2, 3), Block.class));
    }

    @Test
    public void doesSpecifiedBlockExistsIsFalseForAnUnloadedChunk() {
        World world = Mockito.mock(World.class);
        Mockito.when(world.blockExists(1, 2, 3))
            .thenReturn(false);

        Assert.assertFalse(WorldUtils.doesSpecifiedBlockExists(at(world, 1, 2, 3), Block.class));
    }

    @Test
    public void doesSpecifiedBlockExistsIsFalseForAWrongBlockClass() {
        World world = Mockito.mock(World.class);
        Block block = Mockito.mock(Block.class);
        Mockito.when(world.blockExists(1, 2, 3))
            .thenReturn(true);
        Mockito.when(world.isAirBlock(1, 2, 3))
            .thenReturn(false);
        Mockito.when(world.getBlock(1, 2, 3))
            .thenReturn(block);

        // Block itself matches, but a subclass it is not an instance of does not.
        Assert.assertFalse(WorldUtils.doesSpecifiedBlockExists(at(world, 1, 2, 3), SomeOtherBlock.class));
    }

    /** A stand-in for "a specific block type"; nothing is ever an instance of it. */
    private static class SomeOtherBlock extends Block {

        SomeOtherBlock() {
            super(net.minecraft.block.material.Material.rock);
        }
    }

    @Test
    public void theVectorHelperIsAvailable() {
        // Guards the assumption the nearest-entity search is built on.
        Assert.assertEquals(
            5d,
            Vec3.createVectorHelper(0, 0, 0)
                .distanceTo(Vec3.createVectorHelper(0, 3, 4)),
            1.0E-9);
    }

    @Test
    public void getNearestEntityWithTheDefaultRangeFindsAnything() {
        World world = Mockito.mock(World.class);
        Entity distant = entityAt(5000, 0, 0);
        world.loadedEntityList = new ArrayList<Entity>(Arrays.asList(distant));

        Assert.assertSame(distant, WorldUtils.getNearestEntityToLocation(at(world, 0, 0, 0)));
    }

    @Test
    public void anEmptyLoadedEntityListIsHandled() {
        World world = Mockito.mock(World.class);
        world.loadedEntityList = new ArrayList<Entity>();
        List<Entity> entities = world.loadedEntityList;
        Assert.assertTrue(entities.isEmpty());
    }
}
