package dev.lilkuzco.cruisemissileprogram.command;

import com.mojang.serialization.MapCodec;
import dev.lilkuzco.cruisemissileprogram.CruiseSounds;
import dev.lilkuzco.cruisemissileprogram.net.CruiseNet;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The command centre.
 *
 * <p>Opening it is how a console is claimed: the first player to use an unowned console becomes
 * its commander, the same way warfront's display wall binds its owner on first interaction. That
 * makes a freshly placed console immediately useful rather than something you have to configure
 * before it will talk to you, and every other rank is granted from there.
 */
public class FireControlBlock extends BaseEntityBlock {

	public static final MapCodec<FireControlBlock> CODEC = simpleCodec(FireControlBlock::new);

	/**
	 * Which way the readout faces.
	 *
	 * <p>Placed facing the player, like a furnace. A console has a working face and five that are
	 * just armour; giving it a facing is what lets the screen be on one of them.
	 */
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

	public FireControlBlock(Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING,
				context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FireControlBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		if (level.isClientSide()) return InteractionResult.SUCCESS;
		if (!(player instanceof ServerPlayer server)) return InteractionResult.CONSUME;
		if (!(level.getBlockEntity(pos) instanceof FireControlBlockEntity console)) {
			return InteractionResult.CONSUME;
		}
		console.claimIfUnowned(server);
		CommandRank rank = console.rankOf(server.getUUID());
		if (!rank.canView()) {
			server.sendSystemMessage(
					Component.translatable("cruise_missile_program.message.no_authority"));
			level.playSound(null, pos, CruiseSounds.DENIED, SoundSource.BLOCKS, 0.7F, 1.0F);
			return InteractionResult.CONSUME;
		}
		level.playSound(null, pos, CruiseSounds.CONSOLE, SoundSource.BLOCKS, 0.7F, 1.0F);
		CruiseNet.sendConsole(server, pos, true);
		return InteractionResult.CONSUME;
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
			boolean moving) {
		if (level.getBlockEntity(pos) instanceof FireControlBlockEntity console) {
			console.onRemoved(level);
		}
		super.affectNeighborsAfterRemoval(state, level, pos, moving);
	}
}
