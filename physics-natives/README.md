# physics-natives

Rust crate wrapping `rapier3d` 0.22 as a C ABI. Compiled artefacts ship pre-built under
`../src/main/resources/natives/<platform>/`, so a normal Gradle build does NOT call
cargo.

## Rebuild for host

```
./gradlew physicsNativesBuild
```

Requires `cargo` (rustup-installed or on PATH).

## Cross-build everything (Linux host)

Install `cargo-zigbuild` once:

```
cargo install cargo-zigbuild
# zig must also be on PATH (https://ziglang.org/download/)
rustup target add x86_64-unknown-linux-gnu aarch64-unknown-linux-gnu \
                  x86_64-pc-windows-gnu \
                  x86_64-apple-darwin aarch64-apple-darwin
```

Then:

```
./gradlew physicsNativesCrossBuild
```
