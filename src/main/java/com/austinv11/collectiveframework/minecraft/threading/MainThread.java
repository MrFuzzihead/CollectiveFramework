package com.austinv11.collectiveframework.minecraft.threading;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

/**
 * Runs work on the Minecraft main thread.
 * <p>
 * Network message handlers in 1.7.10 are invoked on the netty event loop, and touching the world, a chunk or a
 * tile entity from there is unsafe (chunk loading in particular is not thread-safe). 1.8 added
 * {@code IThreadListener} and {@code MinecraftServer} gained a task queue; 1.7.10 has neither - only
 * {@code Minecraft#func_152344_a} exists, on the client - so the framework keeps its own queues and drains them
 * from {@link MainThreadTickHandler} on the end of each tick (CODE_REVIEW §3.1).
 */
public final class MainThread {

    /**
     * Upper bound on the number of tasks drained in a single tick. A task that re-schedules itself would
     * otherwise stall the game; anything left over is picked up next tick.
     */
    public static final int MAX_TASKS_PER_DRAIN = 1000;

    private static final Logger LOG = LogManager.getLogger("CollectiveFramework");

    private static final Queue<Runnable> SERVER_TASKS = new ConcurrentLinkedQueue<Runnable>();
    private static final Queue<Runnable> CLIENT_TASKS = new ConcurrentLinkedQueue<Runnable>();

    private MainThread() {}

    /**
     * Schedules a task for the main thread of whichever side this is.
     *
     * @param task The task to run
     */
    public static void schedule(Runnable task) {
        if (isClientSide()) scheduleOnClient(task);
        else scheduleOnServer(task);
    }

    /**
     * Schedules a task for the server thread. If called from the server thread it still runs on the next tick
     * rather than immediately, so ordering is predictable.
     *
     * @param task The task to run
     */
    public static void scheduleOnServer(Runnable task) {
        if (task == null) throw new IllegalArgumentException("task must not be null");
        SERVER_TASKS.add(task);
    }

    /**
     * Schedules a task for the client thread.
     *
     * @param task The task to run
     */
    public static void scheduleOnClient(Runnable task) {
        if (task == null) throw new IllegalArgumentException("task must not be null");
        CLIENT_TASKS.add(task);
    }

    /**
     * @return The number of tasks waiting to run on the server thread
     */
    public static int pendingServerCount() {
        return SERVER_TASKS.size();
    }

    /**
     * @return The number of tasks waiting to run on the client thread
     */
    public static int pendingClientCount() {
        return CLIENT_TASKS.size();
    }

    /**
     * Discards every pending task. Intended for tests and for world shutdown.
     */
    public static void clear() {
        SERVER_TASKS.clear();
        CLIENT_TASKS.clear();
    }

    /** Runs the queued server tasks. Called by {@link MainThreadTickHandler} on the server thread. */
    static void drainServer() {
        drain(SERVER_TASKS, "server");
    }

    /** Runs the queued client tasks. Called by {@link MainThreadTickHandler} on the client thread. */
    static void drainClient() {
        drain(CLIENT_TASKS, "client");
    }

    private static void drain(Queue<Runnable> queue, String side) {
        for (int i = 0; i < MAX_TASKS_PER_DRAIN; i++) {
            Runnable task = queue.poll();
            if (task == null) return;
            try {
                task.run();
            } catch (Throwable t) {
                // One failing task must not stop the rest of the queue.
                LOG.error("Main thread task threw on the " + side, t);
            }
        }
        if (!queue.isEmpty()) {
            LOG.warn(
                "Main thread " + side
                    + " queue still has "
                    + queue.size()
                    + " tasks after draining "
                    + MAX_TASKS_PER_DRAIN
                    + "; the rest will run next tick");
        }
    }

    private static boolean isClientSide() {
        try {
            return FMLCommonHandler.instance()
                .getSide() == Side.CLIENT;
        } catch (Throwable t) {
            // Outside FML (unit tests, very early class loading): treat as a server.
            return false;
        }
    }
}
