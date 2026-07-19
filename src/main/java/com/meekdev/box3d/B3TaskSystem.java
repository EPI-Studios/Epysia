package com.meekdev.box3d;

import com.meekdev.box3d.ffi.b3EnqueueTaskCallback;
import com.meekdev.box3d.ffi.b3FinishTaskCallback;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

// drives box3d's fork join tasks on a java thread pool
// enqueue hands back a fake pointer used as a ticket, finish blocks on it
final class B3TaskSystem implements AutoCloseable {

    private static final FunctionDescriptor TASK_DESC =
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS);

    private final ExecutorService pool;
    private final Map<Long, Future<?>> inFlight = new ConcurrentHashMap<>();
    private final AtomicLong tickets = new AtomicLong(1);
    final MemorySegment enqueueStub;
    final MemorySegment finishStub;

    B3TaskSystem(Arena arena, int workers) {
        this.pool = Executors.newFixedThreadPool(workers, runnable -> {
            Thread thread = new Thread(runnable, "box3d-worker");
            thread.setDaemon(true);
            return thread;
        });
        Linker linker = Linker.nativeLinker();

        this.enqueueStub = b3EnqueueTaskCallback.allocate((task, taskContext, userContext, name) -> {
            long ticket = tickets.getAndIncrement();
            MethodHandle handle = linker.downcallHandle(task, TASK_DESC);
            inFlight.put(ticket, pool.submit(() -> {
                try {
                    handle.invokeExact(taskContext);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }));
            return MemorySegment.ofAddress(ticket);
        }, arena);

        this.finishStub = b3FinishTaskCallback.allocate((userTask, userContext) -> {
            Future<?> future = inFlight.remove(userTask.address());
            if (future != null) {
                try {
                    future.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }, arena);
    }

    @Override
    public void close() {
        pool.shutdownNow();
    }
}
