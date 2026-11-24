package gg.vape.vulkanprobe;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.BindGroupLayout;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An isolated integration probe. It submits through Minecraft's GUI render-state
 * queue and deliberately makes no raw OpenGL calls.
 */
public final class VulkanProbeClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("VapeVulkanProbe");
    private static final AtomicBoolean REPORTED = new AtomicBoolean();

    private static final BindGroupLayout MATRICES = BindGroupLayout.builder()
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .build();

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("vape421_vulkan_probe", "pipeline/gui_probe"))
            .withVertexShader(Identifier.fromNamespaceAndPath("vape421_vulkan_probe", "core/gui_probe"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("vape421_vulkan_probe", "core/gui_probe"))
            .withBindGroupLayout(MATRICES)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(Optional.empty())
            .withCull(false)
            .build();

    @Override
    public void onInitializeClient() {
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("vape421_vulkan_probe", "gui_probe"),
                VulkanProbeClient::extract
        );
        LOGGER.info("[VapeVulkanProbe] Fabric entrypoint registered; pipeline={}", PIPELINE.getLocation());
    }

    private static void extract(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
        if (REPORTED.compareAndSet(false, true)) {
            String deviceClass = RenderSystem.getDevice().getClass().getName();
            LOGGER.info("[VapeVulkanProbe] HUD callback reached; active GPU device class={}", deviceClass);
        }

        graphics.fill(PIPELINE, TextureSetup.noTexture(), 8, 8, 252, 42);
        graphics.text(
                Minecraft.getInstance().font,
                "Vape 4.21 - Blaze3D GUI pipeline active",
                16,
                18,
                0xFFFFFFFF,
                false
        );
    }
}
