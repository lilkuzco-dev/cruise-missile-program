package dev.lilkuzco.cruisemissileprogram.launcher;

import com.mojang.serialization.MapCodec;
import dev.lilkuzco.cruisemissileprogram.CruiseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The box launcher. Registers with the command network on placement, deregisters on removal.
 *
 * <p><b>Both hooks are on the block, and that is not an accident.</b> A block entity's
 * {@code setRemoved} also fires on chunk unload, so deregistering there would delete a launcher
 * from its console's roster the moment nobody was standing near it — which for a launcher a
 * kilometre away is the normal case. This repo has learned that lesson three separate times;
 * placement and removal are the two moments that happen exactly once.
 */
public class LaunchTubeBlock extends BaseEntityBlock {

	public static final MapCodec<LaunchTubeBlock> CODEC = simpleCodec(LaunchTubeBlock::new);

	public LaunchTubeBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new LaunchTubeBlockEntity(pos, state);
	}

	/** Opening the tube is loading it. Targeting deliberately lives at the console instead. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		if (level.isClientSide()) return InteractionResult.SUCCESS;
		if (level.getBlockEntity(pos) instanceof LaunchTubeBlockEntity tube) {
			player.openMenu(tube);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous,
			boolean moving) {
		super.onPlace(state, level, pos, previous, moving);
		if (level instanceof ServerLevel server
				&& server.getBlockEntity(pos) instanceof LaunchTubeBlockEntity tube) {
			tube.onPlaced(server);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
			boolean moving) {
		if (level.getBlockEntity(pos) instanceof LaunchTubeBlockEntity tube) {
			tube.onRemoved(level);
			net.minecraft.world.Containers.dropContents(level, pos, tube);
		}
		super.affectNeighborsAfterRemoval(state, level, pos, moving);
	}
}
