use crate::{CreatureRenderInstance, RenderError};
use creature_profile::CreatureProfile;
use std::sync::{
    Arc, Mutex,
    atomic::{AtomicU8, Ordering},
};
use std::time::Instant;
use wgpu::util::DeviceExt;

#[repr(C)]
#[derive(Clone, Copy, bytemuck::Pod, bytemuck::Zeroable)]
struct Globals {
    viewport: [f32; 2],
    reach: f32,
    stance: f32,
    thorax: f32,
    abdomen: f32,
    padding: [f32; 2],
}

#[derive(Clone, Debug, Default, serde::Serialize)]
pub struct RenderStats {
    pub active_instances: u32,
    pub upload_bytes: u64,
    pub submitted_draws: u32,
    pub cpu_render_prep_ms: f64,
    pub gpu_ms: Option<f64>,
    pub lod_counts: [u32; 3],
}

pub struct OffscreenTarget {
    pub(crate) texture: wgpu::Texture,
    pub(crate) view: wgpu::TextureView,
    pub width: u32,
    pub height: u32,
}

impl OffscreenTarget {
    pub fn view(&self) -> &wgpu::TextureView {
        &self.view
    }
}

struct TimestampSlot {
    buffer: wgpu::Buffer,
    state: Arc<AtomicU8>,
}
struct GpuTimer {
    queries: wgpu::QuerySet,
    resolve: wgpu::Buffer,
    slots: Vec<TimestampSlot>,
    period: f64,
    last_ms: Option<f64>,
}
impl GpuTimer {
    fn new(device: &wgpu::Device, queue: &wgpu::Queue) -> Option<Self> {
        if !device.features().contains(wgpu::Features::TIMESTAMP_QUERY) {
            return None;
        }
        let slots = (0..3)
            .map(|_| TimestampSlot {
                buffer: device.create_buffer(&wgpu::BufferDescriptor {
                    label: Some("ant timestamp readback"),
                    size: 16,
                    usage: wgpu::BufferUsages::MAP_READ | wgpu::BufferUsages::COPY_DST,
                    mapped_at_creation: false,
                }),
                state: Arc::new(AtomicU8::new(0)),
            })
            .collect();
        Some(Self {
            queries: device.create_query_set(&wgpu::QuerySetDescriptor {
                label: Some("ant GPU duration"),
                ty: wgpu::QueryType::Timestamp,
                count: 2,
            }),
            resolve: device.create_buffer(&wgpu::BufferDescriptor {
                label: Some("timestamp resolve"),
                size: 256,
                usage: wgpu::BufferUsages::QUERY_RESOLVE | wgpu::BufferUsages::COPY_SRC,
                mapped_at_creation: false,
            }),
            slots,
            period: queue.get_timestamp_period() as f64,
            last_ms: None,
        })
    }
    fn collect(&mut self) {
        for s in &self.slots {
            match s.state.load(Ordering::Acquire) {
                2 => {
                    let Ok(data) = s.buffer.slice(..).get_mapped_range() else {
                        s.buffer.unmap();
                        s.state.store(0, Ordering::Release);
                        continue;
                    };
                    let a = u64::from_le_bytes(
                        data[0..8].try_into().expect("fixed mapped timestamp size"),
                    );
                    let b = u64::from_le_bytes(
                        data[8..16].try_into().expect("fixed mapped timestamp size"),
                    );
                    if b >= a {
                        self.last_ms = Some((b - a) as f64 * self.period / 1_000_000.);
                    }
                    drop(data);
                    s.buffer.unmap();
                    s.state.store(0, Ordering::Release);
                }
                3 => {
                    s.buffer.unmap();
                    s.state.store(0, Ordering::Release);
                }
                _ => {}
            }
        }
    }
    fn available(&self) -> Option<usize> {
        self.slots
            .iter()
            .position(|s| s.state.load(Ordering::Acquire) == 0)
    }
    fn resolve_into(&self, encoder: &mut wgpu::CommandEncoder, index: usize) {
        encoder.resolve_query_set(&self.queries, 0..2, &self.resolve, 0);
        encoder.copy_buffer_to_buffer(&self.resolve, 0, &self.slots[index].buffer, 0, 16);
    }
    fn map(&self, index: usize) {
        let slot = &self.slots[index];
        slot.state.store(1, Ordering::Release);
        let state = slot.state.clone();
        slot.buffer
            .slice(..)
            .map_async(wgpu::MapMode::Read, move |r| {
                state.store(if r.is_ok() { 2 } else { 3 }, Ordering::Release)
            });
    }
}

