package dev.lilkuzco.cruisemissileprogram.command;

import com.mojang.serialization.MapCodec;
import dev.lilkuzco.cruisemissileprogram.net.CruiseNet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
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

	public FireControlBlock(Properties properties) {
		super(properties);
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
			return InteractionResult.CONSUME;
		}
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
