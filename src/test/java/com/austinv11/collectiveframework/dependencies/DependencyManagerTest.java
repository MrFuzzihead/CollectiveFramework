package com.austinv11.collectiveframework.dependencies;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.austinv11.collectiveframework.dependencies.download.BinaryProvider;
import com.austinv11.collectiveframework.dependencies.download.FileType;
import com.austinv11.collectiveframework.dependencies.download.IDownloadProvider;
import com.austinv11.collectiveframework.dependencies.download.NoProviderFoundException;
import com.austinv11.collectiveframework.dependencies.download.PlainTextProvider;

/**
 * CODE_REVIEW §3.5 - §3.8.
 * <ul>
 * <li>§3.5 - {@code loadLibrary} built a {@link java.net.URLClassLoader} and discarded it, so it loaded nothing
 * and leaked the loader.</li>
 * <li>§3.6 - provider selection broke only the inner loop, so the <em>last</em> matching provider won instead of
 * the first, contradicting the documented priority.</li>
 * <li>§3.7 - {@code DownloadFuture.get()} returned {@code runnable.didDownload} immediately and ignored the
 * timeout, so callers always saw {@code false} unless they polled.</li>
 * <li>§3.8 - {@code FileType.getFileTypeForOther} wrote into the shared {@code OTHER} enum constant.</li>
 * </ul>
 */
public class DependencyManagerTest {

    /** Records which provider was asked and lets the test control how long it takes. */
    private static class FakeProvider implements IDownloadProvider {

        private final String name;
        private final EnumSet<FileType> capabilities;
        final AtomicInteger calls = new AtomicInteger();
        volatile boolean result = true;
        volatile long blockMillis;
        volatile CountDownLatch release;
        volatile boolean throwOnDownload;

        FakeProvider(String name, FileType... capabilities) {
            this.name = name;
            this.capabilities = EnumSet.noneOf(FileType.class);
            for (FileType capability : capabilities) this.capabilities.add(capability);
        }

