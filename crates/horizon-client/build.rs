//! Embeds Horizon's icon (`packaging/icons/horizon.ico`) in the Windows executables, as icon
//! resource 1: Explorer and the task bar show it, and the window takes it as its icon. With the
//! `bridge` feature, also compiles the bridge's Java host (`mods/horizon/src/bridge-host`).
fn main() {
    let dir = std::env::var("CARGO_MANIFEST_DIR").unwrap_or_default();
    let ico = format!("{dir}/../../packaging/icons/horizon.ico");
    println!("cargo:rerun-if-changed={ico}");
    #[cfg(windows)]
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("windows") {
        let mut resource = winresource::WindowsResource::new();
        resource.set_icon(&ico);
        if let Err(e) = resource.compile() {
            panic!("embedding {ico}: {e}");
        }
    }
    if std::env::var_os("CARGO_FEATURE_BRIDGE").is_some() {
        bridge_host(&dir);
    }
}

/// javac on the bridge's stand-ins and `HostWorld`, which need nothing but the JDK.
fn bridge_host(dir: &str) {
    use std::path::PathBuf;
    let host = PathBuf::from(dir).join("../../mods/horizon/src/bridge-host/java");
    let out = PathBuf::from(std::env::var("OUT_DIR").unwrap()).join("bridge-host");
    println!("cargo:rerun-if-env-changed=JAVA_HOME");
    let mut sources = Vec::new();
    let mut folders = vec![host.clone()];
    while let Some(folder) = folders.pop() {
        println!("cargo:rerun-if-changed={}", folder.display());
        for entry in std::fs::read_dir(&folder).unwrap() {
            let path = entry.unwrap().path();
            if path.is_dir() {
                folders.push(path);
            } else if path.extension().is_some_and(|e| e == "java") {
                println!("cargo:rerun-if-changed={}", path.display());
                sources.push(path);
            }
        }
    }
    let javac = std::env::var_os("JAVA_HOME")
        .map(|home| PathBuf::from(home).join("bin").join("javac"))
        .unwrap_or_else(|| "javac".into());
    let status = std::process::Command::new(&javac)
        .args(["--release", "25", "-Xlint:all", "-Werror", "-d"])
        .arg(&out)
        .args(&sources)
        .status()
        .unwrap_or_else(|e| {
            panic!(
                "cannot run {}: {e} (set JAVA_HOME to a JDK 25)",
                javac.display()
            )
        });
    assert!(status.success(), "javac failed on {}", host.display());
    println!(
        "cargo:rustc-env=HORIZON_BRIDGE_HOST_CLASSES={}",
        out.display()
    );
}
