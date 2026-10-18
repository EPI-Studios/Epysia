package fr.epistudio.epysia.lang.rust;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

final class CargoToolchain {

    private CargoToolchain() {
    }

    static Optional<String> unavailableReason() {
        if (!runsSuccessfully("cargo", "--version")) {
            return Optional.of("cargo was not found on PATH, install Rust from https://rustup.rs "
                    + "or run lang/rust/tools/setup-toolchain.sh (setup-toolchain.ps1 on Windows)");
        }
        if (!wasmTargetInstalled()) {
            return Optional.of("the wasm32-unknown-unknown target is not installed, run "
                    + "'rustup target add wasm32-unknown-unknown' or lang/rust/tools/setup-toolchain "
                    + "(.sh on Linux and macOS, .ps1 on Windows)");
        }
        return Optional.empty();
    }

    private static boolean wasmTargetInstalled() {
        try {
            Process process = new ProcessBuilder("rustup", "target", "list", "--installed")
                    .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();
            return output.contains("wasm32-unknown-unknown");
        } catch (IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return true;
        }
    }

    private static boolean runsSuccessfully(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
}
