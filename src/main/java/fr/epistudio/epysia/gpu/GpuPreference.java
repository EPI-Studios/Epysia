package fr.epistudio.epysia.gpu;

public enum GpuPreference {
    SYSTEM_DEFAULT("system", "System default"),
    HIGH_PERFORMANCE("high", "High performance"),
    POWER_SAVING("power", "Power saving");

    private final String id;
    private final String displayName;

    GpuPreference(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static GpuPreference fromId(String id) {
        for (GpuPreference value : values()) {
            if (value.id.equals(id)) {
                return value;
            }
        }
        return SYSTEM_DEFAULT;
    }
}
