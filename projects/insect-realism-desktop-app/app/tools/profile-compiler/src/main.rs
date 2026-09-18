use clap::{Parser, Subcommand};
use std::{fs, path::PathBuf};
#[derive(Parser)]
#[command(about = "Compile audited Mega Pack data into deterministic offline profiles")]
struct Cli {
    #[command(subcommand)]
    command: Command,
}
#[derive(Subcommand)]
enum Command {
    Compile {
        #[arg(long)]
        input: PathBuf,
        #[arg(long)]
        output: PathBuf,
        #[arg(long)]
        report: PathBuf,
        #[arg(long)]
        export_input: Option<PathBuf>,
    },
    Verify {
        #[arg(long)]
        bundle: PathBuf,
    },
}
fn run() -> Result<(), Box<dyn std::error::Error>> {
    match Cli::parse().command {
        Command::Compile {
            input,
            output,
            report,
            export_input,
        } => {
            let input = profile_compiler::load_input(&input)?;
            let c = profile_compiler::compile(&input)?;
            if let Some(path) = export_input {
                profile_compiler::export_input(&input, &path)?;
            }
            fs::write(output, &c.bytes)?;
            fs::write(report, serde_json::to_vec_pretty(&c.report)?)?;
            println!(
                "profile={} schema={} bytes={} sha256={}",
                c.bundle.profile_version,
                c.bundle.schema_version,
                c.bytes.len(),
                profile_compiler::digest(&c.bytes)
            );
        }
        Command::Verify { bundle } => {
            let bytes = fs::read(bundle)?;
            let b = creature_profile::RuntimeProfileBundle::decode(&bytes)?;
            println!(
                "verified profile={} schema={} sha256={}",
                b.profile_version,
                b.schema_version,
                profile_compiler::digest(&bytes)
            );
        }
    }
    Ok(())
}
fn main() {
    if let Err(e) = run() {
        eprintln!("{e}");
        std::process::exit(1);
    }
}
