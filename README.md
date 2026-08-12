<div align="center">

# Epysia

<img src="https://img.shields.io/badge/JDK-25-orange?style=for-the-badge&logo=openjdk&logoColor=white" alt="JDK 25" />
<img src="https://img.shields.io/badge/OpenGL-4.3-blue?style=for-the-badge&logo=opengl&logoColor=white" alt="OpenGL 4.3" />
<img src="https://img.shields.io/badge/Physics-Box3D-red?style=for-the-badge" alt="Box3D" />
<img src="https://img.shields.io/badge/license-MIT-lightgrey?style=for-the-badge" alt="MIT" />

A 3D and 2D game engine, written in Java

</div>

Epysia builds games as a tree of GameObjects and components, lets you script
behaviour in plain Java, and exports a native executable with its own trimmed
runtime baked in. Nobody playing your game needs to know or care that a JVM is
involved.

## Installing

Epysia is an app, not a library. It isn't published to Maven, so don't go
looking for it there.

**If you're a player or a designer:** grab an installer from the releases
page. Everything's bundled with a runtime trimmed down by `jpackage`, so you
don't need a JDK on your machine. Windows gets an `.exe`, Linux gets a `.deb`
and a portable `.tar.gz`.

**If you want to build from source:** you'll need JDK 25 and a GPU that can
do OpenGL 4.3.

```sh
git clone https://github.com/Meekiavelique/Epysia
cd Epysia
./gradlew :epysia-editor:run
```

To run a scene without opening the editor:

```sh
./gradlew runGame -Pscene=/path/to/main.epyscene -PprojectRoot=/path/to/project
```

Don't forget `-PprojectRoot`. Without it the runtime has nothing to load
from: no asset database, no compiled scripts, no collision matrix, no input
map.

## What's in it

**Scene runtime.** GameObjects and components on a fixed-step simulation.
60 Hz by default, but you can push it anywhere from 10 to 480 Hz, with frame
pacing and interpolated rendering between simulation steps.

**Rendering.** Forward PBR on OpenGL 4.3 or Vulkan. Metallic-roughness
materials, clustered lights, cascaded sun shadows, spot and point shadow
atlases, image-based lighting, GPU and CPU culling, hardware instancing, and
LOD. The backend is picked at launch and falls back to OpenGL if Vulkan
cannot start, so a driver that refuses one still runs the game.

```java
LitMaterial material = new LitMaterial();
material.setBaseColor(0.85f, 0.82f, 0.78f).setRoughness(0.35f);

MeshRenderer renderer = object.addComponent(new MeshRenderer());
renderer.setMeshPath("res://models/rock.epymesh")
        .setMaterial(material)
        .addLevelOfDetailPath("res://models/rock_lod1.epymesh", 25.0f);
```

**Materials and shaders.** Material uniforms come straight from annotated
Java fields, packed into std140 by reflection so you never hand-write the
layout. Surface shaders hook into five stages of the standard lit shader, so
you override the one you care about and keep shadows, lights, and fog for
free. They hot reload while the editor's running, too.

```glsl
uniform float waveSpeed = 0.4;

void surfaceVertex(inout vec3 worldPosition, in vec3 localPosition,
                   in vec3 worldNormal, in vec2 uv, in float time) {
    worldPosition.y += sin(worldPosition.x * 2.0 + time * waveSpeed) * 0.1;
}
```

**Post-processing.** SSAO, bloom, tonemapping, fog, vignette, FXAA, and room
for your own effect stacks at two points in the pipeline (before or after
tonemap). There's also a pixel-perfect mode that renders at a fixed internal
resolution with integer scaling and letterboxing, for anyone doing pixel art.

**2D.** Sprites all batched into a single dynamic vertex buffer, atlases and
flipbooks, layered tilemaps with autotiling and per-tile collision, 2D
lights, and physics that stays locked to a plane.

**Physics.** Native rigid bodies via Box3D. Box, sphere, capsule, and mesh
colliders, triggers, joints, 3D and 2D character controllers, raycasts and
shape casts, plus a 16-layer collision matrix.

