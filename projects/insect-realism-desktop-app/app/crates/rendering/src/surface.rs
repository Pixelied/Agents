use crate::{CreatureRenderInstance, RenderError, RenderStats, Renderer};
use display_model::PixelSize;
use platform_api::SurfaceSource;
use std::time::Duration;

pub fn transparent_alpha(
    modes: &[wgpu::CompositeAlphaMode],
    backend: wgpu::Backend,
) -> Result<wgpu::CompositeAlphaMode, RenderError> {
    use wgpu::CompositeAlphaMode as A;
    if modes.contains(&A::PreMultiplied) {
        return Ok(A::PreMultiplied);
    }
    // Version-locked wgpu-hal 30.0.1 Metal labels its non-opaque CAMetalLayer
    // PostMultiplied, but CoreAnimation consumes premultiplied layer pixels.
    // Keep this exception local to Metal; never apply it to a real straight-alpha surface.
    // Recheck when upgrading: gfx-rs/wgpu issue 9896 and its reproducer PR 9897.
    if backend == wgpu::Backend::Metal && modes.contains(&A::PostMultiplied) {
        return Ok(A::PostMultiplied);
    }
    Err(RenderError::NoTransparency)
}
pub fn overlay_present_mode(modes: &[wgpu::PresentMode]) -> wgpu::PresentMode {
    // CPU deadlines provide per-display pacing. Avoid waiting on a 60Hz surface
    // while a 144Hz monitor is due. Compositor presentation remains OS-owned.
    for preferred in [
        wgpu::PresentMode::Mailbox,
        wgpu::PresentMode::Immediate,
        wgpu::PresentMode::Fifo,
    ] {
        if modes.contains(&preferred) {
            return preferred;
        }
    }
    wgpu::PresentMode::Fifo
}
#[derive(Clone, Debug)]
pub struct FrameCadence {
    period: Duration,
    next: Duration,
}
impl FrameCadence {
    pub fn new(hz: f32) -> Result<Self, RenderError> {
        if !hz.is_finite() || !(1.0..=1000.0).contains(&hz) {
            return Err(RenderError::Invalid("refresh rate"));
        }
        Ok(Self {
            period: Duration::from_secs_f64(1. / hz as f64),
            next: Duration::ZERO,
        })
    }
    pub fn next_deadline(&self) -> Duration {
        self.next
    }
    pub fn reset(&mut self, now: Duration) {
        self.next = now;
    }
    pub fn take_due(&mut self, now: Duration) -> bool {
        if now < self.next {
            return false;
        }
        let steps = (now - self.next).as_nanos() / self.period.as_nanos() + 1;
        self.next = if steps > u32::MAX as u128 {
            now.saturating_add(self.period)
        } else {
            self.next
                .saturating_add(self.period.saturating_mul(steps as u32))
        };
        true
    }
}

/// Field order is a safety invariant: the GPU surface is dropped BEFORE its native owner.
pub struct SurfaceRenderer {
    surface: wgpu::Surface<'static>,
    _source: SurfaceSource,
    config: wgpu::SurfaceConfiguration,
    pub cadence: FrameCadence,
}
impl SurfaceRenderer {
    pub fn format(&self) -> wgpu::TextureFormat {
        self.config.format
    }
    pub fn size(&self) -> PixelSize {
        PixelSize {
            width: self.config.width,
            height: self.config.height,
        }
    }
    pub fn alpha_mode(&self) -> wgpu::CompositeAlphaMode {
        self.config.alpha_mode
    }
    pub fn present_mode(&self) -> wgpu::PresentMode {
        self.config.present_mode
    }
}
impl Renderer {
    pub fn create_surface(
        &mut self,
        source: SurfaceSource,
        size: PixelSize,
        refresh_hz: f32,
    ) -> Result<SurfaceRenderer, RenderError> {
        self.check_health()?;
        self.validate_size(size.width, size.height)?;
        let cadence = FrameCadence::new(refresh_hz)?;
        // SAFETY: SurfaceSource's constructor requires ownership of both live handles.
        // The returned SurfaceRenderer retains that owner and drops Surface first. Its
        // Rc guard prevents transfer away from the native UI thread. Handles are never
        // replaced/destroyed while this GPU surface exists.
        let surface = unsafe {
            self.instance
                .create_surface_unsafe(wgpu::SurfaceTargetUnsafe::RawHandle {
                    raw_display_handle: Some(source.raw_display()),
                    raw_window_handle: source.raw_window(),
                })
        }
        .map_err(|e| RenderError::Backend(e.to_string()))?;
        let caps = surface.get_capabilities(&self.adapter);
        let alpha_mode = transparent_alpha(&caps.alpha_modes, self.adapter_info().backend)?;
        let format = [
            wgpu::TextureFormat::Bgra8Unorm,
            wgpu::TextureFormat::Rgba8Unorm,
            wgpu::TextureFormat::Bgra8UnormSrgb,
            wgpu::TextureFormat::Rgba8UnormSrgb,
        ]
        .into_iter()
        .find(|f| caps.formats.contains(f))
        .ok_or(RenderError::NoTransparency)?;
        let config = wgpu::SurfaceConfiguration {
            usage: wgpu::TextureUsages::RENDER_ATTACHMENT,
            format,
            color_space: wgpu::SurfaceColorSpace::Auto,
            width: size.width,
            height: size.height,
            present_mode: overlay_present_mode(&caps.present_modes),
            desired_maximum_frame_latency: 1,
            alpha_mode,
            view_formats: vec![],
        };
        self.ensure_pipeline(format)?;
        let scope = self.device.push_error_scope(wgpu::ErrorFilter::Validation);
        surface.configure(&self.device, &config);
        if let Some(e) = pollster::block_on(scope.pop()) {
            return Err(RenderError::Backend(e.to_string()));
        }
        self.check_health()?;
        Ok(SurfaceRenderer {
            surface,
            _source: source,
            config,
            cadence,
        })
    }
    pub fn render_surface(
        &mut self,
        surface: &mut SurfaceRenderer,
        instances: &[CreatureRenderInstance],
    ) -> Result<RenderStats, RenderError> {
        self.poll()?;
        let (frame, reconfigure) = match surface.surface.get_current_texture() {
            wgpu::CurrentSurfaceTexture::Success(frame) => (frame, false),
            wgpu::CurrentSurfaceTexture::Suboptimal(frame) => (frame, true),
            wgpu::CurrentSurfaceTexture::Timeout | wgpu::CurrentSurfaceTexture::Occluded => {
                return Ok(RenderStats::default());
            }
            wgpu::CurrentSurfaceTexture::Outdated => {
                surface.surface.configure(&self.device, &surface.config);
                self.check_health()?;
                return Ok(RenderStats::default());
            }
            wgpu::CurrentSurfaceTexture::Lost => {
                return Err(RenderError::DeviceLost(
                    "native presentation surface lost; recreate behind hidden overlay".into(),
                ));
            }
            wgpu::CurrentSurfaceTexture::Validation => {
                return Err(RenderError::DeviceLost(
                    "native surface validation failure".into(),
                ));
            }
        };
        let view = frame.texture.create_view(&Default::default());
        let stats = self.render_view(
            &view,
            surface.config.format,
            surface.config.width,
            surface.config.height,
            instances,
        )?;
        self.queue.present(frame);
        if reconfigure {
            surface.surface.configure(&self.device, &surface.config);
        }
        self.check_health()?;
        Ok(stats)
    }
}
