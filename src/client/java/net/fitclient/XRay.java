package net.fitclient;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/** Line render type with the depth test turned off, so lines show through walls. */
final class XRay {
    private XRay() {}

    private static RenderType lines;

    static RenderType lines() {
        if (lines == null) {
            try {
                RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                        .withLocation("pipeline/fitclient_lines_xray")
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withDepthWrite(false)
                        .build();
                lines = RenderType.create("fitclient_lines_xray", RenderSetup.builder(pipeline).createRenderSetup());
            } catch (Throwable t) {
                t.printStackTrace();
                lines = RenderTypes.lines(); // fallback: normal depth-tested lines
            }
        }
        return lines;
    }
}
