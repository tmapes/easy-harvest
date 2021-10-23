package info.mapes.easyharvest;

import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootContext;
import net.minecraft.loot.LootParameters;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;

@Mod("easyharvest")
public class EasyHarvest {
    private static final Logger LOGGER = LogManager.getLogger();

    public EasyHarvest() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    private static IGrowable getGrowable(Block block) {

        // Anything that grows into a block (Melon, Pumpkin) do not handle
        if (block instanceof StemBlock || block instanceof AttachedStemBlock) {
            return null;
        }

        if (block instanceof IGrowable) {
            return (IGrowable) block;
        }

        // we also want to be able to harvest netherwart, even though it's not a typical crop
        if (block instanceof NetherWartBlock) {
            return new NetherWartGrowable();
        }

        return null;
    }

    @SubscribeEvent
    public void onPlayerRightClick(RightClickBlock event) {
        PlayerEntity player = event.getPlayer();
        BlockPos pos = event.getPos();
        World world = player.level;
        BlockState state = world.getBlockState(pos);
        Block blockClicked = state.getBlock();

        IGrowable growable = getGrowable(blockClicked);

        if (growable == null) {
            return;
        } else if (growable.isValidBonemealTarget(world, pos, state, world.isClientSide)) {
            return;
        } else if (world.isClientSide || !(world instanceof ServerWorld)) {
            return;
        }

        LootContext.Builder context = new LootContext.Builder((ServerWorld) world)
                .withParameter(
                        LootParameters.ORIGIN,
                        new Vector3d(pos.getX(), pos.getY(), pos.getZ())
                )
                .withParameter(LootParameters.BLOCK_STATE, state)
                .withParameter(LootParameters.THIS_ENTITY, player)
                .withParameter(LootParameters.TOOL, ItemStack.EMPTY);

        List<ItemStack> drops = state.getDrops(context);
        BlockState newState = blockClicked.defaultBlockState();

        // Make the block face the player to make it look like we actually replanted it
        if (state.getProperties().stream().anyMatch(p -> p.equals(HorizontalBlock.FACING))) {
            newState = newState.setValue(HorizontalBlock.FACING, state.getValue(HorizontalBlock.FACING));
        }

        // Don't actually destroy the current block, just make it a baby again,
        if (state.getProperties().stream().anyMatch(p -> p.equals(CropsBlock.AGE))) {
            newState = state.setValue(CropsBlock.AGE, 0);
        }

        // Commit block updates to the world
        world.setBlockAndUpdate(pos, newState);

        // Drop everything that would've dropped had you left-clicked this block
        for (ItemStack stack : drops) {
            InventoryHelper.dropItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack);
        }

    }

}
