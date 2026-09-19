use crate::AppConfig;
use creature_profile::RuntimeProfileBundle;
use display_model::{DisplayId, DisplaySurface};
#[derive(Clone, Debug)]
pub enum UiAction {
    ApplyConfig(AppConfig),
    SaveCalibration(DisplayId, f32),
    MoveToDisplay(DisplayId),
    FitReference(u32),
    HideAll,
    TogglePause,
    Quit,
    ForcePopulation,
    RetryGraphics,
}
#[derive(Clone, Debug)]
pub struct UiApp {
    pub stable_id: String,
    pub name: String,
}
#[derive(Default, Clone, Debug)]
pub struct UiDiagnostics {
    pub creature_count: usize,
    pub rendered_count: usize,
    pub simulation_ms: f64,
    pub behavior_ms: f64,
    pub spatial_ms: f64,
    pub trails_ms: f64,
    pub render_prep_ms: f64,
    pub gpu_ms: Option<f64>,
    pub upload_bytes: u64,
    pub draws: u32,
    pub lod_counts: [u32; 3],
    pub dropped_ticks: u64,
    pub seed: u64,
}
#[derive(Default, Clone, Debug)]
pub struct PreviewCreature {
    pub id: u64,
    pub position_mm: [f32; 2],
    pub heading: f32,
    pub behavior: u8,
    pub lod: u32,
}
#[derive(Default, Clone, Debug)]
pub struct PreviewData {
    pub display: Option<DisplayId>,
    pub size_mm: [f32; 2],
    pub creatures: Vec<PreviewCreature>,
    pub trails: Vec<[f32; 3]>,
    pub grid_mm: f32,
}
pub struct SettingsContext<'a> {
    pub config: &'a AppConfig,
    pub displays: &'a [DisplaySurface],
    pub profiles: &'a RuntimeProfileBundle,
    pub diagnostics: &'a UiDiagnostics,
    pub recent_apps: &'a [UiApp],
    pub preview: Option<&'a PreviewData>,
    pub window_display: Option<DisplayId>,
    pub message: Option<&'a str>,
    pub read_only: bool,
}

