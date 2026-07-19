# Epysia

Epysia is a 3D game engine and editor written in Java 25. You build a game as a
tree of GameObjects with components, script behaviour in plain Java, and export
a native executable that carries its own trimmed runtime, so players never
install or see a JVM.

## Highlights

- A GameObject and Component runtime with a fixed 60 Hz simulation, frame pacing
  and render interpolation.
- A PBR renderer on OpenGL 4.3: metallic and roughness materials, cascaded
  shadow maps, point and spot lights, image based lighting, and post processing
  (bloom, ambient occlusion, FXAA, tone mapping).
- Native rigid body physics through Box3D, with box, sphere, capsule and mesh
  colliders, triggers, joints, a character controller, raycasts, and a 16 layer
  collision matrix.
- Java scripting that compiles and hot reloads while the editor is running.
- OpenAL audio with a bus mixer, spatial sources and reverb.
- A Dear ImGui editor that takes a project from an empty scene to a standalone
  build without leaving the tool.

## Requirements

- JDK 25
- An OpenGL 4.3 capable GPU
- Linux x64 to run from source. The Box3D linux natives are vendored in the
  repo. Windows natives are produced by the release workflow.

## Running the editor

```sh
./gradlew :epysia-editor:run
```

Pick or create a project in the selector and you land in the editor with a
starter scene. From there you can add primitives, lights and cameras, drop in
components, write scripts, and press play to run the game inside the viewport.

## Writing a script

Scripts are `Behaviour` subclasses. The editor compiles and reloads them when
you save. They reach the engine through `EngineServices`.

```java
@EpysiaComponent(name = "Spinner")
public final class Spinner extends Behaviour {

    @Export
    private float degreesPerSecond = 90.0f;

    @Override
    public void onUpdate(float deltaTimeSeconds) {
        transform().rotateY(degreesPerSecond * deltaTimeSeconds);
    }
}
```

Anything marked `@EpysiaComponent` shows up in the Add Component menu, and
`@Export` fields appear in the Inspector and are saved with the scene.

## Exporting a game

`File > Export Game` produces a self contained build for Windows or Linux. The
output is a native launcher next to a bundled runtime and your project content,
so it runs on a machine with no Java installed. Export works across platforms:
the editor downloads a prebuilt template for the target and packs your game into
it, so a Linux editor can produce a Windows `.exe`.

## Building releases

Pushing a `vX.Y.Z` tag runs `.github/workflows/release.yml`. It builds on native
Windows and Linux runners and publishes a GitHub Release containing:

- Editor installers: a Windows `.exe`, a Linux `.deb`, and a portable Linux
  `.tar.gz`, each with a trimmed runtime built by `jpackage`.
- Export templates: one zip per platform, the prebuilt runtimes the editor
  packs games into.

The Box3D natives are compiled from
[box3d-java](https://github.com/Meekiavelique/box3d-java) inside the same
workflow, so every release ships matching binaries.

```sh
git tag v0.1.0
git push origin v0.1.0
```

## Extending the engine

Systems ship as `EngineModule` services and load into both the editor and the
standalone runtime, so you add features without touching the core:

```java
public final class MyGameModule implements EngineModule {

    @Override
    public int order() {
        return 500;
    }

    @Override
    public void registerSystems(SystemRegistry registry) {
        registry.add(new MyGameSystem());
    }
}
```

## Third-party

- Editor icons are derived from the Godot Engine icon set (MIT).
- Physics is powered by Box3D, with vendored FFM bindings under
  `src/main/java/com/meekdev/box3d`.
- Built on LWJGL 3, JOML, Dear ImGui, and ClassGraph.
