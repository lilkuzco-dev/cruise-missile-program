package dev.lilkuzco.cruisemissileprogram.warhead;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.lilkuzco.cruisemissileprogram.CruiseMissileProgram;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The warhead contract, and the reason this mod does not depend on the ballistic missile mod.
 *
 * <p>Cruise missiles need warheads. The obvious move — import the ballistic mod's warhead items
 * and reuse its tier system — would couple two mods' release cycles together and make this one
 * unbuildable until the other shipped. The empire already has a better idiom for this: cosmos
 * declares propellant tags and lets crude_empire fill them, with neither mod naming the other.
 *
 * <p>So: <b>anything in the item tag {@code #cruise_missile_program:warheads} is a warhead</b>,
 * and its numbers come from a datapack file under {@code cruise_warheads/}. This mod ships one
 * warhead of its own so it is playable standing alone, and when the ballistic mod ships its
 * tiered warheads they join the tag and the table with no code change on either side and no
 * version predicate to get wrong.
 */
public final class WarheadRegistry implements SimpleSynchronousResourceReloadListener {

	/** Anything in here can be socketed into a cruise body. Datapacks own the membership. */
	public static final TagKey<Item> WARHEADS =
			TagKey.create(Registries.ITEM, CruiseMissileProgram.id("warheads"));

	private static final Map<Identifier, WarheadSpec> SPECS = new HashMap<>();

	/**
	 * The cruise body's ceiling.
	 *
	 * <p>Three, not four. The split the design wants is precision versus raw power: a cruise
	 * missile goes where a ballistic one cannot — low, around terrain, onto a coordinate somebody
	 * chose from orbit — and a ballistic one carries what a cruise missile cannot. Giving cruise
	 * the top tier as well would make the ballistic missile the strictly worse option, and a
	 * choice nobody would make is not a choice.
	 */
	public static final int MAX_CRUISE_TIER = 3;

	public static void register() {
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new WarheadRegistry());
	}

	@Override
	public Identifier getFabricId() {
		return CruiseMissileProgram.id("warheads");
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		SPECS.clear();
		manager.listResources("cruise_warheads", path -> path.getPath().endsWith(".json"))
				.forEach((path, resource) -> {
					try (InputStreamReader reader = new InputStreamReader(resource.open())) {
						JsonElement json = com.google.gson.JsonParser.parseReader(reader);
						WarheadSpec.CODEC.parse(JsonOps.INSTANCE, json)
								.resultOrPartial(error -> CruiseMissileProgram.LOG.error(
										"warhead spec {} is malformed: {}", path, error))
								.ifPresent(spec -> SPECS.put(spec.item(), spec));
					} catch (Exception e) {
						CruiseMissileProgram.LOG.error("could not read warhead spec {}", path, e);
					}
				});
		CruiseMissileProgram.LOG.info("loaded {} warhead spec(s)", SPECS.size());
	}

	/** Whether this stack may be socketed at all. Tag membership, nothing else. */
	public static boolean isWarhead(ItemStack stack) {
		return !stack.isEmpty() && stack.is(WARHEADS);
	}

	/** The numbers for a stack, falling back to the weakest entry for an undeclared item. */
	public static WarheadSpec specOf(ItemStack stack) {
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		WarheadSpec declared = SPECS.get(id);
		return declared != null ? declared : WarheadSpec.fallback(id);
	}

	/** Whether a cruise body will accept this warhead, tier cap included. */
	public static boolean acceptedByCruiseBody(ItemStack stack) {
		return isWarhead(stack) && specOf(stack).tier() <= MAX_CRUISE_TIER;
	}

	public static List<Identifier> declared() { return List.copyOf(SPECS.keySet()); }

	private WarheadRegistry() {}
}
