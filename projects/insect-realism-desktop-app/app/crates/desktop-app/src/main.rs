#![cfg_attr(target_os = "windows", windows_subsystem = "windows")]
#[cfg(feature = "native-ui")]
fn main() {
    if let Err(error) = entry() {
        eprintln!("Insect Realism: {error}");
        std::process::exit(1);
    }
}
#[cfg(feature = "native-ui")]
fn entry() -> Result<(), Box<dyn std::error::Error>> {
    use clap::Parser;
    #[derive(Parser)]
    #[command(
        version,
        about = "Insect Realism desktop utility. No arguments starts the native menu/tray utility."
    )]
    struct Args {
        /// Developer-only: render deterministic ant scenes into this directory.
        #[arg(long)]
        validate_visuals: Option<std::path::PathBuf>,
        /// Developer-only: render the actual settings/calibration GUI offscreen.
        #[arg(long)]
        validate_ui: Option<std::path::PathBuf>,
        /// Developer-only: measure real simulation and completed offscreen GPU work.
        #[arg(long, value_enum, requires = "json", conflicts_with_all = ["validate_visuals", "validate_ui", "soak_seconds"])]
        benchmark_scenario: Option<desktop_app::benchmark::Scenario>,
        #[arg(long, default_value_t = 600)]
        benchmark_frames: u32,
        #[arg(long, default_value_t = 60)]
        benchmark_warmup: u32,
        #[arg(long)]
        json: Option<std::path::PathBuf>,
        /// Developer-only: wall-clock lifecycle stress, with separate simulated-time accounting.
        #[arg(long, requires = "json", conflicts_with_all = ["benchmark_scenario", "validate_visuals", "validate_ui"])]
        soak_seconds: Option<u64>,
        #[arg(long, default_value_t = 120)]
        soak_cycle_frames: u32,
        /// Internal per-user MSI removal hook; does not launch the desktop utility.
        #[arg(long, hide = true, conflicts_with_all = ["validate_visuals", "validate_ui", "benchmark_scenario", "soak_seconds", "json"])]
        uninstall_cleanup: bool,
    }
    let args = Args::parse();
    if args.uninstall_cleanup {
        #[cfg(target_os = "windows")]
        {
            platform_windows::clear_startup_entry()?;
            return Ok(());
        }
        #[cfg(not(target_os = "windows"))]
        {
            return Err("Windows-only installer cleanup is unavailable on this host".into());
        }
    }
    let profiles = std::sync::Arc::new(creature_profile::RuntimeProfileBundle::decode(
        include_bytes!("../../../assets/creature-profiles/runtime-profiles.bin"),
    )?);
    if let Some(output) = args.validate_visuals {
        let mut renderer =
            pollster::block_on(rendering::Renderer::new_headless(&profiles.creatures[0]))?;
        rendering::run_validation(&mut renderer, &profiles, "all", &output)?;
        return Ok(());
    }
    if let Some(output) = args.validate_ui {
        return desktop_app::validation::ui_scenes(&profiles, &output);
    }
    if let Some(scenario) = args.benchmark_scenario {
        return desktop_app::benchmark::run(
            profiles,
            scenario,
            args.benchmark_frames,
            args.benchmark_warmup,
            args.json.as_deref().ok_or("--json is required")?,
        );
    }
    if let Some(seconds) = args.soak_seconds {
        return desktop_app::soak::run(
            profiles,
            seconds,
            args.soak_cycle_frames,
            args.json.as_deref().ok_or("--json is required")?,
        );
    }
    if args.json.is_some() {
        return Err("--json requires a developer measurement mode".into());
    }
    desktop_app::app::run(profiles)?;
    Ok(())
}
#[cfg(not(feature = "native-ui"))]
fn main() {
    eprintln!(
        "This build contains core tests only; enable the native-ui feature to launch the utility."
    );
    std::process::exit(2);
}
