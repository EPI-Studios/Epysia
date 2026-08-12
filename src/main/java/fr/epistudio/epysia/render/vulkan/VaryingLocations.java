package fr.epistudio.epysia.render.vulkan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VaryingLocations {

    private static final Pattern DECLARATION = Pattern.compile(
            "(?m)^([ \\t]*)((?:flat|noperspective|smooth)[ \\t]+)?(in|out)[ \\t]+(\\w+)[ \\t]+(\\w+)[ \\t]*;");

    private static final String DIRECTION_OUT = "out";
    private static final String DIRECTION_IN = "in";

    private final Map<String, Integer> locationsByName;

    private VaryingLocations(Map<String, Integer> locationsByName) {
        this.locationsByName = locationsByName;
    }

    public static VaryingLocations forPair(String vertexSource, String fragmentSource) {
        Map<String, Integer> assigned = new LinkedHashMap<>();
        namesOf(vertexSource, DIRECTION_OUT).forEach(name -> assigned.putIfAbsent(name, assigned.size()));
        namesOf(fragmentSource, DIRECTION_IN).forEach(name -> assigned.putIfAbsent(name, assigned.size()));
        return new VaryingLocations(assigned);
    }

    public String applyToVertex(String source) {
        return rewriteDeclarations(source, direction -> direction.equals(DIRECTION_OUT),
                new SequentialCounter(), true);
    }

    public String applyToFragment(String source) {
        return rewriteDeclarations(source, direction -> true, new SequentialCounter(), false);
    }

    private String rewriteDeclarations(String source, DirectionFilter filter,
                                       SequentialCounter fragmentOutputs, boolean vertexStage) {
        Matcher matcher = DECLARATION.matcher(source);
        StringBuilder rewritten = new StringBuilder(source.length() + 128);
        while (matcher.find()) {
            String direction = matcher.group(3);
            String replacement = filter.accepts(direction)
                    ? withLocation(matcher, locationFor(matcher.group(5), direction, vertexStage, fragmentOutputs))
                    : matcher.group();
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rewritten);
        return rewritten.toString();
    }

    private int locationFor(String name, String direction, boolean vertexStage,
                            SequentialCounter fragmentOutputs) {
        if (!vertexStage && direction.equals(DIRECTION_OUT)) {
            return fragmentOutputs.next();
        }
        Integer assigned = locationsByName.get(name);
        if (assigned == null) {
            throw new IllegalStateException("Varying " + name + " has no assigned location.");
        }
        return assigned;
    }

    private static String withLocation(Matcher matcher, int location) {
        String indent = matcher.group(1);
        String interpolation = matcher.group(2) == null ? "" : matcher.group(2);
        return indent + "layout(location = " + location + ") " + interpolation
                + matcher.group(3) + " " + matcher.group(4) + " " + matcher.group(5) + ";";
    }

    private static List<String> namesOf(String source, String direction) {
        List<String> names = new ArrayList<>();
        Matcher matcher = DECLARATION.matcher(source);
        while (matcher.find()) {
            if (matcher.group(3).equals(direction)) {
                names.add(matcher.group(5));
            }
        }
        return names;
    }

    private interface DirectionFilter {
        boolean accepts(String direction);
    }

    private static final class SequentialCounter {
        private int next;

        int next() {
            return next++;
        }
    }
}
