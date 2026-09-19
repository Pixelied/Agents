use rendering::{RenderError, Renderer};
/// Shared by the live settings window and offscreen acceptance scenes.
pub struct GuiPainter {
    painter: egui_wgpu::Renderer,
    srgb_target: bool,
}
impl GuiPainter {
    pub fn new(gpu: &Renderer, format: wgpu::TextureFormat) -> Self {
        Self {
            srgb_target: format.is_srgb(),
            painter: egui_wgpu::Renderer::new(
                gpu.device(),
                format,
                egui_wgpu::RendererOptions::PREDICTABLE,
            ),
        }
    }
    pub fn paint(
        &mut self,
        gpu: &Renderer,
        view: &wgpu::TextureView,
        ctx: &egui::Context,
        output: &mut egui::FullOutput,
        size: [u32; 2],
    ) -> Result<(), RenderError> {
        if let Err(error) = gpu.check_health() {
            output.textures_delta.clear();
            return Err(error);
        }
        for (id, deltas) in output.textures_delta.set.drain() {
            for delta in deltas {
                self.painter
                    .update_texture(gpu.device(), gpu.queue(), id, &delta);
            }
        }
        // Clear uncovered pixels with the active theme, in the attachment's color space.
        // Egui's non-sRGB targets carry encoded channel values; sRGB attachments expect linear.
        let color = ctx.global_style().visuals.panel_fill;
        let rgba = if self.srgb_target {
            egui::Rgba::from(color).to_array()
        } else {
            color.to_array().map(|channel| f32::from(channel) / 255.)
        };
        let background = wgpu::Color {
            r: f64::from(rgba[0]),
            g: f64::from(rgba[1]),
            b: f64::from(rgba[2]),
            a: f64::from(rgba[3]),
        };
        let jobs = ctx.tessellate(std::mem::take(&mut output.shapes), output.pixels_per_point);
        let descriptor = egui_wgpu::ScreenDescriptor {
            size_in_pixels: size,
            pixels_per_point: output.pixels_per_point,
        };
        let mut encoder = gpu
            .device()
            .create_command_encoder(&wgpu::CommandEncoderDescriptor {
                label: Some("settings frame"),
            });
        let callbacks = self.painter.update_buffers(
            gpu.device(),
            gpu.queue(),
            &mut encoder,
            &jobs,
            &descriptor,
        );
        {
            // egui's callback API erases the pass lifetime. The encoder is used again only
            // after this pass has been dropped; no callback retains an encoder/pass reference.
            let mut pass = encoder
                .begin_render_pass(&wgpu::RenderPassDescriptor {
                    label: Some("interactive settings"),
                    color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                        view,
                        resolve_target: None,
                        depth_slice: None,
                        ops: wgpu::Operations {
                            load: wgpu::LoadOp::Clear(background),
                            store: wgpu::StoreOp::Store,
                        },
                    })],
                    ..Default::default()
                })
                .forget_lifetime();
            self.painter.render(&mut pass, &jobs, &descriptor);
        }
        gpu.queue()
            .submit(callbacks.into_iter().chain([encoder.finish()]));
        for id in output.textures_delta.free.drain() {
            self.painter.free_texture(&id);
        }
        gpu.check_health()
    }
}
