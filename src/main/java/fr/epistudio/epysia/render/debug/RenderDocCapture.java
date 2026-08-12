package fr.epistudio.epysia.render.debug;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.logging.Logger;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

public final class RenderDocCapture {

    private static final String LIBRARY = "librenderdoc.so";
    private static final int API_VERSION_1_6_0 = 10600;
    private static final int FUNCTION_COUNT = 27;
    private static final int SET_CAPTURE_FILE_PATH_TEMPLATE = 11;
    private static final int TRIGGER_CAPTURE = 15;
    private static final int GET_API_VERSION = 0;

    private final Arena arena;
    private final MemorySegment functionTable;
    private final Logger logger;

    private RenderDocCapture(Arena arena, MemorySegment functionTable, Logger logger) {
        this.arena = arena;
        this.functionTable = functionTable;
        this.logger = logger;
    }

    public static Optional<RenderDocCapture> attach(Logger logger) {
        try {
            return Optional.of(load(logger));
        } catch (RuntimeException unavailable) {
            logger.warn("RenderDoc is not attached to this process: " + unavailable.getMessage());
            return Optional.empty();
        }
    }

    private static RenderDocCapture load(Logger logger) {
        Arena arena = Arena.ofShared();
        SymbolLookup lookup = SymbolLookup.libraryLookup(LIBRARY, arena);
        MemorySegment entryPoint = lookup.find("RENDERDOC_GetAPI")
                .orElseThrow(() -> new EpysiaException("RENDERDOC_GetAPI is missing."));
        MemorySegment table = fetchApiTable(arena, entryPoint);
        RenderDocCapture capture = new RenderDocCapture(arena, table, logger);
        capture.verifyVersion();
        return capture;
    }

    private static MemorySegment fetchApiTable(Arena arena, MemorySegment entryPoint) {
        MethodHandle getApi = Linker.nativeLinker().downcallHandle(entryPoint,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        MemorySegment output = arena.allocate(ValueLayout.ADDRESS);
        int status = invokeInt(getApi, API_VERSION_1_6_0, output);
        if (status != 1) {
            throw new EpysiaException("RENDERDOC_GetAPI returned " + status);
        }
        return output.get(ValueLayout.ADDRESS, 0)
                .reinterpret((long) FUNCTION_COUNT * ValueLayout.ADDRESS.byteSize());
    }

    private static int invokeInt(MethodHandle handle, int version, MemorySegment output) {
        try {
            return (int) handle.invokeExact(version, output);
        } catch (Throwable failed) {
            throw new EpysiaException("Calling RENDERDOC_GetAPI failed.", failed);
        }
    }

    private void verifyVersion() {
        MethodHandle getVersion = downcall(GET_API_VERSION, FunctionDescriptor.ofVoid(
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        MemorySegment major = arena.allocate(ValueLayout.JAVA_INT);
        MemorySegment minor = arena.allocate(ValueLayout.JAVA_INT);
        MemorySegment patch = arena.allocate(ValueLayout.JAVA_INT);
        invokeVoid(getVersion, major, minor, patch);
        logger.info("RenderDoc in-application API " + major.get(ValueLayout.JAVA_INT, 0)
                + "." + minor.get(ValueLayout.JAVA_INT, 0)
                + "." + patch.get(ValueLayout.JAVA_INT, 0) + " attached.");
    }

    public void useFilePathTemplate(String template) {
        MethodHandle setTemplate = downcall(SET_CAPTURE_FILE_PATH_TEMPLATE,
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        invokeVoid(setTemplate, arena.allocateFrom(template));
    }

    public void triggerCapture() {
        MethodHandle trigger = downcall(TRIGGER_CAPTURE, FunctionDescriptor.ofVoid());
        try {
            trigger.invokeExact();
        } catch (Throwable failed) {
            throw new EpysiaException("RenderDoc TriggerCapture failed.", failed);
        }
        logger.info("RenderDoc capture triggered for the next presented frame.");
    }

    private MethodHandle downcall(int index, FunctionDescriptor descriptor) {
        MemorySegment function = functionTable.get(ValueLayout.ADDRESS,
                (long) index * ValueLayout.ADDRESS.byteSize());
        return Linker.nativeLinker().downcallHandle(function, descriptor);
    }

    private static void invokeVoid(MethodHandle handle, MemorySegment first) {
        try {
            handle.invokeExact(first);
        } catch (Throwable failed) {
            throw new EpysiaException("RenderDoc call failed.", failed);
        }
    }

    private static void invokeVoid(MethodHandle handle, MemorySegment first, MemorySegment second,
                                   MemorySegment third) {
        try {
            handle.invokeExact(first, second, third);
        } catch (Throwable failed) {
            throw new EpysiaException("RenderDoc call failed.", failed);
        }
    }
}
