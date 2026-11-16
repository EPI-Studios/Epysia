# lang/rust

Rust scripting for Epysia. Scripts are built with cargo to
`wasm32-unknown-unknown` and run through GraalWasm.

The module is its own Gradle build that includes the engine with
`includeBuild('../..')`, so it is not part of the root build and the release
packaging does not pick it up yet. It needs the same `box3d-java` and
`imgui-java` checkouts as the root build.

```sh
./gradlew -p lang/rust build
```

No toolchain? `tools/setup-toolchain.sh` (`tools/setup-toolchain.ps1` on
Windows) installs rustup and the `wasm32-unknown-unknown` target.

## Writing a component

```rust
use epysia_sdk::{epysia_component, Behaviour, Input, Transform, Vec3};

#[epysia_component(category = "Scripts")]
pub struct Mover {
    #[export(default = 5.0, label = "Speed")]
    pub speed: f32,
}

impl Behaviour for Mover {
    fn on_update(&mut self, transform: &mut Transform, input: &Input, delta_seconds: f32) {
        let mut direction = Vec3::ZERO;
        if input.key("D") {
            direction.x += 1.0;
        }
        transform.translate(direction * self.speed * delta_seconds);
    }
}
```

The `.rs` files in the scripts folder are copied into a managed crate next to
it, `.epysia-rust-build/`, which cargo builds. Every `#[epysia_component]`
struct becomes a component.

## The ABI

Every call goes from Java into wasm. Buffers come back packed in one `i64`,
`(ptr as u32) << 32 | (len as u32)`, and the host frees them with
`epysia_dealloc`. The host reserves its own buffers with `epysia_alloc`.

- `memory`, `epysia_alloc(len) -> ptr`, `epysia_dealloc(ptr, len)`
- `epysia_manifest() -> packed`, JSON `[{name, category, description,
  properties: [{name, label, type, default, min, max, step, color}]}]`
- `epysia_new(component_index) -> handle`
- `epysia_read(handle, name_ptr, name_len) -> packed`
- `epysia_write(handle, name_ptr, name_len, value_ptr, value_len)`
- `epysia_on_start(handle, transform_ptr, transform_len) -> packed`
- `epysia_on_update(handle, delta_seconds, transform_ptr, transform_len,
  input_ptr, input_len) -> packed`
- `epysia_on_fixed_update(handle, fixed_step_seconds, transform_ptr,
  transform_len) -> packed`
- `epysia_on_destroy(handle)`

Property values cross as JSON. The lifecycle calls send the Transform in as
`{"position":[x,y,z],"scale":[x,y,z],"worldPosition":[x,y,z]}` and get the
same shape back. `on_update` also sends the active keys and mouse buttons,
the cursor, the mouse delta and the scroll.

## Limitations

- Exported properties are `bool`, `i32`, `f32` and `String` only.
- wasm cannot call back into the host, so no `find()`, no other GameObject
  and no EngineServices. A component only sees its own Transform and input.
- Only position and scale round-trip, no rotation yet.
- Components are found by a text scan, so keep `#[epysia_component]` and the
  `struct` line on their own lines.
- No generated IDE stub, rust-analyzer reads the SDK crate directly.

## GraalWasm

With `org.graalvm.polyglot:wasm:24.2.1`, `context.eval()` already
instantiates a module without imports and returns its exports, and
`newInstance()` on it throws. Host functions cannot be passed as imports
through the public polyglot API, which is why there are no callbacks.

## Performance

About 440 µs per `onUpdate` call against 40 µs for the same Python component,
nearly all of it `serde_json` running in interpreted wasm.

## Verifying

```sh
./gradlew -p lang/rust build
cargo build --manifest-path lang/rust/sdk/epysia-sdk/Cargo.toml --target wasm32-unknown-unknown --release
./gradlew -p lang/rust test
```
