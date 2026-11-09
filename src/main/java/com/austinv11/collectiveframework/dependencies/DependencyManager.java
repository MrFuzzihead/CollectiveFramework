package com.austinv11.collectiveframework.dependencies;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.austinv11.collectiveframework.dependencies.download.BinaryProvider;
import com.austinv11.collectiveframework.dependencies.download.FileType;
import com.austinv11.collectiveframework.dependencies.download.IDownloadProvider;
import com.austinv11.collectiveframework.dependencies.download.NoProviderFoundException;
import com.austinv11.collectiveframework.dependencies.download.PlainTextProvider;
import com.austinv11.collectiveframework.multithreading.SimpleRunnable;

/**
 * A class for managing dependencies
 */
public class DependencyManager {

    private static final Logger LOG = LogManager.getLogger("CollectiveFramework");

    private static final List<IDownloadProvider> providers = new CopyOnWriteArrayList<IDownloadProvider>();

    private static final AtomicInteger numOfDownloaders = new AtomicInteger();

    static {
        registerDownloadProvider(new PlainTextProvider());
        registerDownloadProvider(new BinaryProvider());
    }

    /**
     * Register a download provider, note: the earlier the registration, the higher priority it has
     *
     * @param provider The download provider
     */
    public static void registerDownloadProvider(IDownloadProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider must not be null");
        providers.add(provider);
    }

    /**
     * Removes every registered download provider. Intended for tests.
     */
    public static void clearDownloadProviders() {
        providers.clear();
    }

    /**
     * Does nothing.
     *
     * @param file The file representing the library
     * @throws MalformedURLException never thrown; declared for source compatibility
     * @deprecated This used to build a {@link URLClassLoader} and immediately discard it, which loaded nothing
     *             and leaked the loader. Adding a jar to a new class loader cannot make it visible to the loader
     *             that is already running the mod. Use
     *             {@link com.austinv11.collectiveframework.minecraft.utils.MinecraftDependencyManager#loadLibrary(File)}
     *             instead, which adds the jar to FML's {@code ModClassLoader}. Kept as a logging no-op so that
     *             existing callers do not start throwing (CODE_REVIEW §3.5).
     */
    @Deprecated
    public static void loadLibrary(File file) throws MalformedURLException {
        LOG.warn(
            "DependencyManager.loadLibrary({}) does nothing; use MinecraftDependencyManager.loadLibrary instead",
            file);
    }

    /**
     * Method to download a file of the given file type
     *
     * @param url            URL to download the file from
     * @param downloadPath   Path to download the file to
     * @param fileToDownload File type of the downlaod
     * @return Whether it succeeded
     * @throws NoProviderFoundException
     */
    public static boolean downloadFile(String url, String downloadPath, FileType fileToDownload)
        throws NoProviderFoundException {
        return findProvider(fileToDownload).downloadFile(url, downloadPath);
    }

    /**
     * Finds the highest-priority provider that can handle the given file type.
     * <p>
     * The old code set {@code provider} inside a doubly nested loop and only broke the inner one, so the
     * <em>last</em> matching provider won rather than the first (CODE_REVIEW §3.6).
     *
     * @param fileToDownload The file type to look for
     * @return The provider
     * @throws NoProviderFoundException if no registered provider handles the type
     */
    private static IDownloadProvider findProvider(FileType fileToDownload) throws NoProviderFoundException {
        if (fileToDownload == null) throw new IllegalArgumentException("fileToDownload must not be null");
        String wanted = fileToDownload.toString();
        for (IDownloadProvider provider : providers) {
            for (FileType capability : provider.getCapabilities()) {
                if (capability.toString()
                    .equals(wanted)) return provider;
            }
        }
        throw new NoProviderFoundException(fileToDownload);
    }

    /**
     * An asynchronous method to download a file of the given file type
     *
     * @param url            URL to download the file from
     * @param downloadPath   Path to download the file to
     * @param fileToDownload File type of the downlaod
     * @return Future object representing the future result
     * @throws NoProviderFoundException
     */
    public static Future<Boolean> downloadFileAsync(String url, String downloadPath, FileType fileToDownload)
        throws NoProviderFoundException {
        return new DownloadFuture(url, downloadPath, findProvider(fileToDownload));
    }

    protected static class DownloadFuture implements Future<Boolean> {

        public DownloadRunnable runnable;
        private volatile boolean isCancelled = false;

        protected DownloadFuture(String url, String downloadPath, IDownloadProvider provider) {
            runnable = new DownloadRunnable(url, downloadPath, provider);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (isCancelled) return true;
            isCancelled = true;
            // Idempotent: a second cancel() used to throw IllegalThreadStateException from disable().
            try {
                runnable.disable(true);
            } catch (IllegalThreadStateException alreadyCleaned) {
                // Nothing left to cancel.
            }
            // Release anyone blocked in get() rather than leaving them to wait for a download that will not
            // report its result.
            runnable.done.countDown();
            return true;
        }

        @Override
        public boolean isCancelled() {
            return isCancelled;
        }

        @Override
        public boolean isDone() {
            return runnable.done.getCount() == 0;
        }

        /**
         * Waits for the download to finish.
         * <p>
         * This used to return {@code runnable.didDownload} immediately, so a caller always saw {@code false}
         * unless it happened to poll after the download had completed (CODE_REVIEW §3.7).
         */
        @Override
        public Boolean get() throws InterruptedException {
            runnable.done.await();
            if (isCancelled) throw new CancellationException("download was cancelled");
            return runnable.didDownload;
        }

        @Override
        public Boolean get(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
            if (!runnable.done.await(timeout, unit)) {
                throw new TimeoutException("download did not finish within " + timeout + " " + unit);
            }
            if (isCancelled) throw new CancellationException("download was cancelled");
            return runnable.didDownload;
        }

        protected static class DownloadRunnable extends SimpleRunnable {

            public volatile boolean isDone = false;
            public volatile boolean didDownload = false;
            /** Released when {@link #run()} returns, whatever the outcome. */
            final CountDownLatch done = new CountDownLatch(1);

            private final String url, downloadPath;
            private final IDownloadProvider provider;
            private final int downloadNum;

            public DownloadRunnable(String url, String downloadPath, IDownloadProvider provider) {
                this.url = url;
                this.downloadPath = downloadPath;
                this.provider = provider;
                this.downloadNum = numOfDownloaders.incrementAndGet();
                // Fire and forget: run() is invoked exactly once.
                startOnce();
            }

            @Override
            public void run() {
                try {
                    didDownload = provider.downloadFile(url, downloadPath);
                } finally {
                    isDone = true;
                    // Always release the waiter, even if the provider threw.
                    done.countDown();
                }
            }

            @Override
            public String getName() {
                // Used to read the static counter, so every downloader shared the last thread's name.
                return "CollectiveFramework Downloader #" + downloadNum;
            }
        }
    }
}