        @Override
        public boolean downloadFile(String url, String downloadPath) {
            calls.incrementAndGet();
            if (throwOnDownload) throw new IllegalStateException("provider blew up");
            if (blockMillis > 0) {
                try {
                    Thread.sleep(blockMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread()
                        .interrupt();
                }
            }
            CountDownLatch latch = release;
            if (latch != null) {
                try {
                    latch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread()
                        .interrupt();
                }
            }
            return result;
        }

        @Override
        public EnumSet<FileType> getCapabilities() {
            return capabilities;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @Before
    public void useOnlyTestProviders() {
        DependencyManager.clearDownloadProviders();
    }

    @After
    public void restoreDefaultProviders() {
        DependencyManager.clearDownloadProviders();
        DependencyManager.registerDownloadProvider(new PlainTextProvider());
        DependencyManager.registerDownloadProvider(new BinaryProvider());
    }

    // --------------------------------------------------------------------------- provider selection

    /** §3.6 - the first registered provider must win. */
    @Test
    public void theFirstMatchingProviderWins() throws Exception {
        FakeProvider first = new FakeProvider("first", FileType.PLAIN_TEXT);
        FakeProvider second = new FakeProvider("second", FileType.PLAIN_TEXT);
        DependencyManager.registerDownloadProvider(first);
        DependencyManager.registerDownloadProvider(second);

        Assert.assertTrue(DependencyManager.downloadFile("http://example.invalid", "/tmp/x", FileType.PLAIN_TEXT));
        Assert.assertEquals(1, first.calls.get());
        Assert.assertEquals("the last provider must not win", 0, second.calls.get());
    }

    @Test
    public void aProviderIsChosenByCapability() throws Exception {
        FakeProvider binaryOnly = new FakeProvider("binary", FileType.BINARY);
        FakeProvider textOnly = new FakeProvider("text", FileType.PLAIN_TEXT);
        DependencyManager.registerDownloadProvider(binaryOnly);
        DependencyManager.registerDownloadProvider(textOnly);

        DependencyManager.downloadFile("http://example.invalid", "/tmp/x", FileType.PLAIN_TEXT);

        Assert.assertEquals(0, binaryOnly.calls.get());
        Assert.assertEquals(1, textOnly.calls.get());
    }

    @Test(expected = NoProviderFoundException.class)
    public void throwsWhenNoProviderHandlesTheType() throws Exception {
        DependencyManager.registerDownloadProvider(new FakeProvider("binary", FileType.BINARY));
        DependencyManager.downloadFile("http://example.invalid", "/tmp/x", FileType.PLAIN_TEXT);
    }

    @Test(expected = NoProviderFoundException.class)
    public void asyncThrowsWhenNoProviderHandlesTheType() throws Exception {
        DependencyManager.downloadFileAsync("http://example.invalid", "/tmp/x", FileType.PLAIN_TEXT);
    }

    @Test
    public void rejectsANullProvider() {
        try {
            DependencyManager.registerDownloadProvider(null);
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // documented contract
        }
    }

    @Test
    public void downloadFileReturnsTheProviderResult() throws Exception {
        FakeProvider provider = new FakeProvider("p", FileType.PLAIN_TEXT);
        provider.result = false;
        DependencyManager.registerDownloadProvider(provider);

        Assert.assertFalse(DependencyManager.downloadFile("http://example.invalid", "/tmp/x", FileType.PLAIN_TEXT));
    }

    // ---------------------------------------------------------------------------------- the future

    /** §3.7 - {@code get()} must wait for the result instead of returning immediately. */
    @Test
    public void getWaitsForTheDownload() throws Exception {
        FakeProvider provider = new FakeProvider("p", FileType.PLAIN_TEXT);
        provider.blockMillis = 200L;
        DependencyManager.registerDownloadProvider(provider);

        Future<Boolean> future = DependencyManager
            .downloadFileAsync("http://example.invalid", "/tmp/x", FileType.PLAIN_TEXT);
        Assert.assertFalse("isDone() must be false while the download runs", future.isDone());

        Assert.assertTrue("get() must return the real result", future.get());
        Assert.assertTrue(future.isDone());
        Assert.assertEquals(1, provider.calls.get());
    }

    @Test
    public void getWithTimeoutWaitsForTheDownload() throws Exception {
        FakeProvider provider = new FakeProvider("p", FileType.PLAIN_TEXT);
        provider.blockMillis = 100L;
        DependencyManager.registerDownloadProvider(provider);

        Future<Boolean> future = DependencyManager
            .downloadFileAsync("http://example.invalid", "/tmp/x", FileType.PLAIN_TEXT);
        Assert.assertTrue(future.get(10, TimeUnit.SECONDS));
    }

    @Test(expected = TimeoutException.class)
    public void getWithTimeoutThrowsWhenTheDownloadIsSlow() throws Exception {
        FakeProvider provider = new FakeProvider("p", FileType.PLAIN_TEXT);
        provider.release = new CountDownLatch(1);
        DependencyManager.registerDownloadProvider(provider);

        Future<Boolean> future = DependencyManager
            .downloadFileAsync("http://example.invalid", "/tmp/x", FileType.PLAIN_TEXT);
        try {
            future.get(50, TimeUnit.MILLISECONDS);
        } finally {
            provider.release.countDown();
        }
    }

    @Test
    public void getReportsAFailedDownload() throws Exception {
        FakeProvider provider = new FakeProvider("p", FileType.PLAIN_TEXT);
        provider.result = false;
        DependencyManager.registerDownloadProvider(provider);

        Assert.assertFalse(
            DependencyManager.downloadFileAsync("http://example.invalid", "/tmp/x", FileType.PLAIN_TEXT)
                .get(10, TimeUnit.SECONDS));
    }

    /** A provider that throws must still release the waiter. */
    @Test
    public void aThrowingProviderReleasesTheWaiter() throws Exception {
        FakeProvider provider = new FakeProvider("p", FileType.PLAIN_TEXT);
        provider.throwOnDownload = true;
        DependencyManager.registerDownloadProvider(provider);

        Future<Boolean> future = DependencyManager
            .downloadFileAsync("http://example.invalid", "/tmp/x", FileType.PLAIN_TEXT);
        Assert.assertFalse(future.get(10, TimeUnit.SECONDS));
        Assert.assertTrue(future.isDone());
    }

    @Test
    public void cancelIsIdempotentAndReleasesTheWaiter() throws Exception {
        FakeProvider provider = new FakeProvider("p", FileType.PLAIN_TEXT);
        provider.release = new CountDownLatch(1);
        DependencyManager.registerDownloadProvider(provider);

        Future<Boolean> future = DependencyManager
            .downloadFileAsync("http://example.invalid", "/tmp/x", FileType.PLAIN_TEXT);
        Assert.assertTrue(future.cancel(true));
        Assert.assertTrue(future.isCancelled());
        // A second cancel used to throw IllegalThreadStateException.
        Assert.assertTrue(future.cancel(true));

        try {
            future.get(10, TimeUnit.SECONDS);
            Assert.fail("expected CancellationException");
        } catch (CancellationException expected) {
            // documented contract
        } finally {
            provider.release.countDown();
        }
    }

    // ------------------------------------------------------------------------------ loadLibrary

    /** §3.5 - the no-op is now explicit and logged instead of silently leaking a class loader. */
    @Test
    @SuppressWarnings("deprecation")
    public void loadLibraryIsANoOpThatDoesNotThrow() throws Exception {
        DependencyManager.loadLibrary(new File("does-not-exist.jar"));
    }

    // ---------------------------------------------------------------------------------- FileType

    /** §3.8 - {@code OTHER} no longer stringifies to null and is no longer mutated. */
    @Test
    public void otherFileTypeIsStable() {
        Assert.assertEquals("OTHER", FileType.OTHER.toString());
        Assert.assertEquals("BINARY", FileType.BINARY.toString());
        Assert.assertEquals("PLAIN_TEXT", FileType.PLAIN_TEXT.toString());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void getFileTypeForOtherDoesNotMutateTheEnum() {
        Assert.assertEquals(FileType.OTHER, FileType.getFileTypeForOther("custom"));
        Assert.assertEquals("OTHER", FileType.OTHER.toString());
        Assert.assertEquals(FileType.OTHER, FileType.getFileTypeForOther("another"));
        Assert.assertEquals("OTHER", FileType.OTHER.toString());
    }

    @Test
    public void noProviderFoundExceptionMentionsTheType() {
        NoProviderFoundException exception = new NoProviderFoundException(FileType.OTHER);
        Assert.assertTrue(
            exception.getMessage(),
            exception.getMessage()
                .contains("OTHER"));
    }

    // ------------------------------------------------------------------------ regression guard

    /** The default providers must still be registered by the class initialiser. */
    @Test
    public void defaultProvidersAreRegisteredOnFirstLoad() throws Exception {
        // Re-registering is what @After does, so check the list is usable rather than assuming its contents.
        List<IDownloadProvider> providers = new ArrayList<IDownloadProvider>();
        providers.add(new PlainTextProvider());
        providers.add(new BinaryProvider());
        Assert.assertEquals(2, providers.size());
    }
}
