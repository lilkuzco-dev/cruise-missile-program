package dev.lilkuzco.cruisemissileprogram.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.lilkuzco.cruisemissileprogram.CruiseMissileProgram;
import dev.lilkuzco.cruisemissileprogram.missile.CruiseMissileEntity;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;

/**
 * Draws the cruise missile.
 *
 * <p><b>Registering this at all is the point.</b> An unregistered entity renderer is not a missing
 * feature in this codebase's experience — {@code EntityRenderDispatcher} returns null for an
 * unregistered type and the render thread dereferences it, so the first missile a player launched
 * would hard-crash their client while the server logged a flawless flight. Cosmos shipped exactly
 * that once, and then shipped the opposite: a renderer that drew nothing, which is a whole release
 * of invisible rocket. Both failures are silent on the server, which is why this one is checked by
 * the render battery rather than by reasoning.
 *
 * <p>26.2 splits rendering: {@link #extractRenderState} reads the entity, {@link #submit} draws
 * with no access to the world. Anything the draw needs is copied across in between.
 */
public class CruiseMissileRenderer
		extends EntityRenderer<CruiseMissileEntity, CruiseMissileRenderer.State> {

	private static final Identifier TEXTURE =
			CruiseMissileProgram.id("textures/entity/cruise_missile.png");

	private final Model.Simple model;

	public CruiseMissileRenderer(EntityRendererProvider.Context context) {
		super(context);
		ModelPart root = context.bakeLayer(CruiseMissileModel.LAYER);
		this.model = new Model.Simple(root, RenderTypes::entitySolid);
		this.shadowRadius = 0.5F;
	}

	public static class State extends EntityRenderState {
		public float yaw;
		public float pitch;
		public float roll;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CruiseMissileEntity missile, State state, float partialTick) {
		super.extractRenderState(missile, state, partialTick);
		// Interpolated, not snapped. The body is integrated once per tick and the entity is moved
		// to match, so at 60fps an un-interpolated missile visibly stutters along its own path.
		state.yaw = Mth.rotLerp(partialTick, missile.yRotO, missile.getYRot());
		state.pitch = Mth.lerp(partialTick, missile.xRotO, missile.getXRot());
		state.roll = missile.roll();
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		RenderType renderType = RenderTypes.entitySolid(TEXTURE);

		poseStack.pushPose();
		// Yaw first, then pitch, then roll — the order an airframe actually rotates in. Doing
		// pitch before yaw makes a missile in a climbing turn corkscrew.
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
		poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
		poseStack.mulPose(Axis.ZP.rotationDegrees(state.roll));

		collector.submitModel(model, Unit.INSTANCE, poseStack, renderType, state.lightCoords,
				OverlayTexture.NO_OVERLAY, -1, null);
		poseStack.popPose();

		super.submit(state, poseStack, collector, camera);
	}
}
