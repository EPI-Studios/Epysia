package fr.epistudio.epysia.net.replication;

import fr.epistudio.epysia.components.Export;

public final class SynchronizedProperty {
    @Export(label = "Component Type")
    private String componentType = "";
    @Export(label = "Field")
    private String fieldName = "";
    @Export(label = "Interpolate")
    private boolean interpolate;
    @Export(label = "Send Rate", min = 0.0f, max = 120.0f, step = 1.0f)
    private int sendRate;
    @Export(label = "Precision", min = 0.0f, max = 1.0f)
    private float precision;

    public String componentType() {
        return componentType;
    }

    public SynchronizedProperty setComponentType(String value) {
        this.componentType = value == null ? "" : value;
        return this;
    }

    public String fieldName() {
        return fieldName;
    }

    public SynchronizedProperty setFieldName(String value) {
        this.fieldName = value == null ? "" : value;
        return this;
    }

    public boolean interpolate() {
        return interpolate;
    }

    public SynchronizedProperty setInterpolate(boolean value) {
        this.interpolate = value;
        return this;
    }

    public int sendRate() {
        return sendRate;
    }

    public SynchronizedProperty setSendRate(int value) {
        this.sendRate = Math.max(0, value);
        return this;
    }

    public float precision() {
        return precision;
    }

    public SynchronizedProperty setPrecision(float value) {
        this.precision = Math.max(0.0f, value);
        return this;
    }

    public boolean isResolvable() {
        return !componentType.isBlank() && !fieldName.isBlank();
    }
}
