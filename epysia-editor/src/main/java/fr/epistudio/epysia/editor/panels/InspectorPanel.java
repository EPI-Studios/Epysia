package fr.epistudio.epysia.editor.panels;

import com.miry.ui.PanelContext;
import com.miry.ui.input.UiInput;
import com.miry.ui.panels.Panel;
import com.miry.ui.render.UiRenderer;
import com.miry.ui.theme.Icon;
import com.miry.ui.theme.Theme;
import com.miry.ui.widgets.ContextMenu;
import com.miry.ui.widgets.DraggableNumberField;
import com.miry.ui.widgets.TextField;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.editor.EditorComponentRegistry;
import fr.epistudio.epysia.editor.EditorSceneHost;
import fr.epistudio.epysia.editor.EditorStyle;
import fr.epistudio.epysia.editor.EditorWorld;
import fr.epistudio.epysia.editor.command.builtin.AddComponentCommand;
import fr.epistudio.epysia.editor.command.builtin.RemoveComponentCommand;
import fr.epistudio.epysia.editor.inspector.InspectorDispatcher;
import fr.epistudio.epysia.editor.reflection.EditorReflection;
import fr.epistudio.epysia.editor.reflection.ExportedProperty;
import fr.epistudio.epysia.gameobjects.GameObject;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class InspectorPanel extends Panel {

    private static final String TITLE = "Inspector";
    private static final int PADDING_X = 10;
    private static final int PADDING_TOP = 8;
    private static final int HEADER_HEIGHT = 56;
    private static final int SECTION_HEADER_HEIGHT = 24;
    private static final int SECTION_TOP_MARGIN = 4;
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_GAP = 2;
    private static final int LABEL_COLUMN_WIDTH = 96;
    private static final int LABEL_GAP = 6;
    private static final int ADD_BUTTON_HEIGHT = 26;
    private static final int FIELD_RANGE_FALLBACK = 1_000_000;
    private static final int CHEVRON_SIZE = 10;
    private static final int SECTION_ICON_SIZE = 12;
    private static final int REMOVE_BUTTON_SIZE = 18;
    private static final int ACCENT_BAR_WIDTH = 2;

    private final EditorWorld world;
    private final EditorSceneHost sceneHost;
    private final InspectorDispatcher dispatcher;
    private final Map<String, DraggableNumberField> fieldsByKey = new HashMap<>();
    private final Map<String, EulerCache> eulerCachesByKey = new HashMap<>();
    private final Map<String, TextField> stringFieldsByKey = new HashMap<>();
    private final Map<String, Boolean> expandedByComponent = new HashMap<>();
    private final Set<String> seenKeysThisFrame = new HashSet<>();
    private final ContextMenu addComponentMenu = new ContextMenu();
    private boolean addComponentMenuOpenedThisFrame;

    public InspectorPanel(EditorWorld world, EditorSceneHost sceneHost) {
        super(TITLE);
        this.world = world;
        this.sceneHost = sceneHost;
        this.dispatcher = new InspectorDispatcher(world);
    }

    private String groupKeyFor(ExportedProperty property) {
        java.util.Optional<fr.epistudio.epysia.gameobjects.GameObject> selected = world.selected();
        int targetHash = selected.map(System::identityHashCode).orElse(0);
        return targetHash + ":" + property.fieldName();
    }

    @Override
    public void render(PanelContext context) {
        addComponentMenuOpenedThisFrame = false;
        seenKeysThisFrame.clear();
        Optional<GameObject> selectedOptional = world.selected();
        if (selectedOptional.isEmpty()) {
            renderEmptyState(context);
            pruneStaleFieldKeys();
            return;
        }
        renderSelected(context, selectedOptional.get());
        updateAddComponentMenuInput(context.ui().input(), context.ui().theme());
        pruneStaleFieldKeys();
    }

    private void pruneStaleFieldKeys() {
        fieldsByKey.keySet().removeIf(key -> !seenKeysThisFrame.contains(key));
        eulerCachesByKey.keySet().removeIf(key -> !seenKeysThisFrame.contains(key));
        stringFieldsByKey.keySet().removeIf(key -> !seenKeysThisFrame.contains(key));
    }

    private void renderEmptyState(PanelContext context) {
        UiRenderer renderer = context.renderer();
        int textX = context.x() + PADDING_X;
        int textY = context.y() + PADDING_TOP + 16;
        renderer.drawText("No selection", textX, textY, EditorStyle.COLOR_TEXT_MUTED);
        renderer.drawText("Pick an object in the scene tree", textX, textY + 20, EditorStyle.COLOR_TEXT_DIM);
    }

    private void renderSelected(PanelContext context, GameObject selected) {
        int contentX = context.x() + PADDING_X;
        int contentY = context.y() + PADDING_TOP;
        int contentWidth = context.width() - PADDING_X * 2;
        renderObjectHeader(context, selected, contentX, contentY, contentWidth);
        int cursorY = contentY + HEADER_HEIGHT;
        List<IComponent> components = selected.components();
        for (IComponent component : components) {
            cursorY = renderComponentSection(context, selected, component, contentX, contentWidth, cursorY);
        }
        renderAddComponentButton(context, contentX, contentWidth, cursorY + 8);
    }

    private void renderObjectHeader(PanelContext context, GameObject selected, int x, int y, int width) {
        UiRenderer renderer = context.renderer();
        renderer.drawRect(x - PADDING_X, y, width + PADDING_X * 2, HEADER_HEIGHT, EditorStyle.COLOR_WINDOW_BG);
        renderer.drawRect(x - PADDING_X, y + HEADER_HEIGHT - 1, width + PADDING_X * 2, 1, EditorStyle.COLOR_SEPARATOR);
        context.ui().theme().icons.draw(renderer, Icon.SETTINGS, x, y + 12, 18, EditorStyle.COLOR_ACCENT);
        renderer.drawText(selected.name(), x + 26, y + 22, EditorStyle.COLOR_TEXT_HEADER);
        renderer.drawText("GameObject", x + 26, y + 40, EditorStyle.COLOR_TEXT_DIM);
    }

    private int renderComponentSection(PanelContext context, GameObject selected, IComponent component,
                                       int x, int width, int y) {
        int sectionTop = y + SECTION_TOP_MARGIN;
        String componentName = component.getClass().getSimpleName();
        boolean expanded = expandedFor(componentName);
        renderSectionHeader(context, selected, component, x, width, sectionTop, expanded);
        int rowY = sectionTop + SECTION_HEADER_HEIGHT + 2;
        if (!expanded) {
            return sectionTop + SECTION_HEADER_HEIGHT;
        }
        List<ExportedProperty> properties = EditorReflection.scan(component);
        if (properties.isEmpty()) {
            context.renderer().drawText("(no @Export fields)", x + 8, rowY + 14, EditorStyle.COLOR_TEXT_DIM);
            return rowY + ROW_HEIGHT;
        }
        String keyPrefix = component.getClass().getName() + "#" + System.identityHashCode(component) + ".";
        for (ExportedProperty property : properties) {
            rowY = renderPropertyRow(context, property, x, width, rowY, keyPrefix);
        }
        return rowY + 4;
    }

    private boolean expandedFor(String componentName) {
        return expandedByComponent.computeIfAbsent(componentName, key -> Boolean.TRUE);
    }

    private void renderSectionHeader(PanelContext context, GameObject selected, IComponent component,
                                     int x, int width, int y, boolean expanded) {
        UiRenderer renderer = context.renderer();
        Theme theme = context.ui().theme();
        UiInput input = context.ui().input();
        int headerX = x - PADDING_X;
        int headerWidth = width + PADDING_X * 2;
        boolean hovered = isMouseOver(input, headerX, y, headerWidth, SECTION_HEADER_HEIGHT);
        int background = hovered ? EditorStyle.COLOR_WIDGET_HOVER : EditorStyle.COLOR_HEADER_BG;
        renderer.drawRect(headerX, y, headerWidth, SECTION_HEADER_HEIGHT, background);
        renderer.drawRect(headerX, y, ACCENT_BAR_WIDTH, SECTION_HEADER_HEIGHT, sectionAccentColor(component));
        renderer.drawRect(headerX, y + SECTION_HEADER_HEIGHT - 1, headerWidth, 1, EditorStyle.COLOR_SEPARATOR);
        drawSectionChevron(renderer, theme, x, y, expanded);
        drawSectionIcon(renderer, theme, component, x, y);
        renderer.drawText(component.getClass().getSimpleName(), x + 36, y + 16, EditorStyle.COLOR_TEXT_PRIMARY);
        int removeX = headerX + headerWidth - REMOVE_BUTTON_SIZE - 6;
        boolean removeHovered = renderRemoveButton(context, selected, component, removeX, y);
        if (!removeHovered && hovered && input != null && input.mousePressed()) {
            expandedByComponent.put(component.getClass().getSimpleName(), !expanded);
        }
    }

    private void drawSectionChevron(UiRenderer renderer, Theme theme, int x, int y, boolean expanded) {
        Icon chevron = expanded ? Icon.CHEVRON_DOWN : Icon.CHEVRON_RIGHT;
        float chevronY = y + (SECTION_HEADER_HEIGHT - CHEVRON_SIZE) / 2.0f;
        theme.icons.draw(renderer, chevron, x + 4, chevronY, CHEVRON_SIZE, EditorStyle.COLOR_TEXT_MUTED);
    }

    private void drawSectionIcon(UiRenderer renderer, Theme theme, IComponent component, int x, int y) {
        Icon icon = sectionIconFor(component);
        float iconY = y + (SECTION_HEADER_HEIGHT - SECTION_ICON_SIZE) / 2.0f;
        theme.icons.draw(renderer, icon, x + 18, iconY, SECTION_ICON_SIZE, sectionAccentColor(component));
    }

    private boolean renderRemoveButton(PanelContext context, GameObject selected, IComponent component,
                                       int x, int y) {
        if (component instanceof fr.epistudio.epysia.components.transforms.Transform3D) {
            return false;
        }
        UiRenderer renderer = context.renderer();
        Theme theme = context.ui().theme();
        UiInput input = context.ui().input();
        int buttonY = y + (SECTION_HEADER_HEIGHT - REMOVE_BUTTON_SIZE) / 2;
        boolean hovered = isMouseOver(input, x, buttonY, REMOVE_BUTTON_SIZE, REMOVE_BUTTON_SIZE);
        if (hovered) {
            renderer.drawRoundedRect(x, buttonY, REMOVE_BUTTON_SIZE, REMOVE_BUTTON_SIZE, 3.0f,
                    EditorStyle.COLOR_WIDGET_ACTIVE);
        }
        float iconSize = 10.0f;
        float iconX = x + (REMOVE_BUTTON_SIZE - iconSize) / 2.0f;
        float iconY = buttonY + (REMOVE_BUTTON_SIZE - iconSize) / 2.0f;
        theme.icons.draw(renderer, Icon.CLOSE, iconX, iconY, iconSize, EditorStyle.COLOR_TEXT_MUTED);
        if (hovered && input != null && input.mouseReleased()) {
            java.util.function.Supplier<IComponent> factory =
                    sceneHost.components().factoryFor(component.getClass()).orElse(null);
            if (factory != null) {
                world.history().execute(new RemoveComponentCommand(selected, component.getClass(), factory));
            } else {
                selected.removeComponent(component.getClass());
            }
        }
        return hovered;
    }

    private boolean isMouseOver(UiInput input, int x, int y, int width, int height) {
        if (input == null) {
            return false;
        }
        float mouseX = input.mousePos().x;
        float mouseY = input.mousePos().y;
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private Icon sectionIconFor(IComponent component) {
        if (component instanceof fr.epistudio.epysia.components.transforms.Transform3D) {
            return Icon.MOVE;
        }
        if (component instanceof fr.epistudio.epysia.components.Camera3D) {
            return Icon.EYE;
        }
        if (component instanceof fr.epistudio.epysia.components.MeshRenderer) {
            return Icon.IMAGE;
        }
        if (isLightComponent(component)) {
            return Icon.VISIBLE;
        }
        return Icon.SETTINGS;
    }

    private boolean isLightComponent(IComponent component) {
        return component instanceof fr.epistudio.epysia.components.DirectionalLight
                || component instanceof fr.epistudio.epysia.components.PointLight
                || component instanceof fr.epistudio.epysia.components.SpotLight;
    }

    private int sectionAccentColor(IComponent component) {
        if (component instanceof fr.epistudio.epysia.components.Camera3D) {
            return 0xFF6FB3E0;
        }
        if (component instanceof fr.epistudio.epysia.components.MeshRenderer) {
            return 0xFF8CBADC;
        }
        if (isLightComponent(component)) {
            return 0xFFE3C167;
        }
        if (component instanceof fr.epistudio.epysia.components.transforms.Transform3D) {
            return EditorStyle.COLOR_ACCENT;
        }
        return EditorStyle.COLOR_ACCENT;
    }

    private int renderPropertyRow(PanelContext context, ExportedProperty property,
                                  int x, int width, int y, String keyPrefix) {
        UiRenderer renderer = context.renderer();
        renderLabel(renderer, property.label(), x, y);
        int fieldsX = x + LABEL_COLUMN_WIDTH + LABEL_GAP;
        int fieldsWidth = width - LABEL_COLUMN_WIDTH - LABEL_GAP;
        return renderPropertyValue(context, property, keyPrefix, fieldsX, y, fieldsWidth);
    }

    private int renderPropertyValue(PanelContext context, ExportedProperty property, String keyPrefix,
                                    int x, int y, int width) {
        switch (property.kind()) {
            case FLOAT:
                renderFloatField(context, property, keyPrefix, x, y, width);
                return y + ROW_HEIGHT + ROW_GAP;
            case INT:
                renderIntField(context, property, keyPrefix, x, y, width);
                return y + ROW_HEIGHT + ROW_GAP;
            case VECTOR3:
                renderVector3Field(context, property, keyPrefix, x, y, width);
                return y + ROW_HEIGHT + ROW_GAP;
            case QUATERNION:
                renderQuaternionField(context, property, keyPrefix, x, y, width);
                return y + ROW_HEIGHT + ROW_GAP;
            case BOOLEAN:
                renderBooleanField(context, property, x, y, width);
                return y + ROW_HEIGHT + ROW_GAP;
            case STRING:
                renderStringField(context, property, keyPrefix, x, y, width);
                return y + ROW_HEIGHT + ROW_GAP;
            default:
                context.renderer().drawText("(unsupported)", x, y + 15, EditorStyle.COLOR_TEXT_DIM);
                return y + ROW_HEIGHT + ROW_GAP;
        }
    }

    private void renderLabel(UiRenderer renderer, String label, int x, int y) {
        renderer.drawText(label, x + 2, y + 15, EditorStyle.COLOR_TEXT_MUTED);
    }

    private void renderFloatField(PanelContext context, ExportedProperty property, String keyPrefix,
                                  int x, int y, int width) {
        float current = (float) property.read();
        DraggableNumberField field = fieldFor(keyPrefix + property.fieldName(), current,
                property.min(), property.max(), property.step(), EditorStyle.COLOR_ACCENT);
        if (!field.isEditing()) {
            field.setValue(current);
        }
        field.render(context.renderer(), context.uiContext(), context.ui().input(), context.ui().theme(),
                x, y, width, ROW_HEIGHT, true);
        dispatcher.writeFloatIfChanged(property, field.value(), groupKeyFor(property));
    }

    private void renderIntField(PanelContext context, ExportedProperty property, String keyPrefix,
                                int x, int y, int width) {
        int current = (int) property.read();
        DraggableNumberField field = fieldFor(keyPrefix + property.fieldName(), current,
                property.min(), property.max(), Math.max(1.0f, property.step()), EditorStyle.COLOR_ACCENT);
        if (!field.isEditing()) {
            field.setValue(current);
        }
        field.render(context.renderer(), context.uiContext(), context.ui().input(), context.ui().theme(),
                x, y, width, ROW_HEIGHT, true);
        dispatcher.writeIntIfChanged(property, Math.round(field.value()), groupKeyFor(property));
    }

    private void renderVector3Field(PanelContext context, ExportedProperty property, String keyPrefix,
                                    int x, int y, int width) {
        Vector3f vector = (Vector3f) property.read();
        String groupKey = groupKeyFor(property);
        renderTripleField(context, keyPrefix + property.fieldName(), x, y, width,
                vector.x, vector.y, vector.z,
                (newX, newY, newZ) -> dispatcher.writeVector3IfChanged(property, newX, newY, newZ, groupKey));
    }

    private void renderQuaternionField(PanelContext context, ExportedProperty property, String keyPrefix,
                                       int x, int y, int width) {
        Quaternionf rotation = (Quaternionf) property.read();
        String baseKey = keyPrefix + property.fieldName();
        String groupKey = groupKeyFor(property);
        EulerCache cache = eulerCacheFor(baseKey);
        if (!isAnyAxisEditing(baseKey) && (!cache.initialized || quaternionsDiffer(cache.lastSeenQuaternion, rotation))) {
            refreshEulerCacheFromQuaternion(cache, rotation);
        }
        renderTripleField(context, baseKey, x, y, width,
                cache.eulerDegrees.x, cache.eulerDegrees.y, cache.eulerDegrees.z,
                (pitch, yaw, roll) -> applyEulerToQuaternion(cache, property, pitch, yaw, roll, groupKey));
    }

    private void refreshEulerCacheFromQuaternion(EulerCache cache, Quaternionf rotation) {
        rotation.getEulerAnglesYXZ(cache.eulerDegrees);
        cache.eulerDegrees.mul(57.29578f);
        cache.lastSeenQuaternion.set(rotation);
        cache.initialized = true;
    }

    private void applyEulerToQuaternion(EulerCache cache, ExportedProperty property,
                                        float pitchDegrees, float yawDegrees, float rollDegrees,
                                        String groupKey) {
        cache.eulerDegrees.set(pitchDegrees, yawDegrees, rollDegrees);
        Quaternionf candidate = new Quaternionf()
                .rotateY((float) Math.toRadians(yawDegrees))
                .rotateX((float) Math.toRadians(pitchDegrees))
                .rotateZ((float) Math.toRadians(rollDegrees));
        cache.lastSeenQuaternion.set(candidate);
        dispatcher.writeQuaternionIfChanged(property, candidate, groupKey);
    }

    private boolean isAnyAxisEditing(String baseKey) {
        return isFieldEditing(baseKey + ".x") || isFieldEditing(baseKey + ".y") || isFieldEditing(baseKey + ".z");
    }

    private boolean isFieldEditing(String key) {
        DraggableNumberField field = fieldsByKey.get(key);
        return field != null && field.isEditing();
    }

    private static boolean quaternionsDiffer(Quaternionf a, Quaternionf b) {
        float dot = Math.abs(a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w);
        return dot < 0.9999f;
    }

    private EulerCache eulerCacheFor(String key) {
        seenKeysThisFrame.add(key);
        EulerCache existing = eulerCachesByKey.get(key);
        if (existing != null) {
            return existing;
        }
        EulerCache created = new EulerCache();
        eulerCachesByKey.put(key, created);
        return created;
    }

    private void renderTripleField(PanelContext context, String keyPrefix,
                                   int x, int y, int width,
                                   float valueX, float valueY, float valueZ,
                                   TripleSetter setter) {
        int fieldWidth = width / 3 - 4;
        DraggableNumberField fieldX = axisField(keyPrefix + ".x", valueX, EditorStyle.COLOR_AXIS_X);
        DraggableNumberField fieldY = axisField(keyPrefix + ".y", valueY, EditorStyle.COLOR_AXIS_Y);
        DraggableNumberField fieldZ = axisField(keyPrefix + ".z", valueZ, EditorStyle.COLOR_AXIS_Z);
        if (!fieldX.isEditing()) fieldX.setValue(valueX);
        if (!fieldY.isEditing()) fieldY.setValue(valueY);
        if (!fieldZ.isEditing()) fieldZ.setValue(valueZ);
        Theme theme = context.ui().theme();
        UiRenderer renderer = context.renderer();
        UiInput input = context.ui().input();
        fieldX.render(renderer, context.uiContext(), input, theme, x, y, fieldWidth, ROW_HEIGHT, true);
        fieldY.render(renderer, context.uiContext(), input, theme, x + fieldWidth + 4, y, fieldWidth, ROW_HEIGHT, true);
        fieldZ.render(renderer, context.uiContext(), input, theme, x + (fieldWidth + 4) * 2, y, fieldWidth, ROW_HEIGHT, true);
        setter.set(fieldX.value(), fieldY.value(), fieldZ.value());
    }

    private DraggableNumberField axisField(String key, float current, int accentArgb) {
        return fieldFor(key, current, -FIELD_RANGE_FALLBACK, FIELD_RANGE_FALLBACK, 0.05f, accentArgb);
    }

    private void renderBooleanField(PanelContext context, ExportedProperty property, int x, int y, int width) {
        boolean current = (boolean) property.read();
        UiRenderer renderer = context.renderer();
        UiInput input = context.ui().input();
        int toggleWidth = 32;
        int toggleHeight = 16;
        int toggleY = y + (ROW_HEIGHT - toggleHeight) / 2;
        boolean hovered = isMouseOver(input, x, toggleY, toggleWidth, toggleHeight);
        int trackColor = current ? EditorStyle.COLOR_ACCENT : EditorStyle.COLOR_WIDGET_BG;
        if (hovered && !current) {
            trackColor = EditorStyle.COLOR_WIDGET_HOVER;
        }
        renderer.drawRoundedRect(x, toggleY, toggleWidth, toggleHeight, toggleHeight / 2.0f,
                trackColor, 1, EditorStyle.COLOR_SEPARATOR);
        int knobSize = toggleHeight - 4;
        int knobX = current ? x + toggleWidth - knobSize - 2 : x + 2;
        renderer.drawRoundedRect(knobX, toggleY + 2, knobSize, knobSize, knobSize / 2.0f,
                EditorStyle.COLOR_TEXT_HEADER);
        renderer.drawText(current ? "true" : "false", x + toggleWidth + 8, y + 15,
                EditorStyle.COLOR_TEXT_MUTED);
        if (hovered && input != null && input.mouseReleased()) {
            dispatcher.writeBooleanIfChanged(property, !current);
        }
    }

    private void renderStringField(PanelContext context, ExportedProperty property, String keyPrefix,
                                   int x, int y, int width) {
        String key = keyPrefix + property.fieldName();
        TextField field = stringFieldFor(key, (String) property.read());
        field.render(context.renderer(), context.uiContext(), context.ui().input(),
                context.ui().theme(), x, y, width, ROW_HEIGHT, true);
        dispatcher.writeStringIfChanged(property, field.text(), groupKeyFor(property));
    }

    private TextField stringFieldFor(String key, String currentValue) {
        seenKeysThisFrame.add(key);
        TextField existing = stringFieldsByKey.get(key);
        if (existing != null) {
            return existing;
        }
        TextField created = new TextField(currentValue == null ? "" : currentValue);
        stringFieldsByKey.put(key, created);
        return created;
    }

    private void renderAddComponentButton(PanelContext context, int x, int width, int y) {
        UiRenderer renderer = context.renderer();
        UiInput input = context.ui().input();
        boolean hovered = isMouseOver(input, x, y, width, ADD_BUTTON_HEIGHT);
        int background = hovered ? EditorStyle.COLOR_WIDGET_HOVER : EditorStyle.COLOR_WIDGET_BG;
        renderer.drawRoundedRect(x, y, width, ADD_BUTTON_HEIGHT, 3.0f,
                background, 1, EditorStyle.COLOR_SEPARATOR);
        String label = "+ Add Component";
        float textWidth = renderer.measureText(label);
        int textX = x + Math.round((width - textWidth) / 2.0f);
        renderer.drawText(label, textX, y + 17, EditorStyle.COLOR_TEXT_PRIMARY);
        if (hovered && input != null && input.mouseReleased()) {
            rebuildAddComponentMenuForSelected();
            addComponentMenu.open(x, y + ADD_BUTTON_HEIGHT + 2);
            addComponentMenuOpenedThisFrame = true;
        }
    }

    private void rebuildAddComponentMenuForSelected() {
        addComponentMenu.clear();
        Optional<GameObject> selectedOptional = world.selected();
        if (selectedOptional.isEmpty()) {
            addComponentMenu.addInfo("Nothing selected");
            return;
        }
        GameObject selected = selectedOptional.get();
        int added = 0;
        for (EditorComponentRegistry.Entry entry : sceneHost.components().entries()) {
            if (selected.getComponent(entry.componentClass()).isPresent()) {
                continue;
            }
            addComponentMenu.addItem(entry.displayName(), () -> world.selected().ifPresent(target -> {
                if (target.getComponent(entry.componentClass()).isPresent()) {
                    return;
                }
                world.history().execute(new AddComponentCommand(target, entry.componentClass(), entry.factory()));
            }));
            added++;
        }
        if (added == 0) {
            addComponentMenu.addInfo("All components already attached");
        }
    }

    private DraggableNumberField fieldFor(String key, float initial, float min, float max, float step, int accentArgb) {
        seenKeysThisFrame.add(key);
        DraggableNumberField existing = fieldsByKey.get(key);
        if (existing != null) {
            return existing;
        }
        DraggableNumberField created = new DraggableNumberField(initial,
                sanitize(min, -FIELD_RANGE_FALLBACK), sanitize(max, FIELD_RANGE_FALLBACK));
        created.setSnapStep(step);
        created.setLeadingAccent(accentArgb, 3);
        fieldsByKey.put(key, created);
        return created;
    }

    private static float sanitize(float value, float fallback) {
        if (Float.isInfinite(value) || Float.isNaN(value)) {
            return fallback;
        }
        return value;
    }

    private void updateAddComponentMenuInput(UiInput input, Theme theme) {
        if (!addComponentMenu.isOpen()) {
            return;
        }
        int itemHeight = Math.max(22, theme.tokens.itemHeight);
        addComponentMenu.updateFromInput(input, theme, itemHeight);
        if (input.mousePressed() && !addComponentMenuOpenedThisFrame) {
            boolean handled = addComponentMenu.handleClick(
                    Math.round(input.mousePos().x), Math.round(input.mousePos().y), itemHeight);
            if (!handled) {
                addComponentMenu.close();
            }
        }
    }

    public void renderOverlayMenus(UiRenderer renderer, Theme theme) {
        if (!addComponentMenu.isOpen()) {
            return;
        }
        int itemHeight = Math.max(22, theme.tokens.itemHeight);
        addComponentMenu.render(renderer, theme, itemHeight,
                EditorStyle.COLOR_PANEL_BG,
                EditorStyle.COLOR_SELECTION,
                EditorStyle.COLOR_TEXT_PRIMARY,
                addComponentMenu.hoverIndex());
    }

    @FunctionalInterface
    private interface TripleSetter {
        void set(float a, float b, float c);
    }

    private static final class EulerCache {
        final Vector3f eulerDegrees = new Vector3f();
        final Quaternionf lastSeenQuaternion = new Quaternionf();
        boolean initialized;
    }
}
