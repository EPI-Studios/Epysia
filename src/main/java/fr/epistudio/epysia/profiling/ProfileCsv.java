package fr.epistudio.epysia.profiling;

import java.util.List;
import java.util.Locale;

public final class ProfileCsv {

    private static final String HEADER = "depth,path,name,totalMs,selfMs,calls";
    private static final String PATH_SEPARATOR = "/";
    private static final double NANOS_PER_MILLISECOND = 1_000_000.0;

    private ProfileCsv() {
    }

    public static String of(ProfileFrame frame) {
        StringBuilder text = new StringBuilder(HEADER).append(System.lineSeparator());
        appendAll(text, frame.roots(), 0, "");
        return text.toString();
    }

    private static void appendAll(StringBuilder text, List<ProfileNode> nodes, int depth, String parentPath) {
        for (ProfileNode node : nodes) {
            String path = parentPath.isEmpty() ? node.name() : parentPath + PATH_SEPARATOR + node.name();
            append(text, node, depth, path);
            appendAll(text, node.children(), depth + 1, path);
        }
    }

    private static void append(StringBuilder text, ProfileNode node, int depth, String path) {
        text.append(depth).append(',')
                .append(quoted(path)).append(',')
                .append(quoted(node.name())).append(',')
                .append(milliseconds(node.totalNanos())).append(',')
                .append(milliseconds(node.selfNanos())).append(',')
                .append(node.calls())
                .append(System.lineSeparator());
    }

    private static String milliseconds(long nanos) {
        return String.format(Locale.ROOT, "%.4f", nanos / NANOS_PER_MILLISECOND);
    }

    private static String quoted(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
