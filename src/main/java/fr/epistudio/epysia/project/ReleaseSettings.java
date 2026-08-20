package fr.epistudio.epysia.project;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ReleaseSettings(String version) {

    public static final String DEFAULT_VERSION = "0.1.0";

    private static final Pattern UNSUPPORTED_CHARACTERS = Pattern.compile("[^A-Za-z0-9.+_-]");
    private static final Pattern LAST_NUMBER = Pattern.compile("(\\d+)(?!.*\\d)");
    private static final int MAXIMUM_LENGTH = 32;

    public static ReleaseSettings defaults() {
        return new ReleaseSettings(DEFAULT_VERSION);
    }

    public ReleaseSettings sanitized() {
        String cleaned = UNSUPPORTED_CHARACTERS.matcher(version.trim()).replaceAll("");
        if (cleaned.isEmpty()) {
            return defaults();
        }
        return new ReleaseSettings(cleaned.substring(0, Math.min(cleaned.length(), MAXIMUM_LENGTH)));
    }

    public ReleaseSettings incremented() {
        String current = sanitized().version();
        Matcher lastNumber = LAST_NUMBER.matcher(current);
        if (!lastNumber.find()) {
            return new ReleaseSettings(current + ".1").sanitized();
        }
        String next = new BigInteger(lastNumber.group(1)).add(BigInteger.ONE).toString();
        return new ReleaseSettings(new StringBuilder(current)
                .replace(lastNumber.start(1), lastNumber.end(1), next).toString()).sanitized();
    }
}
