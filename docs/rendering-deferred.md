# Rendering — Deferred / Out of Scope for v1

Things deliberately not in the initial rendering pipeline design. Each one has a real cost, a known fallback, and a clear signal for when to revisit.

## Sort & batching optimizations

### Texture-id grouping in the opaque sort key
Sorting by `(depth, pipelineId, textureId)` reduces texture rebinds inside the same pipeline. Real win only at ~10k+ draws per pass.
- **Fallback:** secondary sort is `pipelineId` only. Same pipeline means same shader; texture rebinds are cheap compared to pipeline switches.
- **Revisit when:** profiler shows texture-bind cost exceeds 0.5 ms in a real scene.

### Render-target-switching cost factored into sort
Some engines reorder passes globally to minimize framebuffer switches. We don't — passes execute in declared stage order.
- **Fallback:** stages already group everything sharing a target. One swap per stage.
- **Revisit when:** doing offscreen-per-light shadow maps or many full-screen passes per frame.

### Front-to-back sort for WORLD_2D
Considered, rejected. 2D doesn't depth-test by default; sorting by Y / layer is the semantic ordering games want.
- **Revisit when:** 2D content adopts depth testing (e.g., isometric with z-cutout).

## Drawable component types not in v1

- `LineRenderer` — debug lines, gizmos. Add with the editor/debug overlay work.
- `TextRenderer` — needs a font atlas pipeline first.
- `ParticleRenderer` — needs an emitter component model + GPU/CPU sim decision.
- `SkinnedMeshRenderer` — needs a skeleton/animation component first.

## Lights

Phase 3. `DirectionalLight`, `PointLight`, `SpotLight` components live in the lights brainstorm, not this one. Shadows are a separate decision (shadow map cascades, atlasing, vs. ray-traced).

## Frame structure complexity

### Full render graph (Frostbite / Granite style)
Passes declaring read/write resources, engine auto-scheduling and inserting barriers, transient memory aliasing — explicitly rejected for v1.
- **Why:** months of engine work, only pays off with many offscreen targets and a real Vulkan backend.
- **Fallback:** named stages + ordered passes (the current design). Translates cleanly to a render graph later if needed.
- **Revisit when:** we have >6 offscreen passes per frame, or Vulkan synchronization becomes the bottleneck.

### Multi-threaded command collection
Render systems are single-threaded for v1.
- **Fallback:** the API is already shaped for parallel collection — `FrameBuilder.submit` could be a per-thread accumulator that merges in `endFrame`. Render systems produce DrawCommands as immutable values.
- **Revisit when:** collection time exceeds the GPU's per-frame budget, or thousands of draw calls dominate CPU.

### Multi-threaded command recording (Vulkan secondary command buffers)
GL has one thread for GL calls anyway. Defer until Vulkan.

## Material features

### Shader graph / node editor
No. Material = raw GLSL + uniforms + includes. Visual graphs add tooling complexity for marginal authoring benefit.

### Compute shaders
Not in v1. Add when GPU particles or post-fx require them.

### Mesh shaders / GPU-driven rendering
Vulkan-only feature, far future.

### Material variants by macro defines
Some engines pre-compile shader variants for `WITH_FOG`, `WITH_SKINNING`, etc. We don't — one material → one program per pass.
- **Fallback:** if you want fog-enabled and fog-disabled, write two materials.
- **Revisit when:** the same shader needs 5+ variants and Material proliferation becomes annoying.

## Backend

### Vulkan backend
Phase last. Mid-level interface is designed Vulkan-shaped so the swap is mechanical.

### macOS / Windows native classifiers
Linux-only for now. One-line Gradle change when a non-Linux contributor appears.

### Headless / null backend
Useful for CI and tests. Implement when there are tests that need it.
