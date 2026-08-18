package dev.lilkuzco.cruisemissileprogram;

import dev.lilkuzco.cruisemissileprogram.command.FireControlBlockEntity;
import dev.lilkuzco.cruisemissileprogram.launcher.LaunchTubeBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;

public final class CruiseBlockEntities {

	public static final BlockEntityType<FireControlBlockEntity> FIRE_CONTROL =
			Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
					CruiseMissileProgram.id("fire_control_console"),
					new BlockEntityType<>(FireControlBlockEntity::new,
							Set.of(CruiseBlocks.FIRE_CONTROL_CONSOLE)));

	public static final BlockEntityType<LaunchTubeBlockEntity> LAUNCH_TUBE =
			Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
					CruiseMissileProgram.id("launch_tube"),
					new BlockEntityType<>(LaunchTubeBlockEntity::new,
							Set.of(CruiseBlocks.LAUNCH_TUBE)));

	public static void register() {}

	private CruiseBlockEntities() {}
}