```java
@Override
public void onUpdate(InputState input, float deltaTimeSeconds) {
    float forward = actions.value(InputActions.MOVE_FORWARD, input);
    float right = actions.value(InputActions.MOVE_RIGHT, input);
    controller.move(new Vector3f(right, 0.0f, -forward).mul(speed));

    if (actions.wasPressed(InputActions.JUMP, input) && controller.isGrounded()) {
        controller.jump();
    }
}
```

**Animation.** Skeletons up to 256 joints, `.epyclip` clips, cross-fade
blending, GPU skinning, and joint sockets if you need to bolt something onto
a rig.

**VFX.** GPU particle systems built from node graphs, compiled down to
compute shaders. Shapes, curl noise, curves and gradients get baked to
lookup textures, plus burst schedules for the chaotic stuff.

**Visual scripting.** One graph format doing five jobs: logic graphs, state
machines, surface shader graphs, post-effect graphs, and VFX graphs. Any
public method on your components shows up as a node automatically, through
reflection.

**Audio.** OpenAL underneath, with a bus mixer and ducking, spatial
sources, a 48-voice one-shot pool, streaming, and EFX reverb.

**Navigation.** Navmeshes baked from the geometry a surface declares, walked
by agents that path around what you put in front of them.

**Shipping.** An exported game reads `epysia-settings.json` sitting beside it
before the window exists, so a player can change the render backend, adapter,
resolution, vsync and frame cap without a rebuild. Saves are written
atomically into `saves/` so an interrupted write cannot corrupt progress, and
an uncaught exception leaves a crash report the next launch can collect.

**Steam.** Lobbies, achievements, cloud saves, rich presence and the overlay,
plus friends, avatars, DLC and the app you were launched as. The app id lives
in the project and ships in `steam_appid.txt`, so a build either identifies
itself or carries no Steam dependency at all.

**Web requests.** `services.web()` runs a request off the main thread and
hands the response back on it, so a script can touch the scene in the
callback. Failures arrive as a response rather than an exception.

**Scripting.** `Behaviour` subclasses get compiled and hot reloaded while
the editor's open. Slap `@EpysiaComponent` on a class and it shows up in the
Add Component menu; `@Export` puts a field in the inspector and in the scene
file.

```java
@EpysiaComponent(name = "Spinner", category = "Gameplay")
public final class Spinner extends Behaviour {

    @Export(label = "Degrees Per Second", min = -720.0f, max = 720.0f, step = 5.0f)
    private float degreesPerSecond = 90.0f;

    private Transform3D transform;

    @Override
    public void onStart(EngineServices services) {
        transform = ownerOrNull().getComponentOrNull(Transform3D.class);
    }

    @Override
    public void onUpdate(InputState input, float deltaTimeSeconds) {
        transform.rotateAxisAngle(0.0f, 1.0f, 0.0f,
                (float) Math.toRadians(degreesPerSecond * deltaTimeSeconds));
    }
}
```

**Editor.** A Dear ImGui editor with the scene view, inspector, asset
browser, graph canvas, sprite and tilemap authoring, profiler, lighting
bakes, and export, all in one place. Code editing highlights Java, Kotlin and
GLSL, every action is reachable from one command palette, and the interface
scales with Ctrl +/- from 80% to 150%.

**Extending it.** Subsystems are just `EngineModule` services discovered
through `ServiceLoader`. They load into both the editor and the standalone
runtime, so you can bolt on new features without touching the core.

```java
public final class WeatherModule implements EngineModule {

    @Override
    public int order() {
        return 150;
    }

    @Override
    public void registerSystems(SystemRegistry registry) {
        registry.add(new WeatherSystem());
    }
}
```

## Releases

Push a `vX.Y.Z` tag and `.github/workflows/release.yml` takes it from there.
It builds on native Windows and Linux runners, publishes editor installers plus
the export templates the editor uses to pack games. The Box3D natives get
compiled straight from [box3d-java](https://github.com/Meekiavelique/box3d-java)
in the same workflow, so every release ships with binaries that actually
match.

```sh
git tag v0.1.0
git push origin v0.1.0
```

## Third-party

- Editor icons are derived from the Godot Engine icon set (MIT).
- Physics runs on Box3D, with vendored FFM bindings under `src/main/java/com/meekdev/box3d`.
- Built on LWJGL 3, JOML, Dear ImGui, and ClassGraph.

## License

MIT.