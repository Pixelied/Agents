use crate::gui::GuiPainter;
use platform_api::PlatformAdapter;
use rendering::{RenderError, Renderer};
use settings::ui::{SettingsContext, SettingsUi, UiAction};
use std::sync::Arc;
use winit::{dpi::LogicalSize, event::WindowEvent, event_loop::ActiveEventLoop, window::Window};

/// Surface is dropped before the Arc<Window>. This is a normal interactive window,
/// separate from every click-through overlay.
pub struct SettingsWindow {
    surface: wgpu::Surface<'static>,
    pub window: Arc<Window>,
    config: wgpu::SurfaceConfiguration,
    ctx: egui::Context,
    input: egui_winit::State,
    painter: GuiPainter,
    pub occluded: bool,
}
impl SettingsWindow {
    pub fn new(event_loop: &ActiveEventLoop, gpu: &Renderer) -> Result<Self, RenderError> {
        let desired = event_loop
            .primary_monitor()
            .or_else(|| event_loop.available_monitors().next())
            .map(|m| {
                let size = m.size().to_logical::<f64>(m.scale_factor());
                LogicalSize::new(
                    (size.width - 80.0).clamp(500.0, 680.0),
                    (size.height - 120.0).clamp(400.0, 740.0),
                )
            })
            .unwrap_or(LogicalSize::new(680.0, 740.0));
        let window = Arc::new(
            event_loop
                .create_window(
                    Window::default_attributes()
                        .with_title("Insect Realism - Settings")
                        .with_inner_size(desired)
                        .with_min_inner_size(LogicalSize::new(500., 400.))
                        .with_visible(false),
                )
                .map_err(|e| RenderError::Backend(e.to_string()))?,
        );
        let surface = gpu
            .instance()
            .create_surface(window.clone())
            .map_err(|e| RenderError::Backend(e.to_string()))?;
        let caps = surface.get_capabilities(gpu.adapter());
        let format = [
            wgpu::TextureFormat::Bgra8Unorm,
            wgpu::TextureFormat::Rgba8Unorm,
            wgpu::TextureFormat::Bgra8UnormSrgb,
            wgpu::TextureFormat::Rgba8UnormSrgb,
        ]
        .into_iter()
        .find(|f| caps.formats.contains(f))
        .ok_or(RenderError::Invalid("settings surface format"))?;
        let alpha_mode = if caps.alpha_modes.contains(&wgpu::CompositeAlphaMode::Opaque) {
            wgpu::CompositeAlphaMode::Opaque
        } else {
            *caps
                .alpha_modes
                .first()
                .ok_or(RenderError::Invalid("settings alpha modes"))?
        };
        let size = window.inner_size();
        let config = wgpu::SurfaceConfiguration {
            usage: wgpu::TextureUsages::RENDER_ATTACHMENT,
            format,
            color_space: wgpu::SurfaceColorSpace::Auto,
            width: size.width.max(1),
            height: size.height.max(1),
            present_mode: rendering::overlay_present_mode(&caps.present_modes),
            desired_maximum_frame_latency: 1,
            alpha_mode,
            view_formats: vec![],
        };
        surface.configure(gpu.device(), &config);
        let ctx = egui::Context::default();
        ctx.set_visuals(egui::Visuals::light());
        let input = egui_winit::State::new(
            ctx.clone(),
            egui::ViewportId::ROOT,
            event_loop,
            Some(window.scale_factor() as f32),
            window.theme(),
            Some(gpu.device().limits().max_texture_dimension_2d as usize),
        );
        let painter = GuiPainter::new(gpu, format);
        gpu.check_health()?;
        Ok(Self {
            surface,
            window,
            config,
            ctx,
            input,
            painter,
            occluded: false,
        })
    }
    pub fn event(&mut self, event: &WindowEvent) -> bool {
        if let WindowEvent::Occluded(value) = event {
            self.occluded = *value;
        }
        self.input.on_window_event(&self.window, event).repaint
    }
    pub fn show(&self) {
        self.window.set_visible(true);
        self.window.focus_window();
        self.window.request_redraw();
    }
    pub fn resize(&mut self, gpu: &Renderer) -> Result<(), RenderError> {
        let size = self.window.inner_size();
        if size.width == 0 || size.height == 0 {
            return Ok(());
        }
        if size.width > gpu.device().limits().max_texture_dimension_2d
            || size.height > gpu.device().limits().max_texture_dimension_2d
        {
            return Err(RenderError::Invalid(
                "settings window exceeds texture limits",
            ));
        }
        self.config.width = size.width;
        self.config.height = size.height;
        self.surface.configure(gpu.device(), &self.config);
        gpu.check_health()
    }
    pub fn draw(
        &mut self,
        gpu: &Renderer,
        ui: &mut SettingsUi,
        context: &SettingsContext<'_>,
    ) -> Result<Vec<UiAction>, RenderError> {
        let size = self.window.inner_size();
        if self.occluded || size.width == 0 || size.height == 0 {
            return Ok(Vec::new());
        }
        gpu.check_health()?;
        if size.width != self.config.width || size.height != self.config.height {
            self.resize(gpu)?;
        }
        // Acquire first: never consume egui's texture deltas on a skipped surface frame.
        let frame = match self.surface.get_current_texture() {
            wgpu::CurrentSurfaceTexture::Success(f)
            | wgpu::CurrentSurfaceTexture::Suboptimal(f) => f,
            wgpu::CurrentSurfaceTexture::Timeout | wgpu::CurrentSurfaceTexture::Occluded => {
                return Ok(Vec::new());
            }
            wgpu::CurrentSurfaceTexture::Outdated => {
                self.resize(gpu)?;
                return Ok(Vec::new());
            }
            wgpu::CurrentSurfaceTexture::Lost | wgpu::CurrentSurfaceTexture::Validation => {
                return Err(RenderError::DeviceLost(
                    "settings presentation surface".into(),
                ));
            }
        };
        let raw = self.input.take_egui_input(&self.window);
        let mut actions = Vec::new();
        let mut output = self
            .ctx
            .run_ui(raw, |root| actions = ui.draw(root, context));
        self.input
            .handle_platform_output(&self.window, std::mem::take(&mut output.platform_output));
        let view = frame.texture.create_view(&Default::default());
        let result = self.painter.paint(
            gpu,
            &view,
            &self.ctx,
            &mut output,
            [size.width, size.height],
        );
        output.drop_without_applying_deltas();
        result?;
        self.window.pre_present_notify();
        gpu.queue().present(frame);
        gpu.check_health()?;
        Ok(actions)
    }
    pub fn display_id(&self, platform: &dyn PlatformAdapter) -> Option<display_model::DisplayId> {
        platform.window_display(self.window.as_ref()).ok().flatten()
    }
}
impl Drop for SettingsWindow {
    fn drop(&mut self) {
        self.window.set_visible(false);
    }
}
