package com.ronec.voicemagic.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ronec.voicemagic.entity.ArcaneBoltEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import com.mojang.math.Axis;
import org.joml.Vector3f;

/** A white translucent double-pointed crystal, oriented along its travel direction and rolling in flight. */
public final class ArcaneBoltRenderer extends EntityRenderer<ArcaneBoltEntity> {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("voicemagic", "textures/entity/white.png");
    private static final float[] FRONT = {0, 0, 0.48F};
    private static final float[] REAR  = {0, 0,-0.28F};
    private static final float[][] RING = {
            {0,0.19F,0}, {0.19F,0,0}, {0,-0.19F,0}, {-0.19F,0,0}
    };

    public ArcaneBoltRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(ArcaneBoltEntity entity) { return WHITE; }

    @Override public void render(ArcaneBoltEntity entity, float yaw, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int light) {
        poses.pushPose();
        Vec3 velocity = entity.getDeltaMovement();
        if (velocity.lengthSqr() > 0.0001) {
            double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            poses.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(Math.atan2(velocity.x, velocity.z))));
            poses.mulPose(Axis.XP.rotationDegrees((float) -Math.toDegrees(Math.atan2(velocity.y, horizontal))));
        }
        poses.mulPose(Axis.ZP.rotationDegrees((entity.tickCount + partialTick) * 9.0F));
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        for (int i = 0; i < 4; i++) {
            int next = (i + 1) % 4;
            triangle(vertices, poses, FRONT, RING[i], RING[next], 144);
            triangle(vertices, poses, REAR, RING[next], RING[i], 112);
        }
        poses.popPose();
        super.render(entity, yaw, partialTick, poses, buffers, light);
    }

    private static void triangle(VertexConsumer vertices, PoseStack poses, float[] a, float[] b, float[] c, int alpha) {
        Vector3f e1 = new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2]);
        Vector3f e2 = new Vector3f(c[0] - a[0], c[1] - a[1], c[2] - a[2]);
        Vector3f normal = e1.cross(e2).normalize();
        point(vertices, poses, a, 0.5F, 0, normal, alpha);
        point(vertices, poses, b, 0, 1, normal, alpha);
        point(vertices, poses, c, 1, 1, normal, alpha);
    }
    private static void point(VertexConsumer vertices, PoseStack poses, float[] xyz, float u, float v,
                              Vector3f normal, int alpha) {
        vertices.addVertex(poses.last().pose(), xyz[0], xyz[1], xyz[2])
                .setColor(245, 252, 255, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(poses.last(), normal.x(), normal.y(), normal.z());
    }
}