/// Draft state lives outside the native window, surviving renderer recreation.
pub struct SettingsUi {
    draft: AppConfig,
    base: AppConfig,
    calibration: Option<DisplayId>,
    reference_px: f32,
    exclusion_text: String,
    advanced_open: bool,
}
impl SettingsUi {
    pub fn new(config: &AppConfig) -> Self {
        Self {
            draft: config.clone(),
            base: config.clone(),
            calibration: None,
            reference_px: 324.0,
            exclusion_text: String::new(),
            advanced_open: false,
        }
    }
    pub fn draft(&self) -> &AppConfig {
        &self.draft
    }
    pub fn accept_config(&mut self, config: &AppConfig) {
        self.draft = config.clone();
        self.base = config.clone();
    }
    pub fn open_calibration(&mut self, display: &DisplaySurface) {
        self.calibration = Some(display.id);
        self.reference_px = 85.60 / display.calibration.mm_per_physical_px;
    }
    pub fn set_advanced_open(&mut self, open: bool) {
        self.advanced_open = open;
    }
    pub fn calibration_display(&self) -> Option<DisplayId> {
        self.calibration
    }
    fn synchronize(&mut self, config: &AppConfig) {
        if &self.base == config {
            return;
        }
        // Merge externally changed controls without discarding unrelated unsaved edits.
        macro_rules! merge { ($($field:ident),*) => {$({
            if self.draft.$field == self.base.$field { self.draft.$field = config.$field.clone(); }
        })*}; }
        merge!(
            enabled,
            paused,
            preset,
            target_population,
            cursor_reaction,
            continuous_monitors,
            launch_at_login,
            safe_overlay_mode,
            panic_hotkey,
            creature_scale,
            developer_mode,
            display_calibration,
            display_enabled,
            app_exclusions,
            secondary_creatures,
            advanced
        );
        self.base = config.clone();
    }
    pub fn draw(&mut self, ui: &mut egui::Ui, context: &SettingsContext<'_>) -> Vec<UiAction> {
        self.synchronize(context.config);
        let mut actions = Vec::new();
        egui::Frame::new().fill(ui.visuals().panel_fill).inner_margin(20.0).show(ui, |ui| {
            ui.horizontal(|ui| {
                ui.heading("Insect Realism");
                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    if ui.button("Hide all").on_hover_text("Immediately hide every overlay and freeze the simulation. This is temporary.").clicked() { actions.push(UiAction::HideAll); }
                    if ui.button(if context.config.paused {"Resume"} else {"Pause"}).clicked() { actions.push(UiAction::TogglePause); }
                });
            });
            ui.label("Tiny creatures, calibrated to the glass.");
            ui.add_space(8.0);
            if let Some(message) = context.message {
                ui.label(egui::RichText::new(message).color(ui.visuals().warn_fg_color));
            }
            if context.read_only { ui.label("The saved configuration is newer or unavailable. Changes apply to this session only."); }
            ui.separator();
            if self.calibration.is_some() {
                self.draw_calibration(ui, context, &mut actions);
            } else {
                let scroll_height = (ui.available_height() - 72.0).max(120.0);
                egui::ScrollArea::vertical().max_height(scroll_height).show(ui, |ui| {
                    self.draw_controls(ui, context, &mut actions);
                });
                ui.separator();
                if let Err(error) = self.draft.validate() { ui.label(egui::RichText::new(error.to_string()).color(ui.visuals().error_fg_color)); }
                ui.horizontal(|ui| {
                    let changed = self.draft != *context.config;
                    if ui.add_enabled(changed && self.draft.validate().is_ok(), egui::Button::new(if context.read_only {"Apply for this session"} else {"Apply changes"})).clicked() {
                        actions.push(UiAction::ApplyConfig(self.draft.clone()));
                    }
                    if ui.add_enabled(changed, egui::Button::new("Revert")).clicked() { self.accept_config(context.config); }
                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                        if ui.button("Quit").clicked() { actions.push(UiAction::Quit); }
                    });
                });
                ui.small("Closing settings keeps the utility running. The menu/tray always provides Hide all and Quit.");
            }
        });
        actions
    }
    fn draw_controls(
        &mut self,
        ui: &mut egui::Ui,
        context: &SettingsContext<'_>,
        actions: &mut Vec<UiAction>,
    ) {
        use crate::{AppExclusion, AppExclusionMode, PresetId};
        ui.checkbox(&mut self.draft.enabled, "Enable creatures");
        let old_preset = self.draft.preset;
        ui.horizontal(|ui| {
            ui.label("Preset");
            egui::ComboBox::from_id_salt("preset")
                .selected_text(self.draft.preset.label())
                .show_ui(ui, |ui| {
                    for id in [
                        PresetId::Realistic,
                        PresetId::Light,
                        PresetId::Heavy,
                        PresetId::Nightmare,
                        PresetId::Custom,
                    ] {
                        ui.selectable_value(&mut self.draft.preset, id, id.label());
                    }
                });
        });
        if old_preset != self.draft.preset && self.draft.preset != PresetId::Custom {
            let _ = self.draft.apply_preset(self.draft.preset);
        }
        let before_population = self.draft.target_population;
        ui.add(
            egui::Slider::new(
                &mut self.draft.target_population,
                0..=self.draft.advanced.population_cap,
            )
            .text("Ants per monitor"),
        );
        if before_population != self.draft.target_population {
            self.draft.preset = PresetId::Custom;
        }
        ui.small("Population changes enter and leave at the edges; presets do not multiply biological speed.");
        ui.add_space(12.0);
        ui.strong("Displays");
        if context.displays.is_empty() {
            ui.label("No active displays have been discovered.");
        }
        for display in context.displays {
            ui.push_id(display.id.0, |ui| {
                ui.horizontal(|ui| {
                    let mut enabled = self.draft.display_is_enabled(&display.fingerprint);
                    if ui.checkbox(&mut enabled, &display.name).changed() {
                        self.draft.display_enabled.insert(display.fingerprint.clone(), enabled);
                    }
                    ui.label(format!("{} x {}", display.pixels.width, display.pixels.height));
                    if ui.button(if display.calibration.is_ready() {"Recalibrate"} else {"Calibrate"}).clicked() {
                        self.open_calibration(display); actions.push(UiAction::MoveToDisplay(display.id));
                    }
                });
                if display.calibration.is_ready() {
                    ui.small(format!("{:.1} PPI, {:?}, {:.0} Hz", 25.4 / display.calibration.mm_per_physical_px,
                        display.calibration.confidence, display.refresh_hz));
                } else { ui.small("Hidden until physical size is calibrated. UI scaling is not a PPI measurement."); }
            });
        }
        ui.checkbox(
            &mut self.draft.continuous_monitors,
            "Allow crossings between touching monitors",
        );
        ui.add_space(12.0);
        ui.strong("Utility");
        ui.checkbox(&mut self.draft.launch_at_login, "Launch at login");
        if ui
            .checkbox(&mut self.draft.cursor_reaction, "Subtle cursor reaction")
            .changed()
        {
            self.draft.preset = PresetId::Custom;
        }
        ui.checkbox(
            &mut self.draft.safe_overlay_mode,
            "Safe overlay mode (hide over full-screen or unknown foreground)",
        );
        ui.horizontal(|ui| {
            ui.label("Panic hide");
            ui.text_edit_singleline(&mut self.draft.panic_hotkey);
        });
        ui.small("For example Ctrl+Alt+Shift+H. A conflicting shortcut is rejected without removing the previous one.");
        let secondaries: Vec<_> = context
            .profiles
            .creatures
            .iter()
            .filter(|p| p.qualified && p.id.0 != "ant")
            .collect();
        if secondaries.is_empty() {
            ui.small("Ants only: no secondary creature has passed the evidence and quality gate.");
        }
        for profile in secondaries {
            ui.checkbox(
                self.draft
                    .secondary_creatures
                    .entry(profile.id.0.clone())
                    .or_insert(false),
                &profile.species,
            );
        }
        ui.add_space(8.0);
        egui::CollapsingHeader::new("Application exclusions").show(ui, |ui| {
            ui.small("Local application identifiers only. Nothing underneath an ant is captured or interpreted.");
            egui::ComboBox::from_id_salt("recent-apps").selected_text("Add a recently active application").show_ui(ui, |ui| {
                for app in context.recent_apps {
                    if ui.selectable_label(false, &app.name).clicked() { self.exclusion_text.clone_from(&app.stable_id); }
                }
            });
            ui.horizontal(|ui| {
                ui.text_edit_singleline(&mut self.exclusion_text).on_hover_text("macOS bundle identifier or Windows executable path");
                if ui.add_enabled(!self.exclusion_text.trim().is_empty() && self.draft.app_exclusions.len() < 256, egui::Button::new("Add")).clicked() {
                    let id = self.exclusion_text.trim().to_owned();
                    if !self.draft.app_exclusions.iter().any(|e| e.stable_id == id) {
                        self.draft.app_exclusions.push(AppExclusion { stable_id: id, mode: AppExclusionMode::Hide });
                    }
                    self.exclusion_text.clear();
                }
            });
            let mut remove = None;
            for (index, rule) in self.draft.app_exclusions.iter_mut().enumerate() {
                ui.push_id(index, |ui| { ui.horizontal_wrapped(|ui| {
                    ui.label(&rule.stable_id);
                    egui::ComboBox::from_id_salt("rule-mode").selected_text(match rule.mode {AppExclusionMode::Hide=>"Always hide",_=>"Full-screen only"}).show_ui(ui, |ui| {
                        ui.selectable_value(&mut rule.mode, AppExclusionMode::Hide, "Always hide");
                        ui.selectable_value(&mut rule.mode, AppExclusionMode::Compatibility, "Full-screen only");
                    });
                    if ui.small_button("Remove").clicked() { remove = Some(index); }
                }); });
            }
            if let Some(index) = remove { self.draft.app_exclusions.remove(index); }
        });
        egui::CollapsingHeader::new("Advanced").default_open(self.advanced_open).show(ui, |ui| {
            let old = self.draft.advanced.clone();
            ui.add(egui::Slider::new(&mut self.draft.advanced.population_cap, 1..=10_000).text("Global population cap"));
            self.draft.target_population = self.draft.target_population.min(self.draft.advanced.population_cap);
            ui.add(egui::Slider::new(&mut self.draft.advanced.spawn_per_second, 0.1..=2000.0).logarithmic(true).text("Entries / second"));
            ui.add(egui::Slider::new(&mut self.draft.advanced.activity, 0.25..=2.0).text("Activity weighting"));
            ui.add(egui::Slider::new(&mut self.draft.advanced.trail_strength, 0.0..=2.0).text("Trail influence"));
            ui.add(egui::Slider::new(&mut self.draft.advanced.trail_lifetime_multiplier, 0.1..=10.0).text("Trail persistence"));
            let scale_before = self.draft.creature_scale;
            ui.add(egui::Slider::new(&mut self.draft.creature_scale, 0.25..=8.0).text("Creature scale"));
            ui.small("1.0 uses the compiled physical body sizes. Other scales are deliberate visual overrides, not new monitor calibration.");
            ui.horizontal(|ui| { ui.label("Seed"); ui.add(egui::DragValue::new(&mut self.draft.advanced.seed)); });
            ui.small("Changing the seed or capacity resets the population. Other changes preserve existing creatures.");
            if old.spawn_per_second != self.draft.advanced.spawn_per_second || old.activity != self.draft.advanced.activity
                || old.trail_strength != self.draft.advanced.trail_strength || old.trail_lifetime_multiplier != self.draft.advanced.trail_lifetime_multiplier
                || scale_before != self.draft.creature_scale { self.draft.preset = PresetId::Custom; }
            ui.checkbox(&mut self.draft.developer_mode, "Developer diagnostics");
            if self.draft.developer_mode {
                ui.horizontal_wrapped(|ui| {
                    ui.checkbox(&mut self.draft.advanced.show_ids, "IDs"); ui.checkbox(&mut self.draft.advanced.show_states, "States");
                    ui.checkbox(&mut self.draft.advanced.show_grid, "Spatial grid"); ui.checkbox(&mut self.draft.advanced.show_trails, "Trails");
                    ui.checkbox(&mut self.draft.advanced.show_lod, "LOD");
                });
                if ui.add_enabled(context.config.developer_mode, egui::Button::new("Force population now (diagnostic placement)")).clicked() { actions.push(UiAction::ForcePopulation); }
                if ui.button("Retry graphics initialization").clicked() { actions.push(UiAction::RetryGraphics); }
                draw_diagnostics(ui, context.diagnostics);
                if let Some(preview) = context.preview { draw_preview(ui, preview, &self.draft); }
            }
            ui.separator(); ui.strong("Biology provenance");
            ui.small(format!("Profile {} / schema {}", context.profiles.profile_version, context.profiles.schema_version));
            for profile in &context.profiles.creatures {
                ui.label(&profile.species);
                if let Ok(range) = profile.range("body_length_mm") { ui.small(format!("Body length: {:.2}-{:.2} mm", range.min, range.max)); }
                for limitation in &profile.limitations { ui.small(limitation); }
            }
        });
    }
    fn draw_calibration(
        &mut self,
        ui: &mut egui::Ui,
        context: &SettingsContext<'_>,
        actions: &mut Vec<UiAction>,
    ) {
        let Some(id) = self.calibration else {
            return;
        };
        let Some(display) = context.displays.iter().find(|d| d.id == id) else {
            ui.label("This display was disconnected. Its saved calibration has not changed.");
            if ui.button("Back").clicked() {
                self.calibration = None;
            }
            return;
        };
        ui.heading(format!("Calibrate {}", display.name));
        ui.label("Hold a standard credit card against the display. Match its 85.60 mm width to the rectangle below.");
        ui.small("Use the width, not the diagonal. The entire rectangle must fit on this monitor.");
        if context.window_display != Some(id) {
            ui.label(
                egui::RichText::new("Move this window to the selected monitor before saving.")
                    .color(ui.visuals().warn_fg_color),
            );
            if ui.button("Move to this monitor").clicked() {
                actions.push(UiAction::MoveToDisplay(id));
            }
        }
        ui.add(
            egui::Slider::new(&mut self.reference_px, 85.60..=4280.0)
                .text("Reference width (physical pixels)")
                .logarithmic(true),
        );
        let points =
            crate::calibration_ui::reference_points(self.reference_px, ui.ctx().pixels_per_point())
                .unwrap_or(324.0);
        let (rect, _) = ui.allocate_exact_size(
            egui::vec2(points, points * (53.98 / 85.60)),
            egui::Sense::hover(),
        );
        let visible = ui.clip_rect().contains_rect(rect);
        ui.painter()
            .rect_filled(rect, 8.0, egui::Color32::from_gray(208));
        ui.painter().rect_stroke(
            rect,
            8.0,
            egui::Stroke::new(1.0, egui::Color32::from_gray(55)),
            egui::StrokeKind::Inside,
        );
        ui.painter().text(
            rect.center(),
            egui::Align2::CENTER_CENTER,
            "85.60 mm",
            egui::FontId::proportional(20.0),
            egui::Color32::from_gray(35),
        );
        if !visible {
            ui.label(
                "The reference is clipped. Enlarge this window or reduce its width before saving.",
            );
        }
        if ui.button("Fit window to reference").clicked() {
            actions.push(UiAction::FitReference(self.reference_px.ceil() as u32));
        }
        let result = crate::calibration_ui::accept_reference(
            id,
            context.window_display,
            self.reference_px,
            visible,
        );
        if let Ok(calibration) = &result {
            ui.small(format!(
                "Calibration: {:.5} mm / physical pixel ({:.1} PPI)",
                calibration.mm_per_physical_px,
                25.4 / calibration.mm_per_physical_px
            ));
        }
        ui.horizontal(|ui| {
            if ui
                .add_enabled(result.is_ok(), egui::Button::new("Save calibration"))
                .clicked()
            {
                if let Ok(calibration) = result {
                    actions.push(UiAction::SaveCalibration(
                        id,
                        calibration.mm_per_physical_px,
                    ));
                }
                self.calibration = None;
            }
            if ui.button("Back without saving").clicked() {
                self.calibration = None;
            }
        });
    }
}
fn draw_diagnostics(ui: &mut egui::Ui, d: &UiDiagnostics) {
    egui::Grid::new("diagnostics")
        .num_columns(2)
        .show(ui, |ui| {
            for (name, value) in [
                (
                    "Creatures / rendered",
                    format!("{} / {}", d.creature_count, d.rendered_count),
                ),
                (
                    "Simulation / behavior",
                    format!("{:.3} / {:.3} ms", d.simulation_ms, d.behavior_ms),
                ),
                (
                    "Spatial / trails",
                    format!("{:.3} / {:.3} ms", d.spatial_ms, d.trails_ms),
                ),
                ("Render preparation", format!("{:.3} ms", d.render_prep_ms)),
                (
                    "GPU time",
                    d.gpu_ms
                        .map_or_else(|| "Unavailable".into(), |v| format!("{v:.3} ms")),
                ),
                (
                    "Upload / draws",
                    format!("{} bytes / {}", d.upload_bytes, d.draws),
                ),
                (
                    "LOD tiny / standard / detailed",
                    format!("{:?}", d.lod_counts),
                ),
                (
                    "Dropped ticks / seed",
                    format!("{} / {}", d.dropped_ticks, d.seed),
                ),
            ] {
                ui.label(name);
                ui.monospace(value);
                ui.end_row();
            }
        });
}
fn draw_preview(ui: &mut egui::Ui, data: &PreviewData, config: &AppConfig) {
    if data.size_mm[0] <= 0.0 || data.size_mm[1] <= 0.0 {
        return;
    }
    ui.small("Physical simulation view; not a capture of your desktop.");
    let (rect, _) = ui.allocate_exact_size(
        egui::vec2(ui.available_width().max(20.0), 210.0),
        egui::Sense::hover(),
    );
    let painter = ui.painter_at(rect);
    painter.rect_filled(rect, 0.0, egui::Color32::from_gray(228));
    let project = |x: f32, y: f32| {
        egui::pos2(
            rect.left() + x / data.size_mm[0] * rect.width(),
            rect.top() + y / data.size_mm[1] * rect.height(),
        )
    };
    if config.advanced.show_grid && data.grid_mm > 0.0 {
        for n in 0..((data.size_mm[0] / data.grid_mm) as usize).min(512) {
            let x = n as f32 * data.grid_mm;
            painter.line_segment(
                [project(x, 0.0), project(x, data.size_mm[1])],
                egui::Stroke::new(0.5, egui::Color32::from_gray(188)),
            );
        }
        for n in 0..((data.size_mm[1] / data.grid_mm) as usize).min(512) {
            let y = n as f32 * data.grid_mm;
            painter.line_segment(
                [project(0.0, y), project(data.size_mm[0], y)],
                egui::Stroke::new(0.5, egui::Color32::from_gray(188)),
            );
        }
    }
    if config.advanced.show_trails {
        for cell in &data.trails {
            painter.circle_filled(
                project(cell[0], cell[1]),
                2.5,
                egui::Color32::from_rgba_unmultiplied(
                    38,
                    120,
                    68,
                    (cell[2].clamp(0.0, 1.0) * 180.0) as u8,
                ),
            );
        }
    }
    for c in &data.creatures {
        let p = project(c.position_mm[0], c.position_mm[1]);
        let color = if config.advanced.show_states {
            egui::Color32::from_rgb(30 + c.behavior.wrapping_mul(21), 80, 170)
        } else {
            egui::Color32::from_gray(30)
        };
        painter.line_segment(
            [p, p + egui::vec2(c.heading.cos(), c.heading.sin()) * 4.0],
            egui::Stroke::new(1.3, color),
        );
        if config.advanced.show_ids || config.advanced.show_lod {
            painter.text(
                p,
                egui::Align2::LEFT_BOTTOM,
                if config.advanced.show_ids {
                    format!("{}", c.id)
                } else {
                    format!("L{}", c.lod)
                },
                egui::FontId::monospace(8.0),
                color,
            );
        }
    }
    ui.small(format!(
        "Monitor {:?}: {:.1} x {:.1} mm",
        data.display, data.size_mm[0], data.size_mm[1]
    ));
}
