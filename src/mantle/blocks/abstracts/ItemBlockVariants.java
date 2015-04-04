package mantle.blocks.abstracts;

import mantle.blocks.util.IBlockWithVariants;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public class ItemBlockVariants extends ItemBlock
{
	private final IBlockWithVariants variantBlock;

	public ItemBlockVariants(Block block)
	{
		super(block);

		this.variantBlock = (IBlockWithVariants) block;
		this.setMaxDamage(0);
		this.setHasSubtypes(true);
	}

	@Override
	public int getMetadata(int damage)
	{
		return damage;
	}

	@Override
	public String getUnlocalizedName(ItemStack stack)
	{
		return super.getUnlocalizedName() + "." + this.variantBlock.getVariantNameFromStack(stack);
	}

}