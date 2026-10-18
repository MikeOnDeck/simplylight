package com.flanks255.simplylight.blocks;

import com.flanks255.simplylight.SLBlockReg;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;


public class LampPostItem extends BaseBlockItem {
    public LampPostItem(Block pBlock, Item.Properties properties) {
        super(pBlock, properties);
    }

    public LampPostItem(Block pBlock, Item.Properties properties, SLBlockReg<?, ?> parent) {
        super(pBlock, properties, parent);
    }
}
