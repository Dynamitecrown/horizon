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

    // The bridge host (M1): our own stand-ins and HostWorld, which need nothing but the JDK.
    let host = root.join("mods/horizon/src/bridge-host/java");
    let host_out = PathBuf::from(std::env::var("OUT_DIR").unwrap()).join("host-classes");
    println!("cargo:rerun-if-changed={}", host.display());
    let mut sources = Vec::new();
    let mut folders = vec![host.clone()];
    while let Some(folder) = folders.pop() {
        for entry in std::fs::read_dir(&folder).unwrap() {
            let path = entry.unwrap().path();
            if path.is_dir() {
                println!("cargo:rerun-if-changed={}", path.display());
                folders.push(path);
            } else if path.extension().is_some_and(|e| e == "java") {
                sources.push(path);
            }
        }
    }
    let status = Command::new(&javac)
        .args(["--release", "25", "-Xlint:all", "-Werror", "-d"])
        .arg(&host_out)
        .args(&sources)
        .status()
        .unwrap();
    assert!(status.success(), "javac failed on {}", host.display());
    println!("cargo:rustc-env=SPIKE_HOST_CLASSES={}", host_out.display());
}
