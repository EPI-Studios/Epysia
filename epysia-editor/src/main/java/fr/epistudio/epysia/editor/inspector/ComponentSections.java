package fr.epistudio.epysia.editor.inspector;

import fr.epistudio.epysia.components.Animator;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.MultiMeshRenderer;
import fr.epistudio.epysia.components.TilemapRenderer;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.graph.GraphComponent;
import fr.epistudio.epysia.navigation.NavMeshSurface;
import fr.epistudio.epysia.physics.components.RigidBodyComponent;
import fr.epistudio.epysia.ui.UiElement;
import fr.epistudio.epysia.vfx.ParticleEffect;

import java.util.ArrayList;
import java.util.List;

public final class ComponentSections {

    private final List<ComponentSection> sections = new ArrayList<>();

    public void register(ComponentSection section) {
        sections.add(section);
    }

    public <T extends IComponent> void registerTyped(Class<T> componentType,
                                                     java.util.function.BiConsumer<GameObject, T> body) {
        sections.add(TypedComponentSection.of(componentType, body));
    }

    public void render(GameObject gameObject, IComponent component) {
        for (ComponentSection section : sections) {
            if (section.handles(component)) {
                section.render(gameObject, component);
            }
        }
    }

    public static ComponentSections of(InspectorSectionBundle bundle) {
        ComponentSections registry = new ComponentSections();
        registry.registerTyped(UiElement.class, bundle.uiElement()::render);
        registry.registerTyped(MeshRenderer.class, (object, renderer) -> bundle.materials().render(renderer));
        registry.registerTyped(MultiMeshRenderer.class, (object, multiMesh) -> {
            bundle.materials().render(multiMesh);
            bundle.populate().render(multiMesh);
        });
        registry.registerTyped(Animator.class, bundle.animator()::render);
        registry.registerTyped(ParticleEffect.class, (object, effect) -> bundle.vfx().render(effect));
        registry.registerTyped(Camera3D.class, (object, camera) -> bundle.cameraPostEffects().render(camera));
        registry.registerTyped(GraphComponent.class, (object, graph) -> bundle.graph().render(graph));
        registry.register(bundle.colliderFit());
        registry.registerTyped(TilemapRenderer.class,
                (object, renderer) -> bundle.tilemapSummary().render(renderer));
        registry.registerTyped(RigidBodyComponent.class,
                (object, body) -> bundle.rigidBodyLive().render(body, bundle.playModeActive().getAsBoolean()));
        registry.registerTyped(NavMeshSurface.class,
                (object, surface) -> bundle.navMeshSurface().render(surface));
        return registry;
    }
}
