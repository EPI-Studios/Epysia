package fr.epistudio.epysia.editor.inspector;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.editor.assets.ThumbnailCache;
import fr.epistudio.epysia.editor.notify.Notifier;
import fr.epistudio.epysia.editor.scene.GameObjectFactory;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.reflection.ComponentRegistry;

import java.util.function.Supplier;

public record InspectorDependencies(Supplier<SceneDocument> activeDocument,
                                    ComponentRegistry componentRegistry,
                                    Notifier notifier,
                                    IconWidgets icons,
                                    ThumbnailCache thumbnails,
                                    Project project,
                                    GameObjectFactory objectFactory,
                                    EngineServices engineServices) {
}
