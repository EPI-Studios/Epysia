package fr.epistudio.epysia.graph;

import java.util.List;

public record NodeDefinition(
        String typeKey,
        String displayName,
        String category,
        boolean memoized,
        boolean event,
        List<PinDefinition> inputPins,
        List<PinDefinition> outputPins,
        List<NodeSetting> settings,
        NodeBehavior behavior) {

    public boolean hasExecPins() {
        return hasExecPin(inputPins) || hasExecPin(outputPins);
    }

    private static boolean hasExecPin(List<PinDefinition> pins) {
        for (PinDefinition pin : pins) {
            if (pin.type() == PinType.EXEC) {
                return true;
            }
        }
        return false;
    }
}