/// One shared GPU device and reusable upload buffers. Native visibility policy lives elsewhere.
pub struct Renderer {
    pub(crate) instance: wgpu::Instance,
    pub(crate) adapter: wgpu::Adapter,
    pub(crate) device: wgpu::Device,
    pub(crate) queue: wgpu::Queue,
    info: wgpu::AdapterInfo,
    error: Arc<Mutex<Option<String>>>,
    layout: wgpu::BindGroupLayout,
    pipeline_layout: wgpu::PipelineLayout,
    shader: wgpu::ShaderModule,
    pipelines: Vec<(wgpu::TextureFormat, wgpu::RenderPipeline)>,
    instances: wgpu::Buffer,
    uniform: wgpu::Buffer,
    binding: wgpu::BindGroup,
    capacity: usize,
    globals: Globals,
    timer: Option<GpuTimer>,
}
impl Renderer {
    pub async fn new_headless(profile: &CreatureProfile) -> Result<Self, RenderError> {
        let mut desc = wgpu::InstanceDescriptor::new_without_display_handle();
        desc.backends = if cfg!(target_os = "macos") {
            wgpu::Backends::METAL
        } else if cfg!(target_os = "windows") {
            wgpu::Backends::DX12
        } else {
            wgpu::Backends::VULKAN
        };
        // HWND flip swapchains are opaque. Select the upstream, GPU-only DirectComposition path.
        desc.backend_options.dx12.presentation_system = wgpu::Dx12SwapchainKind::DxgiFromVisual;
        let instance = wgpu::Instance::new(desc);
        let adapter = instance
            .request_adapter(&wgpu::RequestAdapterOptions {
                power_preference: wgpu::PowerPreference::LowPower,
                force_fallback_adapter: false,
                compatible_surface: None,
                ..Default::default()
            })
            .await
            .map_err(|e| RenderError::Backend(e.to_string()))?;
        let info = adapter.get_info();
        let features = adapter.features() & wgpu::Features::TIMESTAMP_QUERY;
        let (device, queue) = adapter
            .request_device(&wgpu::DeviceDescriptor {
                label: Some("Insect Realism"),
                required_features: features,
                required_limits: wgpu::Limits::default().using_resolution(adapter.limits()),
                ..Default::default()
            })
            .await
            .map_err(|e| RenderError::Backend(e.to_string()))?;
        let error = Arc::new(Mutex::new(None));
        let errors = error.clone();
        device.on_uncaptured_error(Arc::new(move |e: wgpu::Error| {
            if let Ok(mut state) = errors.lock()
                && state.is_none()
            {
                *state = Some(e.to_string());
            }
        }));
        let errors = error.clone();
        device.set_device_lost_callback(move |reason, message| {
            // A deliberately destroyed device is just as unusable as an unexpected
            // loss. Do not suppress Destroyed: fault-injection and explicit teardown
            // must never leave the surviving Renderer reporting successful frames.
            if let Ok(mut state) = errors.lock() {
                *state = Some(format!("{reason:?}: {message}"));
            }
        });
        let scope = device.push_error_scope(wgpu::ErrorFilter::Validation);
        let shader = device.create_shader_module(wgpu::ShaderModuleDescriptor {
            label: Some("procedural ant"),
            source: wgpu::ShaderSource::Wgsl(include_str!("../shaders/ant.wgsl").into()),
        });
        let layout = device.create_bind_group_layout(&wgpu::BindGroupLayoutDescriptor {
            label: Some("ant instance layout"),
            entries: &[
                wgpu::BindGroupLayoutEntry {
                    binding: 0,
                    visibility: wgpu::ShaderStages::VERTEX_FRAGMENT,
                    ty: wgpu::BindingType::Buffer {
                        ty: wgpu::BufferBindingType::Storage { read_only: true },
                        has_dynamic_offset: false,
                        min_binding_size: None,
                    },
                    count: None,
                },
                wgpu::BindGroupLayoutEntry {
                    binding: 1,
                    visibility: wgpu::ShaderStages::VERTEX_FRAGMENT,
                    ty: wgpu::BindingType::Buffer {
                        ty: wgpu::BufferBindingType::Uniform,
                        has_dynamic_offset: false,
                        min_binding_size: None,
                    },
                    count: None,
                },
            ],
        });
        let pipeline_layout = device.create_pipeline_layout(&wgpu::PipelineLayoutDescriptor {
            label: Some("ant pipeline layout"),
            bind_group_layouts: &[Some(&layout)],
            immediate_size: 0,
        });
        let get = |key| {
            profile
                .range(key)
                .map(|r| (r.min + r.max) * 0.5)
                .map_err(|e| RenderError::Backend(e.to_string()))
        };
        let globals = Globals {
            viewport: [1., 1.],
            reach: get("leg_reach_body_fraction")?,
            stance: get("gait_stance_fraction")?,
            thorax: get("thorax_length_fraction")?,
            abdomen: get("abdomen_length_fraction")?,
            padding: [crate::STANDARD_THRESHOLD, crate::DETAIL_THRESHOLD],
        };
        let uniform = device.create_buffer_init(&wgpu::util::BufferInitDescriptor {
            label: Some("ant globals"),
            contents: bytemuck::bytes_of(&globals),
            usage: wgpu::BufferUsages::UNIFORM | wgpu::BufferUsages::COPY_DST,
        });
        let capacity = 1024;
        let instances = Self::instance_buffer(&device, capacity);
        let binding = Self::binding(&device, &layout, &instances, &uniform);
        let timer = GpuTimer::new(&device, &queue);
        if let Some(e) = scope.pop().await {
            return Err(RenderError::Backend(e.to_string()));
        }
        let mut out = Self {
            instance,
            adapter,
            device,
            queue,
            info,
            error,
            layout,
            pipeline_layout,
            shader,
            pipelines: Vec::with_capacity(4),
            instances,
            uniform,
            binding,
            capacity,
            globals,
            timer,
        };
        out.ensure_pipeline(wgpu::TextureFormat::Rgba8Unorm)?;
        out.check_health()?;
        Ok(out)
    }
    pub fn device(&self) -> &wgpu::Device {
        &self.device
    }
    pub fn queue(&self) -> &wgpu::Queue {
        &self.queue
    }
    pub fn instance(&self) -> &wgpu::Instance {
        &self.instance
    }
    pub fn adapter(&self) -> &wgpu::Adapter {
        &self.adapter
    }
    pub fn adapter_info(&self) -> &wgpu::AdapterInfo {
        &self.info
    }
    pub fn capacity(&self) -> usize {
        self.capacity
    }
    pub fn last_gpu_ms(&self) -> Option<f64> {
        self.timer.as_ref().and_then(|t| t.last_ms)
    }
    pub fn check_health(&self) -> Result<(), RenderError> {
        let guard = self
            .error
            .lock()
            .map_err(|_| RenderError::DeviceLost("error handler lock poisoned".into()))?;
        match guard.as_ref() {
            Some(e) => Err(RenderError::DeviceLost(e.clone())),
            None => Ok(()),
        }
    }
    pub fn poll(&mut self) -> Result<(), RenderError> {
        self.device
            .poll(wgpu::PollType::Poll)
            .map_err(|e| RenderError::DeviceLost(e.to_string()))?;
        if let Some(timer) = &mut self.timer {
            timer.collect();
        }
        self.check_health()
    }
    fn instance_buffer(device: &wgpu::Device, capacity: usize) -> wgpu::Buffer {
        device.create_buffer(&wgpu::BufferDescriptor {
            label: Some("ant instances"),
            size: (capacity * std::mem::size_of::<CreatureRenderInstance>()) as u64,
            usage: wgpu::BufferUsages::STORAGE | wgpu::BufferUsages::COPY_DST,
            mapped_at_creation: false,
        })
    }
    fn binding(
        device: &wgpu::Device,
        layout: &wgpu::BindGroupLayout,
        instances: &wgpu::Buffer,
        uniform: &wgpu::Buffer,
    ) -> wgpu::BindGroup {
        device.create_bind_group(&wgpu::BindGroupDescriptor {
            label: Some("ant bindings"),
            layout,
            entries: &[
                wgpu::BindGroupEntry {
                    binding: 0,
                    resource: instances.as_entire_binding(),
                },
                wgpu::BindGroupEntry {
                    binding: 1,
                    resource: uniform.as_entire_binding(),
                },
            ],
        })
    }
    fn ensure_capacity(&mut self, count: usize) -> Result<(), RenderError> {
        if count > 100_000 {
            return Err(RenderError::Invalid("GPU population cap"));
        }
        if count > self.capacity {
            self.capacity = count.next_power_of_two();
            self.instances = Self::instance_buffer(&self.device, self.capacity);
            self.binding =
                Self::binding(&self.device, &self.layout, &self.instances, &self.uniform);
        }
        Ok(())
    }
    pub(crate) fn ensure_pipeline(
        &mut self,
        format: wgpu::TextureFormat,
    ) -> Result<usize, RenderError> {
        if let Some(i) = self.pipelines.iter().position(|(f, _)| *f == format) {
            return Ok(i);
        }
        let scope = self.device.push_error_scope(wgpu::ErrorFilter::Validation);
        let pipeline = self
            .device
            .create_render_pipeline(&wgpu::RenderPipelineDescriptor {
                label: Some("instanced procedural ant"),
                layout: Some(&self.pipeline_layout),
                vertex: wgpu::VertexState {
                    module: &self.shader,
                    entry_point: Some("vs_main"),
                    compilation_options: Default::default(),
                    buffers: &[],
                },
                fragment: Some(wgpu::FragmentState {
                    module: &self.shader,
                    entry_point: Some("fs_main"),
                    compilation_options: Default::default(),
                    targets: &[Some(wgpu::ColorTargetState {
                        format,
                        blend: Some(wgpu::BlendState::PREMULTIPLIED_ALPHA_BLENDING),
                        write_mask: wgpu::ColorWrites::ALL,
                    })],
                }),
                primitive: wgpu::PrimitiveState {
                    cull_mode: None,
                    ..Default::default()
                },
                depth_stencil: None,
                multisample: Default::default(),
                multiview_mask: None,
                cache: None,
            });
        if let Some(e) = pollster::block_on(scope.pop()) {
            return Err(RenderError::Backend(e.to_string()));
        }
        self.pipelines.push((format, pipeline));
        Ok(self.pipelines.len() - 1)
    }
    pub fn offscreen(&self, width: u32, height: u32) -> Result<OffscreenTarget, RenderError> {
        self.validate_size(width, height)?;
        let texture = self.device.create_texture(&wgpu::TextureDescriptor {
            label: Some("ant offscreen validation"),
            size: wgpu::Extent3d {
                width,
                height,
                depth_or_array_layers: 1,
            },
            mip_level_count: 1,
            sample_count: 1,
            dimension: wgpu::TextureDimension::D2,
            format: wgpu::TextureFormat::Rgba8Unorm,
            usage: wgpu::TextureUsages::RENDER_ATTACHMENT | wgpu::TextureUsages::COPY_SRC,
            view_formats: &[],
        });
        let view = texture.create_view(&Default::default());
        Ok(OffscreenTarget {
            texture,
            view,
            width,
            height,
        })
    }
    pub(crate) fn validate_size(&self, width: u32, height: u32) -> Result<(), RenderError> {
        let max = self.device.limits().max_texture_dimension_2d;
        if width == 0
            || height == 0
            || width > max
            || height > max
            || u64::from(width) * u64::from(height) > 64_000_000
        {
            return Err(RenderError::Invalid("surface dimensions"));
        }
        Ok(())
    }
    pub fn render_offscreen(
        &mut self,
        target: &OffscreenTarget,
        frame: &[CreatureRenderInstance],
    ) -> Result<RenderStats, RenderError> {
        self.render_view(
            &target.view,
            wgpu::TextureFormat::Rgba8Unorm,
            target.width,
            target.height,
            frame,
        )
    }
    pub(crate) fn render_view(
        &mut self,
        view: &wgpu::TextureView,
        format: wgpu::TextureFormat,
        width: u32,
        height: u32,
        frame: &[CreatureRenderInstance],
    ) -> Result<RenderStats, RenderError> {
        self.poll()?;
        self.validate_size(width, height)?;
        let start = Instant::now();
        if frame.iter().any(|f| !f.validate()) {
            return Err(RenderError::Invalid("GPU instance"));
        }
        self.ensure_capacity(frame.len())?;
        let pipeline = self.ensure_pipeline(format)?;
        self.globals.viewport = [width as f32, height as f32];
        self.queue
            .write_buffer(&self.uniform, 0, bytemuck::bytes_of(&self.globals));
        if !frame.is_empty() {
            self.queue
                .write_buffer(&self.instances, 0, bytemuck::cast_slice(frame));
        }
        let timing = self.timer.as_ref().and_then(|t| t.available());
        let mut encoder = self
            .device
            .create_command_encoder(&wgpu::CommandEncoderDescriptor {
                label: Some("ant frame"),
            });
        {
            let timestamps =
                timing
                    .and(self.timer.as_ref())
                    .map(|t| wgpu::RenderPassTimestampWrites {
                        query_set: &t.queries,
                        beginning_of_pass_write_index: Some(0),
                        end_of_pass_write_index: Some(1),
                    });
            let mut pass = encoder.begin_render_pass(&wgpu::RenderPassDescriptor {
                label: Some("ant glass overlay"),
                color_attachments: &[Some(wgpu::RenderPassColorAttachment {
                    view,
                    depth_slice: None,
                    resolve_target: None,
                    ops: wgpu::Operations {
                        load: wgpu::LoadOp::Clear(wgpu::Color::TRANSPARENT),
                        store: wgpu::StoreOp::Store,
                    },
                })],
                timestamp_writes: timestamps,
                ..Default::default()
            });
            if !frame.is_empty() {
                pass.set_pipeline(&self.pipelines[pipeline].1);
                pass.set_bind_group(0, &self.binding, &[]);
                pass.draw(0..6, 0..frame.len() as u32);
            }
        }
        if let (Some(timer), Some(index)) = (&self.timer, timing) {
            timer.resolve_into(&mut encoder, index);
        }
        self.queue.submit([encoder.finish()]);
        if let (Some(timer), Some(index)) = (&self.timer, timing) {
            timer.map(index);
        }
        let mut lod_counts = [0; 3];
        for f in frame {
            lod_counts[f.lod as usize] += 1;
        }
        self.check_health()?;
        Ok(RenderStats {
            active_instances: frame.len() as u32,
            upload_bytes: std::mem::size_of_val(frame) as u64,
            submitted_draws: u32::from(!frame.is_empty()),
            cpu_render_prep_ms: start.elapsed().as_secs_f64() * 1000.,
            gpu_ms: self.last_gpu_ms(),
            lod_counts,
        })
    }
    /// Synchronous readback is for developer validation only. Live overlays never read screen pixels.
    pub fn read_rgba(&mut self, target: &OffscreenTarget) -> Result<Vec<u8>, RenderError> {
        let row = (target.width * 4).div_ceil(wgpu::COPY_BYTES_PER_ROW_ALIGNMENT)
            * wgpu::COPY_BYTES_PER_ROW_ALIGNMENT;
        let buffer = self.device.create_buffer(&wgpu::BufferDescriptor {
            label: Some("validation readback"),
            size: u64::from(row) * u64::from(target.height),
            usage: wgpu::BufferUsages::COPY_DST | wgpu::BufferUsages::MAP_READ,
            mapped_at_creation: false,
        });
        let mut encoder = self.device.create_command_encoder(&Default::default());
        encoder.copy_texture_to_buffer(
            wgpu::TexelCopyTextureInfo {
                texture: &target.texture,
                mip_level: 0,
                origin: wgpu::Origin3d::ZERO,
                aspect: wgpu::TextureAspect::All,
            },
            wgpu::TexelCopyBufferInfo {
                buffer: &buffer,
                layout: wgpu::TexelCopyBufferLayout {
                    offset: 0,
                    bytes_per_row: Some(row),
                    rows_per_image: Some(target.height),
                },
            },
            wgpu::Extent3d {
                width: target.width,
                height: target.height,
                depth_or_array_layers: 1,
            },
        );
        self.queue.submit([encoder.finish()]);
        let (tx, rx) = std::sync::mpsc::sync_channel(1);
        buffer.slice(..).map_async(wgpu::MapMode::Read, move |r| {
            let _ = tx.send(r);
        });
        self.device
            .poll(wgpu::PollType::Wait {
                submission_index: None,
                timeout: Some(std::time::Duration::from_secs(30)),
            })
            .map_err(|e| RenderError::Backend(e.to_string()))?;
        rx.recv()
            .map_err(|e| RenderError::Backend(e.to_string()))?
            .map_err(|e| RenderError::Backend(e.to_string()))?;
        let mapped = buffer
            .slice(..)
            .get_mapped_range()
            .map_err(|e| RenderError::Backend(e.to_string()))?;
        let mut bytes = Vec::with_capacity(target.width as usize * target.height as usize * 4);
        for line in mapped.chunks_exact(row as usize) {
            bytes.extend_from_slice(&line[..target.width as usize * 4]);
        }
        drop(mapped);
        buffer.unmap();
        if let Some(timer) = &mut self.timer {
            timer.collect();
        }
        self.check_health()?;
        Ok(bytes)
    }
}
