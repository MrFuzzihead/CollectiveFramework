package com.austinv11.collectiveframework.multithreading;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.austinv11.collectiveframework.utils.ReflectionUtils;

/**
 * This class is used to simplify multithreading for performance by delegating multiple concurrent calculations
 * across a variable amount of threads.
 * <p>
 * The previous implementation hand-rolled a thread pool out of {@link SimpleRunnable}s and its own
 * {@link Future}. That {@code Future} busy-waited in {@code get()}, threw
 * {@code IllegalMonitorStateException} from {@code get(timeout, unit)}, never threw {@code TimeoutException}, and
 * hung forever if {@link #kill()} had been called. This version delegates to a
 * {@link java.util.concurrent.ExecutorService}, so the returned futures are real ones
 * (CODE_REVIEW §1.12).
 */
public class HeavyCalculations {

    private static final AtomicInteger THREAD_IDS = new AtomicInteger();

    private final ExecutorService executor;
    private final AtomicBoolean killed = new AtomicBoolean(false);

    /**
     * Constructor for HeavyCalculations
     *
     * @param numberOfThreads The number of threads to delegate calculations to
     * @throws IllegalArgumentException if {@code numberOfThreads} is less than 1
     */
    public HeavyCalculations(int numberOfThreads) {
        if (numberOfThreads < 1)
            throw new IllegalArgumentException("numberOfThreads must be at least 1, got " + numberOfThreads);
        this.executor = Executors.newFixedThreadPool(numberOfThreads, new ThreadFactory() {

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "Calculation Thread #" + THREAD_IDS.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    /**
     * Adds a calculation to the queues
     *
     * @param calculation The calculation it MUST implement {@link ICalculations}
     * @return A future representing the eventual calculation; {@link Future#get()} returns the object passed,
     *         although after the calculations finished
     * @throws RejectedExecutionException if {@link #kill()} has already been called
     */
    public <T> Future<T> addCalculation(T calculation) {
        ICalculations calculations = (ICalculations) calculation;
        return submit(calculations, calculation);
    }

    /**
     * Adds a calculation to the queues by wrapping an object to implement {@link ICalculations}
     * <b>This method is discouraged! It is meant for objects where it isn't possible to implement
     * {@link ICalculations}</b>
     *
     * @param calculation       The object representing the calculations
     * @param methodToCalculate The method to call for calculations
     * @param params            The parameters for the method
     * @return A future representing the eventual calculation; {@link Future#get()} returns the object passed,
     *         although after the calculations finished
     * @throws RejectedExecutionException if {@link #kill()} has already been called
     */
    public <T> Future<T> addCalculation(T calculation, String methodToCalculate, Object... params) {
        ICalculations calculations = new ICalculationsWrapper(calculation, methodToCalculate, params);
        return submit(calculations, calculation);
    }

    /**
     * Adds a calculation to the queues by wrapping a class to implement {@link ICalculations}, used for static
     * methods when the class can't be instantiated
     * <b>This method is discouraged! It is meant for objects where it isn't possible to implement
     * {@link ICalculations}</b>
     *
     * @param calculationClass  The class representing the calculations
     * @param methodToCalculate The method to call for calculations
     * @param params            The parameters for the method
     * @return A future representing the eventual calculation; {@link Future#get()} returns the wrapper that ran
     *         the method
     * @throws RejectedExecutionException if {@link #kill()} has already been called
     */
    public Future<ICalculations> addCalculation(Class calculationClass, String methodToCalculate, Object... params) {
        ICalculations calculations = new ICalculationsWrapper(calculationClass, methodToCalculate, params);
        return submit(calculations, calculations);
    }

    private <T> Future<T> submit(final ICalculations calculations, final T result) {
        if (killed.get()) throw new RejectedExecutionException("HeavyCalculations has been killed");
        return executor.submit(new Callable<T>() {

            @Override
            public T call() {
                calculations.doCalculation();
                return result;
            }
        });
    }

    /**
     * Stops further calculations from occurring
     * <b>You can no longer add calculations after this!</b>
     */
    public void kill() {
        killed.set(true);
        executor.shutdown();
    }

    /**
     * Use this to check if you could add calculations
     *
     * @return True if this is dead
     */
    public boolean isDead() {
        return killed.get();
    }

    /**
     * Waits for every queued calculation to finish.
     *
     * @param timeout The maximum time to wait
     * @param unit    The unit of {@code timeout}
     * @return True if the executor terminated within the timeout
     * @throws InterruptedException if the calling thread is interrupted
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }

    private static class ICalculationsWrapper implements ICalculations {

        private final Object object;
        private final String methodName;
        private final Object[] params;

        ICalculationsWrapper(Object object, String methodName, Object[] params) {
            if (object == null) throw new IllegalArgumentException("object must not be null");
            if (methodName == null) throw new IllegalArgumentException("methodName must not be null");
            this.object = object;
            this.methodName = methodName;
            this.params = params == null ? new Object[0] : params;
        }

        @Override
        public void doCalculation() {
            boolean isStatic = object instanceof Class;
            Class<?> owner = isStatic ? (Class<?>) object : object.getClass();
            Method method = ReflectionUtils.getDeclaredOrNormalMethod(methodName, owner);
            if (method == null) {
                throw new IllegalStateException("No method named '" + methodName + "' on " + owner.getName());
            }
            try {
                method.setAccessible(true);
                method.invoke(isStatic ? null : object, params);
            } catch (ReflectiveOperationException e) {
                // Previously swallowed with printStackTrace, which silently lost every failure.
                throw new IllegalStateException("Unable to invoke " + owner.getName() + "#" + methodName, e);
            }
        }
    }
}
