use crate::{
    compatibility::{VisibilityDecision, visibility},
    controller::{AppError, Controller},
    cursor::CursorTracker,
    overlay_host::OverlayHost,
    preferences::apply_preferences,
    recovery::Recovery,
    settings_window::SettingsWindow,
};
use creature_profile::RuntimeProfileBundle;
use display_model::DisplaySurface;
use platform_api::*;
use rendering::{InstanceBuilder, Renderer, SurfaceRenderer};
use settings::{AppConfig, ConfigStore, JsonConfigStore, PresetId, ui::*};
use std::{
    sync::{Arc, mpsc},
    time::{Duration, Instant},
};
use winit::{
    application::ApplicationHandler,
    dpi::LogicalSize,
    event::WindowEvent,
    event_loop::{ActiveEventLoop, ControlFlow, EventLoop, EventLoopProxy},
    window::WindowId,
};
fn error(e: impl std::fmt::Display) -> AppError {
    AppError(e.to_string())
}
fn native_adapter() -> Result<Box<dyn PlatformAdapter>, PlatformError> {
    #[cfg(target_os = "macos")]
    {
        Ok(Box::new(platform_macos::MacAdapter::new()?))
    }
    #[cfg(target_os = "windows")]
    {
        Ok(Box::new(platform_windows::WindowsAdapter::new()?))
    }
    #[cfg(not(any(target_os = "macos", target_os = "windows")))]
    {
        Err(PlatformError::Unsupported(
            "Native overlays target macOS and Windows; Linux supports developer validation only",
        ))
    }
}
struct PendingDevice {
    receiver: mpsc::Receiver<Result<Renderer, String>>,
    started: Instant,
    timed_out: bool,
}
struct OverlaySlot {
    host: OverlayHost<SurfaceRenderer>,
    builder: InstanceBuilder,
    display: DisplaySurface,
}
/// Native callbacks never own policy. All app state changes pass through this UI-thread root.
struct Runtime {
    // Explicit shutdown/drop order: presentation resources, device, platform utility.
    overlays: Vec<OverlaySlot>,
    settings: Option<SettingsWindow>,
    gpu: Option<Renderer>,
    platform: Box<dyn PlatformAdapter>,
    controller: Controller,
    ui: SettingsUi,
    store: JsonConfigStore,
    read_only: bool,
    recovery: Recovery,
    pending: Option<PendingDevice>,
    proxy: EventLoopProxy<()>,
    start: Instant,
    last_tick: Instant,
    next_metadata: Instant,
    next_ui: Instant,
    foreground: Option<ForegroundContext>,
    recent: Vec<UiApp>,
    cursor: CursorTracker,
    topology_dirty: bool,
    resources_dirty: bool,
    message: Option<String>,
    diagnostics: UiDiagnostics,
    preview: PreviewData,
    shortcut_ready: bool,
}
impl Runtime {
    fn new(
        proxy: EventLoopProxy<()>,
        profiles: Arc<RuntimeProfileBundle>,
    ) -> Result<Self, AppError> {
        let mut platform = native_adapter().map_err(error)?;
        let paths = directories::ProjectDirs::from("studio", "Pixelied", "InsectRealism")
            .ok_or_else(|| AppError("User configuration directory is unavailable".into()))?;
        let store = JsonConfigStore::new(paths.config_dir().join("settings.json"));
        let (config, read_only, mut message) = match store.load_report() {
            Ok(report) => (
                report.config,
                false,
                report.corrupt_backup.map(|p| {
                    format!(
                        "Recovered corrupt settings; original saved at {}",
                        p.display()
                    )
                }),
            ),
            Err(e) => (AppConfig::default(), true, Some(e.to_string())),
        };
        let shortcut_ready = match platform.register_panic_hotkey(&config.panic_hotkey) {
            Ok(()) => true,
            Err(e) => {
                message = Some(format!(
                    "Overlays remain hidden until a panic shortcut is registered: {e}"
                ));
                false
            }
        };
        if config.launch_at_login
            && let Err(e) = platform.set_launch_at_login(true)
        {
            message = Some(e.to_string());
        }
        let mut controller = Controller::new(config.clone(), profiles)?;
        controller.set_displays(platform.enumerate_displays().map_err(error)?, true)?;
        let now = Instant::now();
        Ok(Self {
            overlays: Vec::new(),
            settings: None,
            gpu: None,
            platform,
            controller,
            ui: SettingsUi::new(&config),
            store,
            read_only,
            recovery: Recovery::default(),
            pending: None,
            proxy,
            start: now,
            last_tick: now,
            next_metadata: now,
            next_ui: now,
            foreground: None,
            recent: Vec::new(),
            cursor: CursorTracker::default(),
            topology_dirty: false,
            resources_dirty: true,
            message,
            diagnostics: UiDiagnostics::default(),
            preview: PreviewData::default(),
            shortcut_ready,
        })
    }
    fn report(&mut self, message: impl Into<String>) {
        let message = message.into();
        tracing::warn!("{message}");
        self.message = Some(message);
    }
    fn hide_windows(&self) {
        for slot in &self.overlays {
            let _ = slot.host.set_visible(false);
        }
    }
    fn invalidate_graphics(&mut self, message: impl Into<String>) {
        self.hide_windows();
        for slot in &mut self.overlays {
            slot.host.invalidate();
        }
        self.overlays.clear();
        self.settings = None;
        self.gpu = None;
        self.last_tick = Instant::now();
        self.resources_dirty = true;
        self.recovery.fail(self.start.elapsed());
        self.report(message);
    }
    fn shutdown(&mut self) {
        self.hide_windows();
        self.overlays.clear();
        self.settings = None;
        self.gpu = None;
    }
    fn device_progress(&mut self) {
        if let Some(pending) = &mut self.pending {
            match pending.receiver.try_recv() {
                Ok(result) => {
                    let timed_out = pending.timed_out;
                    self.pending = None;
                    if !timed_out {
                        match result {
                            Ok(gpu) => {
                                self.gpu = Some(gpu);
                                self.recovery.succeed();
                                self.resources_dirty = true;
                                self.last_tick = Instant::now();
                            }
                            Err(e) => {
                                self.recovery.fail(self.start.elapsed());
                                self.report(format!("Graphics initialization failed: {e}"));
                            }
                        }
                    }
                }
                Err(mpsc::TryRecvError::Disconnected) => {
                    self.pending = None;
                    self.recovery.fail(self.start.elapsed());
                    self.report("Graphics initialization worker terminated unexpectedly");
                }
                Err(mpsc::TryRecvError::Empty) => {
                    if pending.started.elapsed() > Duration::from_secs(10) && !pending.timed_out {
                        pending.timed_out = true;
                        self.recovery.stop_permanently();
                        self.report("Graphics driver initialization did not respond. Overlays stay hidden; the menu remains available. Restart the utility before retrying a stalled driver.");
                    }
                }
            }
        }
        if self.gpu.is_none()
            && self.pending.is_none()
            && !self.controller.lifecycle.suspended
            && self.recovery.begin(self.start.elapsed())
        {
            let profile = self.controller.profiles().creatures[0].clone();
            let (sender, receiver) = mpsc::sync_channel(1);
            let proxy = self.proxy.clone();
            match std::thread::Builder::new()
                .name("insect-gpu-init".into())
                .spawn(move || {
                    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                        pollster::block_on(Renderer::new_headless(&profile))
                    }));
                    let result = match result {
                        Ok(r) => r.map_err(|e| e.to_string()),
                        Err(_) => Err("graphics backend panicked during initialization".into()),
                    };
                    let _ = sender.send(result);
                    let _ = proxy.send_event(());
                }) {
                Ok(_) => {
                    self.pending = Some(PendingDevice {
                        receiver,
                        started: Instant::now(),
                        timed_out: false,
                    })
                }
                Err(e) => {
                    self.recovery.fail(self.start.elapsed());
                    self.report(e.to_string());
                }
            }
        }
    }
    fn rebuild_resources(&mut self) {
        self.hide_windows();
        self.overlays.clear();
        let Some(gpu) = self.gpu.as_mut() else {
            return;
        };
        let mut failures = Vec::new();
        for display in self.controller.topology().displays() {
            let result = (|| -> Result<OverlaySlot, PlatformError> {
                let window = self.platform.create_overlay(display)?;
                let host = OverlayHost::new(window, |w| {
                    let source = w
                        .surface_source()
                        .map_err(|e| rendering::RenderError::Backend(e.to_string()))?;
                    gpu.create_surface(source, display.pixels, display.refresh_hz)
                })?;
                let builder = InstanceBuilder::new(
                    self.controller.config().advanced.population_cap as usize,
                    &self.controller.profiles().creatures[0],
                )
                .map_err(|e| PlatformError::Native(e.to_string()))?;
                Ok(OverlaySlot {
                    host,
                    builder,
                    display: display.clone(),
                })
            })();
            match result {
                Ok(slot) => self.overlays.push(slot),
                Err(e) => failures.push(format!("{}: {e}", display.name)),
            }
        }
        self.resources_dirty = false;
        if !failures.is_empty() {
            self.report(failures.join("; "));
        }
    }
    fn apply_config(&mut self, mut new: AppConfig) -> Result<(), AppError> {
        #[cfg(target_os = "windows")]
        for rule in &mut new.app_exclusions {
            rule.stable_id = rule.stable_id.to_lowercase();
        }
        #[cfg(not(target_os = "windows"))]
        let _ = &mut new;
        new.validate().map_err(error)?;
        HotkeyBinding::parse(&new.panic_hotkey).map_err(error)?;
        let old = self.controller.config().clone();
        // A failed initial registration is explicitly retried even if the text did not change.
        if !self.shortcut_ready {
            self.platform
                .register_panic_hotkey(&new.panic_hotkey)
                .map_err(error)?;
            self.shortcut_ready = true;
        }
        let store = (!self.read_only).then_some(&self.store as &dyn ConfigStore);
        if let Err(e) = apply_preferences(&old, &new, self.platform.as_mut(), store) {
            if self
                .platform
                .register_panic_hotkey(&old.panic_hotkey)
                .is_err()
            {
                self.shortcut_ready = false;
                self.hide_windows();
            }
            return Err(e);
        }
        match self.controller.apply_config(new.clone()) {
            Ok(changed) => {
                self.resources_dirty |= changed
                    || old.advanced.population_cap != new.advanced.population_cap
                    || old.advanced.seed != new.advanced.seed;
                self.message = None;
                Ok(())
            }
            Err(e) => {
                let rollback = apply_preferences(&new, &old, self.platform.as_mut(), store);
                Err(AppError(format!(
                    "{e}{}",
                    rollback
                        .err()
                        .map(|r| format!("; rollback: {r}"))
                        .unwrap_or_default()
                )))
            }
        }
    }
    fn show_settings(&mut self, event_loop: &ActiveEventLoop) {
        self.controller.lifecycle.open_settings();
        if let Some(settings) = &self.settings {
            settings.show();
            return;
        }
        let Some(gpu) = &self.gpu else {
            self.platform.present_diagnostic(self.message.as_deref().unwrap_or("Graphics are initializing. Settings will open when ready. Hide all and Quit remain available in the menu."));
            return;
        };
        match SettingsWindow::new(event_loop, gpu) {
            Ok(window) => {
                if self.ui.calibration_display().is_none()
                    && let Some(display) = self.controller.displays().iter().find(|d| {
                        !d.calibration.is_ready()
                            && self.controller.config().display_is_enabled(&d.fingerprint)
                    })
                {
                    self.ui.open_calibration(display);
                }
                if let Some(id) = self.ui.calibration_display() {
                    let _ = self
                        .platform
                        .move_settings_to_display(window.window.as_ref(), id);
                }
                window.show();
                self.settings = Some(window);
            }
            Err(e) => self.invalidate_graphics(e.to_string()),
        }
    }
    fn ui_action(&mut self, action: UiAction, event_loop: &ActiveEventLoop) {
        let result = (|| -> Result<(), AppError> {
            match action {
                UiAction::ApplyConfig(config) => {
                    self.apply_config(config)?;
                    self.ui.accept_config(self.controller.config());
                }
                UiAction::SaveCalibration(id, mm) => {
                    let d = self
                        .controller
                        .displays()
                        .iter()
                        .find(|d| d.id == id)
                        .ok_or_else(|| {
                            AppError(
                                "Display disconnected before calibration could be saved".into(),
                            )
                        })?;
                    if self
                        .settings
                        .as_ref()
                        .and_then(|w| w.display_id(self.platform.as_ref()))
                        != Some(id)
                    {
                        return Err(AppError(
                            "Settings moved to another monitor; calibration was not saved".into(),
                        ));
                    }
                    let mut config = self.controller.config().clone();
                    config.display_calibration.insert(calibration_key(d), mm);
                    self.apply_config(config)?;
                }
                UiAction::MoveToDisplay(id) => {
                    if let Some(w) = &self.settings {
                        self.platform
                            .move_settings_to_display(w.window.as_ref(), id)
                            .map_err(error)?;
                    }
                }
                UiAction::FitReference(px) => {
                    if let Some(w) = &self.settings {
                        let scale = w.window.scale_factor();
                        let width = (px as f64 / scale + 80.).max(680.);
                        let height = (px as f64 / scale * 53.98 / 85.60 + 390.).max(600.);
                        let _ = w.window.request_inner_size(LogicalSize::new(width, height));
                    }
                }
                UiAction::HideAll => {
                    self.controller.lifecycle.hide_all();
                    self.hide_windows();
                }
                UiAction::TogglePause => {
                    let mut c = self.controller.config().clone();
                    c.paused = !c.paused;
                    self.apply_config(c)?;
                    self.hide_windows();
                }
                UiAction::Quit => {
                    self.controller.lifecycle.quit = true;
                    self.shutdown();
                    event_loop.exit();
                }
                UiAction::ForcePopulation => {
                    self.controller.force_population()?;
                }
                UiAction::RetryGraphics => {
                    if self.pending.as_ref().is_some_and(|p| p.timed_out) {
                        return Err(AppError("The driver is still stalled. Restart the utility rather than accumulating initialization threads.".into()));
                    }
                    self.shutdown();
                    self.recovery.retry_by_user();
                    self.resources_dirty = true;
                }
            }
            Ok(())
        })();
        if let Err(e) = result {
            self.report(e.to_string());
        }
    }
    fn utility_action(&mut self, action: UtilityAction, event_loop: &ActiveEventLoop) {
        match action {
            UtilityAction::ToggleVisible => {
                if self.controller.lifecycle.panic_hidden {
                    self.controller.lifecycle.show_all();
                } else {
                    self.ui_action(UiAction::HideAll, event_loop);
                }
            }
            UtilityAction::TogglePause => self.ui_action(UiAction::TogglePause, event_loop),
            UtilityAction::OpenSettings => self.show_settings(event_loop),
            UtilityAction::Quit => self.ui_action(UiAction::Quit, event_loop),
            UtilityAction::ToggleLaunchAtLogin => {
                let mut c = self.controller.config().clone();
                c.launch_at_login = !c.launch_at_login;
                if let Err(e) = self.apply_config(c) {
                    self.report(e.to_string());
                }
            }
            UtilityAction::SetPreset(name) => {
                let id = match name.to_lowercase().as_str() {
                    "realistic" => Some(PresetId::Realistic),
                    "light" | "light infestation" => Some(PresetId::Light),
                    "heavy" | "heavy infestation" => Some(PresetId::Heavy),
                    "nightmare" => Some(PresetId::Nightmare),
                    _ => None,
                };
                if let Some(id) = id {
                    let mut c = self.controller.config().clone();
                    let result = c
                        .apply_preset(id)
                        .map_err(error)
                        .and_then(|()| self.apply_config(c));
                    if let Err(e) = result {
                        self.report(e.to_string());
                    }
                }
            }
        }
    }
    fn pump(&mut self, event_loop: &ActiveEventLoop) {
        match self.platform.poll_events() {
            Ok(events) => {
                for event in events {
                    match event {
                        PlatformEvent::PanicHotkey => self.ui_action(UiAction::HideAll, event_loop),
                        PlatformEvent::QuitRequested => self.ui_action(UiAction::Quit, event_loop),
                        PlatformEvent::UtilityAction(a) => self.utility_action(a, event_loop),
                        PlatformEvent::Suspend => {
                            self.controller.lifecycle.suspended = true;
                            self.hide_windows();
                            self.last_tick = Instant::now();
                        }
                        PlatformEvent::Resume => {
                            self.controller.lifecycle.suspended = false;
                            self.last_tick = Instant::now();
                            self.topology_dirty = true;
                        }
                        PlatformEvent::DisplaysChanged => {
                            self.hide_windows();
                            self.topology_dirty = true;
                        }
                        PlatformEvent::ForegroundChanged(context) => self.foreground = context,
                    }
                }
            }
            Err(e) => {
                self.hide_windows();
                self.shortcut_ready = false;
                self.report(format!(
                    "Native event handling failed; overlays hidden: {e}"
                ));
            }
        }
        if self.controller.lifecycle.quit {
            return;
        }
        let now = Instant::now();
        if now >= self.next_metadata {
            self.next_metadata = now + Duration::from_millis(250);
            self.foreground = self.platform.foreground_context().ok().flatten();
            if self.controller.lifecycle.settings_open {
                self.recent = self
                    .platform
                    .recent_apps()
                    .unwrap_or_default()
                    .into_iter()
                    .map(|a| UiApp {
                        stable_id: a.stable_id,
                        name: a.display_name,
                    })
                    .collect();
            }
            let state = UtilityState {
                visible: !self.controller.lifecycle.panic_hidden,
                paused: self.controller.config().paused,
                launch_at_login: self.controller.config().launch_at_login,
                preset: self.controller.config().preset.label().into(),
                diagnostic: self.message.clone(),
            };
            self.platform.set_utility_state(&state);
        }
        if self.topology_dirty {
            self.hide_windows();
            self.overlays.clear();
            let result = self
                .platform
                .enumerate_displays()
                .map_err(error)
                .and_then(|displays| self.controller.set_displays(displays, false));
            self.topology_dirty = false;
            self.resources_dirty = true;
            self.last_tick = now;
            if let Err(e) = result {
                self.controller.lifecycle.hide_all();
                self.report(e.to_string());
            }
        }
        self.device_progress();
        if self.resources_dirty && self.gpu.is_some() && !self.controller.lifecycle.suspended {
            self.rebuild_resources();
        }
        if self.controller.lifecycle.settings_open && self.settings.is_none() && self.gpu.is_some()
        {
            self.show_settings(event_loop);
        }
        for slot in &self.overlays {
            let decision = visibility(
                self.controller.config(),
                &self.controller.lifecycle,
                slot.display.id,
                self.controller
                    .config()
                    .display_is_enabled(&slot.display.fingerprint),
                slot.host.safe() && self.shortcut_ready,
                self.foreground.as_ref(),
            );
            if let Err(e) = slot.host.set_visible(decision == VisibilityDecision::Show) {
                tracing::warn!("{e}");
            }
        }
        let visible = self.overlays.iter().any(|s| s.host.is_visible());
        let cursor = if self.controller.config().cursor_reaction && visible {
            self.platform.cursor_position().ok().flatten()
        } else {
            None
        };
        let disturbance = self.cursor.sample(
            self.start.elapsed(),
            cursor,
            self.controller.topology().displays(),
        );
        let dt = now.saturating_duration_since(self.last_tick);
        self.last_tick = now;
        if let Err(e) = self.controller.advance(dt, disturbance, visible) {
            self.controller.lifecycle.hide_all();
            self.hide_windows();
            self.report(e.to_string());
        }
        let mut failure = None;
        if let Some(gpu) = &mut self.gpu {
            if let Err(e) = gpu.poll() {
                failure = Some(e.to_string());
            } else {
                self.diagnostics.rendered_count = 0;
                self.diagnostics.draws = 0;
                self.diagnostics.upload_bytes = 0;
                self.diagnostics.render_prep_ms = 0.;
                self.diagnostics.lod_counts = [0; 3];
                for slot in &mut self.overlays {
                    if !slot.host.is_visible() {
                        continue;
                    }
                    let due = slot
                        .host
                        .surface_mut()
                        .is_some_and(|s| s.cadence.take_due(self.start.elapsed()));
                    if !due {
                        continue;
                    }
                    slot.builder.set_debug_colors(
                        self.controller.config().developer_mode
                            && self.controller.config().advanced.show_states,
                    );
                    let instances = match slot.builder.build(
                        self.controller.simulation().visual_states(),
                        &slot.display,
                        self.controller.alpha,
                        self.controller.config().creature_scale,
                    ) {
                        Ok(v) => v,
                        Err(e) => {
                            failure = Some(e.to_string());
                            break;
                        }
                    };
                    match slot
                        .host
                        .present_if_visible(|surface| gpu.render_surface(surface, instances))
                    {
                        Ok(Some(stats)) => {
                            self.recovery.record_presented(self.start.elapsed());
                            self.diagnostics.rendered_count += stats.active_instances as usize;
                            self.diagnostics.draws += stats.submitted_draws;
                            self.diagnostics.upload_bytes += stats.upload_bytes;
                            self.diagnostics.render_prep_ms += stats.cpu_render_prep_ms;
                            self.diagnostics.gpu_ms = stats.gpu_ms;
                            for (a, b) in
                                self.diagnostics.lod_counts.iter_mut().zip(stats.lod_counts)
                            {
                                *a += b;
                            }
                        }
                        Ok(None) => (),
                        Err(e) => {
                            failure = Some(e.to_string());
                            break;
                        }
                    }
                }
            }
        }
        if let Some(e) = failure {
            self.invalidate_graphics(e);
        }
        if now >= self.next_ui {
            self.next_ui = now + Duration::from_secs_f64(1. / 60.);
            if let Some(w) = &self.settings
                && !w.occluded
            {
                w.window.request_redraw();
            }
        }
        let mut deadline = now + Duration::from_millis(250);
        for slot in &mut self.overlays {
            if slot.host.is_visible()
                && let Some(s) = slot.host.surface_mut()
            {
                deadline = deadline.min(self.start + s.cadence.next_deadline());
            }
        }
        if self.settings.as_ref().is_some_and(|w| !w.occluded) {
            deadline = deadline.min(self.next_ui);
        }
        if let Some(retry) = self.recovery.deadline() {
            deadline = deadline.min(self.start + retry);
        }
        // Never spin on a deadline from before a sleep/resume or a bounded retry.
        event_loop.set_control_flow(ControlFlow::WaitUntil(
            deadline.max(Instant::now() + Duration::from_millis(1)),
        ));
    }
    fn update_preview(&mut self) {
        self.diagnostics.creature_count = self.controller.simulation().len();
        self.diagnostics.seed = self.controller.config().advanced.seed;
        let t = self.controller.last_simulation;
        self.diagnostics.simulation_ms = t.total_ms;
        self.diagnostics.behavior_ms = t.behavior_ms;
        self.diagnostics.spatial_ms = t.spatial_ms;
        self.diagnostics.trails_ms = t.trails_ms;
        self.diagnostics.dropped_ticks = self.controller.dropped_ticks;
        self.preview.creatures.clear();
        self.preview.trails.clear();
        self.preview.display = None;
        if !self.controller.config().developer_mode {
            return;
        }
        let selected = self
            .settings
            .as_ref()
            .and_then(|w| w.display_id(self.platform.as_ref()));
        let display = self
            .controller
            .topology()
            .displays()
            .iter()
            .find(|d| Some(d.id) == selected)
            .or_else(|| self.controller.topology().displays().first());
        let Some(display) = display else {
            return;
        };
        self.preview.display = Some(display.id);
        self.preview.size_mm = display.size_mm().to_array();
        self.preview.grid_mm = self.controller.simulation().spatial_cell_mm();
        for s in self
            .controller
            .simulation()
            .visual_states()
            .iter()
            .filter(|s| s.display == display.id)
        {
            let pixels = s.body_length_mm
                / self
                    .controller
                    .displays()
                    .iter()
                    .find(|d| d.id == s.display)
                    .map_or(0.25, |d| d.calibration.mm_per_physical_px);
            self.preview.creatures.push(PreviewCreature {
                id: s.id.0,
                position_mm: [s.position_mm.x, s.position_mm.y],
                heading: s.heading_rad,
                behavior: s.behavior as u8,
                lod: if pixels < 12. {
                    0
                } else if pixels < 32. {
                    1
                } else {
                    2
                },
            });
        }
        if self.controller.config().advanced.show_trails
            && let Some(trails) = self.controller.simulation().trails()
        {
            trails.visit_cells(display.id, |p, v| self.preview.trails.push([p.x, p.y, v]));
        }
    }
    fn redraw_settings(&mut self, event_loop: &ActiveEventLoop) {
        self.update_preview();
        let (Some(window), Some(gpu)) = (&mut self.settings, &self.gpu) else {
            return;
        };
        let context = SettingsContext {
            config: self.controller.config(),
            displays: self.controller.displays(),
            profiles: self.controller.profiles(),
            diagnostics: &self.diagnostics,
            recent_apps: &self.recent,
            preview: self
                .controller
                .config()
                .developer_mode
                .then_some(&self.preview),
            window_display: window.display_id(self.platform.as_ref()),
            message: self.message.as_deref(),
            read_only: self.read_only,
        };
        match window.draw(gpu, &mut self.ui, &context) {
            Ok(actions) => {
                for a in actions {
                    self.ui_action(a, event_loop)
                }
            }
            Err(e) => self.invalidate_graphics(e.to_string()),
        }
    }
}
impl Drop for Runtime {
    fn drop(&mut self) {
        self.shutdown();
    }
}
struct Desktop {
    runtime: Option<Runtime>,
    profiles: Arc<RuntimeProfileBundle>,
    proxy: EventLoopProxy<()>,
    fatal: Option<String>,
}
impl ApplicationHandler for Desktop {
    fn resumed(&mut self, event_loop: &ActiveEventLoop) {
        if self.runtime.is_none() {
            match Runtime::new(self.proxy.clone(), self.profiles.clone()) {
                Ok(r) => self.runtime = Some(r),
                Err(e) => {
                    self.fatal = Some(e.to_string());
                    event_loop.exit();
                }
            }
        } else if let Some(r) = &mut self.runtime {
            r.controller.lifecycle.suspended = false;
            r.last_tick = Instant::now();
            r.topology_dirty = true;
        }
    }
    fn suspended(&mut self, _: &ActiveEventLoop) {
        if let Some(r) = &mut self.runtime {
            r.controller.lifecycle.suspended = true;
            r.hide_windows();
            r.last_tick = Instant::now();
        }
    }
    fn user_event(&mut self, _: &ActiveEventLoop, _: ()) {}
    fn window_event(&mut self, event_loop: &ActiveEventLoop, id: WindowId, event: WindowEvent) {
        let Some(r) = &mut self.runtime else {
            return;
        };
        if !r.settings.as_ref().is_some_and(|w| w.window.id() == id) {
            return;
        }
        if matches!(event, WindowEvent::CloseRequested | WindowEvent::Destroyed) {
            r.settings = None;
            r.controller.lifecycle.close_settings();
            return;
        }
        if let Some(w) = &mut r.settings {
            let _ = w.event(&event);
        }
        match event {
            WindowEvent::RedrawRequested => r.redraw_settings(event_loop),
            WindowEvent::Resized(_) | WindowEvent::ScaleFactorChanged { .. } => {
                if let (Some(w), Some(gpu)) = (&mut r.settings, &r.gpu)
                    && let Err(e) = w.resize(gpu)
                {
                    r.invalidate_graphics(e.to_string());
                }
            }
            _ => (),
        }
    }
    fn about_to_wait(&mut self, event_loop: &ActiveEventLoop) {
        if let Some(r) = &mut self.runtime {
            r.pump(event_loop);
        }
    }
    fn exiting(&mut self, _: &ActiveEventLoop) {
        if let Some(r) = &mut self.runtime {
            r.shutdown();
        }
    }
}
pub fn run(profiles: Arc<RuntimeProfileBundle>) -> Result<(), AppError> {
    #[cfg(target_os = "windows")]
    platform_windows::enable_per_monitor_v2().map_err(error)?;
    let mut builder = EventLoop::builder();
    #[cfg(target_os = "macos")]
    {
        use winit::platform::macos::{ActivationPolicy, EventLoopBuilderExtMacOS};
        builder.with_activation_policy(ActivationPolicy::Accessory);
    }
    let event_loop = builder.build().map_err(error)?;
    let mut app = Desktop {
        runtime: None,
        profiles,
        proxy: event_loop.create_proxy(),
        fatal: None,
    };
    event_loop.run_app(&mut app).map_err(error)?;
    if let Some(message) = app.fatal {
        Err(AppError(message))
    } else {
        Ok(())
    }
}
