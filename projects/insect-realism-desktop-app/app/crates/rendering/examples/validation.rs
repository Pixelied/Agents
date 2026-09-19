use clap::Parser;
#[derive(Parser)]
struct Args {
    #[arg(long, default_value = "core")]
    scene: String,
    #[arg(long, default_value = "target/validation")]
    output: std::path::PathBuf,
}
fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args = Args::parse();
    let profiles = creature_profile::RuntimeProfileBundle::decode(include_bytes!(
        "../../../assets/creature-profiles/runtime-profiles.bin"
    ))?;
    let mut gpu = pollster::block_on(rendering::Renderer::new_headless(&profiles.creatures[0]))?;
    let report = rendering::run_validation(&mut gpu, &profiles, &args.scene, &args.output)?;
    println!(
        "{} actual GPU images; {} / {}",
        report.images.len(),
        report.backend,
        report.device
    );
    Ok(())
}
