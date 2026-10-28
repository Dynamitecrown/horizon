//! Compiles the spike's Java probes against the fixture's source (types only: `-implicit:none` keeps
//! the fixture's classes out, so at run time they come from the fixture JAR itself).
use std::path::PathBuf;
use std::process::Command;

fn main() {
    let root = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../..");
    let spike = root.join("mods/horizon/src/bridge-spike/java/dev/horizonlab/bridge/spike");
    let sources = [spike.join("HostProbe.java"), spike.join("TraceMain.java")];
    let fixture = root.join("mods/horizon/src/neoforge-fixture/java");
    let out = PathBuf::from(std::env::var("OUT_DIR").unwrap()).join("classes");
    println!("cargo:rerun-if-changed={}", spike.display());
    println!(
        "cargo:rerun-if-changed={}",
        fixture
            .join("dev/horizonlab/bridge/CounterState.java")
            .display()
    );
    println!("cargo:rerun-if-env-changed=JAVA_HOME");
    let javac = std::env::var_os("JAVA_HOME")
        .map(|home| PathBuf::from(home).join("bin").join("javac"))
        .unwrap_or_else(|| "javac".into());
    let status = Command::new(&javac)
        .args(["--release", "25", "-Werror", "-implicit:none", "-d"])
        .arg(&out)
        .arg("--source-path")
        .arg(&fixture)
        .args(&sources)
        .status()
        .unwrap_or_else(|e| {
            panic!(
                "cannot run {}: {e} (set JAVA_HOME to a JDK 25)",
                javac.display()
            )
        });
    assert!(status.success(), "javac failed on {}", spike.display());
    println!("cargo:rustc-env=SPIKE_CLASSES={}", out.display());
}
