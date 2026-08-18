package dev.lilkuzco.cruisemissileprogram;

import dev.lilkuzco.cruisemissileprogram.warhead.WarheadRegistry;
import dev.lilkuzco.cruisemissileprogram.warhead.WarheadSpec;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.Optional;
import java.util.function.Consumer;

/** The airframe, and the one warhead this mod ships so it is playable standing alone. */
public final class CruiseItems {

	/**
	 * The cruise airframe: slim, winged, and useless until something is socketed into it.
	 *
	 * <p>A bare body will not load into a tube. That is deliberate — an unarmed missile that flies
	 * a perfect profile and does nothing on arrival is a very confusing bug report, so the refusal
	 * happens at the loading slot where the player can see it.
	 */
	public static class CruiseBodyItem extends Item {

		public CruiseBodyItem(Properties properties) {
			super(properties);
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext context,
				TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
			Optional<Identifier> socketed = socketedWarhead(stack);
			if (socketed.isEmpty()) {
				lines.accept(Component.translatable("cruise_missile_program.tooltip.body.empty")
						.withStyle(ChatFormatting.DARK_GRAY));
				return;
			}
			lines.accept(Component.translatable("cruise_missile_program.tooltip.body.warhead",
					Component.translatable(socketed.get().toLanguageKey("item")))
					.withStyle(ChatFormatting.GRAY));
		}
	}

	/** The airframe. */
	public static final Item CRUISE_MISSILE_BODY = register("cruise_missile_body",
			CruiseBodyItem::new, new Item.Properties().stacksTo(4));

	/**
	 * A plain high-explosive warhead, tier 1.
	 *
	 * <p>Shipping exactly one is the point: it makes the mod complete on its own without
	 * pre-empting the ballistic missile mod's tier ladder, which will arrive through the
	 * {@code #cruise_missile_program:warheads} tag rather than through a dependency.
	 */
	public static final Item CONVENTIONAL_WARHEAD = register("conventional_warhead",
			Item::new, new Item.Properties().stacksTo(16));

	/** The warhead socketed into a body stack, if any. */
	public static Optional<Identifier> socketedWarhead(ItemStack stack) {
		return Optional.ofNullable(stack.get(CruiseComponents.WARHEAD));
	}

	/** A body with this warhead socketed into it. Does not validate — callers check the tier. */
	public static ItemStack socket(ItemStack body, ItemStack warhead) {
		ItemStack out = body.copyWithCount(1);
		out.set(CruiseComponents.WARHEAD, BuiltInRegistries.ITEM.getKey(warhead.getItem()));
		return out;
	}

	/** Whether this stack is an assembled round: a cruise body with an accepted warhead in it. */
	public static boolean isLoadedRound(ItemStack stack) {
		if (!stack.is(CRUISE_MISSILE_BODY)) return false;
		Optional<Identifier> warhead = socketedWarhead(stack);
		if (warhead.isEmpty()) return false;
		return specOf(stack).tier() <= WarheadRegistry.MAX_CRUISE_TIER;
	}

	/** The warhead numbers for an assembled round. */
	public static WarheadSpec specOf(ItemStack round) {
		Identifier id = socketedWarhead(round).orElse(null);
		if (id == null) return WarheadSpec.fallback(Identifier.withDefaultNamespace("air"));
		return BuiltInRegistries.ITEM.getOptional(id)
				.map(item -> WarheadRegistry.specOf(new ItemStack(item)))
				.orElseGet(() -> WarheadSpec.fallback(id));
	}

	private static Item register(String name, java.util.function.Function<Item.Properties, Item> factory,
			Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, CruiseMissileProgram.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(out -> {
			out.insertAfter(Items.FIREWORK_ROCKET, CRUISE_MISSILE_BODY);
			out.insertAfter(CRUISE_MISSILE_BODY, CONVENTIONAL_WARHEAD);
		});
	}

	private CruiseItems() {}
}
