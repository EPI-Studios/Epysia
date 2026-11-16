#!/usr/bin/env bash
# Bootstraps the Rust toolchain this language pack needs: rustup (if
# missing), a stable toolchain, and the wasm32-unknown-unknown target.
# RustRuntime checks for exactly these two things at load time (see
# CargoToolchain.java) and points here when either is absent.
set -euo pipefail

if ! command -v rustup >/dev/null 2>&1; then
    echo "rustup not found, installing (https://rustup.rs)..."
    curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --default-toolchain stable
    # shellcheck disable=SC1090
    source "${HOME}/.cargo/env"
fi

echo "rustup: $(rustup --version)"
echo "cargo:  $(cargo --version)"

rustup target add wasm32-unknown-unknown

echo "wasm32-unknown-unknown target ready."
