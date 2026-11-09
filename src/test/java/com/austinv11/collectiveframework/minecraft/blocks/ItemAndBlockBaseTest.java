package com.austinv11.collectiveframework.minecraft.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.austinv11.collectiveframework.minecraft.items.ItemBase;

/**
 * {@link ItemBase} and {@link BlockBase} are the framework's item/block bases and had no test. Their name
 * formatting is pure string logic, so it needs no game.
 */
public class ItemAndBlockBaseTest {

    private static class TestItem extends ItemBase {

        private final String modId;

        TestItem(String modId) {
            this.modId = modId;
        }

        @Override
        public CreativeTabs getTab() {
            return null;
        }

        @Override
        public String getModId() {
            return modId;
        }
    }

    private static class TestBlock extends BlockBase {

        private final String modId;

        TestBlock(String modId) {
            super(Material.rock);
            this.modId = modId;
        }

        @Override
        public CreativeTabs getTab() {
            return null;
        }

        @Override
        public String getModId() {
            return modId;
        }
    }

    // ------------------------------------------------------------------------------------ ItemBase

    @Test
    public void itemNameIsPrefixedWithTheModId() {
        TestItem item = new TestItem("mymod");
        item.setUnlocalizedName("thing");
        Assert.assertEquals("item.mymod:thing", item.getUnlocalizedName());
    }

    @Test
    public void itemNameFromAStackMatchesThePlainName() {
        TestItem item = new TestItem("mymod");
        item.setUnlocalizedName("thing");
        Assert.assertEquals(item.getUnlocalizedName(), item.getUnlocalizedName(new ItemStack(item)));
    }

    @Test
    public void itemModIdIsLowercased() {
        TestItem item = new TestItem("MyMod");
        item.setUnlocalizedName("thing");
        Assert.assertEquals("item.mymod:thing", item.getUnlocalizedName());
    }

    @Test
    public void itemDefaultsToAStackSizeOfSixtyFour() {
        Assert.assertEquals(64, new TestItem("mymod").getItemStackLimit());
    }

    @Test
    public void itemIconIsRegisteredUnderTheUnwrappedName() {
        TestItem item = new TestItem("mymod");
        item.setUnlocalizedName("thing");
        net.minecraft.client.renderer.texture.IIconRegister register = Mockito
            .mock(net.minecraft.client.renderer.texture.IIconRegister.class);
        Mockito.when(register.registerIcon(Mockito.anyString()))
            .thenReturn(Mockito.mock(net.minecraft.util.IIcon.class));

        item.registerIcons(register);

        Mockito.verify(register)
            .registerIcon("mymod:thing");
    }

    // ----------------------------------------------------------------------------------- BlockBase

    @Test
    public void blockNameIsPrefixedWithTheModId() {
        TestBlock block = new TestBlock("mymod");
        block.setBlockName("thing");
        Assert.assertEquals("tile.mymod:thing", block.getUnlocalizedName());
    }

    @Test
    public void blockModIdIsLowercased() {
        TestBlock block = new TestBlock("MyMod");
        block.setBlockName("thing");
        Assert.assertEquals("tile.mymod:thing", block.getUnlocalizedName());
    }

    @Test
    public void blockDefaultsToRockWithHardnessFour() {
        TestBlock block = new TestBlock("mymod");
        Assert.assertEquals(Material.rock, block.getMaterial());
        Assert.assertEquals(4f, block.getBlockHardness(null, 0, 0, 0), 1.0E-6f);
    }

    @Test
    public void blockIconIsRegisteredUnderTheUnwrappedName() {
        TestBlock block = new TestBlock("mymod");
        block.setBlockName("thing");
        net.minecraft.client.renderer.texture.IIconRegister register = Mockito
            .mock(net.minecraft.client.renderer.texture.IIconRegister.class);
        Mockito.when(register.registerIcon(Mockito.anyString()))
            .thenReturn(Mockito.mock(net.minecraft.util.IIcon.class));

        block.registerBlockIcons(register);

        Mockito.verify(register)
            .registerIcon("mymod:thing");
    }

    /**
     * {@code getUnwrappedUnlocalizedName} is applied twice in {@code BlockBase}, which works by accident: the
     * second call finds no '.' and returns the string unchanged. Pinned so a change is deliberate
     * (CODE_REVIEW §3.16).
     */
    @Test
    public void doubleUnwrappingIsIdempotentForANameWithoutDots() {
        TestBlock block = new TestBlock("mymod");
        block.setBlockName("thing");
        Assert.assertEquals(
            "mymod:thing",
            block.getUnwrappedUnlocalizedName(block.getUnwrappedUnlocalizedName("tile.mymod:thing")));
    }

    @Test
    public void anUnsetNameFallsBackToNull() {
        // Item#getUnlocalizedName() is "item." + the name, and the name is null until setUnlocalizedName runs.
        Assert.assertEquals("item.mymod:null", new TestItem("mymod").getUnlocalizedName());
        Assert.assertEquals("tile.mymod:null", new TestBlock("mymod").getUnlocalizedName());
    }

    @Test
    public void theModIdIsReadFromTheSubclass() {
        Assert.assertEquals("a", new TestItem("a").getModId());
        Assert.assertEquals("b", new TestBlock("b").getModId());
    }

    @Test
    public void blockMaterialCanBeOverridden() {
        BlockBase block = new BlockBase(Material.glass) {

            @Override
            public CreativeTabs getTab() {
                return null;
            }

            @Override
            public String getModId() {
                return "mymod";
            }
        };
        Assert.assertEquals(Material.glass, block.getMaterial());
    }

    @Test
    public void itemAndBlockClassesAreDistinct() {
        Assert.assertFalse(Item.class.isAssignableFrom(TestBlock.class));
        Assert.assertFalse(Block.class.isAssignableFrom(TestItem.class));
    }
}
