package fr.epistudio.epysia.vfx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record ParticleBurst(float timeSeconds, int count, int cycles, float intervalSeconds) {

    private static final String ENTRY_SEPARATOR = ";";
    private static final String FIELD_SEPARATOR = ",";
    private static final int FIELD_COUNT = 4;

    public static ParticleBurst at(float timeSeconds, int count) {
        return new ParticleBurst(timeSeconds, count, 1, 1.0f);
    }

    public int firingsBetween(float fromSecondsExclusive, float toSecondsInclusive) {
        int repeats = Math.max(1, cycles);
        float interval = intervalSeconds > 0.0f ? intervalSeconds : 1.0f;
        int firings = 0;
        for (int cycle = 0; cycle < repeats; cycle++) {
            float fireTime = timeSeconds + cycle * interval;
            if (fireTime > fromSecondsExclusive && fireTime <= toSecondsInclusive) {
                firings += Math.max(0, count);
            }
        }
        return firings;
    }

    public static String encode(List<ParticleBurst> bursts) {
        StringBuilder text = new StringBuilder();
        for (ParticleBurst burst : bursts) {
            if (!text.isEmpty()) {
                text.append(ENTRY_SEPARATOR);
            }
            text.append(String.format(Locale.ROOT, "%.4f,%d,%d,%.4f",
                    burst.timeSeconds(), burst.count(), burst.cycles(), burst.intervalSeconds()));
        }
        return text.toString();
    }

    public static List<ParticleBurst> decode(String encoded) {
        List<ParticleBurst> bursts = new ArrayList<>();
        for (String entry : encoded.split(ENTRY_SEPARATOR)) {
            decodeEntry(entry.trim()).ifPresent(bursts::add);
        }
        return List.copyOf(bursts);
    }

    private static Optional<ParticleBurst> decodeEntry(String entry) {
        String[] fields = entry.split(FIELD_SEPARATOR);
        if (fields.length != FIELD_COUNT) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ParticleBurst(
                    Float.parseFloat(fields[0].trim()),
                    Integer.parseInt(fields[1].trim()),
                    Integer.parseInt(fields[2].trim()),
                    Float.parseFloat(fields[3].trim())));
        } catch (NumberFormatException malformed) {
            return Optional.empty();
        }
    }
}
