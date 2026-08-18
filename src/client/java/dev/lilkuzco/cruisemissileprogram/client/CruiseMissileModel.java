package dev.lilkuzco.cruisemissileprogram.client;

import dev.lilkuzco.cruisemissileprogram.CruiseMissileProgram;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The cruise missile's geometry: a long slim tube, two wings, a tail cruciform, an intake.
 *
 * <p><b>The silhouette is the point.</b> A ballistic missile is a vertical stack with a nose cone
 * and base fins, read against the sky. This is a horizontal dart with a wingspan, read against
 * the ground it is skimming — and the two must be tellable apart in one glance from a distance,
 * because "which of my missiles is that" is a question a player will ask mid-flight.
 *
 * <p>Built nose-toward-+Z rather than tip-up, because unlike a rocket this thing spends its whole
 * life pointing along its velocity rather than away from the planet. The renderer then only has
 * to apply the body's yaw and pitch.
 *
 * <p><b>The UV layout is computed, not eyeballed.</b> A box of size (sx, sy, sz) claims a
 * {@code 2*(sz+sx)} by {@code sz+sy} region on the sheet, and the first draft of this model put
 * the tail at an offset whose region ran four pixels off the right-hand edge of a 64-wide sheet.
 * Minecraft does not complain about that; it wraps, and a tail fin quietly gets painted with
 * whatever is at the far left. Every offset below was solved against the box it belongs to.
 *
 * <p>Positive y is up and no manual 1/16 scale is applied here — {@code submitModel} does the
 * model-space transform itself. Cosmos and crude_empire both paid for that lesson (a model built
 * to the old flipped convention renders underground; one that also scales by hand renders as a
 * smudge), and neither mistake is repeated here.
 */
public final class CruiseMissileModel {

	public static final ModelLayerLocation LAYER =
			new ModelLayerLocation(CruiseMissileProgram.id("cruise_missile"), "main");

	public static final String BODY = "body";
	public static final String NOSE = "nose";
	public static final String INTAKE = "intake";

	private CruiseMissileModel() {}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		// The fuselage: 22 pixels long, 4 across. Slim, which is what separates it on sight from
		// the ballistic body's 8-wide stack.
		PartDefinition body = root.addOrReplaceChild(BODY,
				CubeListBuilder.create()
						.texOffs(0, 0).addBox(-2.0F, -2.0F, -11.0F, 4, 4, 22),
				PartPose.ZERO);

		// An ogive nose, as close as cubes get: a shoulder then a tip.
		body.addOrReplaceChild(NOSE,
				CubeListBuilder.create()
						.texOffs(52, 0).addBox(-1.5F, -1.5F, 11.0F, 3, 3, 3)
						.texOffs(52, 6).addBox(-1.0F, -1.0F, 14.0F, 2, 2, 2),
				PartPose.ZERO);

		// The ventral intake — the single most cruise-missile-looking feature there is.
		body.addOrReplaceChild(INTAKE,
				CubeListBuilder.create()
						.texOffs(0, 26).addBox(-1.5F, -4.0F, -4.0F, 3, 2, 9),
				PartPose.ZERO);

		// Mid-body wings. Long span, thin chord: this is what makes it read as something that
		// flies level rather than something that falls.
		root.addOrReplaceChild("wing_left",
				CubeListBuilder.create()
						.texOffs(24, 26).addBox(2.0F, -0.5F, -4.0F, 11, 1, 7),
				PartPose.ZERO);
		root.addOrReplaceChild("wing_right",
				CubeListBuilder.create()
						.texOffs(24, 26).mirror().addBox(-13.0F, -0.5F, -4.0F, 11, 1, 7),
				PartPose.ZERO);

		// Tail cruciform: horizontal stabilisers plus a fin, at the very back.
		root.addOrReplaceChild("tail_horizontal",
				CubeListBuilder.create()
						.texOffs(0, 38).addBox(-6.0F, -0.5F, -11.0F, 12, 1, 4),
				PartPose.ZERO);
		root.addOrReplaceChild("tail_vertical",
				CubeListBuilder.create()
						.texOffs(32, 38).addBox(-0.5F, 0.0F, -11.0F, 1, 5, 4),
				PartPose.ZERO);

		return LayerDefinition.create(mesh, 64, 64);
	}
}
