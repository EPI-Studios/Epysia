# Bootstraps the Rust toolchain this language pack needs: rustup (if
# missing), a stable toolchain, and the wasm32-unknown-unknown target.
# RustRuntime checks for exactly these two things at load time (see
# CargoToolchain.java) and points here when either is absent.
$ErrorActionPreference = 'Stop'

if (-not (Get-Command rustup -ErrorAction SilentlyContinue)) {
    Write-Host "rustup not found, installing (https://rustup.rs)..."
    $installer = Join-Path $env:TEMP 'rustup-init.exe'
    Invoke-WebRequest -Uri 'https://win.rustup.rs/x86_64' -OutFile $installer
    & $installer -y --default-toolchain stable
    Remove-Item $installer -Force
    $env:Path = "$env:USERPROFILE\.cargo\bin;$env:Path"
}

Write-Host "rustup: $(rustup --version)"
Write-Host "cargo:  $(cargo --version)"

rustup target add wasm32-unknown-unknown

Write-Host "wasm32-unknown-unknown target ready."
