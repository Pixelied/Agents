//! Manual native acceptance aid: normal window event delivery, no hooks or input injection.
//! Event counts never automatically qualify the separate insect-overlay process.
#[path = "support/probe_state.rs"]
mod probe_state;
use clap::Parser;
use probe_state::{Counts, Event};
use std::{
    io::Write,
    path::{Path, PathBuf},
    time::{Duration, Instant},
};
use winit::{
    application::ApplicationHandler,
    dpi::LogicalSize,
    event::{ElementState, MouseButton, WindowEvent},
    event_loop::{ActiveEventLoop, ControlFlow, EventLoop},
    keyboard::{Key, NamedKey},
    window::{Window, WindowId},
};

fn write_snapshot(
    output: &Path,
    counts: &Counts,
    complete: bool,
) -> Result<(), Box<dyn std::error::Error>> {
    let parent = output
        .parent()
        .filter(|p| !p.as_os_str().is_empty())
        .unwrap_or(Path::new("."));
    std::fs::create_dir_all(parent)?;
    let report = serde_json::json!({
        "schema": 1,
        "tool": "Insect Realism underlying-window input probe",
        "app_version": env!("CARGO_PKG_VERSION"),
        "host_os": std::env::consts::OS,
        "host_arch": std::env::consts::ARCH,
        "counts": counts,
        "complete": complete,
        "native_input_qualified": false,
        "overlay_presence_verified": false,
        "limitations": [
            "Counts only events delivered to this ordinary window; does not inspect another process.",
            "No typed text, key names, cursor coordinates or screen pixels are saved.",
            "Mouse press/release counts cover the primary (left) button; paired clicks do not certify OS double-click semantics.",
            "Record the overlay binary hash and manually attest whether it was visibly active for each sequence.",
            "Completion means the operator closed this probe, not that the overlay passed its acceptance matrix."
        ]
    });
    let mut file = tempfile::NamedTempFile::new_in(parent)?;
    serde_json::to_writer_pretty(&mut file, &report)?;
    writeln!(file)?;
    file.as_file().sync_all()?;
    file.persist(output)?;
    Ok(())
}
#[derive(Parser)]
#[command(
    about = "Receive ordinary native input beneath an overlay and save counts, not typed text. Escape saves and exits."
)]
struct Args {
    /// New output JSON path; an existing report is never silently overwritten by a new run.
    #[arg(long)]
    output: PathBuf,
}
struct Probe {
    counts: Counts,
    window: Option<Window>,
    output: PathBuf,
    next_snapshot: Instant,
    error: Option<String>,
}
impl Probe {
    fn snapshot(&mut self, complete: bool, event_loop: &ActiveEventLoop) {
        if let Err(error) = write_snapshot(&self.output, &self.counts, complete) {
            self.error = Some(error.to_string());
            event_loop.exit();
        }
        if let Some(window) = &self.window {
            window.set_title(&format!(
                "Input probe | clicks {} | drag moves {} | scroll {} | keys {} | focus losses {} | Esc saves",
                self.counts.mouse_presses, self.counts.drag_moves, self.counts.scroll_events,
                self.counts.key_presses, self.counts.focus_lost,
            ));
        }
    }
}
impl ApplicationHandler for Probe {
    fn resumed(&mut self, event_loop: &ActiveEventLoop) {
        if self.window.is_none() {
            match event_loop.create_window(
                Window::default_attributes()
                    .with_title(
                        "Insect input probe - click, drag, scroll and type here; Escape saves",
                    )
                    .with_inner_size(LogicalSize::new(860.0, 420.0)),
            ) {
                Ok(window) => self.window = Some(window),
                Err(error) => {
                    self.error = Some(error.to_string());
                    event_loop.exit();
                }
            }
        }
    }
    fn window_event(&mut self, event_loop: &ActiveEventLoop, id: WindowId, event: WindowEvent) {
        if !self.window.as_ref().is_some_and(|w| w.id() == id) {
            return;
        }
        match event {
            WindowEvent::MouseInput {
                state,
                button: MouseButton::Left,
                ..
            } => self.counts.receive(if state == ElementState::Pressed {
                Event::Press
            } else {
                Event::Release
            }),
            WindowEvent::CursorMoved { .. } => self.counts.receive(Event::Motion),
            WindowEvent::MouseWheel { .. } => self.counts.receive(Event::Scroll),
            WindowEvent::Focused(value) => self.counts.receive(Event::Focus(value)),
            WindowEvent::KeyboardInput {
                event,
                is_synthetic: false,
                ..
            } if event.state == ElementState::Pressed => {
                self.counts.receive(Event::Key {
                    repeat: event.repeat,
                });
                if event.logical_key == Key::Named(NamedKey::Escape) {
                    self.snapshot(true, event_loop);
                    event_loop.exit();
                }
            }
            WindowEvent::CloseRequested => {
                self.snapshot(true, event_loop);
                event_loop.exit();
            }
            _ => (),
        }
    }
    fn about_to_wait(&mut self, event_loop: &ActiveEventLoop) {
        if event_loop.exiting() {
            return;
        }
        let now = Instant::now();
        if now >= self.next_snapshot {
            self.snapshot(false, event_loop);
            self.next_snapshot = now + Duration::from_millis(500);
        }
        event_loop.set_control_flow(ControlFlow::WaitUntil(self.next_snapshot));
    }
}
fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args = Args::parse();
    let output = std::path::absolute(args.output)?;
    if output.exists() {
        return Err("Choose a new --output path to preserve the earlier evidence".into());
    }
    #[cfg(target_os = "windows")]
    platform_windows::enable_per_monitor_v2()?;
    let event_loop = EventLoop::new()?;
    let mut app = Probe {
        counts: Counts::default(),
        window: None,
        output,
        next_snapshot: Instant::now(),
        error: None,
    };
    println!(
        "Keep this normal window under the visibly active insect overlay. Click twice, drag, scroll, move and type. Counts appear in its title; typed text is never logged. Test panic with this window focused. Escape or Close saves and exits. This tool does not automatically mark native acceptance passed."
    );
    event_loop.run_app(&mut app)?;
    if let Some(error) = app.error {
        return Err(error.into());
    }
    println!("Saved received-event evidence to {}", app.output.display());
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn received_events_are_saved_without_auto_qualifying_native_input() {
        let temp = tempfile::tempdir().unwrap();
        let out = temp.path().join("probe.json");
        let mut counts = probe_state::Counts::default();
        counts.receive(probe_state::Event::Press);
        write_snapshot(&out, &counts, false).unwrap();
        assert!(
            out.is_file(),
            "received event evidence must survive process exit"
        );
        let report: serde_json::Value =
            serde_json::from_slice(&std::fs::read(&out).unwrap()).unwrap();
        assert_eq!(report["counts"]["mouse_presses"], 1);
        assert_eq!(report["native_input_qualified"], false);
        assert_eq!(report["complete"], false);
        write_snapshot(&out, &counts, true).unwrap();
        let report: serde_json::Value =
            serde_json::from_slice(&std::fs::read(&out).unwrap()).unwrap();
        assert_eq!(report["native_input_qualified"], false);
        assert_eq!(report["complete"], true);
    }
}
