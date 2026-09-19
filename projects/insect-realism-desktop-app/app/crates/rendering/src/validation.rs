//! Deterministic developer fixtures. Layouts here deliberately spawn in the middle;
//! normal runtime entry is exclusively controlled by the simulation's edge spawner.
use crate::{CreatureRenderInstance, RenderError, Renderer, select_lod};
use creature_profile::{CreatureProfile, RuntimeProfileBundle};
use sha2::{Digest, Sha256};
use std::path::{Path, PathBuf};

#[derive(Clone, Copy, Debug, PartialEq, Eq, serde::Serialize)]
pub enum ValidationSceneId {
    Ant2mmStatic,
    Ant3mmWalk,
    Ant4mmTurn,
    Population100,
    Population500,
    Population1000,
    HeadingSweep,
    GaitSweep,
    LodSweep,
    AnatomyDiagnostic,
}
impl ValidationSceneId {
    pub fn all() -> &'static [Self] {
        &[
            Self::Ant2mmStatic,
            Self::Ant3mmWalk,
            Self::Ant4mmTurn,
            Self::Population100,
            Self::Population500,
            Self::Population1000,
            Self::HeadingSweep,
            Self::GaitSweep,
            Self::LodSweep,
            Self::AnatomyDiagnostic,
        ]
    }
    pub fn name(self) -> &'static str {
        match self {
            Self::Ant2mmStatic => "ant-2mm-static",
            Self::Ant3mmWalk => "ant-3mm-walk",
            Self::Ant4mmTurn => "ant-4mm-turn",
            Self::Population100 => "population-100",
            Self::Population500 => "population-500",
            Self::Population1000 => "population-1000",
            Self::HeadingSweep => "heading",
            Self::GaitSweep => "gait",
            Self::LodSweep => "lod",
            Self::AnatomyDiagnostic => "anatomy-magnified-diagnostic",
        }
    }
    pub fn parse(text: &str) -> Result<Self, RenderError> {
        Self::all()
            .iter()
            .copied()
            .find(|s| s.name() == text)
            .ok_or(RenderError::Invalid("unknown validation scene"))
    }
}
#[derive(Clone, Copy, Debug, serde::Serialize)]
pub enum ValidationBackground {
    Bright,
    Dark,
    HighContrast,
}
impl ValidationBackground {
    fn name(self) -> &'static str {
        match self {
            Self::Bright => "bright",
            Self::Dark => "dark",
            Self::HighContrast => "contrast",
        }
    }
}
#[derive(serde::Serialize)]
pub struct ValidationImage {
    pub scene: ValidationSceneId,
    pub background: ValidationBackground,
    pub ppi: f32,
    pub dimensions_px: [u32; 2],
    pub creature_count: usize,
    pub body_lengths_mm: Vec<f32>,
    pub magnified_diagnostic: bool,
    pub file: String,
    pub sha256: String,
    pub alpha_coverage_px: f64,
    pub render: crate::RenderStats,
}
#[derive(serde::Serialize)]
pub struct ValidationReport {
    pub app_version: String,
    pub profile_version: String,
    pub profile_schema: u32,
    pub seed: u64,
    pub git_commit: Option<String>,
    pub working_tree_dirty: bool,
    pub backend: String,
    pub device: String,
    pub device_type: String,
    pub shader_sha256: String,
    pub images: Vec<ValidationImage>,
    pub caveats: Vec<String>,
}
fn mean(profile: &CreatureProfile, key: &str) -> Result<f32, RenderError> {
    profile
        .range(key)
        .map(|r| (r.min + r.max) * 0.5)
        .map_err(|e| RenderError::Backend(e.to_string()))
}
fn instance(
    profile: &CreatureProfile,
    length_mm: f32,
    ppi: f32,
    center: [f32; 2],
    seed: u32,
) -> Result<CreatureRenderInstance, RenderError> {
    let scale = length_mm / mean(profile, "body_length_mm")?;
    let ppm = ppi / 25.4;
    let length_px = length_mm * ppm;
    Ok(CreatureRenderInstance {
        position_px: center,
        heading_rad: 0.,
        length_px,
        width_px: mean(profile, "head_width_mm")? * scale * ppm,
        head_length_px: mean(profile, "head_length_mm")? * scale * ppm,
        head_width_px: mean(profile, "head_width_mm")? * scale * ppm,
        gait_phase: 0.2,
        antenna: [0.64, 0.75],
        speed_norm: 1.,
        turn_amount: 0.,
        pose_blend: 0.,
        stride_px: mean(profile, "stride_length_mm")? * scale * ppm,
        leg_radius_px: mean(profile, "leg_thickness_mm")? * scale * ppm * 0.5,
        padding: 0.,
        morphology_seed: seed,
        lod: select_lod(length_px, None) as u32,
        behavior: 0,
        flags: 0,
        material: [0.013, 0.010, 0.007, 0.98],
    })
}
fn fixture(
    scene: ValidationSceneId,
    profile: &CreatureProfile,
    ppi: f32,
) -> Result<([u32; 2], Vec<CreatureRenderInstance>, bool), RenderError> {
    let base = mean(profile, "body_length_mm")?;
    let (size, count) = match scene {
        ValidationSceneId::Population100 => ([1800, 1000], 100),
        ValidationSceneId::Population500 => ([1800, 1000], 500),
        ValidationSceneId::Population1000 => ([1800, 1000], 1000),
        ValidationSceneId::HeadingSweep => ([1080, 540], 72),
        ValidationSceneId::GaitSweep => ([960, 360], 24),
        ValidationSceneId::LodSweep => ([1200, 560], 42),
        ValidationSceneId::AnatomyDiagnostic => ([720, 520], 1),
        _ => ([360, 240], 1),
    };
    let mut ants = Vec::with_capacity(count);
    let columns = match scene {
        ValidationSceneId::HeadingSweep => 12,
        ValidationSceneId::GaitSweep => 8,
        ValidationSceneId::LodSweep => 14,
        _ => (count as f32 * (size[0] as f32 / size[1] as f32))
            .sqrt()
            .ceil() as usize,
    };
    let columns = columns.min(count).max(1);
    let rows = count.div_ceil(columns);
    for index in 0..count {
        let x = (index % columns) as f32 + 0.5;
        let y = (index / columns) as f32 + 0.5;
        let center = [
            x * size[0] as f32 / columns as f32,
            y * size[1] as f32 / rows as f32,
        ];
        let mm = match scene {
            ValidationSceneId::Ant2mmStatic => 2.,
            ValidationSceneId::Ant3mmWalk => 3.,
            ValidationSceneId::Ant4mmTurn => 4.,
            ValidationSceneId::LodSweep => (7. + (index % 14) as f32 * 2.5) * 25.4 / ppi,
            _ => base,
        };
        let mut ant = instance(
            profile,
            mm,
            ppi,
            center,
            0xA172026u32.wrapping_add(index as u32 * 1664525),
        )?;
        match scene {
            ValidationSceneId::Ant2mmStatic => {
                ant.gait_phase = 0.;
                ant.speed_norm = 0.;
            }
            ValidationSceneId::Ant4mmTurn => {
                ant.heading_rad = 0.85;
                ant.turn_amount = 0.5;
                ant.antenna = [0.35, 0.95];
            }
            ValidationSceneId::HeadingSweep => {
                ant.heading_rad = index as f32 * std::f32::consts::TAU / count as f32
            }
            ValidationSceneId::GaitSweep => {
                ant.gait_phase = index as f32 / count as f32;
                ant.antenna = [0.3 + index as f32 * 0.025, 0.9 - index as f32 * 0.02];
            }
            ValidationSceneId::LodSweep => {
                ant.heading_rad = (index / 14) as f32 * 0.65;
            }
            ValidationSceneId::AnatomyDiagnostic => {
                let f = 230. / ant.length_px;
                ant.length_px *= f;
                ant.width_px *= f;
                ant.head_length_px *= f;
                ant.head_width_px *= f;
                ant.stride_px *= f;
                ant.leg_radius_px *= f;
                ant.lod = 2;
            }
            _ => {
                ant.heading_rad = ((index * 137) % 360) as f32 * std::f32::consts::PI / 180.;
                ant.gait_phase = ((index * 97) % 1000) as f32 / 1000.;
            }
        }
        ants.push(ant);
    }
    Ok((size, ants, scene == ValidationSceneId::AnatomyDiagnostic))
}
fn composite(pixels: &[u8], width: u32, background: ValidationBackground) -> Vec<u8> {
    let mut out = Vec::with_capacity(pixels.len());
    for (i, p) in pixels.as_chunks::<4>().0.iter().enumerate() {
        let bg = match background {
            ValidationBackground::Bright => [246.; 3],
            ValidationBackground::Dark => [10., 12., 15.],
            ValidationBackground::HighContrast => {
                if ((i % width as usize) / 12 + (i / width as usize) / 12).is_multiple_of(2) {
                    [245.; 3]
                } else {
                    [22.; 3]
                }
            }
        };
        let a = p[3] as f32 / 255.;
        for c in 0..3 {
            out.push((p[c] as f32 + bg[c] * (1. - a)).round().clamp(0., 255.) as u8);
        }
        out.push(255);
    }
    out
}
pub fn run_validation(
    renderer: &mut Renderer,
    profiles: &RuntimeProfileBundle,
    selection: &str,
    output: &Path,
) -> Result<ValidationReport, RenderError> {
    std::fs::create_dir_all(output).map_err(|e| RenderError::Backend(e.to_string()))?;
    let selected = if selection == "all" {
        ValidationSceneId::all().to_vec()
    } else if selection == "core" {
        vec![
            ValidationSceneId::Ant2mmStatic,
            ValidationSceneId::Ant3mmWalk,
            ValidationSceneId::Ant4mmTurn,
            ValidationSceneId::HeadingSweep,
            ValidationSceneId::GaitSweep,
            ValidationSceneId::LodSweep,
            ValidationSceneId::AnatomyDiagnostic,
        ]
    } else {
        vec![ValidationSceneId::parse(selection)?]
    };
    let profile = profiles
        .creatures
        .iter()
        .find(|c| c.id.0 == "ant")
        .ok_or(RenderError::Invalid("ant profile"))?;
    let git_commit = std::process::Command::new("git")
        .args(["rev-parse", "HEAD"])
        .output()
        .ok()
        .filter(|o| o.status.success())
        .and_then(|o| String::from_utf8(o.stdout).ok())
        .map(|s| s.trim().to_owned());
    let working_tree_dirty = std::process::Command::new("git")
        .args(["status", "--porcelain"])
        .output()
        .map(|o| !o.stdout.is_empty())
        .unwrap_or(true);
    let info = renderer.adapter_info();
    let mut report=ValidationReport{app_version:env!("CARGO_PKG_VERSION").into(),profile_version:profiles.profile_version.clone(),profile_schema:profiles.schema_version,seed:0xA172026,git_commit,working_tree_dirty,backend:format!("{:?}",info.backend),device:info.name.clone(),device_type:format!("{:?}",info.device_type),shader_sha256:format!("{:x}",Sha256::digest(include_bytes!("../shaders/ant.wgsl"))),images:vec![],caveats:vec!["Pixels are actual offscreen GPU output, not native compositor/input validation.".into(),"PPI labels specify intended physical display density; real 1:1 review still needs a calibrated monitor.".into(),"2/3/4 mm and magnified fixtures are explicit developer size overrides, not expanded species measurements.".into()]};
    for scene in selected {
        let ppies: &[f32] = if matches!(
            scene,
            ValidationSceneId::AnatomyDiagnostic
                | ValidationSceneId::Population100
                | ValidationSceneId::Population500
                | ValidationSceneId::Population1000
        ) {
            &[144.]
        } else {
            &[96., 144., 220.]
        };
        for &ppi in ppies {
            let (size, ants, magnified) = fixture(scene, profile, ppi)?;
            let target = renderer.offscreen(size[0], size[1])?;
            let mut stats = renderer.render_offscreen(&target, &ants)?;
            let pixels = renderer.read_rgba(&target)?;
            stats.gpu_ms = renderer.last_gpu_ms();
            let coverage = pixels
                .as_chunks::<4>()
                .0
                .iter()
                .map(|p| p[3] as f64 / 255.)
                .sum();
            for background in [
                ValidationBackground::Bright,
                ValidationBackground::Dark,
                ValidationBackground::HighContrast,
            ] {
                let name = format!(
                    "{}-{}ppi-{}.png",
                    scene.name(),
                    ppi as u32,
                    background.name()
                );
                let file: PathBuf = output.join(&name);
                let composed = composite(&pixels, size[0], background);
                image::save_buffer_with_format(
                    &file,
                    &composed,
                    size[0],
                    size[1],
                    image::ColorType::Rgba8,
                    image::ImageFormat::Png,
                )
                .map_err(|e| RenderError::Backend(e.to_string()))?;
                let encoded =
                    std::fs::read(&file).map_err(|e| RenderError::Backend(e.to_string()))?;
                report.images.push(ValidationImage {
                    scene,
                    background,
                    ppi,
                    dimensions_px: size,
                    creature_count: ants.len(),
                    body_lengths_mm: ants.iter().map(|a| a.length_px * 25.4 / ppi).collect(),
                    magnified_diagnostic: magnified,
                    file: name,
                    sha256: format!("{:x}", Sha256::digest(encoded)),
                    alpha_coverage_px: coverage,
                    render: stats.clone(),
                });
            }
        }
    }
    std::fs::write(
        output.join("validation-report.json"),
        serde_json::to_vec_pretty(&report).map_err(|e| RenderError::Backend(e.to_string()))?,
    )
    .map_err(|e| RenderError::Backend(e.to_string()))?;
    Ok(report)
}
